package com.kmnexus.codexmeter.providers.claude.auth

import com.kmnexus.codexmeter.data.secure.PayloadCipher
import com.kmnexus.codexmeter.data.secure.ProviderSessionEnvelope
import com.kmnexus.codexmeter.domain.model.LocalAccountId
import com.kmnexus.codexmeter.domain.model.ProviderAccount
import com.kmnexus.codexmeter.domain.model.ProviderAccountId
import com.kmnexus.codexmeter.domain.model.ProviderId
import com.kmnexus.codexmeter.domain.quota.QuotaSnapshotSource
import com.kmnexus.codexmeter.domain.refresh.QuotaError
import com.kmnexus.codexmeter.providers.claude.mapper.ClaudeUsageMapper
import com.kmnexus.codexmeter.providers.claude.network.ClaudeUsageClient
import com.kmnexus.codexmeter.providers.claude.session.ClaudeSessionPayload
import com.kmnexus.codexmeter.providers.codex.auth.CodexSessionImporter
import com.kmnexus.codexmeter.providers.common.auth.PkceSession
import java.time.Clock
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json

/** Exchanges and stores a Claude authorization code; see [ClaudeAccountConnector]. */
fun interface ClaudeConnect {
    suspend fun connect(
        code: String,
        pkce: PkceSession,
        redirectUri: String,
        expectedProviderAccountId: ProviderAccountId?,
    ): ClaudeAccountConnector.ConnectResult
}

/**
 * Turns an authorization code into a stored Claude account.
 *
 * Order matters: the code is exchanged, the new session is validated against the usage endpoint,
 * and only a session that produced real usage data is encrypted and committed (account row, first
 * snapshot and session envelope together, rolled back on failure by [persistence]). Nothing is
 * written when any step fails.
 */
class ClaudeAccountConnector(
    private val tokenClient: ClaudeTokenClient,
    private val usageClient: ClaudeUsageClient,
    private val payloadCipher: PayloadCipher,
    private val persistence: CodexSessionImporter.ImportPersistence,
    private val defaultDisplayName: String = "Claude",
    private val newLocalAccountId: () -> LocalAccountId = { LocalAccountId("claude-${UUID.randomUUID()}") },
    private val clock: Clock = Clock.systemUTC(),
) : ClaudeConnect {
    /**
     * @param expectedProviderAccountId the Claude account uuid when re-authenticating an existing
     *   account; signing in to a different account then returns [ConnectResult.AccountMismatch].
     */
    override suspend fun connect(
        code: String,
        pkce: PkceSession,
        redirectUri: String,
        expectedProviderAccountId: ProviderAccountId?,
    ): ConnectResult {
        val tokens = when (val exchange = tokenClient.exchangeCode(code, pkce.codeVerifier, redirectUri, pkce.state)) {
            is ClaudeTokenClient.Result.Failure -> return ConnectResult.Failed(exchange.error, ConnectStage.Exchange)
            is ClaudeTokenClient.Result.Success -> exchange.tokens
        }
        if (expectedProviderAccountId != null && tokens.accountUuid != null &&
            tokens.accountUuid != expectedProviderAccountId.value
        ) {
            return ConnectResult.AccountMismatch(signedInEmail = tokens.accountEmail)
        }

        val dto = when (val usage = usageClient.fetchUsage(tokens.accessToken)) {
            is ClaudeUsageClient.Result.Failure -> return ConnectResult.Failed(usage.error, ConnectStage.Validate)
            is ClaudeUsageClient.Result.Success -> usage.dto
        }

        val now = clock.instant()
        val localAccountId = newLocalAccountId()
        // Without an account uuid in the answer a re-login is trusted to be the same account, as before.
        val providerAccountId = (tokens.accountUuid ?: expectedProviderAccountId?.value)?.let(::ProviderAccountId)
        val snapshot = ClaudeUsageMapper.map(
            dto = dto,
            localAccountId = localAccountId,
            providerAccountId = providerAccountId,
            fetchedAt = now,
            source = QuotaSnapshotSource.OAuthPkceLogin,
        )
        if (snapshot.windows.isEmpty()) {
            return ConnectResult.Failed(
                QuotaError.ResponseInvalid(diagnosticsDigest = "claude_usage_no_limits"),
                ConnectStage.Validate,
            )
        }

        return try {
            val payload = ClaudeSessionPayload(
                accessToken = tokens.accessToken,
                refreshToken = tokens.refreshToken,
                tokenExpiresAtEpochSeconds = tokens.expiresInSeconds?.let { now.epochSecond + it },
                organizationId = tokens.organizationUuid,
                accountId = providerAccountId?.value,
                accountEmail = tokens.accountEmail,
                scope = tokens.scope,
            )
            val encrypted = payloadCipher.encrypt(
                json.encodeToString(ClaudeSessionPayload.serializer(), payload).encodeToByteArray(),
            )
            val account = ProviderAccount.createNew(
                localAccountId = localAccountId,
                providerId = CLAUDE_PROVIDER_ID,
                providerAccountId = providerAccountId,
                displayName = tokens.accountEmail ?: defaultDisplayName,
                now = now,
            )
            val committed = persistence.save(
                account = account,
                sessionEnvelope = ProviderSessionEnvelope(
                    providerId = CLAUDE_PROVIDER_ID.value,
                    localAccountId = localAccountId.value,
                    providerAccountId = providerAccountId?.value,
                    schemaVersion = SESSION_SCHEMA_VERSION,
                    payloadCiphertext = encrypted.ciphertext,
                    payloadNonce = encrypted.nonce,
                    createdAt = now.toString(),
                    updatedAt = now.toString(),
                ),
                snapshot = snapshot,
            )
            ConnectResult.Connected(committed.account)
        } catch (exception: CancellationException) {
            throw exception
        } catch (_: Exception) {
            ConnectResult.Failed(error = null, stage = ConnectStage.Persist)
        }
    }

    sealed interface ConnectResult {
        data class Connected(val account: ProviderAccount) : ConnectResult

        /** The browser session belongs to another Claude account than the one being re-authenticated. */
        data class AccountMismatch(val signedInEmail: String?) : ConnectResult

        /** [error] is null only for [ConnectStage.Persist] (a local storage failure). */
        data class Failed(val error: QuotaError?, val stage: ConnectStage) : ConnectResult
    }

    enum class ConnectStage { Exchange, Validate, Persist }

    private companion object {
        val CLAUDE_PROVIDER_ID = ProviderId("claude")
        const val SESSION_SCHEMA_VERSION = 2
        val json = Json { ignoreUnknownKeys = true }
    }
}
