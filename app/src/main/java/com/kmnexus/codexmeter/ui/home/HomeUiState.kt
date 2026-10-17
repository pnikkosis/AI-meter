package com.kmnexus.codexmeter.ui.home

import androidx.annotation.StringRes
import com.kmnexus.codexmeter.domain.quota.CurrentQuotaState
import com.kmnexus.codexmeter.domain.quota.QuotaLevel
import java.time.Instant

/** One saved account's state for Home, and whether the user hid it there. */
data class HomeAccountEntry(
    val state: CurrentQuotaState,
    val hidden: Boolean,
)

/** How far the figures on a card can be trusted. Values stay visible in every case but [NoData]. */
enum class HomeAccountStatus {
    Fresh,
    /** Last update over 30 minutes ago. */
    Stale,
    /** Last update over 2 hours ago. */
    Expired,
    /** The latest refresh failed; the card keeps the last good figures. */
    RefreshFailed,
    /** The provider no longer accepts the session; the account has to sign in again. */
    NeedsSignIn,
    /** No figures yet (never refreshed): shown as missing, never as zero. */
    NoData,
}

data class HomeWindowUi(
    val windowId: String,
    @get:StringRes val labelResId: Int,
    /** The provider's own name for a meter with no label of its own here; see quotaWindowLabelName. */
    val labelName: String?,
    /** Share still available, 0..100; null when the provider sent no figure. */
    val percentLeft: Int?,
    /** A figure that is not a percent: Claude's extra-usage spend, a balance or a count. */
    val valueText: String?,
    val level: QuotaLevel,
    val resetAt: Instant?,
)

data class HomeAccountCardUi(
    val localAccountId: String,
    val providerId: String,
    val providerAccountId: String?,
    val providerName: String,
    val displayName: String,
    val planName: String?,
    val windows: List<HomeWindowUi>,
    val status: HomeAccountStatus,
    val lastUpdatedAt: Instant?,
    @get:StringRes val errorMessageResId: Int?,
)

data class HomeUiState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    /** Visible accounts, in the user's order. */
    val accounts: List<HomeAccountCardUi> = emptyList(),
    val hiddenAccountCount: Int = 0,
    /** Newest update among the visible accounts. */
    val lastUpdatedAt: Instant? = null,
) {
    val accountCount: Int get() = accounts.size + hiddenAccountCount
}
