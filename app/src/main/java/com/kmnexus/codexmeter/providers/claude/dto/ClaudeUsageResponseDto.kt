package com.kmnexus.codexmeter.providers.claude.dto

import kotlinx.serialization.Serializable

/**
 * Claude OAuth usage response (`GET https://api.anthropic.com/api/oauth/usage`).
 *
 * Current servers send [limits]: one row per meter, in display order, where `percent` is the share
 * of the window already USED (0–100, above 100 once over) and `resets_at` an ISO-8601 instant. Older
 * servers only send the fixed windows ([five_hour], [seven_day], …) whose `utilization` is also the
 * used percentage. [limits] is null when the server predates the rows and empty when it reports no
 * meters.
 */
@Serializable
data class ClaudeUsageResponseDto(
    val limits: List<Limit>? = null,
    val five_hour: Window? = null,
    val seven_day: Window? = null,
    val seven_day_opus: Window? = null,
    val seven_day_sonnet: Window? = null,
    val extra_usage: ExtraUsage? = null,
) {
    @Serializable
    data class Limit(
        /** Server meter kind, e.g. `session`, `weekly_all`, `weekly_scoped`, `spend`. */
        val kind: String? = null,
        /** Server row group, e.g. `session`, `weekly`, `monthly`. */
        val group: String? = null,
        val percent: Double? = null,
        val resets_at: String? = null,
        val scope: Scope? = null,
        val severity: String? = null,
        val is_active: Boolean? = null,
    )

    @Serializable
    data class Scope(
        val model: Label? = null,
        val surface: Label? = null,
    )

    @Serializable
    data class Label(
        val display_name: String? = null,
    )

    @Serializable
    data class Window(
        val utilization: Double? = null,
        val resets_at: String? = null,
    )

    /**
     * Pay-as-you-go spend beyond the subscription. Amounts are in cents; `utilization` is a 0–100
     * percent of the monthly cap.
     */
    @Serializable
    data class ExtraUsage(
        val is_enabled: Boolean? = null,
        val monthly_limit: Double? = null,
        val used_credits: Double? = null,
        val utilization: Double? = null,
        val currency: String? = null,
    )
}
