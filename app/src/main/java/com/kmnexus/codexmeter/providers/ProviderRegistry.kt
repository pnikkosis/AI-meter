package com.kmnexus.codexmeter.providers

import com.kmnexus.codexmeter.domain.model.ProviderId

/**
 * The two subscriptions AI Usage Widget tracks. Upstream CodexMeter registered nine more providers;
 * they were removed on purpose (scope: Codex + Claude only), together with their network clients.
 */
object ProviderRegistry {
    /** Known provider ID constants. */
    val CODEX = ProviderId("codex")
    val CLAUDE = ProviderId("claude")

    /**
     * All registered providers, ordered alphabetically by display name (the order shown in the
     * provider picker). The default provider is identified by the [ProviderConfig.isDefault] flag,
     * not by list position.
     */
    val all: List<ProviderConfig> = listOf(
        ProviderConfig(
            providerId = CLAUDE,
            displayName = "Claude",
            iconResId = com.kmnexus.codexmeter.R.drawable.ic_brand_claude,
            authKind = ProviderAuthKind.OAuthPkceLogin,
            supportsBalance = false,
            supportsRefill = false,
            supportsInviteLimitSnooping = false,
            isDefault = false,
        ),
        ProviderConfig(
            providerId = CODEX,
            displayName = "Codex",
            iconResId = com.kmnexus.codexmeter.R.drawable.ic_brand_codex,
            authKind = ProviderAuthKind.OAuthPkceLogin,
            supportsBalance = false,
            supportsRefill = false,
            supportsInviteLimitSnooping = false,
            isDefault = true,
        ),
    )

    fun configFor(providerId: ProviderId): ProviderConfig =
        all.first { it.providerId == providerId }

    /** Brand icon for a provider, or null if the provider id is unknown (e.g. legacy data). */
    @androidx.annotation.DrawableRes
    fun iconFor(providerId: ProviderId): Int? =
        all.firstOrNull { it.providerId == providerId }?.iconResId

    /** Human-facing display name for a provider, falling back to the raw id when unknown. */
    fun displayNameFor(providerId: ProviderId): String =
        all.firstOrNull { it.providerId == providerId }?.displayName ?: providerId.value

    fun defaultProvider(): ProviderConfig = all.first { it.isDefault }
}

enum class ProviderAuthKind {
    /**
     * OAuth device-code sign-in: the user approves a code in the external browser. Codex keeps it
     * as an alternative to its browser sign-in (OpenAI only allows it once the user enables it).
     */
    DeviceCode,

    /** OAuth authorization-code + PKCE sign-in in the browser with a loopback redirect (both providers). */
    OAuthPkceLogin,
}

data class ProviderConfig(
    val providerId: ProviderId,
    val displayName: String,
    @androidx.annotation.DrawableRes val iconResId: Int,
    val authKind: ProviderAuthKind,
    val supportsBalance: Boolean,
    val supportsRefill: Boolean,
    val supportsInviteLimitSnooping: Boolean,
    val isDefault: Boolean,
)
