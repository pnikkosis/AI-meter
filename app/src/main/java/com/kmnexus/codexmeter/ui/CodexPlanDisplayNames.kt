package com.kmnexus.codexmeter.ui

import com.kmnexus.codexmeter.domain.model.ProviderId
import com.kmnexus.codexmeter.providers.ProviderRegistry
import java.util.Locale

/**
 * Provider-aware subscription/plan display name:
 *
 * - Codex: `codexPlanDisplayName` (pro -> "Pro 20x", prolite -> "Pro 5x", else titlecase)
 * - Claude: no plan value in the usage response (returns null); any other value is shown verbatim
 */
fun providerPlanDisplayName(providerId: ProviderId, planType: String?): String? {
    val raw = planType?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    return when (providerId) {
        ProviderRegistry.CODEX -> codexPlanDisplayName(raw)
        else -> raw
    }
}

internal fun codexPlanDisplayName(planType: String?): String? {
    val raw = planType?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    return when (raw.lowercase(Locale.ROOT)) {
        "pro" -> "Pro 20x"
        "prolite", "pro_lite", "pro-lite", "pro lite" -> "Pro 5x"
        else -> raw.replaceFirstChar { it.titlecase(Locale.ROOT) }
    }
}
