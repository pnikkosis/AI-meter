package com.kmnexus.codexmeter.providers.claude

import com.kmnexus.codexmeter.core.network.ProviderHttpClient
import com.kmnexus.codexmeter.data.secure.AesGcmPayloadCipher
import com.kmnexus.codexmeter.data.secure.FakeSecureSessionStore
import com.kmnexus.codexmeter.data.secure.ProviderSessionEnvelope
import com.kmnexus.codexmeter.domain.model.LocalAccountId
import com.kmnexus.codexmeter.domain.model.ProviderAccount
import com.kmnexus.codexmeter.domain.model.ProviderAccountId
import com.kmnexus.codexmeter.domain.model.ProviderId
import com.kmnexus.codexmeter.domain.refresh.QuotaError
import com.kmnexus.codexmeter.domain.refresh.RefreshTrigger
import com.kmnexus.codexmeter.providers.claude.auth.ClaudeTokenClient
import com.kmnexus.codexmeter.providers.claude.network.ClaudeUsageClient
import com.kmnexus.codexmeter.providers.claude.session.ClaudeSessionPayload
import com.kmnexus.codexmeter.refresh.ProviderRefreshResult
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

class ClaudeRefreshProviderTest {
    private val server = MockWebServer()
    private val now = Instant.parse("2026-10-17T12:00:00Z")
    private val key = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
    private val cipher = AesGcmPayloadCipher { key }
    private val store = FakeSecureSessionStore()
    private val account = ProviderAccount.createNew(
        localAccountId = LocalAccountId("claude-1"),
        providerId = ProviderId("claude"),
        providerAccountId = ProviderAccountId("account-uuid-1"),
        displayName = "person@example.com",
        now = now,
    )
    private val tokenResponses = ArrayDeque<MockResponse>()
    private val usageResponses = ArrayDeque<MockResponse>()

    @Before
    fun setUp() {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = when (request.url.encodedPath) {
                "/v1/oauth/token" -> tokenResponses.removeFirstOrNull()
                "/api/oauth/usage" -> usageResponses.removeFirstOrNull()
                else -> null
            } ?: MockResponse.Builder().code(404).build()
        }
        server.start()
    }

    @After
    fun tearDown() {
        server.close()
    }

    @Test
    fun `a valid token reads usage without renewing it`() = runTest {
        storeSession(accessToken = "access-1", refreshToken = "refresh-1", expiresAt = now.epochSecond + 3600)
        usageResponses += json(200, USAGE)

        val result = provider().refresh(account, RefreshTrigger.Manual)

        val snapshot = (result as ProviderRefreshResult.Success).snapshot
        assertEquals(listOf("claude_5h_window", "claude_7d_window"), snapshot.windows.map { it.windowId.value })
        assertEquals(1, server.requestCount)
        assertEquals("Bearer access-1", server.takeRequest().headers["Authorization"])
    }

    @Test
    fun `an expiring token is renewed and the rotated tokens are stored before use`() = runTest {
        storeSession(accessToken = "access-1", refreshToken = "refresh-1", expiresAt = now.epochSecond + 60)
        tokenResponses += json(200, """{"access_token":"access-2","refresh_token":"refresh-2","expires_in":28800}""")
        usageResponses += json(200, USAGE)

        val result = provider().refresh(account, RefreshTrigger.Periodic)

        assertTrue(result is ProviderRefreshResult.Success)
        assertEquals("/v1/oauth/token", server.takeRequest().url.encodedPath)
        assertEquals("Bearer access-2", server.takeRequest().headers["Authorization"])
        val stored = storedSession()
        assertEquals("access-2", stored.accessToken)
        assertEquals("refresh-2", stored.refreshToken)
        assertEquals(now.epochSecond + 28800, stored.tokenExpiresAtEpochSeconds)
    }

    @Test
    fun `a 401 triggers one renewal and a retry`() = runTest {
        storeSession(accessToken = "access-1", refreshToken = "refresh-1", expiresAt = null)
        usageResponses += json(401, """{"type":"error","error":{"type":"authentication_error"}}""")
        tokenResponses += json(200, """{"access_token":"access-2","expires_in":28800}""")
        usageResponses += json(200, USAGE)

        val result = provider().refresh(account, RefreshTrigger.Manual)

        assertTrue(result is ProviderRefreshResult.Success)
        assertEquals(3, server.requestCount)
        val stored = storedSession()
        assertEquals("access-2", stored.accessToken)
        // The endpoint did not rotate the refresh token, so the old one is kept.
        assertEquals("refresh-1", stored.refreshToken)
    }

    @Test
    fun `a rejected refresh token asks for a new sign-in and keeps the stored session`() = runTest {
        storeSession(accessToken = "access-1", refreshToken = "refresh-1", expiresAt = now.epochSecond - 10)
        tokenResponses += json(400, """{"error":"invalid_grant"}""")

        val result = provider().refresh(account, RefreshTrigger.Manual)

        val error = (result as ProviderRefreshResult.Failure).error
        assertTrue(error is QuotaError.AuthRequired)
        assertEquals("access-1", storedSession().accessToken)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `a network failure while renewing is not reported as a lost session`() = runTest {
        storeSession(accessToken = "access-1", refreshToken = "refresh-1", expiresAt = now.epochSecond - 10)
        val provider = provider()
        server.close()

        val error = (provider.refresh(account, RefreshTrigger.Manual) as ProviderRefreshResult.Failure).error

        assertTrue(error is QuotaError.Network)
    }

    @Test
    fun `usage without any meter is an invalid answer, not an empty snapshot`() = runTest {
        storeSession(accessToken = "access-1", refreshToken = "refresh-1", expiresAt = now.epochSecond + 3600)
        usageResponses += json(200, """{"limits":[]}""")

        val error = (provider().refresh(account, RefreshTrigger.Manual) as ProviderRefreshResult.Failure).error

        assertEquals(QuotaError.ResponseInvalid(diagnosticsDigest = "claude_usage_no_limits"), error)
    }

    @Test
    fun `rate limiting is passed through as its own error`() = runTest {
        storeSession(accessToken = "access-1", refreshToken = "refresh-1", expiresAt = now.epochSecond + 3600)
        usageResponses += MockResponse.Builder().code(429).addHeader("Retry-After", "60").body("{}").build()

        val error = (provider().refresh(account, RefreshTrigger.Manual) as ProviderRefreshResult.Failure).error

        assertEquals(QuotaError.RateLimited(diagnosticsDigest = "claude_usage_http_429", retryAfterSeconds = 60), error)
    }

    @Test
    fun `a missing or unreadable session requires signing in`() = runTest {
        val missing = (provider().refresh(account, RefreshTrigger.Manual) as ProviderRefreshResult.Failure).error
        store.save(envelope(ByteArray(16), ByteArray(12)))
        val unreadable = (provider().refresh(account, RefreshTrigger.Manual) as ProviderRefreshResult.Failure).error

        assertEquals(QuotaError.AuthRequired(httpStatus = null, diagnosticsDigest = "claude_session_missing"), missing)
        assertEquals(QuotaError.AuthRequired(httpStatus = null, diagnosticsDigest = "claude_session_decode_failed"), unreadable)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `stored session is encrypted`() = runTest {
        storeSession(accessToken = "access-plain-text", refreshToken = "refresh-plain-text", expiresAt = null)

        val stored = store.load("claude", "claude-1")!!
        val raw = String(stored.payloadCiphertext, Charsets.ISO_8859_1)
        assertFalse(raw.contains("access-plain-text"))
        assertFalse(raw.contains("refresh-plain-text"))
    }

    private fun provider() = ClaudeRefreshProvider(
        usageClient = ClaudeUsageClient(ProviderHttpClient(), url = server.url("/api/oauth/usage").toString()),
        tokenClient = ClaudeTokenClient(ProviderHttpClient(), tokenUrl = server.url("/v1/oauth/token").toString()),
        sessionStore = store,
        payloadCipher = cipher,
        clock = Clock.fixed(now, ZoneOffset.UTC),
    )

    private suspend fun storeSession(accessToken: String, refreshToken: String?, expiresAt: Long?) {
        val payload = ClaudeSessionPayload(
            accessToken = accessToken,
            refreshToken = refreshToken,
            tokenExpiresAtEpochSeconds = expiresAt,
            accountId = "account-uuid-1",
        )
        val encrypted = cipher.encrypt(Json.encodeToString(ClaudeSessionPayload.serializer(), payload).encodeToByteArray())
        store.save(envelope(encrypted.ciphertext, encrypted.nonce))
    }

    private fun envelope(ciphertext: ByteArray, nonce: ByteArray) = ProviderSessionEnvelope(
        providerId = "claude",
        localAccountId = "claude-1",
        providerAccountId = "account-uuid-1",
        schemaVersion = 2,
        payloadCiphertext = ciphertext,
        payloadNonce = nonce,
        createdAt = now.toString(),
        updatedAt = now.toString(),
    )

    private suspend fun storedSession(): ClaudeSessionPayload {
        val envelope = store.load("claude", "claude-1")!!
        return Json.decodeFromString(
            ClaudeSessionPayload.serializer(),
            cipher.decrypt(envelope.payloadCiphertext, envelope.payloadNonce).decodeToString(),
        )
    }

    private fun json(code: Int, body: String) =
        MockResponse.Builder().code(code).addHeader("Content-Type", "application/json").body(body).build()

    private companion object {
        const val USAGE = """{"limits":[
            {"kind":"session","group":"session","percent":10,"resets_at":"2026-10-17T15:00:00Z"},
            {"kind":"weekly_all","group":"weekly","percent":20,"resets_at":"2026-10-21T00:00:00Z"}]}"""
    }
}
