package com.kmnexus.codexmeter.providers.claude.auth

import com.kmnexus.codexmeter.core.network.ProviderHttpClient
import com.kmnexus.codexmeter.domain.refresh.QuotaError
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ClaudeTokenClientTest {
    private val server = MockWebServer()
    private val elsewhere = MockWebServer()

    @Before
    fun setUp() {
        server.start()
        elsewhere.start()
    }

    @After
    fun tearDown() {
        server.close()
        elsewhere.close()
    }

    @Test
    fun `exchange posts the Claude Code JSON body`() = runTest {
        server.enqueue(json(200, TOKEN_RESPONSE))

        val result = client().exchangeCode(
            code = "auth-code",
            codeVerifier = "verifier-123",
            redirectUri = "http://localhost:4567/callback",
            state = "state-abc",
        )

        assertTrue(result is ClaudeTokenClient.Result.Success)
        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/v1/oauth/token", request.url.encodedPath)
        assertTrue(request.headers["Content-Type"].orEmpty().startsWith("application/json"))
        assertEquals("application/json", request.headers["Accept"])
        assertEquals("AIMeter/1.1.0 (Android)", request.headers["User-Agent"])
        val body = Json.parseToJsonElement(request.body?.utf8().orEmpty()) as JsonObject
        assertEquals(
            mapOf(
                "grant_type" to "authorization_code",
                "code" to "auth-code",
                "redirect_uri" to "http://localhost:4567/callback",
                "client_id" to "9d1c250a-e61b-44d9-88ed-5944d1962f5e",
                "code_verifier" to "verifier-123",
                "state" to "state-abc",
            ),
            body.mapValues { it.value.jsonPrimitive.content },
        )
    }

    @Test
    fun `successful answer exposes tokens and account identity but toString stays redacted`() = runTest {
        server.enqueue(json(200, TOKEN_RESPONSE))

        val result = client().exchangeCode("c", "v", "http://localhost:1/callback", "s")

        val tokens = (result as ClaudeTokenClient.Result.Success).tokens
        assertEquals("synthetic-access-token", tokens.accessToken)
        assertEquals("synthetic-refresh-token", tokens.refreshToken)
        assertEquals(28_800L, tokens.expiresInSeconds)
        assertEquals("user:profile", tokens.scope)
        assertEquals("account-uuid-1", tokens.accountUuid)
        assertEquals("person@example.com", tokens.accountEmail)
        assertEquals("org-uuid-1", tokens.organizationUuid)
        assertFalse(result.toString().contains("synthetic"))
        assertFalse(tokens.toString().contains("synthetic"))
    }

    @Test
    fun `refresh sends the refresh grant with only the profile scope`() = runTest {
        server.enqueue(json(200, """{"access_token":"new-access","expires_in":3600}"""))

        val result = client().refresh("old-refresh")

        val tokens = (result as ClaudeTokenClient.Result.Success).tokens
        assertEquals("new-access", tokens.accessToken)
        assertNull(tokens.refreshToken)
        val body = Json.parseToJsonElement(server.takeRequest().body?.utf8().orEmpty()) as JsonObject
        assertEquals(
            mapOf(
                "grant_type" to "refresh_token",
                "refresh_token" to "old-refresh",
                "client_id" to "9d1c250a-e61b-44d9-88ed-5944d1962f5e",
                "scope" to "user:profile",
            ),
            body.mapValues { it.value.jsonPrimitive.content },
        )
    }

    @Test
    fun `invalid grant means signing in again and the digest holds no secret`() = runTest {
        server.enqueue(json(400, """{"error":"invalid_grant","error_description":"Refresh token old-refresh expired"}"""))

        val error = (client().refresh("old-refresh") as ClaudeTokenClient.Result.Failure).error

        assertTrue(error is QuotaError.AuthRequired)
        assertEquals(400, error.httpStatus)
        assertEquals("claude_token_refresh_http_400_invalid_grant", error.diagnosticsDigest)
        assertFalse(error.toString().contains("old-refresh"))
    }

    @Test
    fun `anthropic style error object is read as well`() = runTest {
        server.enqueue(json(401, """{"type":"error","error":{"type":"authentication_error","message":"bad"}}"""))

        val error = (client().exchangeCode("c", "v", "http://localhost:1/callback", "s") as ClaudeTokenClient.Result.Failure).error

        assertTrue(error is QuotaError.AuthRequired)
        assertEquals("claude_token_exchange_http_401_authentication_error", error.diagnosticsDigest)
    }

    @Test
    fun `rate limit server error and unreadable answers keep their own kinds`() = runTest {
        server.enqueue(MockResponse.Builder().code(429).addHeader("Retry-After", "30").body("{}").build())
        server.enqueue(json(503, "{}"))
        server.enqueue(json(200, """{"token_type":"bearer"}"""))
        server.enqueue(json(200, "<html>"))

        val rateLimited = (client().refresh("r") as ClaudeTokenClient.Result.Failure).error
        val unavailable = (client().refresh("r") as ClaudeTokenClient.Result.Failure).error
        val missingToken = (client().refresh("r") as ClaudeTokenClient.Result.Failure).error
        val notJson = (client().refresh("r") as ClaudeTokenClient.Result.Failure).error

        assertEquals(QuotaError.RateLimited(diagnosticsDigest = "claude_token_refresh_http_429", retryAfterSeconds = 30), rateLimited)
        assertEquals(QuotaError.ProviderUnavailable(httpStatus = 503, diagnosticsDigest = "claude_token_refresh_http_503"), unavailable)
        assertTrue(missingToken is QuotaError.ResponseInvalid)
        assertTrue(notJson is QuotaError.ResponseInvalid)
    }

    @Test
    fun `redirects are not followed so the body never reaches another host`() = runTest {
        server.enqueue(
            MockResponse.Builder()
                .code(307)
                .addHeader("Location", elsewhere.url("/steal").toString())
                .build(),
        )

        val error = (client().exchangeCode("c", "v", "http://localhost:1/callback", "s") as ClaudeTokenClient.Result.Failure).error

        assertEquals(QuotaError.ProviderUnavailable(httpStatus = 307, diagnosticsDigest = "claude_token_exchange_http_307"), error)
        assertEquals(0, elsewhere.requestCount)
    }

    @Test
    fun `connection failure is a network error`() = runTest {
        val url = server.url("/v1/oauth/token").toString()
        server.close()

        val error = (ClaudeTokenClient(ProviderHttpClient(), tokenUrl = url).refresh("r") as ClaudeTokenClient.Result.Failure).error

        assertEquals(QuotaError.Network(diagnosticsDigest = "claude_token_refresh_network"), error)
    }

    private fun client() = ClaudeTokenClient(
        httpClient = ProviderHttpClient(),
        tokenUrl = server.url("/v1/oauth/token").toString(),
    )

    private fun json(code: Int, body: String) =
        MockResponse.Builder().code(code).addHeader("Content-Type", "application/json").body(body).build()

    private companion object {
        val TOKEN_RESPONSE = """
            {
              "token_type": "Bearer",
              "access_token": "synthetic-access-token",
              "refresh_token": "synthetic-refresh-token",
              "expires_in": 28800,
              "refresh_token_expires_in": 2592000,
              "scope": "user:profile",
              "account": {"uuid": "account-uuid-1", "email_address": "person@example.com"},
              "organization": {"uuid": "org-uuid-1", "name": "Personal"}
            }
        """.trimIndent()
    }
}
