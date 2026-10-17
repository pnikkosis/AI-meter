package com.kmnexus.codexmeter.ui.quota

import androidx.annotation.StringRes
import com.kmnexus.codexmeter.R

/**
 * Maps a provider's `QuotaWindow.windowId` to a localized display label, so Home/Account/Widget
 * never surface raw mapper keys like `claude_7d_window`. Unknown ids fall back to a generic label.
 */
/**
 * Formats a balance amount with a currency symbol (¥ / $) consistently across Home, Account and the
 * widget. Unknown currencies keep their ISO code as a prefix. Returns null for a blank amount.
 */
fun formatProviderBalance(amount: String?, currency: String?): String? {
    if (amount.isNullOrBlank()) return null
    val symbol = currencySymbol(currency)
    return "$symbol$amount"
}

/** Symbol for an ISO currency code, matching formatProviderBalance's mapping. */
fun currencySymbol(currency: String?): String = when (currency?.uppercase()) {
    "CNY", "RMB" -> "¥"
    "USD" -> "$"
    "EUR" -> "€"
    "GBP" -> "£"
    "JPY" -> "JP¥"
    null, "" -> ""
    else -> "$currency "
}

/**
 * Codex names its windows by position: the usage API exposes
 * `primary_window` / `secondary_window`, so the mapper can only guess `five_hour` / `weekly` from
 * the slot an entry arrived in. That guess broke when Codex dropped the 5-hour window and its
 * `primary_window` started carrying the weekly quota. For these two ids the window duration the
 * provider reports is the truth. Claude's named windows keep winning — its 7-day Opus and Sonnet
 * windows share a duration but must stay distinguishable.
 */
private val POSITIONAL_WINDOW_IDS = setOf("five_hour", "weekly")

// Claude's weekly limit of its own for one model or product, keyed by the name the server gives it.
private const val CLAUDE_SCOPED_WEEKLY_PREFIX = "claude_7d_scoped_"

private const val FIVE_HOUR_SECONDS = 5 * 60 * 60
private const val DAY_SECONDS = 24 * 60 * 60
private const val WEEK_SECONDS = 7 * DAY_SECONDS

@StringRes
fun quotaWindowLabelRes(windowId: String, limitWindowSeconds: Int? = null): Int {
    if (windowId in POSITIONAL_WINDOW_IDS) {
        // A duration we cannot name (or a missing window that reports none) is still better served by
        // the neutral label than by a positional guess that would state a window length the provider
        // never reported — and could collide with the sibling slot's real label.
        return limitWindowSeconds?.let(::windowDurationLabelRes) ?: R.string.window_label_generic
    }
    return providerNamedLabelRes(windowId)
}

/**
 * The provider's own name for a meter that has no label of its own here, shown after the label as
 * "7 días · Fable": a weekly limit Claude keeps for one model or product, or a meter this version
 * does not know yet (after the generic label). Null when the label already names the window, and for
 * the spend figure Claude's extra usage carries in the same field.
 */
fun quotaWindowLabelName(windowId: String, subLabel: String?): String? {
    val name = subLabel?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    val unnamedHere = windowId.startsWith(CLAUDE_SCOPED_WEEKLY_PREFIX) ||
        (windowId !in POSITIONAL_WINDOW_IDS && providerNamedLabelRes(windowId) == R.string.window_label_generic)
    return name.takeIf { unnamedHere }
}

@StringRes
private fun windowDurationLabelRes(limitWindowSeconds: Int): Int? = when (limitWindowSeconds) {
    FIVE_HOUR_SECONDS -> R.string.account_quota_five_hour_label
    DAY_SECONDS -> R.string.window_label_daily
    WEEK_SECONDS -> R.string.account_quota_weekly_label
    else -> null
}

@StringRes
private fun providerNamedLabelRes(windowId: String): Int = when (windowId) {
    "claude_5h_window" -> R.string.account_quota_five_hour_label
    "claude_7d_window" -> R.string.window_label_7d
    "claude_7d_opus_window" -> R.string.window_label_7d_opus
    "claude_7d_sonnet_window" -> R.string.window_label_7d_sonnet
    "claude_extra_usage" -> R.string.window_label_extra_usage
    else -> if (windowId.startsWith(CLAUDE_SCOPED_WEEKLY_PREFIX)) R.string.window_label_7d else R.string.window_label_generic
}
