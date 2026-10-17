package com.kmnexus.codexmeter.providers.claude

import com.kmnexus.codexmeter.data.secure.PayloadCipher
import com.kmnexus.codexmeter.data.secure.ProviderSessionEnvelope
import com.kmnexus.codexmeter.data.secure.SecureSessionStore
import com.kmnexus.codexmeter.domain.model.ProviderAccount
import com.kmnexus.codexmeter.domain.model.ProviderId
import com.kmnexus.codexmeter.domain.quota.QuotaSnapshotSource
import com.kmnexus.codexmeter.domain.refresh.QuotaError
import com.kmnexus.codexmeter.domain.refresh.RefreshTrigger
import com.kmnexus.codexmeter.providers.claude.auth.ClaudeTokenClient
import com.kmnexus.codexmeter.providers.claude.mapper.ClaudeUsageMapper
import com.kmnexus.codexmeter.providers.claude.network.ClaudeUsageClient
import com.kmnexus.codexmeter.providers.claude.session.ClaudeSessionPayload
import com.kmnexus.codexmeter.refresh.ProviderRefreshResult
import com.kmnexus.codexmeter.refresh.RefreshProvider
import java.time.Clock
import java.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json

/**
 * Refreshes one Claude account: renews the access token when it is about to expire (or once after a
 * 401), persists the rotated tokens before using them, then reads the usage endpoint.
 */
class ClaudeRefreshProvider(
    private val usageClient: ClaudeUsageClient,
    private val tokenClient: ClaudeTokenClient,
    private val sessionStore: SecureSessionStore,
    private val payloadCipher: PayloadCipher,
    private val clock: Clock = Clock.systemUTC(),
) : RefreshProvider {
    override suspend fun refresh(
        account: ProviderAccount,
        trigger: RefreshTrigger,
    ): ProviderRefreshResult {
        if (account.providerId != CLAUDE_PROVIDER_ID) return authRequired("claude_refresh_wrong_provider")
        val envelope = sessionStore.load(account.providerId.value, account.localAccountId.value)
            ?: return authRequired("claude_session_missing")
        var session = decode(envelope) ?: return authRequired("claude_session_decode_failed")

        var alreadyRenewed = false
        if (session.accessTokenExpiresSoon(clock.instant())) {
            session = when (val renewal = renew(session, envelope)) {
                is Renewal.Failed -> return ProviderRefreshResult.Failure(renewal.error)
                is Renewal.Renewed -> renewal.session
            }
            alreadyRenewed = true
        }

        var usage = usageClient.fetchUsage(session.accessToken)
        val failure = (usage as? ClaudeUsageClient.Result.Failure)?.error
        if (failure is QuotaError.AuthRequired && failure.httpStatus == 401 && !alreadyRenewed) {
            session = when (val renewal = renew(session, envelope)) {
                // Report why the renewal failed: a network error here must not look like a dead login.
                is Renewal.Failed -> return ProviderRefreshResult.Failure(renewal.error)
                is Renewal.Renewed -> renewal.session
            }
            usage = usageClient.fetchUsage(session.accessToken)
        }

        return when (usage) {
            is ClaudeUsageClient.Result.Failure -> ProviderRefreshResult.Failure(usage.error)
            is ClaudeUsageClient.Result.Success -> {
                val snapshot = ClaudeUsageMapper.map(
                    dto = usage.dto,
                    localAccountId = account.localAccountId,
                    providerAccountId = account.providerAccountId,
                    fetchedAt = clock.instant(),
                    source = trigger.toSnapshotSource(),
                )
                if (snapshot.windows.isEmpty()) {
                    // Never store an empty snapshot: it would read as "no usage" instead of "unknown".
                    ProviderRefreshResult.Failure(QuotaError.ResponseInvalid(diagnosticsDigest = "claude_usage_no_limits"))
                } else {
                    ProviderRefreshResult.Success(snapshot)
                }
            }
        }
    }

    private suspend fun renew(session: ClaudeSessionPayload, envelope: ProviderSessionEnvelope): Renewal {
        val refreshToken = session.refreshToken
            ?: return Renewal.Failed(QuotaError.AuthRequired(httpStatus = null, diagnosticsDigest = "claude_refresh_token_missing"))
        val tokens = when (val result = tokenClient.refresh(refreshToken)) {
            is ClaudeTokenClient.Result.Failure -> return Renewal.Failed(result.error)
            is ClaudeTokenClient.Result.Success -> result.tokens
        }
        if (tokens.accountUuid != null && session.accountId != null && tokens.accountUuid != session.accountId) {
            return Renewal.Failed(QuotaError.AuthRequired(httpStatus = null, diagnosticsDigest = "claude_refresh_account_changed"))
        }
        val now = clock.instant()
        val renewed = session.copy(
            accessToken = tokens.accessToken,
            // The endpoint may omit refresh_token when it does not rotate it.
            refreshToken = tokens.refreshToken ?: session.refreshToken,
            tokenExpiresAtEpochSeconds = tokens.expiresInSeconds?.let { now.epochSecond + it },
            scope = tokens.scope ?: session.scope,
        )
        return try {
            persist(renewed, envelope, now)
            Renewal.Renewed(renewed)
        } catch (exception: CancellationException) {
            throw exception
        } catch (_: Exception) {
            Renewal.Failed(QuotaError.Network(diagnosticsDigest = "claude_refresh_session_save_failed"))
        }
    }

    private fun decode(envelope: ProviderSessionEnvelope): ClaudeSessionPayload? =
        try {
            json.decodeFromString<ClaudeSessionPayload>(
                payloadCipher.decrypt(envelope.payloadCiphertext, envelope.payloadNonce).decodeToString(),
            )
        } catch (exception: CancellationException) {
            throw exception
        } catch (_: Exception) {
            null
        }

    private suspend fun persist(session: ClaudeSessionPayload, previous: ProviderSessionEnvelope, now: Instant) {
        val encrypted = payloadCipher.encrypt(
            json.encodeToString(ClaudeSessionPayload.serializer(), session).encodeToByteArray(),
        )
        sessionStore.save(
            previous.copy(
                payloadCiphertext = encrypted.ciphertext,
                payloadNonce = encrypted.nonce,
                updatedAt = now.toString(),
            ),
        )
    }

    /** Unknown expiry (no `expires_in`) is left to the 401 path instead of renewing on every poll. */
    private fun ClaudeSessionPayload.accessTokenExpiresSoon(now: Instant): Boolean =
        tokenExpiresAtEpochSeconds?.let { it <= now.epochSecond + EXPIRY_SKEW_SECONDS } ?: false

    private fun authRequired(digest: String): ProviderRefreshResult.Failure =
        ProviderRefreshResult.Failure(QuotaError.AuthRequired(httpStatus = null, diagnosticsDigest = digest))

    private fun RefreshTrigger.toSnapshotSource(): QuotaSnapshotSource = when (this) {
        RefreshTrigger.AppOpen -> QuotaSnapshotSource.AppOpenRefresh
        RefreshTrigger.Manual -> QuotaSnapshotSource.ManualRefresh
        RefreshTrigger.Widget -> QuotaSnapshotSource.WidgetRefresh
        RefreshTrigger.ImportValidation -> QuotaSnapshotSource.OAuthPkceLogin
        RefreshTrigger.AccountSwitch -> QuotaSnapshotSource.ManualRefresh
        RefreshTrigger.Periodic -> QuotaSnapshotSource.BackgroundRefresh
    }

    private sealed interface Renewal {
        class Renewed(val session: ClaudeSessionPayload) : Renewal
        data class Failed(val error: QuotaError) : Renewal
    }

    companion object {
        val CLAUDE_PROVIDER_ID = ProviderId("claude")

        /** Renew five minutes early so a background refresh never races the expiry. */
        private const val EXPIRY_SKEW_SECONDS = 300L
        private val json = Json { ignoreUnknownKeys = true }
    }
}
