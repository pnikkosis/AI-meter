package com.kmnexus.codexmeter.domain.quota

import com.kmnexus.codexmeter.domain.settings.DEFAULT_BALANCE_CAUTION_THRESHOLD
import com.kmnexus.codexmeter.domain.settings.DEFAULT_BALANCE_WARNING_THRESHOLD
import com.kmnexus.codexmeter.domain.settings.DEFAULT_CAUTION_THRESHOLD
import com.kmnexus.codexmeter.domain.settings.DEFAULT_WARNING_THRESHOLD
import com.kmnexus.codexmeter.domain.settings.NotificationPreferences

/**
 * How much of a window is left, in the bands the user sets for alerts. Home, Accounts, the widgets
 * and the notification all read this one classification, so the same window never gets two words.
 * [Unknown] means there is no figure to show; it must never be drawn as zero.
 */
enum class QuotaLevel { Normal, Caution, Warning, Depleted, Unknown }

data class QuotaLevelThresholds(
    val cautionPercent: Int = DEFAULT_CAUTION_THRESHOLD,
    val warningPercent: Int = DEFAULT_WARNING_THRESHOLD,
    val balanceCaution: Double = DEFAULT_BALANCE_CAUTION_THRESHOLD,
    val balanceWarning: Double = DEFAULT_BALANCE_WARNING_THRESHOLD,
)

fun NotificationPreferences.quotaLevelThresholds(): QuotaLevelThresholds =
    QuotaLevelThresholds(
        cautionPercent = cautionThreshold,
        warningPercent = warningThreshold,
        balanceCaution = balanceCautionThreshold,
        balanceWarning = balanceWarningThreshold,
    )

fun QuotaWindow.quotaLevel(thresholds: QuotaLevelThresholds): QuotaLevel {
    if (availability == QuotaWindowAvailability.Depleted) return QuotaLevel.Depleted
    if (availability != QuotaWindowAvailability.Available) return QuotaLevel.Unknown
    if (displayKind == QuotaWindowDisplayKind.Balance) {
        val amount = balanceAmount?.toDoubleOrNull() ?: return QuotaLevel.Unknown
        return when {
            amount <= 0.0 -> QuotaLevel.Depleted
            amount <= thresholds.balanceWarning -> QuotaLevel.Warning
            amount <= thresholds.balanceCaution -> QuotaLevel.Caution
            else -> QuotaLevel.Normal
        }
    }
    val left = remainingPercent ?: return QuotaLevel.Unknown
    return when {
        left <= 0 -> QuotaLevel.Depleted
        left <= thresholds.warningPercent -> QuotaLevel.Warning
        left <= thresholds.cautionPercent -> QuotaLevel.Caution
        else -> QuotaLevel.Normal
    }
}

/** Claude's extra usage is a spend meter, not a limit that stops you; it never headlines. */
const val EXTRA_USAGE_WINDOW_ID = "claude_extra_usage"

/**
 * The window that limits the account right now: the percent window with the least left (the first
 * one on a tie). Null when no window has a figure. Used where an account gets a single number.
 */
fun List<QuotaWindow>.headlineWindow(): QuotaWindow? =
    filter {
        it.displayKind != QuotaWindowDisplayKind.Balance &&
            it.windowId.value != EXTRA_USAGE_WINDOW_ID &&
            (it.availability == QuotaWindowAvailability.Available || it.availability == QuotaWindowAvailability.Depleted) &&
            it.remainingPercent != null
    }.minByOrNull { it.remainingPercent ?: Int.MAX_VALUE }
