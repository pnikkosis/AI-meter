package com.kmnexus.codexmeter.providers.codex.auth

import com.kmnexus.codexmeter.data.secure.ProviderSessionEnvelope
import com.kmnexus.codexmeter.domain.model.LocalAccountId
import com.kmnexus.codexmeter.domain.model.ProviderAccount
import com.kmnexus.codexmeter.domain.model.ProviderAccountId
import com.kmnexus.codexmeter.domain.model.ProviderId
import com.kmnexus.codexmeter.domain.model.SnapshotId
import com.kmnexus.codexmeter.domain.quota.QuotaSnapshot
import com.kmnexus.codexmeter.domain.quota.QuotaSnapshotSource
import com.kmnexus.codexmeter.domain.refresh.QuotaError
import com.kmnexus.codexmeter.providers.codex.auth.CodexBrowserConnector.ConnectResult
import com.kmnexus.codexmeter.providers.codex.auth.CodexBrowserConnector.ConnectStage
import com.kmnexus.codexmeter.providers.codex.session.CodexSessionPayload
import com.kmnexus.codexmeter.providers.common.auth.PkceSession
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CodexBrowserConnectorTest {
    private val pkce = PkceSession.create()
    private val redirectUri = "http://127.0.0.1:1455/auth/callback"
    private val now = Instant.parse("2026-10-17T15:00:00Z")
    private val session = CodexSessionPayload(
        accessToken = "access",
        refreshToken = "refresh",
        idToken = "id",
        accountId = "chatgpt-account-1",
        accountEmail = "person@example.com",
        lastRefresh = now,
    )

    private val exchanged = mutableListOf<CodexOAuthTokenExchangeRequest>()
    private val prepared = mutableListOf<CodexSessionPayload>()
    private val committed = mutableListOf<CodexSessionImporter.PreparedImport>()

    @Test
    fun `the code is exchanged with the browser redirect and the validated session is saved`() = runTest {
        val connector = connector()

        val result = connector.connect("auth-code", pkce, redirectUri, expectedProviderAccountId = null)

        assertTrue(result is ConnectResult.Connected)
        assertEquals("person@example.com", (result as ConnectResult.Connected).account.displayName)
        val request = exchanged.single()
        assertEquals("auth-code", request.authorizationCode.value)
        assertEquals(pkce.codeVerifier, request.pkceVerifier.value)
        assertEquals(redirectUri, request.redirectUri)
        assertEquals(listOf(session), prepared)
        assertEquals(1, committed.size)
    }

    @Test
    fun `a failed exchange stops before validating or saving`() = runTest {
        val connector = connector(exchange = { CodexOAuthTokenExchanger.Result.Failure(QuotaError.Network("d")) })

        val result = connector.connect("auth-code", pkce, redirectUri, expectedProviderAccountId = null)

        assertEquals(ConnectStage.Exchange, (result as ConnectResult.Failed).stage)
        assertTrue(result.error is QuotaError.Network)
        assertTrue(prepared.isEmpty())
        assertTrue(committed.isEmpty())
    }

    @Test
    fun `a session the usage endpoint refuses is never saved`() = runTest {
        val refusal = QuotaError.AuthRequired(httpStatus = 403, diagnosticsDigest = "d")
        val connector = connector(prepare = { CodexSessionImporter.PrepareResult.Failure(refusal.safeMessageKey, refusal) })

        val result = connector.connect("auth-code", pkce, redirectUri, expectedProviderAccountId = null)

        assertEquals(ConnectStage.Validate, (result as ConnectResult.Failed).stage)
        assertEquals(refusal, result.error)
        assertTrue(committed.isEmpty())
    }

    @Test
    fun `a storage failure is reported without an error from the provider`() = runTest {
        val connector = connector(commit = { CodexSessionImporter.Result.Failure("error_session_persistence", null) })

        val result = connector.connect("auth-code", pkce, redirectUri, expectedProviderAccountId = null)

        assertEquals(ConnectStage.Persist, (result as ConnectResult.Failed).stage)
        assertNull(result.error)
    }

    @Test
    fun `a re-login into another account changes nothing`() = runTest {
        val connector = connector()

        val result = connector.connect("auth-code", pkce, redirectUri, ProviderAccountId("chatgpt-account-2"))

        assertEquals("person@example.com", (result as ConnectResult.AccountMismatch).signedInEmail)
        assertTrue(prepared.isEmpty())
        assertTrue(committed.isEmpty())
    }

    @Test
    fun `a re-login into the same account is saved`() = runTest {
        val connector = connector()

        val result = connector.connect("auth-code", pkce, redirectUri, ProviderAccountId("chatgpt-account-1"))

        assertTrue(result is ConnectResult.Connected)
        assertEquals(1, committed.size)
    }

    private fun connector(
        exchange: suspend (CodexOAuthTokenExchangeRequest) -> CodexOAuthTokenExchanger.Result =
            { CodexOAuthTokenExchanger.Result.Success(session) },
        prepare: suspend (CodexSessionPayload) -> CodexSessionImporter.PrepareResult =
            { CodexSessionImporter.PrepareResult.Success(preparedImport()) },
        commit: suspend (CodexSessionImporter.PreparedImport) -> CodexSessionImporter.Result =
            { prepared -> committedImport(prepared) },
    ) = CodexBrowserConnector(
        exchange = { request -> exchanged += request; exchange(request) },
        prepare = { payload -> prepared += payload; prepare(payload) },
        commit = { import -> committed += import; commit(import) },
    )

    private fun preparedImport(): CodexSessionImporter.PreparedImport {
        val localAccountId = LocalAccountId("codex-1")
        val providerAccountId = ProviderAccountId("chatgpt-account-1")
        return CodexSessionImporter.PreparedImport(
            account = ProviderAccount.createNew(
                localAccountId = localAccountId,
                providerId = ProviderId("codex"),
                providerAccountId = providerAccountId,
                displayName = "person@example.com",
                now = now,
            ),
            displayNameSuggestion = "person@example.com",
            sessionEnvelope = ProviderSessionEnvelope(
                providerId = "codex",
                localAccountId = localAccountId.value,
                providerAccountId = providerAccountId.value,
                schemaVersion = 1,
                payloadCiphertext = byteArrayOf(1),
                payloadNonce = byteArrayOf(2),
                createdAt = now.toString(),
                updatedAt = now.toString(),
            ),
            snapshot = QuotaSnapshot(
                snapshotId = SnapshotId("s-1"),
                providerId = ProviderId("codex"),
                localAccountId = localAccountId,
                providerAccountId = providerAccountId,
                fetchedAt = now,
                source = QuotaSnapshotSource.OAuthPkceLogin,
                planType = null,
                windows = emptyList(),
                credits = null,
                responseDigest = null,
            ),
        )
    }

    private fun committedImport(prepared: CodexSessionImporter.PreparedImport) =
        CodexSessionImporter.Result.Success(
            account = prepared.account,
            displayNameSuggestion = prepared.displayNameSuggestion,
            sessionEnvelopeReference = CodexSessionImporter.SessionEnvelopeReference(
                providerId = prepared.sessionEnvelope.providerId,
                localAccountId = prepared.sessionEnvelope.localAccountId,
                providerAccountId = prepared.sessionEnvelope.providerAccountId,
            ),
            snapshot = prepared.snapshot,
        )
}
