package com.kmnexus.codexmeter.ui.home

import com.kmnexus.codexmeter.domain.quota.CurrentQuotaState
import com.kmnexus.codexmeter.domain.quota.CurrentQuotaStatus
import com.kmnexus.codexmeter.domain.quota.EXTRA_USAGE_WINDOW_ID
import com.kmnexus.codexmeter.domain.quota.QuotaLevelThresholds
import com.kmnexus.codexmeter.domain.quota.QuotaWindow
import com.kmnexus.codexmeter.domain.quota.QuotaWindowAvailability
import com.kmnexus.codexmeter.domain.quota.QuotaWindowDisplayKind
import com.kmnexus.codexmeter.domain.quota.quotaLevel
import com.kmnexus.codexmeter.providers.ProviderRegistry
import com.kmnexus.codexmeter.ui.providerPlanDisplayName
import com.kmnexus.codexmeter.ui.quota.formatProviderBalance
import com.kmnexus.codexmeter.ui.quota.quotaErrorMessageRes
import com.kmnexus.codexmeter.ui.quota.quotaWindowLabelName
import com.kmnexus.codexmeter.ui.quota.quotaWindowLabelRes

/** Turns every account's stored state into Home's cards. Pure: no clock, no I/O. */
class HomeUiStateMapper(
    private val thresholds: QuotaLevelThresholds,
) {
    fun map(entries: List<HomeAccountEntry>, isRefreshing: Boolean): HomeUiState {
        val visible = entries.filterNot { it.hidden }.mapNotNull { it.state.toCard() }
        return HomeUiState(
            isLoading = false,
            isRefreshing = isRefreshing,
            accounts = visible,
            hiddenAccountCount = entries.count { it.hidden },
            lastUpdatedAt = visible.mapNotNull { it.lastUpdatedAt }.maxOrNull(),
        )
    }

    private fun CurrentQuotaState.toCard(): HomeAccountCardUi? {
        val account = account ?: return null
        val status = status.toHomeStatus()
        return HomeAccountCardUi(
            localAccountId = account.localAccountId.value,
            providerId = account.providerId.value,
            providerAccountId = account.providerAccountId?.value,
            providerName = ProviderRegistry.displayNameFor(account.providerId),
            displayName = account.displayName,
            planName = providerPlanDisplayName(account.providerId, snapshot?.planType),
            // A slot the provider simply did not send (Codex without a 5-hour window) would only be
            // a blank placeholder; a window that failed to decode stays visible as "no figure".
            windows = snapshot?.windows.orEmpty()
                .filterNot { it.availability == QuotaWindowAvailability.Missing }
                .map { it.toWindowUi() },
            status = status,
            lastUpdatedAt = snapshot?.fetchedAt,
            errorMessageResId = if (status == HomeAccountStatus.RefreshFailed) {
                quotaErrorMessageRes(error?.safeMessageKey)
            } else {
                null
            },
        )
    }

    private fun QuotaWindow.toWindowUi(): HomeWindowUi =
        HomeWindowUi(
            windowId = windowId.value,
            labelResId = quotaWindowLabelRes(windowId.value, limitWindowSeconds),
            labelName = quotaWindowLabelName(windowId.value, subLabel),
            percentLeft = remainingPercent?.takeIf {
                displayKind != QuotaWindowDisplayKind.Balance &&
                    (availability == QuotaWindowAvailability.Available || availability == QuotaWindowAvailability.Depleted)
            },
            valueText = when {
                windowId.value == EXTRA_USAGE_WINDOW_ID -> subLabel
                displayKind == QuotaWindowDisplayKind.Balance -> formatProviderBalance(balanceAmount, balanceCurrency)
                displayKind == QuotaWindowDisplayKind.UsageCount && usedCount != null && limitCount != null ->
                    "$usedCount / $limitCount"
                else -> null
            },
            level = quotaLevel(thresholds),
            resetAt = resetAt,
        )

    private fun CurrentQuotaStatus.toHomeStatus(): HomeAccountStatus =
        when (this) {
            CurrentQuotaStatus.Fresh,
            CurrentQuotaStatus.Loading -> HomeAccountStatus.Fresh
            CurrentQuotaStatus.PossiblyStale -> HomeAccountStatus.Stale
            CurrentQuotaStatus.Expired -> HomeAccountStatus.Expired
            CurrentQuotaStatus.ErrorWithLastKnownGood -> HomeAccountStatus.RefreshFailed
            CurrentQuotaStatus.AuthRequired -> HomeAccountStatus.NeedsSignIn
            CurrentQuotaStatus.NoData,
            CurrentQuotaStatus.Unauthenticated -> HomeAccountStatus.NoData
        }
}
