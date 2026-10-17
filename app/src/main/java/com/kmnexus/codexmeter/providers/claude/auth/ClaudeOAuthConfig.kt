package com.kmnexus.codexmeter.providers.claude.auth

import com.kmnexus.codexmeter.providers.common.auth.PkceSession
import java.net.URLEncoder

/**
 * Claude subscription sign-in, mirroring what the official Claude Code CLI does: an OAuth 2.0
 * authorization-code flow with PKCE (S256) in the user's browser, returning to a loopback redirect
 * (`http://localhost:<port>/callback`). Values were taken from the Claude Code 2.1.292 release.
 *
 * Scope: only `user:profile`, which is what the usage endpoint needs. Claude Code itself also asks
 * for `user:inference` (send messages billed to the plan), `org:create_api_key` and others; this app
 * never requests them. Anthropic does not document everything `user:profile` allows, so the token is
 * handled as a full credential for the Claude account, never as a read-only one.
 */
object ClaudeOAuthConfig {
    /** Public OAuth client id of Claude Code (no client secret exists for it). */
    const val CLIENT_ID = "9d1c250a-e61b-44d9-88ed-5944d1962f5e"

    /** Claude Code's claude.ai sign-in entry point; it redirects to `claude.ai/oauth/authorize`. */
    const val AUTHORIZE_URL = "https://claude.com/cai/oauth/authorize"

    const val TOKEN_URL = "https://platform.claude.com/v1/oauth/token"
    const val USAGE_URL = "https://api.anthropic.com/api/oauth/usage"

    /** Required by the OAuth-authenticated endpoints of api.anthropic.com. */
    const val OAUTH_BETA_HEADER = "oauth-2025-04-20"

    const val SCOPE = "user:profile"
    const val CALLBACK_PATH = "/callback"

    fun redirectUri(port: Int): String {
        require(port in 1..65535) { "invalid loopback port" }
        return "http://localhost:$port$CALLBACK_PATH"
    }

    /** The URL opened in the browser. Parameter set and order follow Claude Code. */
    fun authorizationUrl(pkce: PkceSession, redirectUri: String): String {
        val params = listOf(
            "code" to "true",
            "client_id" to CLIENT_ID,
            "response_type" to "code",
            "redirect_uri" to redirectUri,
            "scope" to SCOPE,
            "code_challenge" to pkce.codeChallenge,
            "code_challenge_method" to PkceSession.CHALLENGE_METHOD,
            "state" to pkce.state,
        )
        return AUTHORIZE_URL + "?" + params.joinToString("&") { (name, value) ->
            "${encode(name)}=${encode(value)}"
        }
    }

    private fun encode(value: String): String = URLEncoder.encode(value, Charsets.UTF_8.name())
}
