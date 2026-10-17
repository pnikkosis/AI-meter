package com.kmnexus.codexmeter.providers.claude.mapper

import com.kmnexus.codexmeter.domain.model.LocalAccountId
import com.kmnexus.codexmeter.domain.model.ProviderAccountId
import com.kmnexus.codexmeter.domain.model.ProviderId
import com.kmnexus.codexmeter.domain.model.QuotaWindowId
import com.kmnexus.codexmeter.domain.model.SnapshotId
import com.kmnexus.codexmeter.domain.quota.QuotaSnapshot
import com.kmnexus.codexmeter.domain.quota.QuotaSnapshotSource
import com.kmnexus.codexmeter.domain.quota.QuotaWindow
import com.kmnexus.codexmeter.domain.quota.QuotaWindowAvailability
import com.kmnexus.codexmeter.domain.quota.QuotaWindowDisplayKind
import com.kmnexus.codexmeter.providers.claude.dto.ClaudeUsageResponseDto
import java.time.Instant
import java.time.OffsetDateTime
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Maps the Claude usage response into quota windows. Both formats report the share of each window
 * that is already USED; [QuotaWindow.remainingPercent] derives what is left.
 *
 * The `limits[]` rows are preferred and kept in the server's order; the fixed legacy windows are only
 * used when a server sends no rows. The 5-hour session window is the primary candidate.
 */
object ClaudeUsageMapper {
    fun map(
        dto: ClaudeUsageResponseDto,
        localAccountId: LocalAccountId,
        providerAccountId: ProviderAccountId?,
        fetchedAt: Instant,
        source: QuotaSnapshotSource,
    ): QuotaSnapshot = QuotaSnapshot(
        snapshotId = SnapshotId("claude_${fetchedAt.toEpochMilli()}"),
        providerId = CLAUDE_PROVIDER_ID,
        localAccountId = localAccountId,
        providerAccountId = providerAccountId,
        fetchedAt = fetchedAt,
        source = source,
        planType = null,
        windows = windows(dto),
        credits = null,
        responseDigest = null,
    )

    /** Empty when the response carries no usable meter; callers treat that as an invalid answer. */
    fun windows(dto: ClaudeUsageResponseDto): List<QuotaWindow> {
        val fromRows = dto.limits.orEmpty().mapNotNull { limitWindow(it, dto.extra_usage) }
            .distinctBy { it.windowId }
        return fromRows.ifEmpty { legacyWindows(dto) }
    }

    private fun limitWindow(
        row: ClaudeUsageResponseDto.Limit,
        extra: ClaudeUsageResponseDto.ExtraUsage?,
    ): QuotaWindow? {
        val kind = row.kind?.trim()?.lowercase(Locale.ROOT)?.takeIf { it.isNotEmpty() } ?: return null
        val scopeLabel = (row.scope?.model?.display_name ?: row.scope?.surface?.display_name)?.cleanLabel()
        return when (kind) {
            "session" -> percentWindow(row.percent, row.resets_at, SESSION_WINDOW_ID, FIVE_HOUR_SECONDS, null, true)
            "weekly_all" -> percentWindow(row.percent, row.resets_at, WEEKLY_WINDOW_ID, SEVEN_DAY_SECONDS, null, false)
            "weekly_scoped" -> percentWindow(
                usedPercent = row.percent,
                resetsAt = row.resets_at,
                windowId = scopedWeeklyWindowId(scopeLabel),
                limitWindowSeconds = SEVEN_DAY_SECONDS,
                subLabel = scopeLabel,
                isPrimary = false,
            )
            "spend" -> percentWindow(
                usedPercent = row.percent ?: extra?.utilization,
                resetsAt = row.resets_at,
                windowId = EXTRA_USAGE_WINDOW_ID,
                limitWindowSeconds = null,
                subLabel = extra?.let(::spendLabel),
                isPrimary = false,
            )
            // A meter this version does not know yet: still shown, under a neutral label.
            else -> percentWindow(
                usedPercent = row.percent,
                resetsAt = row.resets_at,
                windowId = "claude_" + slug(kind) + (scopeLabel?.let { "_" + slug(it) } ?: ""),
                limitWindowSeconds = null,
                subLabel = scopeLabel ?: row.group?.cleanLabel(),
                isPrimary = false,
            )
        }
    }

    private fun legacyWindows(dto: ClaudeUsageResponseDto): List<QuotaWindow> = buildList {
        dto.five_hour?.let {
            add(percentWindow(it.utilization, it.resets_at, SESSION_WINDOW_ID, FIVE_HOUR_SECONDS, null, true))
        }
        dto.seven_day?.let {
            add(percentWindow(it.utilization, it.resets_at, WEEKLY_WINDOW_ID, SEVEN_DAY_SECONDS, null, false))
        }
        legacyExtraUsageWindow(dto.extra_usage)?.let { add(it) }
        dto.seven_day_opus?.let {
            add(percentWindow(it.utilization, it.resets_at, OPUS_WEEKLY_WINDOW_ID, SEVEN_DAY_SECONDS, "Opus", false))
        }
        dto.seven_day_sonnet?.let {
            add(percentWindow(it.utilization, it.resets_at, SONNET_WEEKLY_WINDOW_ID, SEVEN_DAY_SECONDS, "Sonnet", false))
        }
    }

    private fun percentWindow(
        usedPercent: Double?,
        resetsAt: String?,
        windowId: String,
        limitWindowSeconds: Int?,
        subLabel: String?,
        isPrimary: Boolean,
    ): QuotaWindow {
        val used = usedPercent?.takeIf { it.isFinite() }?.coerceIn(0.0, 100.0)?.roundToInt()
        val availability = when {
            used == null -> QuotaWindowAvailability.Missing
            used >= 100 -> QuotaWindowAvailability.Depleted
            else -> QuotaWindowAvailability.Available
        }
        return QuotaWindow(
            windowId = QuotaWindowId(windowId),
            titleKey = windowId,
            usedPercent = used,
            resetAt = parseIso(resetsAt),
            limitWindowSeconds = limitWindowSeconds,
            isPrimaryCandidate = isPrimary,
            availability = availability,
            displayKind = QuotaWindowDisplayKind.Percent,
            subLabel = subLabel,
        )
    }

    private fun legacyExtraUsageWindow(extra: ClaudeUsageResponseDto.ExtraUsage?): QuotaWindow? {
        if (extra == null || extra.is_enabled != true) return null
        val limit = extra.monthly_limit ?: return null
        val used = extra.used_credits ?: 0.0
        val usedPercent = extra.utilization ?: if (limit > 0) used / limit * 100 else 0.0
        return percentWindow(usedPercent, null, EXTRA_USAGE_WINDOW_ID, null, spendLabel(extra), false)
    }

    /** Cents -> "$25.00 / $100.00". Null when the cap is unknown. */
    private fun spendLabel(extra: ClaudeUsageResponseDto.ExtraUsage): String? {
        val limitCents = extra.monthly_limit ?: return null
        val usedCents = extra.used_credits ?: 0.0
        val currency = extra.currency?.trim()?.uppercase(Locale.ROOT)
        val symbol = when (currency) {
            null, "", "USD" -> "$"
            "EUR" -> "€"
            "GBP" -> "£"
            else -> "$currency "
        }
        fun money(cents: Double) = symbol + String.format(Locale.ROOT, "%.2f", cents / 100.0)
        return "${money(usedCents)} / ${money(limitCents)}"
    }

    private fun scopedWeeklyWindowId(scopeLabel: String?): String {
        val name = scopeLabel?.lowercase(Locale.ROOT).orEmpty()
        return when {
            "opus" in name -> OPUS_WEEKLY_WINDOW_ID
            "sonnet" in name -> SONNET_WEEKLY_WINDOW_ID
            else -> SCOPED_WEEKLY_WINDOW_PREFIX + slug(scopeLabel ?: "other")
        }
    }

    private fun slug(value: String): String =
        value.lowercase(Locale.ROOT).map { if (it in 'a'..'z' || it in '0'..'9') it else '_' }
            .joinToString("").trim('_').take(32).ifEmpty { "other" }

    /** Server display names are shown verbatim, minus control characters and excess length. */
    private fun String.cleanLabel(): String? =
        filterNot { it.isISOControl() }.trim().take(40).takeIf { it.isNotEmpty() }

    private fun parseIso(value: String?): Instant? {
        if (value.isNullOrBlank()) return null
        return runCatching { Instant.parse(value) }.getOrNull()
            ?: runCatching { OffsetDateTime.parse(value).toInstant() }.getOrNull()
    }

    const val SESSION_WINDOW_ID = "claude_5h_window"
    const val WEEKLY_WINDOW_ID = "claude_7d_window"
    const val OPUS_WEEKLY_WINDOW_ID = "claude_7d_opus_window"
    const val SONNET_WEEKLY_WINDOW_ID = "claude_7d_sonnet_window"
    const val SCOPED_WEEKLY_WINDOW_PREFIX = "claude_7d_scoped_"
    const val EXTRA_USAGE_WINDOW_ID = "claude_extra_usage"

    private const val FIVE_HOUR_SECONDS = 5 * 3600
    private const val SEVEN_DAY_SECONDS = 7 * 86400
    private val CLAUDE_PROVIDER_ID = ProviderId("claude")
}
