package com.kmnexus.codexmeter.core.network

import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

class ProviderHttpClientSecurityTest {
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
    fun `allowlisted client refuses cleartext and foreign hosts before sending anything`() = runTest {
        val client = ProviderHttpClient(allowedHosts = ProviderHosts.ALL)
        val urls = listOf(
            server.url("/api/oauth/usage").toString(),
            "http://api.anthropic.com/api/oauth/usage",
            "https://example.com/api/oauth/usage",
            "https://api.anthropic.com.evil.example/api/oauth/usage",
        )

        urls.forEach { url ->
            try {
                client.get(url, headers = mapOf("Authorization" to "Bearer secret"))
                fail("request to $url must be blocked")
            } catch (_: DisallowedHostException) {
                // expected
            }
            try {
                client.postJson(url, jsonBody = """{"refresh_token":"secret"}""")
                fail("request to $url must be blocked")
            } catch (_: DisallowedHostException) {
                // expected
            }
        }
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `production allowlist is exactly the provider API hosts`() {
        assertEquals(
            setOf("auth.openai.com", "chatgpt.com", "platform.claude.com", "api.anthropic.com"),
            ProviderHosts.ALL,
        )
    }

    @Test
    fun `redirects are returned instead of followed`() = runTest {
        server.enqueue(MockResponse.Builder().code(302).addHeader("Location", elsewhere.url("/x").toString()).build())
        server.enqueue(MockResponse.Builder().code(308).addHeader("Location", elsewhere.url("/y").toString()).build())
        val client = ProviderHttpClient()

        val get = client.get(server.url("/usage").toString(), headers = mapOf("Authorization" to "Bearer secret"))
        val post = client.postJson(server.url("/token").toString(), jsonBody = """{"code":"secret"}""")

        assertEquals(302, get.statusCode)
        assertEquals(308, post.statusCode)
        assertEquals(0, elsewhere.requestCount)
    }

    @Test
    fun `response headers are exposed case-insensitively and Retry-After seconds are parsed`() = runTest {
        server.enqueue(MockResponse.Builder().code(429).addHeader("Retry-After", " 42 ").body("{}").build())
        server.enqueue(MockResponse.Builder().code(429).addHeader("Retry-After", "Wed, 21 Oct 2026 07:28:00 GMT").build())

        val seconds = ProviderHttpClient().get(server.url("/a").toString())
        val date = ProviderHttpClient().get(server.url("/b").toString())

        assertEquals(" 42 ".trim(), seconds.header("RETRY-AFTER")?.trim())
        assertEquals(42L, seconds.retryAfterSeconds())
        assertNull(date.retryAfterSeconds())
    }
}
