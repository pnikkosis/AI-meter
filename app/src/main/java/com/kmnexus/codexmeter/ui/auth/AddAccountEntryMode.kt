package com.kmnexus.codexmeter.ui.auth

import com.kmnexus.codexmeter.domain.model.LocalAccountId
import com.kmnexus.codexmeter.domain.model.ProviderId

sealed class AddAccountEntryMode {
    /** First screen: pick a provider to add. Replaces the old Choose mode. */
    data object ProviderSelection : AddAccountEntryMode()

    /** Codex device-code sign-in in the external browser. */
    data object LoginToCodex : AddAccountEntryMode()

    /**
     * Codex device-code re-login for an existing account. [expectedProviderAccountId] is the account's
     * chatgpt_account_id, used to detect when the user signs into a different Codex account.
     */
    data class CodexRelogin(val expectedProviderAccountId: String?) : AddAccountEntryMode()

    /** OAuth PKCE flow for the given provider (re-login when [reloginAccountId] set). */
    data class WebViewOAuthPkce(
        val providerId: ProviderId,
        val reloginAccountId: LocalAccountId? = null,
    ) : AddAccountEntryMode()

    val routeValue: String
        get() = when (this) {
            is ProviderSelection -> "select"
            is LoginToCodex -> "login"
            is CodexRelogin -> "codexrelogin:${expectedProviderAccountId.orEmpty()}"
            is WebViewOAuthPkce -> "pkce:${providerId.value}".withReloginSuffix(reloginAccountId)
        }

    companion object {
        /** Backward-compat alias kept so existing call-sites compile. */
        val Choose: AddAccountEntryMode get() = ProviderSelection

        private fun String.withReloginSuffix(reloginAccountId: LocalAccountId?): String =
            if (reloginAccountId == null) this else "$this:${reloginAccountId.value}"

        fun fromRouteValue(routeValue: String?): AddAccountEntryMode {
            if (routeValue == null) return ProviderSelection
            return when {
                routeValue == "select" -> ProviderSelection
                routeValue == "login" -> LoginToCodex
                routeValue.startsWith("codexrelogin:") ->
                    CodexRelogin(routeValue.removePrefix("codexrelogin:").takeIf { it.isNotBlank() })
                routeValue.startsWith("pkce:") ->
                    routeValue.removePrefix("pkce:").splitProviderAndRelogin { providerId, relogin ->
                        WebViewOAuthPkce(providerId, relogin)
                    }
                else -> ProviderSelection
            }
        }

        private inline fun String.splitProviderAndRelogin(
            build: (ProviderId, LocalAccountId?) -> AddAccountEntryMode,
        ): AddAccountEntryMode {
            val parts = split(":", limit = 2)
            val relogin = parts.getOrNull(1)?.takeIf { it.isNotBlank() }?.let(::LocalAccountId)
            return build(ProviderId(parts[0]), relogin)
        }
    }
}
