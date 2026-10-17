package com.kmnexus.codexmeter.providers.claude.auth

import com.kmnexus.codexmeter.core.network.ProviderHttpClient
import com.kmnexus.codexmeter.data.secure.AesGcmPayloadCipher
import com.kmnexus.codexmeter.data.secure.ProviderSessionEnvelope
import com.kmnexus.codexmeter.domain.model.LocalAccountId
import com.kmnexus.codexmeter.domain.model.ProviderAccount
import com.kmnexus.codexmeter.domain.model.ProviderAccountId
import com.kmnexus.codexmeter.domain.quota.QuotaSnapshot
import com.kmnexus.codexmeter.domain.refresh.QuotaError
import com.kmnexus.codexmeter.providers.claude.auth.ClaudeAccountConnector.ConnectResult
import com.kmnexus.codexmeter.providers.claude.auth.ClaudeAccountConnector.ConnectStage
import com.kmnexus.codexmeter.providers.claude.network.ClaudeUsageClient
import com.kmnexus.codexmeter.providers.claude.session.ClaudeSessionPayload
import com.kmnexus.codexmeter.providers.codex.auth.CodexSessionImporter
import com.kmnexus.codexmeter.providers.common.auth.PkceSession
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import javax.crypto.KeyGenerator
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ClaudeAccountConnectorTest {
    private val server = MockWebServer()
    private val now = Instant.parse("2026-10-17T12:00:00Z")
    private val key = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
    private val cipher = AesGcmPayloadCipher { key }
    private val pkce = PkceSession.create()
    private val redirectUri = "http://localhost:43210/callback"
    private var tokenResponse = json(200, TOKENS)
    private var usageResponse = json(200, USAGE)

    @Before
    fun setUp() {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = when (request.url.encodedPath) {
                "/v1/oauth/token" -> tokenResponse
                "/api/oauth/usage" -> usageResponse
                else -> MockResponse.Builder().code(404).build()
            }
        }
        server.start()
    }

    @After
    fun tearDown() {
        server.close()
    }

    @Test
    fun `exchanges, validates and stores an encrypted session for the signed-in account`() = runTest {
        val persistence = RecordingPersistence()

        val result = connector(persistence).connect("code-1", pkce, redirectUri, expectedProviderAccountId = null)

        val account = (result as ConnectResult.Connected).account
        assertEquals("claude", account.providerId.value)
        assertEquals(ProviderAccountId("account-uuid-1"), account.providerAccountId)
        assertEquals("person@example.com", account.displayName)
        val saved = persistence.saved.single()
        assertTrue(saved.snapshot.windows.isNotEmpty())
        val raw = String(saved.envelope.payloadCiphertext, Charsets.ISO_8859_1)
        assertFalse(raw.contains("synthetic-access-token"))
        assertFalse(raw.contains("synthetic-refresh-token"))
        val payload = Json.decodeFromString(
            ClaudeSessionPayload.serializer(),
            cipher.decrypt(saved.envelope.payloadCiphertext, saved.envelope.payloadNonce).decodeToString(),
        )
        assertEquals("synthetic-access-token", payload.accessToken)
        assertEquals("synthetic-refresh-token", payload.refreshToken)
        assertEquals(now.epochSecond + 28800, payload.tokenExpiresAtEpochSeconds)
        assertEquals("account-uuid-1", payload.accountId)
        // The PKCE verifier and state are sent with the exchange, then dropped.
        val exchange = server.takeRequest().body?.utf8().orEmpty()
        assertTrue(exchange.contains(pkce.codeVerifier))
        assertTrue(exchange.contains(pkce.state))
    }

    @Test
    fun `nothing is stored when the code exchange fails`() = runTest {
        tokenResponse = json(400, """{"error":"invalid_grant"}""")
        val persistence = RecordingPersistence()

        val result = connector(persistence).connect("expired-code", pkce, redirectUri, null)

        assertTrue(result is ConnectResult.Failed)
        assertEquals(ConnectStage.Exchange, (result as ConnectResult.Failed).stage)
        assertTrue(result.error is QuotaError.AuthRequired)
        assertTrue(persistence.saved.isEmpty())
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `nothing is stored when usage cannot be read with the new session`() = runTest {
        usageResponse = json(403, """{"type":"error","error":{"type":"permission_error"}}""")
        val persistence = RecordingPersistence()

        val result = connector(persistence).connect("code-1", pkce, redirectUri, null) as ConnectResult.Failed

        assertEquals(ConnectStage.Validate, result.stage)
        assertTrue(result.error is QuotaError.AuthRequired)
        assertTrue(persistence.saved.isEmpty())
    }

    @Test
    fun `an answer without meters is refused instead of storing an empty account`() = runTest {
        usageResponse = json(200, """{"limits":[]}""")
        val persistence = RecordingPersistence()

        val result = connector(persistence).connect("code-1", pkce, redirectUri, null) as ConnectResult.Failed

        assertEquals(ConnectStage.Validate, result.stage)
        assertTrue(result.error is QuotaError.ResponseInvalid)
        assertTrue(persistence.saved.isEmpty())
    }

    @Test
    fun `re-login into a different account changes nothing`() = runTest {
        val persistence = RecordingPersistence()

        val result = connector(persistence).connect(
            code = "code-1",
            pkce = pkce,
            redirectUri = redirectUri,
            expectedProviderAccountId = ProviderAccountId("another-account"),
        )

        assertEquals(ConnectResult.AccountMismatch(signedInEmail = "person@example.com"), result)
        assertTrue(persistence.saved.isEmpty())
        assertEquals("usage must not be read for a mismatched account", 1, server.requestCount)
    }

    @Test
    fun `re-login into the same account is stored`() = runTest {
        val persistence = RecordingPersistence()

        val result = connector(persistence).connect("code-1", pkce, redirectUri, ProviderAccountId("account-uuid-1"))

        assertTrue(result is ConnectResult.Connected)
        assertEquals("account-uuid-1", persistence.saved.single().envelope.providerAccountId)
    }

    @Test
    fun `a storage failure is reported as such`() = runTest {
        val result = connector { _, _, _ -> error("disk full") }.connect("code-1", pkce, redirectUri, null)

        assertEquals(ConnectResult.Failed(error = null, stage = ConnectStage.Persist), result)
    }

    private fun connector(persistence: CodexSessionImporter.ImportPersistence) = ClaudeAccountConnector(
        tokenClient = ClaudeTokenClient(ProviderHttpClient(), tokenUrl = server.url("/v1/oauth/token").toString()),
        usageClient = ClaudeUsageClient(ProviderHttpClient(), url = server.url("/api/oauth/usage").toString()),
        payloadCipher = cipher,
        persistence = persistence,
        newLocalAccountId = { LocalAccountId("claude-new") },
        clock = Clock.fixed(now, ZoneOffset.UTC),
    )

    private class RecordingPersistence : CodexSessionImporter.ImportPersistence {
        val saved = mutableListOf<Saved>()

        override suspend fun save(
            account: ProviderAccount,
            sessionEnvelope: ProviderSessionEnvelope,
            snapshot: QuotaSnapshot,
        ): CodexSessionImporter.CommittedImport {
            saved += Saved(account, sessionEnvelope, snapshot)
            return CodexSessionImporter.CommittedImport(account, sessionEnvelope, snapshot)
        }
    }

    private data class Saved(val account: ProviderAccount, val envelope: ProviderSessionEnvelope, val snapshot: QuotaSnapshot)

    private fun json(code: Int, body: String) =
        MockResponse.Builder().code(code).addHeader("Content-Type", "application/json").body(body).build()

    private companion object {
        const val TOKENS = """{"access_token":"synthetic-access-token","refresh_token":"synthetic-refresh-token",
            "expires_in":28800,"scope":"user:profile",
            "account":{"uuid":"account-uuid-1","email_address":"person@example.com"},"organization":{"uuid":"org-1"}}"""
        const val USAGE = """{"limits":[{"kind":"session","percent":10,"resets_at":"2026-10-17T15:00:00Z"}]}"""
    }
}
