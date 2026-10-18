package com.kmnexus.codexmeter.widget

import com.kmnexus.codexmeter.domain.quota.CurrentQuotaState
import com.kmnexus.codexmeter.domain.quota.CurrentQuotaStatus
import com.kmnexus.codexmeter.domain.quota.QuotaLevel
import com.kmnexus.codexmeter.domain.quota.QuotaLevelThresholds
import com.kmnexus.codexmeter.domain.quota.QuotaWindow
import com.kmnexus.codexmeter.domain.quota.mostUrgent
import com.kmnexus.codexmeter.domain.quota.quotaLevel
import com.kmnexus.codexmeter.domain.quota.quotaLevelThresholds
import com.kmnexus.codexmeter.domain.quota.QuotaWindowAvailability
import com.kmnexus.codexmeter.domain.quota.QuotaWindowDisplayKind
import com.kmnexus.codexmeter.domain.settings.NotificationPreferences
import com.kmnexus.codexmeter.providers.ProviderRegistry
import com.kmnexus.codexmeter.ui.quota.quotaWindowLabelName

class WidgetQuotaStateFactory(
    private val providerDisplayName: String = UNCONFIGURED_HEADER,
) {
    /** 微件未选账号时的引导态。 */
    fun unconfigured(hasAccounts: Boolean): WidgetQuotaState =
        WidgetQuotaState(
            status = WidgetQuotaStatus.NoAccount,
            providerName = providerDisplayName,
            providerId = null,
            localAccountId = null,
            accountName = null,
            level = QuotaLevel.Unknown,
            // Tapping an unconfigured widget opens the app's Home tab (where the no-account state already
            // surfaces an add-account entry). It must not deep-link to the retired provider-selection
            // screen; the no-accounts hint still tells the user to add an account in-app first.
            clickTarget = WidgetClickTarget.Home,
            fields = emptyList(),
            isUnconfigured = true,
            hasAccounts = hasAccounts,
        )

    fun create(
        state: CurrentQuotaState,
        notificationPreferences: NotificationPreferences = NotificationPreferences(),
        selectedWindowIds: List<String> = emptyList(),
    ): WidgetQuotaState {
        val account = state.account
        if (account == null) {
            return unconfigured(hasAccounts = false)
        }
        val isCurrent = state.status == CurrentQuotaStatus.Fresh || state.status == CurrentQuotaStatus.Loading
        val fields = state.buildFields(selectedWindowIds, notificationPreferences.quotaLevelThresholds())
            // Values that are not current are drawn muted so they never read as live usage.
            .let { built -> if (isCurrent) built else built.map { it.copy(level = QuotaLevel.Unknown) } }
        return WidgetQuotaState(
            status = state.status.toWidgetQuotaStatus(),
            providerName = ProviderRegistry.displayNameFor(account.providerId),
            providerId = account.providerId.value,
            localAccountId = account.localAccountId.value,
            accountName = account.displayName,
            level = if (isCurrent) fields.map { it.level }.mostUrgent() else QuotaLevel.Unknown,
            clickTarget = WidgetClickTarget.Home,
            fields = fields,
            isUnconfigured = false,
            hasAccounts = true,
            lastUpdatedAt = state.snapshot?.fetchedAt,
            errorMessageKey = state.error?.safeMessageKey,
        )
    }

    private fun CurrentQuotaState.buildFields(
        selectedWindowIds: List<String>,
        thresholds: QuotaLevelThresholds,
    ): List<WidgetField> {
        val windows = snapshot?.windows.orEmpty()
        val selected = if (selectedWindowIds.isEmpty()) {
            windows
        } else {
            windows.filter { selectedWindowIds.contains(it.windowId.value) }
        }
        return selected
            .filter { it.isDisplayable() }
            .take(WIDGET_MAX_FIELDS)
            .map { window ->
                val isBalance = window.displayKind == QuotaWindowDisplayKind.Balance
                WidgetField(
                    windowId = window.windowId.value,
                    isBalance = isBalance,
                    percent = if (isBalance) null else window.displayPercent,
                    balanceAmount = if (isBalance) window.balanceAmount else null,
                    balanceCurrency = if (isBalance) window.balanceCurrency else null,
                    resetAt = window.resetAt,
                    level = window.quotaLevel(thresholds),
                    limitWindowSeconds = window.limitWindowSeconds,
                    labelName = quotaWindowLabelName(window.windowId.value, window.subLabel),
                )
            }
    }

    private fun QuotaWindow.isDisplayable(): Boolean =
        when (displayKind) {
            QuotaWindowDisplayKind.Balance ->
                availability == QuotaWindowAvailability.Available && !balanceAmount.isNullOrBlank()
            // A spent window is shown as 0 % left: hiding it would hide the most important number.
            else ->
                (availability == QuotaWindowAvailability.Available || availability == QuotaWindowAvailability.Depleted) &&
                    displayPercent != null
        }

    private companion object {
        // Header shown on the unconfigured/no-account widget: app branding, not a single provider.
        const val UNCONFIGURED_HEADER = "AIMeter"
    }
}
