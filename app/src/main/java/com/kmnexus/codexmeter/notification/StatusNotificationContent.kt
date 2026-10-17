package com.kmnexus.codexmeter.notification

import androidx.annotation.StringRes
import com.kmnexus.codexmeter.R
import com.kmnexus.codexmeter.domain.model.ProviderAccount
import com.kmnexus.codexmeter.domain.quota.CurrentQuotaState
import com.kmnexus.codexmeter.domain.quota.CurrentQuotaStatus
import com.kmnexus.codexmeter.domain.quota.QuotaWindow
import com.kmnexus.codexmeter.domain.quota.QuotaWindowAvailability
import com.kmnexus.codexmeter.domain.quota.headlineWindow
import com.kmnexus.codexmeter.domain.quota.limitWindows
import com.kmnexus.codexmeter.providers.ProviderRegistry
import com.kmnexus.codexmeter.ui.quota.quotaWindowLabelName
import com.kmnexus.codexmeter.ui.quota.quotaWindowLabelRes

/**
 * Text of the persistent status notification for any number of accounts. The title gives each
 * account the share left of the window that limits it ("Left: Codex 0% · Claude 67%"); the body has
 * one line per account with every window. Figures come only from what the provider reported: an
 * account without them says so instead of showing a number, and old or failed figures are marked.
 */
internal object StatusNotificationContent {
    fun title(states: List<CurrentQuotaState>): NotificationText {
        val accounts = states.mapNotNull { state -> state.account?.let { it to state } }
        if (accounts.isEmpty()) return NotificationText(R.string.notification_status_title)
        val repeatedProviders = accounts.groupingBy { (account, _) -> account.providerId }
            .eachCount()
            .filterValues { it > 1 }
            .keys
        val entries = accounts.map { (account, state) ->
            val label = if (account.providerId in repeatedProviders) {
                NotificationText(
                    R.string.notification_status_account_label_format,
                    listOf(providerName(account), account.displayName),
                )
            } else {
                providerName(account)
            }
            val headline = state.snapshot?.windows.orEmpty().headlineWindow()?.remainingPercent
            if (headline == null) {
                NotificationText(R.string.notification_status_no_figure_format, listOf(label))
            } else {
                NotificationText(R.string.notification_status_figure_format, listOf(label, headline))
            }
        }
        return NotificationText(
            R.string.notification_status_title_figures_format,
            listOf(entries.joinedWith(R.string.notification_status_join_format)),
        )
    }

    fun body(states: List<CurrentQuotaState>): NotificationText {
        val lines = states.mapNotNull { state -> state.account?.let { accountLine(it, state) } }
        if (lines.isEmpty()) return NotificationText(R.string.notification_status_body_unauthenticated)
        return lines.joinedWith(R.string.notification_status_lines_format)
    }

    private fun accountLine(account: ProviderAccount, state: CurrentQuotaState): NotificationText {
        val identity = NotificationText(
            R.string.notification_status_join_format,
            listOf(providerName(account), account.displayName),
        )
        val windowFigures = state.snapshot?.windows.orEmpty().limitWindows().mapNotNull(::windowFigure)
        val figures = if (windowFigures.isEmpty()) {
            NotificationText(R.string.notification_status_no_figures)
        } else {
            windowFigures.joinedWith(R.string.notification_status_list_format)
        }
        val line = NotificationText(R.string.notification_status_line_format, listOf(identity, figures))
        val note = noteResource(state.status) ?: return line
        return NotificationText(R.string.notification_status_join_format, listOf(line, NotificationText(note)))
    }

    private fun windowFigure(window: QuotaWindow): NotificationText? {
        val left = window.remainingPercent ?: return null
        val baseLabel = NotificationText(quotaWindowLabelRes(window.windowId.value, window.limitWindowSeconds))
        val label = quotaWindowLabelName(window.windowId.value, window.subLabel)
            ?.let { name -> NotificationText(R.string.window_label_named_format, listOf(baseLabel, name)) }
            ?: baseLabel
        return if (window.availability == QuotaWindowAvailability.Depleted || left <= 0) {
            NotificationText(R.string.notification_status_window_used_up_format, listOf(label))
        } else {
            NotificationText(R.string.notification_status_figure_format, listOf(label, left))
        }
    }

    @StringRes
    private fun noteResource(status: CurrentQuotaStatus): Int? =
        when (status) {
            CurrentQuotaStatus.PossiblyStale,
            CurrentQuotaStatus.Expired,
            -> R.string.notification_status_note_stale
            CurrentQuotaStatus.ErrorWithLastKnownGood -> R.string.notification_status_note_failed
            CurrentQuotaStatus.AuthRequired -> R.string.notification_status_note_sign_in
            CurrentQuotaStatus.Unauthenticated,
            CurrentQuotaStatus.Loading,
            CurrentQuotaStatus.Fresh,
            CurrentQuotaStatus.NoData,
            -> null
        }

    private fun providerName(account: ProviderAccount): String = ProviderRegistry.displayNameFor(account.providerId)

    private fun List<NotificationText>.joinedWith(@StringRes formatResId: Int): NotificationText =
        reduce { joined, next -> NotificationText(formatResId, listOf(joined, next)) }
}
