package com.kmnexus.codexmeter.widget

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kmnexus.codexmeter.domain.quota.CurrentQuotaState
import com.kmnexus.codexmeter.domain.quota.CurrentQuotaStatus
import com.kmnexus.codexmeter.domain.quota.QuotaLevel
import com.kmnexus.codexmeter.domain.quota.QuotaLevelThresholds
import com.kmnexus.codexmeter.domain.quota.headlineWindow
import com.kmnexus.codexmeter.domain.quota.limitWindows
import com.kmnexus.codexmeter.domain.quota.quotaLevel
import com.kmnexus.codexmeter.providers.ProviderRegistry
import com.kmnexus.codexmeter.ui.quota.quotaWindowLabelName
import kotlinx.serialization.Serializable

/** Which accounts the accounts widget lists: every account visible on Home, or the chosen ones. */
data class AccountsWidgetConfiguration(
    val chosenAccountIds: List<String> = emptyList(),
) {
    val followsVisibleAccounts: Boolean
        get() = chosenAccountIds.isEmpty()
}

/**
 * One window of an account row. Labels stay ids and durations so they resolve against the current
 * resources when drawn; resource ids are never stored.
 */
@Serializable
data class AccountsWidgetWindow(
    val windowId: String,
    val limitWindowSeconds: Int? = null,
    val labelName: String? = null,
    val percentLeft: Int,
    /** [QuotaLevel.Unknown] when the figure is not current, so it is drawn muted. */
    val level: QuotaLevel,
    val resetAt: String? = null,
)

@Serializable
data class AccountsWidgetRow(
    val providerId: String,
    val localAccountId: String,
    val providerName: String,
    val accountName: String,
    val status: WidgetQuotaStatus,
    /** The limit windows that have a figure, in the provider's order. */
    val windows: List<AccountsWidgetWindow>,
    /** The window with the least left: the account's one number. */
    val headlineWindowId: String?,
    val lastUpdatedAt: String? = null,
    val errorMessageKey: String? = null,
) {
    val headline: AccountsWidgetWindow?
        get() = windows.firstOrNull { it.windowId == headlineWindowId }

    val isCurrent: Boolean
        get() = status == WidgetQuotaStatus.Fresh

    /**
     * Up to [capacity] windows for the wide layout's cells, in the provider's order. When they do
     * not all fit, the headline window always stays, so the limiting figure is never left out.
     */
    fun cellWindows(capacity: Int): List<AccountsWidgetWindow> {
        if (windows.size <= capacity) return windows
        val headline = headline ?: return windows.take(capacity)
        val others = windows.filter { it != headline }.take(capacity - 1)
        return windows.filter { it == headline || it in others }
    }
}

data class AccountsWidgetState(
    val rows: List<AccountsWidgetRow> = emptyList(),
    /** Whether any account is saved at all, to tell "connect one" from "none chosen to show". */
    val hasAccounts: Boolean = false,
)

fun interface AccountsWidgetStateLoader {
    suspend fun loadAccountsWidgetState(configuration: AccountsWidgetConfiguration): AccountsWidgetState
}

class AccountsWidgetStateFactory(
    private val thresholds: QuotaLevelThresholds,
) {
    fun create(states: List<CurrentQuotaState>, hasAccounts: Boolean): AccountsWidgetState =
        AccountsWidgetState(
            rows = states.mapNotNull { it.toRow() },
            hasAccounts = hasAccounts,
        )

    private fun CurrentQuotaState.toRow(): AccountsWidgetRow? {
        val account = account ?: return null
        val isCurrent = status == CurrentQuotaStatus.Fresh || status == CurrentQuotaStatus.Loading
        val windows = snapshot?.windows.orEmpty()
        return AccountsWidgetRow(
            providerId = account.providerId.value,
            localAccountId = account.localAccountId.value,
            providerName = ProviderRegistry.displayNameFor(account.providerId),
            accountName = account.displayName,
            status = status.toWidgetQuotaStatus(),
            windows = windows.limitWindows().mapNotNull { window ->
                val left = window.remainingPercent ?: return@mapNotNull null
                AccountsWidgetWindow(
                    windowId = window.windowId.value,
                    limitWindowSeconds = window.limitWindowSeconds,
                    labelName = quotaWindowLabelName(window.windowId.value, window.subLabel),
                    percentLeft = left,
                    level = if (isCurrent) window.quotaLevel(thresholds) else QuotaLevel.Unknown,
                    resetAt = window.resetAt?.toString(),
                )
            },
            headlineWindowId = windows.headlineWindow()?.windowId?.value,
            lastUpdatedAt = snapshot?.fetchedAt?.toString(),
            errorMessageKey = error?.safeMessageKey,
        )
    }
}

internal fun CurrentQuotaStatus.toWidgetQuotaStatus(): WidgetQuotaStatus =
    when (this) {
        CurrentQuotaStatus.Unauthenticated -> WidgetQuotaStatus.NoAccount
        CurrentQuotaStatus.Loading,
        CurrentQuotaStatus.Fresh,
        -> WidgetQuotaStatus.Fresh
        CurrentQuotaStatus.PossiblyStale -> WidgetQuotaStatus.PossiblyStale
        CurrentQuotaStatus.Expired -> WidgetQuotaStatus.Expired
        CurrentQuotaStatus.AuthRequired -> WidgetQuotaStatus.AuthRequired
        CurrentQuotaStatus.ErrorWithLastKnownGood -> WidgetQuotaStatus.ErrorWithLastKnownGood
        CurrentQuotaStatus.NoData -> WidgetQuotaStatus.NoData
    }

/**
 * Rows that fit under the header; never fewer than one, so a small widget still says something, and
 * never more than [ACCOUNTS_WIDGET_MAX_ROWS].
 */
internal fun accountsWidgetRowCapacity(height: Dp, wide: Boolean): Int {
    val available = height - ACCOUNTS_WIDGET_VERTICAL_PADDING - ACCOUNTS_WIDGET_HEADER_HEIGHT
    val pitch = if (wide) ACCOUNTS_WIDGET_WIDE_ROW_PITCH else ACCOUNTS_WIDGET_NARROW_ROW_PITCH
    return (available / pitch).toInt().coerceIn(1, ACCOUNTS_WIDGET_MAX_ROWS)
}

/** Glance containers take at most ten children; the list column also holds the header. */
internal const val ACCOUNTS_WIDGET_MAX_ROWS = 8

/** From this width on, rows show each window with its bar instead of only the account's number. */
internal val ACCOUNTS_WIDGET_WIDE_WIDTH = 250.dp

internal val ACCOUNTS_WIDGET_VERTICAL_PADDING = 20.dp
internal val ACCOUNTS_WIDGET_HEADER_HEIGHT = 18.dp
internal val ACCOUNTS_WIDGET_NARROW_ROW_PITCH = 40.dp
internal val ACCOUNTS_WIDGET_WIDE_ROW_PITCH = 44.dp
