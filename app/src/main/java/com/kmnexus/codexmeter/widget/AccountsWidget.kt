package com.kmnexus.codexmeter.widget

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalSize
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.kmnexus.codexmeter.CodexMeterApp
import com.kmnexus.codexmeter.R
import com.kmnexus.codexmeter.ui.navigation.CodexMeterLaunchDestination
import com.kmnexus.codexmeter.ui.quota.quotaWindowLabelRes
import com.kmnexus.codexmeter.ui.quota.quotaWindowLabelString
import java.time.Instant
import kotlinx.coroutines.CancellationException

/** Several accounts at once: each with the number that limits it, or every window when wide. */
class AccountsWidget : GlanceAppWidget() {
    // Exact sizes, because how many rows fit depends on the real height.
    override val sizeMode: SizeMode = SizeMode.Exact
    override val stateDefinition = PreferencesGlanceStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        fillIfNothingStored(context, id)
        provideContent {
            AccountsWidgetContent(
                context = context,
                state = currentState<Preferences>().toAccountsWidgetState(),
                size = LocalSize.current,
            )
        }
    }

    /**
     * Placed without its dialog (configuration is optional), a widget has nothing stored yet. Fill
     * it here once so it shows the accounts right away instead of waiting for the next refresh.
     */
    private suspend fun fillIfNothingStored(context: Context, id: GlanceId) {
        val app = context.applicationContext as? CodexMeterApp ?: return
        try {
            val stored = getAppWidgetState(context, PreferencesGlanceStateDefinition, id)
            if (stored.hasAccountsWidgetState()) return
            val state = app.accountsWidgetStateLoader.loadAccountsWidgetState(stored.toAccountsWidgetConfiguration())
            updateAppWidgetState(context, id) { preferences -> preferences.writeAccountsWidgetState(state) }
        } catch (exception: CancellationException) {
            throw exception
        } catch (_: Exception) {
            // Drawn without rows; the next refresh writes them.
        }
    }
}

@Composable
internal fun AccountsWidgetContent(context: Context, state: AccountsWidgetState, size: DpSize) {
    val destination = if (state.hasAccounts) CodexMeterLaunchDestination.Home else CodexMeterLaunchDestination.AddAccount
    WidgetSurface(onClick = actionStartActivity(widgetLaunchIntent(context, destination))) {
        when {
            !state.hasAccounts -> AccountsWidgetMessage(
                context,
                title = R.string.accounts_widget_empty_title,
                hint = R.string.accounts_widget_empty_hint,
            )
            state.rows.isEmpty() -> AccountsWidgetMessage(
                context,
                title = R.string.accounts_widget_nothing_title,
                hint = R.string.accounts_widget_nothing_hint,
            )
            else -> AccountsList(context, state.rows, size)
        }
    }
}

@Composable
private fun AccountsList(context: Context, rows: List<AccountsWidgetRow>, size: DpSize) {
    val cells = accountsWidgetCellCapacity(size.width)
    val shown = rows.take(accountsWidgetRowCapacity(size.height, wide = cells > 0))
    Column(modifier = GlanceModifier.fillMaxSize()) {
        AccountsHeader(context, rows, notShown = rows.size - shown.size)
        // Rows carry their own top margin: no spacers, so the column stays within Glance's
        // ten-children limit (header + up to ACCOUNTS_WIDGET_MAX_ROWS rows).
        shown.forEach { row ->
            val modifier = GlanceModifier.fillMaxWidth().padding(top = 6.dp)
            if (cells > 0) WideAccountRow(context, row, cells, modifier) else NarrowAccountRow(context, row, modifier)
        }
    }
}

/** App name, how many accounts did not fit, and the time of the newest data with a refresh button. */
@Composable
private fun AccountsHeader(context: Context, rows: List<AccountsWidgetRow>, notShown: Int) {
    val newest = rows.mapNotNull { it.lastUpdatedAt?.toInstantOrNull() }.maxOrNull()
    Row(
        modifier = GlanceModifier.fillMaxWidth().height(ACCOUNTS_WIDGET_HEADER_HEIGHT),
        verticalAlignment = Alignment.Vertical.CenterVertically,
    ) {
        Text(
            text = context.getString(R.string.app_name),
            style = TextStyle(color = WidgetColors.secondary, fontSize = 11.sp, fontWeight = FontWeight.Bold),
            maxLines = 1,
        )
        if (notShown > 0) {
            Text(
                text = context.getString(R.string.accounts_widget_more_format, notShown),
                modifier = GlanceModifier.padding(start = 6.dp),
                style = TextStyle(color = WidgetColors.tertiary, fontSize = 11.sp, fontWeight = FontWeight.Medium),
                maxLines = 1,
            )
        }
        Spacer(GlanceModifier.defaultWeight())
        WidgetRefreshStatus(context, newest?.formatWidgetTime(context).orEmpty(), WidgetColors.tertiary)
    }
}

/** Narrow: provider, name and the account's one number, then its bar or what is wrong. */
@Composable
private fun NarrowAccountRow(context: Context, row: AccountsWidgetRow, modifier: GlanceModifier) {
    val headline = row.headline
    Column(modifier = modifier) {
        Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.Vertical.CenterVertically) {
            WidgetProviderLabel(row.providerName)
            Spacer(GlanceModifier.width(6.dp))
            Text(
                text = row.accountName,
                modifier = GlanceModifier.defaultWeight(),
                style = TextStyle(color = WidgetColors.primary, fontSize = 12.sp, fontWeight = FontWeight.Medium),
                maxLines = 1,
            )
            if (headline != null) {
                Text(
                    text = headline.label(context),
                    modifier = GlanceModifier.padding(start = 6.dp, end = 4.dp),
                    style = TextStyle(color = WidgetColors.tertiary, fontSize = 10.sp, fontWeight = FontWeight.Medium),
                    maxLines = 1,
                )
            }
            Text(
                text = headline.percentText(context),
                style = TextStyle(
                    color = headline?.level?.figureColor() ?: WidgetColors.tertiary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                ),
                maxLines = 1,
            )
        }
        Spacer(GlanceModifier.height(4.dp))
        if (row.isCurrent && headline != null) {
            WidgetUsageBar(headline.percentLeft, headline.level)
        } else {
            Text(
                text = row.statusText(context),
                style = TextStyle(color = row.statusColor(), fontSize = 10.sp, fontWeight = FontWeight.Medium),
                maxLines = 1,
            )
        }
    }
}

/** Wide: name and provider (plus what is wrong, if anything) beside one cell per window. */
@Composable
private fun WideAccountRow(context: Context, row: AccountsWidgetRow, cells: Int, modifier: GlanceModifier) {
    Row(modifier = modifier, verticalAlignment = Alignment.Vertical.CenterVertically) {
        Column(modifier = GlanceModifier.defaultWeight()) {
            Text(
                text = row.accountName,
                style = TextStyle(color = WidgetColors.primary, fontSize = 12.sp, fontWeight = FontWeight.Medium),
                maxLines = 1,
            )
            Row(verticalAlignment = Alignment.Vertical.CenterVertically) {
                WidgetProviderLabel(row.providerName)
                if (!row.isCurrent || row.windows.isEmpty()) {
                    Spacer(GlanceModifier.width(4.dp))
                    Text(
                        text = context.getString(R.string.accounts_widget_status_after_provider_format, row.statusText(context)),
                        style = TextStyle(color = row.statusColor(), fontSize = 10.sp, fontWeight = FontWeight.Medium),
                        maxLines = 1,
                    )
                }
            }
        }
        row.cellWindows(cells).forEach { window ->
            // Width includes the gap, given as start padding: Glance padding sits inside the width.
            Column(
                modifier = GlanceModifier
                    .width(ACCOUNTS_WIDGET_CELL_WIDTH + ACCOUNTS_WIDGET_CELL_GAP)
                    .padding(start = ACCOUNTS_WIDGET_CELL_GAP),
            ) {
                Text(
                    text = window.label(context),
                    style = TextStyle(color = WidgetColors.secondary, fontSize = 9.sp, fontWeight = FontWeight.Medium),
                    maxLines = 1,
                )
                Text(
                    text = window.percentText(context),
                    style = TextStyle(color = window.level.figureColor(), fontSize = 14.sp, fontWeight = FontWeight.Bold),
                    maxLines = 1,
                )
                Spacer(GlanceModifier.height(2.dp))
                WidgetUsageBar(window.percentLeft, window.level)
            }
        }
    }
}

@Composable
private fun AccountsWidgetMessage(context: Context, @StringRes title: Int, @StringRes hint: Int) {
    Column(
        modifier = GlanceModifier.fillMaxSize(),
        verticalAlignment = Alignment.Vertical.CenterVertically,
        horizontalAlignment = Alignment.Horizontal.CenterHorizontally,
    ) {
        Text(
            text = context.getString(title),
            style = TextStyle(color = WidgetColors.primary, fontSize = 13.sp, fontWeight = FontWeight.Bold),
            maxLines = 1,
        )
        Text(
            text = context.getString(hint),
            modifier = GlanceModifier.padding(top = 2.dp),
            style = TextStyle(color = WidgetColors.secondary, fontSize = 11.sp, fontWeight = FontWeight.Medium),
            maxLines = 2,
        )
    }
}

private fun AccountsWidgetWindow.label(context: Context): String =
    context.quotaWindowLabelString(quotaWindowLabelRes(windowId, limitWindowSeconds), labelName)

private fun AccountsWidgetWindow?.percentText(context: Context): String =
    this?.let { context.getString(R.string.widget_percent_format, it.percentLeft) }
        ?: context.getString(R.string.widget_percent_unavailable)

/** What a row says instead of a bar: why its figures are old or missing, with their time if any. */
private fun AccountsWidgetRow.statusText(context: Context): String {
    val word = context.getString(
        when (status) {
            WidgetQuotaStatus.PossiblyStale -> R.string.widget_status_possibly_stale
            WidgetQuotaStatus.Expired -> R.string.widget_status_expired
            WidgetQuotaStatus.AuthRequired -> R.string.widget_status_auth_required
            WidgetQuotaStatus.ErrorWithLastKnownGood ->
                errorStatusResId(errorMessageKey) ?: R.string.widget_status_refresh_failed
            WidgetQuotaStatus.NoData -> errorStatusResId(errorMessageKey) ?: R.string.widget_status_no_data
            WidgetQuotaStatus.Fresh -> R.string.widget_status_no_data
            WidgetQuotaStatus.NoAccount -> R.string.widget_connect_codex
        },
    )
    val dataTime = lastUpdatedAt?.toInstantOrNull()
        ?.takeIf { status != WidgetQuotaStatus.Fresh && status != WidgetQuotaStatus.AuthRequired }
        ?: return word
    return context.getString(R.string.widget_status_compact_format, word, dataTime.formatWidgetTime(context))
}

private fun AccountsWidgetRow.statusColor(): ColorProvider =
    when (status) {
        WidgetQuotaStatus.AuthRequired -> WidgetColors.danger
        WidgetQuotaStatus.ErrorWithLastKnownGood -> WidgetColors.warning
        WidgetQuotaStatus.NoData -> if (errorMessageKey != null) WidgetColors.warning else WidgetColors.tertiary
        WidgetQuotaStatus.Fresh,
        WidgetQuotaStatus.PossiblyStale,
        WidgetQuotaStatus.Expired,
        WidgetQuotaStatus.NoAccount,
        -> WidgetColors.tertiary
    }

/** No cells (narrow rows) below the wide width; two cells, or three on very wide widgets. */
internal fun accountsWidgetCellCapacity(width: Dp): Int =
    when {
        width < ACCOUNTS_WIDGET_WIDE_WIDTH -> 0
        width < ACCOUNTS_WIDGET_EXTRA_WIDE_WIDTH -> 2
        else -> 3
    }

private fun String.toInstantOrNull(): Instant? = runCatching { Instant.parse(this) }.getOrNull()

private val ACCOUNTS_WIDGET_EXTRA_WIDE_WIDTH = 360.dp
private val ACCOUNTS_WIDGET_CELL_WIDTH = 76.dp
private val ACCOUNTS_WIDGET_CELL_GAP = 10.dp
