package com.kmnexus.codexmeter.providers.codex.auth

import com.kmnexus.codexmeter.providers.common.auth.PkceSession
import java.net.URLEncoder

internal object CodexOAuthConfig {
    const val ISSUER_URL = "https://auth.openai.com"
    const val TOKEN_ENDPOINT_URL = "$ISSUER_URL/oauth/token"
    const val DEVICE_USER_CODE_ENDPOINT_URL = "$ISSUER_URL/api/accounts/deviceauth/usercode"
    const val DEVICE_TOKEN_ENDPOINT_URL = "$ISSUER_URL/api/accounts/deviceauth/token"
    const val DEVICE_VERIFICATION_URL = "$ISSUER_URL/codex/device"
    const val DEVICE_REDIRECT_URI = "$ISSUER_URL/deviceauth/callback"

    /** RFC 7009 revocation endpoint the Codex CLI calls on logout (best effort). */
    const val REVOKE_ENDPOINT_URL = "$ISSUER_URL/oauth/revoke"
    const val SCOPE = "openid profile email offline_access"

    // Source: docs/ARCHITECTURE.md 11.2 and docs/SPEC.md 9.3 define this Codex OAuth client id.
    const val CLIENT_ID = "app_EMoamEEZ73f0CkXaXp7hrann"

    /*
     * Browser sign-in, as `codex login` does it (not the device code, which OpenAI only allows after
     * the user enables it in ChatGPT's security settings): authorization code + PKCE (S256) in the
     * user's browser, returning to a loopback redirect. The client only accepts the redirect URIs
     * Codex CLI uses, so the receiver binds those exact ports: 1455, or 1457 when 1455 is taken.
     */
    const val AUTHORIZE_URL = "$ISSUER_URL/oauth/authorize"
    const val BROWSER_CALLBACK_PATH = "/auth/callback"
    val BROWSER_CALLBACK_PORTS: List<Int> = listOf(1455, 1457)

    fun browserRedirectUri(port: Int): String = "http://127.0.0.1:$port$BROWSER_CALLBACK_PATH"

    /** The page opened in the browser. Codex CLI's parameters, without its `originator` identity. */
    fun browserAuthorizationUrl(pkce: PkceSession, redirectUri: String): String {
        val params = listOf(
            "response_type" to "code",
            "client_id" to CLIENT_ID,
            "redirect_uri" to redirectUri,
            "code_challenge" to pkce.codeChallenge,
            "code_challenge_method" to PkceSession.CHALLENGE_METHOD,
            "state" to pkce.state,
            "scope" to SCOPE,
            "id_token_add_organizations" to "true",
            "codex_cli_simplified_flow" to "true",
        )
        return AUTHORIZE_URL + "?" + params.joinToString("&") { (name, value) ->
            "${encode(name)}=${encode(value)}"
        }
    }

    private fun encode(value: String): String = URLEncoder.encode(value, Charsets.UTF_8.name())
}
