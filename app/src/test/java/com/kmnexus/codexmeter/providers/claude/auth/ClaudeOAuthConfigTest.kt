package com.kmnexus.codexmeter.providers.claude.auth

import com.kmnexus.codexmeter.providers.common.auth.PkceSession
import java.net.URI
import java.net.URLDecoder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.fail
import org.junit.Test

class ClaudeOAuthConfigTest {
    @Test
    fun `authorization url carries the Claude Code parameters with only the profile scope`() {
        val pkce = PkceSession.create()
        val redirectUri = ClaudeOAuthConfig.redirectUri(54321)

        val url = ClaudeOAuthConfig.authorizationUrl(pkce, redirectUri)
        val uri = URI(url)
        val params = uri.rawQuery.split('&').associate {
            val (name, value) = it.split('=', limit = 2)
            name to URLDecoder.decode(value, "UTF-8")
        }

        assertEquals("https", uri.scheme)
        assertEquals("claude.com", uri.host)
        assertEquals("/cai/oauth/authorize", uri.path)
        assertEquals(
            listOf(
                "code", "client_id", "response_type", "redirect_uri", "scope",
                "code_challenge", "code_challenge_method", "state",
            ),
            uri.rawQuery.split('&').map { it.substringBefore('=') },
        )
        assertEquals("true", params["code"])
        assertEquals("9d1c250a-e61b-44d9-88ed-5944d1962f5e", params["client_id"])
        assertEquals("code", params["response_type"])
        assertEquals("http://localhost:54321/callback", params["redirect_uri"])
        assertEquals("user:profile", params["scope"])
        assertEquals(pkce.codeChallenge, params["code_challenge"])
        assertEquals("S256", params["code_challenge_method"])
        assertEquals(pkce.state, params["state"])
        // The verifier never leaves the device in the authorization request.
        assertFalse(url.contains(pkce.codeVerifier))
        assertFalse(url.contains("user:inference") || url.contains("user%3Ainference"))
        assertFalse(url.contains("org:create_api_key") || url.contains("org%3Acreate_api_key"))
    }

    @Test
    fun `redirect uri is the loopback callback and rejects invalid ports`() {
        assertEquals("http://localhost:1/callback", ClaudeOAuthConfig.redirectUri(1))
        listOf(0, -1, 65536).forEach { port ->
            try {
                ClaudeOAuthConfig.redirectUri(port)
                fail("port $port must be rejected")
            } catch (_: IllegalArgumentException) {
                // expected
            }
        }
    }

    @Test
    fun `token and usage endpoints are https on the expected Anthropic hosts`() {
        assertEquals("platform.claude.com", URI(ClaudeOAuthConfig.TOKEN_URL).host)
        assertEquals("api.anthropic.com", URI(ClaudeOAuthConfig.USAGE_URL).host)
        assertEquals("https", URI(ClaudeOAuthConfig.TOKEN_URL).scheme)
        assertEquals("https", URI(ClaudeOAuthConfig.USAGE_URL).scheme)
    }
}
