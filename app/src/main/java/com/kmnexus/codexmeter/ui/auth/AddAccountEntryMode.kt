package com.kmnexus.codexmeter.ui.auth

sealed class AddAccountEntryMode {
    /** First screen: pick a provider to add. Replaces the old Choose mode. */
    data object ProviderSelection : AddAccountEntryMode()

    /**
     * Codex sign-in in the browser (authorization code + PKCE, loopback redirect), as `codex login`
     * does it. For a re-login, [expectedProviderAccountId] is the account's chatgpt_account_id.
     */
    data class CodexBrowserLogin(val expectedProviderAccountId: String? = null) : AddAccountEntryMode()

    /** Codex device-code sign-in in the external browser (alternative offered by the browser sign-in). */
    data object LoginToCodex : AddAccountEntryMode()

    /**
     * Codex device-code re-login for an existing account. [expectedProviderAccountId] is the account's
     * chatgpt_account_id, used to detect when the user signs into a different Codex account.
     */
    data class CodexRelogin(val expectedProviderAccountId: String?) : AddAccountEntryMode()

    /**
     * Claude sign-in in the browser (authorization code + PKCE, loopback redirect). For a re-login,
     * [expectedProviderAccountId] is the Claude account uuid the new session must belong to.
     */
    data class ClaudeLogin(val expectedProviderAccountId: String? = null) : AddAccountEntryMode()

    val routeValue: String
        get() = when (this) {
            is ProviderSelection -> "select"
            is CodexBrowserLogin -> "codexbrowser:${expectedProviderAccountId.orEmpty()}"
            is LoginToCodex -> "login"
            is CodexRelogin -> "codexrelogin:${expectedProviderAccountId.orEmpty()}"
            is ClaudeLogin -> "claudelogin:${expectedProviderAccountId.orEmpty()}"
        }

    companion object {
        /** Backward-compat alias kept so existing call-sites compile. */
        val Choose: AddAccountEntryMode get() = ProviderSelection

        fun fromRouteValue(routeValue: String?): AddAccountEntryMode {
            if (routeValue == null) return ProviderSelection
            return when {
                routeValue == "select" -> ProviderSelection
                routeValue == "login" -> LoginToCodex
                routeValue.startsWith("codexbrowser:") ->
                    CodexBrowserLogin(routeValue.removePrefix("codexbrowser:").takeIf { it.isNotBlank() })
                routeValue.startsWith("codexrelogin:") ->
                    CodexRelogin(routeValue.removePrefix("codexrelogin:").takeIf { it.isNotBlank() })
                routeValue.startsWith("claudelogin:") ->
                    ClaudeLogin(routeValue.removePrefix("claudelogin:").takeIf { it.isNotBlank() })
                // Retired routes (API key, cookie, WebView OAuth, other providers) fall back.
                else -> ProviderSelection
            }
        }
    }
}
