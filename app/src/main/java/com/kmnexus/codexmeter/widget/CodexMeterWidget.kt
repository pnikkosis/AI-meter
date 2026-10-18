package com.kmnexus.codexmeter.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.TextUnit
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
import com.kmnexus.codexmeter.R
import com.kmnexus.codexmeter.domain.quota.QuotaLevel
import com.kmnexus.codexmeter.ui.navigation.CodexMeterLaunchDestination
import com.kmnexus.codexmeter.ui.quota.formatProviderBalance
import com.kmnexus.codexmeter.ui.quota.quotaErrorMessageRes
import com.kmnexus.codexmeter.ui.quota.quotaWindowLabelRes
import com.kmnexus.codexmeter.ui.quota.quotaWindowLabelString

/** One account's windows, sized from one figure (3×1) to four (4×2). */
class CodexMeterWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Responsive(WidgetLayoutVariant.referenceSizes)
    override val stateDefinition = PreferencesGlanceStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            val preferences = currentState<Preferences>()
            val widgetSize = LocalSize.current
            CodexMeterWidgetContent(
                context = context,
                state = preferences.toWidgetQuotaState(),
                widgetSize = widgetSize,
                layoutVariant = WidgetLayoutVariant.fromSize(widgetSize),
            )
        }
    }
}

@Composable
internal fun CodexMeterWidgetContent(
    context: Context,
    state: WidgetQuotaState,
    widgetSize: DpSize,
    layoutVariant: WidgetLayoutVariant,
) {
    WidgetSurface(onClick = actionStartActivity(state.launchIntent(context))) {
        when {
            state.isUnconfigured -> UnconfiguredContent(context, state)
            // Nothing current to show (signed out, no data yet, failed without a last snapshot):
            // say so instead of drawing empty value cells that could read as "no usage".
            state.fields.isEmpty() && state.status != WidgetQuotaStatus.Fresh -> StatusOnlyContent(context, state)
            else -> when (layoutVariant) {
                WidgetLayoutVariant.ThreeByOne -> ThreeByOneContent(context, state)
                WidgetLayoutVariant.FourByOne -> FourByOneContent(context, state)
                WidgetLayoutVariant.ThreeByTwo -> ThreeByTwoContent(context, state)
                WidgetLayoutVariant.FourByTwo -> FourByTwoContent(context, state)
            }
        }
    }
}

/** The fields this size shows (the first N, in the provider's order). */
private fun WidgetQuotaState.displayedFields(variant: WidgetLayoutVariant): List<WidgetField> =
    fields.take(variant.fieldCapacity)

// ---------- 3×1: who and how it is on the left, the one figure on the right ----------
@Composable
private fun ThreeByOneContent(context: Context, state: WidgetQuotaState) {
    val field = state.displayedFields(WidgetLayoutVariant.ThreeByOne).firstOrNull()
    Row(modifier = GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.Vertical.CenterVertically) {
        AccountIdentity(context, state, modifier = GlanceModifier.defaultWeight())
        Spacer(GlanceModifier.width(8.dp))
        if (field != null) {
            Column(horizontalAlignment = Alignment.Horizontal.End) {
                Text(
                    text = field.label(context),
                    style = TextStyle(color = WidgetColors.secondary, fontSize = 10.sp, fontWeight = FontWeight.Medium),
                    maxLines = 1,
                )
                Text(
                    text = field.valueText(context),
                    style = TextStyle(color = field.level.figureColor(), fontSize = 28.sp, fontWeight = FontWeight.Bold),
                    maxLines = 1,
                )
                Text(
                    text = field.suffixText(context, compact = true),
                    style = TextStyle(color = WidgetColors.secondary, fontSize = 10.sp, fontWeight = FontWeight.Medium),
                    maxLines = 1,
                )
            }
        }
    }
}

// ---------- 4×1: the account, then two figures with their bars ----------
@Composable
private fun FourByOneContent(context: Context, state: WidgetQuotaState) {
    val fields = state.displayedFields(WidgetLayoutVariant.FourByOne)
    Row(modifier = GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.Vertical.CenterVertically) {
        AccountIdentity(context, state, modifier = GlanceModifier.defaultWeight())
        fields.forEach { field ->
            FieldCell(
                context,
                field,
                valueSize = 20.sp,
                compact = true,
                modifier = GlanceModifier.defaultWeight().padding(start = 10.dp),
            )
        }
    }
}

// ---------- 3×2: the account on top, up to three windows as a list ----------
@Composable
private fun ThreeByTwoContent(context: Context, state: WidgetQuotaState) {
    val fields = state.displayedFields(WidgetLayoutVariant.ThreeByTwo)
    Column(modifier = GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.Vertical.CenterVertically) {
        AccountHeaderRow(context, state)
        // One column per window keeps every container within Glance's ten-children limit.
        fields.forEach { field ->
            Column(modifier = GlanceModifier.fillMaxWidth().padding(top = 7.dp)) {
                Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.Vertical.CenterVertically) {
                    Text(
                        text = field.label(context),
                        modifier = GlanceModifier.defaultWeight(),
                        style = TextStyle(color = WidgetColors.secondary, fontSize = 11.sp, fontWeight = FontWeight.Medium),
                        maxLines = 1,
                    )
                    FigureWithSuffix(context, field, valueSize = 15.sp, compact = false)
                }
                Spacer(GlanceModifier.height(3.dp))
                WidgetUsageBar(field.percent, field.level)
            }
        }
    }
}

// ---------- 4×2: the account on top, up to four windows two by two ----------
@Composable
private fun FourByTwoContent(context: Context, state: WidgetQuotaState) {
    val rows = state.displayedFields(WidgetLayoutVariant.FourByTwo).chunked(2)
    Column(modifier = GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.Vertical.CenterVertically) {
        AccountHeaderRow(context, state)
        rows.forEach { row ->
            Row(modifier = GlanceModifier.fillMaxWidth().padding(top = 10.dp)) {
                row.forEachIndexed { index, field ->
                    FieldCell(
                        context,
                        field,
                        valueSize = 22.sp,
                        compact = false,
                        showReset = true,
                        // Half the gap on each side, so both cells' bars are equally long.
                        modifier = GlanceModifier.defaultWeight().padding(
                            start = if (index > 0) 7.dp else 0.dp,
                            end = if (index == 0 && row.size > 1) 7.dp else 0.dp,
                        ),
                    )
                }
            }
        }
    }
}

/** Provider label, account name (when it has one of its own) and the status with a refresh button. */
@Composable
private fun AccountIdentity(context: Context, state: WidgetQuotaState, modifier: GlanceModifier = GlanceModifier) {
    Column(modifier = modifier) {
        WidgetProviderLabel(state.providerName)
        state.ownAccountName()?.let { name ->
            Text(
                text = name,
                style = TextStyle(color = WidgetColors.primary, fontSize = 14.sp, fontWeight = FontWeight.Medium),
                maxLines = 1,
            )
        }
        Spacer(GlanceModifier.height(2.dp))
        // Narrow layouts have height to spare but not width: a long status wraps instead of
        // hiding the time of the data.
        WidgetRefreshStatus(context, state.statusLine(context, compact = true), state.statusColor(), maxLines = 2)
    }
}

/** One line for the taller layouts: provider and account on the left, the status on the right. */
@Composable
private fun AccountHeaderRow(context: Context, state: WidgetQuotaState) {
    // A row measures its unweighted children first, in order: the provider label is never cut, the
    // status after it gets what is left, and the weighted account name gives way first.
    Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.Vertical.CenterVertically) {
        WidgetProviderLabel(state.providerName)
        Spacer(GlanceModifier.width(6.dp))
        Text(
            text = state.ownAccountName().orEmpty(),
            modifier = GlanceModifier.defaultWeight(),
            style = TextStyle(color = WidgetColors.primary, fontSize = 13.sp, fontWeight = FontWeight.Medium),
            maxLines = 1,
        )
        Spacer(GlanceModifier.width(6.dp))
        WidgetRefreshStatus(context, state.statusLine(context, compact = false), state.statusColor())
    }
}

@Composable
private fun FieldCell(
    context: Context,
    field: WidgetField,
    valueSize: TextUnit,
    compact: Boolean,
    modifier: GlanceModifier = GlanceModifier,
    showReset: Boolean = false,
) {
    Column(modifier = modifier) {
        Text(
            text = field.label(context),
            style = TextStyle(color = WidgetColors.secondary, fontSize = 10.sp, fontWeight = FontWeight.Medium),
            maxLines = 1,
        )
        FigureWithSuffix(context, field, valueSize, compact)
        Spacer(GlanceModifier.height(4.dp))
        WidgetUsageBar(field.percent, field.level)
        if (showReset) {
            Text(
                text = field.resetLine(context).orEmpty(),
                modifier = GlanceModifier.padding(top = 3.dp),
                style = TextStyle(color = WidgetColors.tertiary, fontSize = 9.sp, fontWeight = FontWeight.Medium),
                maxLines = 1,
            )
        }
    }
}

/** "67%" with what the number means next to it ("left", "used up"), as on Home. */
@Composable
private fun FigureWithSuffix(context: Context, field: WidgetField, valueSize: TextUnit, compact: Boolean) {
    Row(verticalAlignment = Alignment.Vertical.Bottom) {
        Text(
            text = field.valueText(context),
            style = TextStyle(color = field.level.figureColor(), fontSize = valueSize, fontWeight = FontWeight.Bold),
            maxLines = 1,
        )
        Spacer(GlanceModifier.width(3.dp))
        Text(
            text = field.suffixText(context, compact),
            modifier = GlanceModifier.padding(bottom = 2.dp),
            style = TextStyle(color = WidgetColors.secondary, fontSize = 10.sp, fontWeight = FontWeight.Medium),
            maxLines = 1,
        )
    }
}

@Composable
private fun StatusOnlyContent(context: Context, state: WidgetQuotaState) {
    Column(modifier = GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.Vertical.CenterVertically) {
        AccountIdentity(context, state)
        Text(
            text = context.getString(state.statusMessageResId()),
            modifier = GlanceModifier.padding(top = 4.dp),
            style = TextStyle(color = WidgetColors.secondary, fontSize = 10.sp, fontWeight = FontWeight.Medium),
            maxLines = 2,
        )
    }
}

@Composable
private fun UnconfiguredContent(context: Context, state: WidgetQuotaState) {
    Column(
        modifier = GlanceModifier.fillMaxSize(),
        verticalAlignment = Alignment.Vertical.CenterVertically,
        horizontalAlignment = Alignment.Horizontal.CenterHorizontally,
    ) {
        Text(
            text = context.getString(R.string.widget_unconfigured_title),
            style = TextStyle(color = WidgetColors.primary, fontSize = 13.sp, fontWeight = FontWeight.Bold),
            maxLines = 1,
        )
        Text(
            text = context.getString(
                if (state.hasAccounts) R.string.widget_unconfigured_hint else R.string.widget_no_accounts_hint,
            ),
            modifier = GlanceModifier.padding(top = 2.dp),
            style = TextStyle(color = WidgetColors.secondary, fontSize = 11.sp, fontWeight = FontWeight.Medium),
            maxLines = 1,
        )
        Text(
            text = context.getString(R.string.widget_long_press_to_configure),
            style = TextStyle(color = WidgetColors.tertiary, fontSize = 10.sp, fontWeight = FontWeight.Medium),
            maxLines = 1,
        )
    }
}

private fun WidgetField.label(context: Context): String =
    context.quotaWindowLabelString(quotaWindowLabelRes(windowId, limitWindowSeconds), labelName)

/** The account's own name, or null when it only repeats the provider's. */
private fun WidgetQuotaState.ownAccountName(): String? =
    accountName?.takeIf { it.isNotBlank() && it != providerName }

/**
 * "Normal · Updated 14:05"; just the status word when there is no data yet. [compact] layouts drop
 * "Updated" ("Normal · 14:05") so the time of the data still fits.
 */
private fun WidgetQuotaState.statusLine(context: Context, compact: Boolean): String {
    val status = context.getString(statusLabelResId())
    val updated = lastUpdatedAt ?: return status
    val format = if (compact) R.string.widget_status_compact_format else R.string.widget_status_updated_format
    return context.getString(format, status, updated.formatWidgetTime(context))
}

private fun WidgetQuotaState.statusColor(): ColorProvider =
    when (status) {
        WidgetQuotaStatus.AuthRequired -> WidgetColors.danger
        WidgetQuotaStatus.ErrorWithLastKnownGood -> WidgetColors.warning
        WidgetQuotaStatus.NoData -> if (errorMessageKey != null) WidgetColors.warning else WidgetColors.tertiary
        WidgetQuotaStatus.PossiblyStale,
        WidgetQuotaStatus.Expired,
        -> WidgetColors.tertiary
        WidgetQuotaStatus.NoAccount -> WidgetColors.secondary
        WidgetQuotaStatus.Fresh -> when (level) {
            QuotaLevel.Depleted -> WidgetColors.danger
            QuotaLevel.Warning -> WidgetColors.warning
            QuotaLevel.Normal,
            QuotaLevel.Caution,
            QuotaLevel.Unknown,
            -> WidgetColors.secondary
        }
    }

@androidx.annotation.StringRes
internal fun WidgetQuotaState.statusMessageResId(): Int = when (status) {
    WidgetQuotaStatus.AuthRequired -> R.string.widget_message_sign_in_again
    WidgetQuotaStatus.NoData, WidgetQuotaStatus.ErrorWithLastKnownGood ->
        if (errorMessageKey != null) {
            quotaErrorMessageRes(errorMessageKey)
        } else {
            R.string.widget_message_no_data
        }
    WidgetQuotaStatus.PossiblyStale, WidgetQuotaStatus.Expired -> R.string.widget_message_no_limits
    WidgetQuotaStatus.Fresh -> R.string.widget_message_no_limits
    WidgetQuotaStatus.NoAccount -> R.string.widget_no_accounts_hint
}

private fun WidgetField.valueText(context: Context): String =
    if (isBalance) {
        formatProviderBalance(balanceAmount, balanceCurrency)
            ?: context.getString(R.string.widget_percent_unavailable)
    } else {
        percent?.let { context.getString(R.string.widget_percent_format, it) }
            ?: context.getString(R.string.widget_percent_unavailable)
    }

/** What the figure means: "left", or "used up" once nothing is left; balances need no word. */
private fun WidgetField.suffixText(context: Context, compact: Boolean): String =
    when {
        isBalance || percent == null -> ""
        percent <= 0 -> context.getString(R.string.widget_used_up_suffix)
        compact -> context.getString(R.string.widget_left_suffix_short)
        else -> context.getString(R.string.home_window_available)
    }

private fun WidgetField.resetLine(context: Context): String? =
    resetAt?.let { context.getString(R.string.widget_reset_at_compact_format, it.formatWidgetTime(context)) }

private fun WidgetQuotaState.launchIntent(context: Context): Intent =
    widgetLaunchIntent(
        context,
        when (clickTarget) {
            WidgetClickTarget.Home -> CodexMeterLaunchDestination.Home
            WidgetClickTarget.AddAccount -> CodexMeterLaunchDestination.AddAccount
        },
    )

internal fun WidgetQuotaState.statusLabelResId(): Int =
    when (status) {
        WidgetQuotaStatus.NoAccount -> R.string.widget_connect_codex
        // The same words Home uses for each level, so a spent window reads "Used up" everywhere.
        WidgetQuotaStatus.Fresh -> when (level) {
            QuotaLevel.Depleted -> R.string.home_window_depleted
            QuotaLevel.Warning -> R.string.home_quota_status_warning
            QuotaLevel.Caution -> R.string.home_quota_status_caution
            QuotaLevel.Normal -> R.string.home_quota_status_normal
            QuotaLevel.Unknown -> R.string.home_quota_status_unavailable
        }
        WidgetQuotaStatus.PossiblyStale -> R.string.widget_status_possibly_stale
        WidgetQuotaStatus.Expired -> R.string.widget_status_expired
        WidgetQuotaStatus.AuthRequired -> R.string.widget_status_auth_required
        WidgetQuotaStatus.ErrorWithLastKnownGood -> errorStatusResId(errorMessageKey) ?: R.string.widget_status_refresh_failed
        WidgetQuotaStatus.NoData -> errorStatusResId(errorMessageKey) ?: R.string.widget_status_no_data
    }

/** Short status word naming the kind of the last error, so "offline" never looks like "no usage". */
internal fun errorStatusResId(errorMessageKey: String?): Int? = when (errorMessageKey) {
    null -> null
    "error_network" -> R.string.widget_status_offline
    "error_rate_limited" -> R.string.widget_status_rate_limited
    "error_provider_unavailable" -> R.string.widget_status_provider_error
    "error_response_invalid" -> R.string.widget_status_unreadable
    "error_auth_required" -> R.string.widget_status_auth_required
    else -> R.string.widget_status_refresh_failed
}
