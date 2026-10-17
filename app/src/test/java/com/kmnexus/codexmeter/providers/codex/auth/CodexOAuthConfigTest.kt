package com.kmnexus.codexmeter.providers.codex.auth

import com.kmnexus.codexmeter.providers.common.auth.PkceSession
import java.net.URI
import java.net.URLDecoder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class CodexOAuthConfigTest {
    @Test
    fun `browser authorization url carries the codex login parameters`() {
        val pkce = PkceSession.create()
        val redirectUri = CodexOAuthConfig.browserRedirectUri(1455)

        val url = CodexOAuthConfig.browserAuthorizationUrl(pkce, redirectUri)
        val uri = URI(url)
        val params = uri.rawQuery.split('&').associate {
            val (name, value) = it.split('=', limit = 2)
            name to URLDecoder.decode(value, "UTF-8")
        }

        assertEquals("https", uri.scheme)
        assertEquals("auth.openai.com", uri.host)
        assertEquals("/oauth/authorize", uri.path)
        assertEquals(
            listOf(
                "response_type", "client_id", "redirect_uri", "code_challenge", "code_challenge_method",
                "state", "scope", "id_token_add_organizations", "codex_cli_simplified_flow",
            ),
            uri.rawQuery.split('&').map { it.substringBefore('=') },
        )
        assertEquals("code", params["response_type"])
        assertEquals("app_EMoamEEZ73f0CkXaXp7hrann", params["client_id"])
        assertEquals("http://127.0.0.1:1455/auth/callback", params["redirect_uri"])
        assertEquals(pkce.codeChallenge, params["code_challenge"])
        assertEquals("S256", params["code_challenge_method"])
        assertEquals(pkce.state, params["state"])
        assertEquals("openid profile email offline_access", params["scope"])
        assertEquals("true", params["id_token_add_organizations"])
        assertEquals("true", params["codex_cli_simplified_flow"])
        // The verifier never leaves the device in the authorization request, and the app does not
        // claim to be Codex CLI (no originator).
        assertFalse(url.contains(pkce.codeVerifier))
        assertFalse(url.contains("originator"))
    }

    @Test
    fun `browser sign-in uses the loopback ports and path registered for codex login`() {
        assertEquals(listOf(1455, 1457), CodexOAuthConfig.BROWSER_CALLBACK_PORTS)
        assertEquals("/auth/callback", CodexOAuthConfig.BROWSER_CALLBACK_PATH)
        assertEquals("http://127.0.0.1:1457/auth/callback", CodexOAuthConfig.browserRedirectUri(1457))
    }
}
