package com.kmnexus.codexmeter.providers.claude.network

import com.kmnexus.codexmeter.core.network.ProviderHttpClient
import com.kmnexus.codexmeter.domain.refresh.QuotaError
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ClaudeUsageClientTest {
    private val server = MockWebServer()

    @Before
    fun setUp() {
        server.start()
    }

    @After
    fun tearDown() {
        server.close()
    }

    @Test
    fun `sends a read-only GET with the bearer, the oauth beta header and the app's own user agent`() = runTest {
        server.enqueue(response(200, """{"limits":[]}"""))

        val result = client().fetchUsage("synthetic-access-token")

        assertTrue(result is ClaudeUsageClient.Result.Success)
        val request = server.takeRequest()
        assertEquals("GET", request.method)
        assertEquals("/api/oauth/usage", request.url.encodedPath)
        assertEquals("Bearer synthetic-access-token", request.headers["Authorization"])
        assertEquals("oauth-2025-04-20", request.headers["anthropic-beta"])
        assertEquals("AIMeter/1.1.0 (Android)", request.headers["User-Agent"])
        assertFalse(request.headers["User-Agent"].orEmpty().contains("claude", ignoreCase = true))
    }

    @Test
    fun `decodes limits rows`() = runTest {
        server.enqueue(
            response(
                200,
                """{"limits":[{"kind":"session","group":"session","percent":12,"resets_at":"2026-10-17T10:00:00Z","severity":"normal","is_active":true}]}""",
            ),
        )

        val dto = (client().fetchUsage("t") as ClaudeUsageClient.Result.Success).dto

        assertEquals("session", dto.limits?.single()?.kind)
        assertEquals(12.0, dto.limits?.single()?.percent)
    }

    @Test
    fun `each failure keeps its own error kind`() = runTest {
        server.enqueue(response(401, """{"type":"error","error":{"type":"authentication_error"}}"""))
        server.enqueue(response(403, """{"type":"error","error":{"type":"permission_error","message":"scope"}}"""))
        server.enqueue(response(403, "<html>blocked</html>"))
        server.enqueue(MockResponse.Builder().code(429).addHeader("Retry-After", "120").body("{}").build())
        server.enqueue(response(500, "{}"))
        server.enqueue(response(200, "not json"))

        val errors = List(6) { (client().fetchUsage("t") as ClaudeUsageClient.Result.Failure).error }

        assertEquals(QuotaError.AuthRequired(httpStatus = 401, diagnosticsDigest = "claude_usage_http_401"), errors[0])
        assertEquals(
            QuotaError.AuthRequired(httpStatus = 403, diagnosticsDigest = "claude_usage_http_403_permission_error"),
            errors[1],
        )
        assertEquals(QuotaError.ProviderUnavailable(httpStatus = 403, diagnosticsDigest = "claude_usage_http_403"), errors[2])
        assertEquals(QuotaError.RateLimited(diagnosticsDigest = "claude_usage_http_429", retryAfterSeconds = 120), errors[3])
        assertEquals(QuotaError.ProviderUnavailable(httpStatus = 500, diagnosticsDigest = "claude_usage_http_500"), errors[4])
        assertEquals(QuotaError.ResponseInvalid(diagnosticsDigest = "claude_usage_decode"), errors[5])
    }

    @Test
    fun `no answer at all is a network error, never empty usage`() = runTest {
        val url = server.url("/api/oauth/usage").toString()
        server.close()

        val result = ClaudeUsageClient(ProviderHttpClient(), url = url).fetchUsage("t")

        assertEquals(
            ClaudeUsageClient.Result.Failure(QuotaError.Network(diagnosticsDigest = "claude_usage_network")),
            result,
        )
    }

    private fun client() = ClaudeUsageClient(
        httpClient = ProviderHttpClient(),
        url = server.url("/api/oauth/usage").toString(),
    )

    private fun response(code: Int, body: String) =
        MockResponse.Builder().code(code).addHeader("Content-Type", "application/json").body(body).build()
}
