package com.kmnexus.codexmeter.widget

import android.content.Context
import android.content.Intent
import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.Action
import androidx.glance.action.clickable
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider as GlanceColorProvider
import com.kmnexus.codexmeter.MainActivity
import com.kmnexus.codexmeter.R
import com.kmnexus.codexmeter.domain.quota.QuotaLevel
import com.kmnexus.codexmeter.ui.navigation.CodexMeterLaunchDestination
import com.kmnexus.codexmeter.ui.navigation.CodexMeterRoute
import com.kmnexus.codexmeter.ui.theme.CodexMeterColorPalette
import com.kmnexus.codexmeter.ui.theme.DarkCodexMeterColors
import com.kmnexus.codexmeter.ui.theme.LightCodexMeterColors
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

/** The app's Sobria palette for widgets; the launcher switches day and night values itself. */
internal object WidgetColors {
    val surface = dayNight { it.surface }
    val primary = dayNight { it.primary }
    val secondary = dayNight { it.secondary }
    val tertiary = dayNight { it.tertiary }
    val track = dayNight { it.track }
    val accent = dayNight { it.accent }
    val warning = dayNight { it.warning }
    val danger = dayNight { it.danger }

    private fun dayNight(pick: (CodexMeterColorPalette) -> Color): GlanceColorProvider =
        ColorProvider(day = pick(LightCodexMeterColors), night = pick(DarkCodexMeterColors))
}

/** Figures in the colors Home uses: urgent levels take their hue, the rest stay ink, none is muted. */
internal fun QuotaLevel.figureColor(): GlanceColorProvider =
    when (this) {
        QuotaLevel.Depleted -> WidgetColors.danger
        QuotaLevel.Warning -> WidgetColors.warning
        QuotaLevel.Unknown -> WidgetColors.tertiary
        QuotaLevel.Normal,
        QuotaLevel.Caution,
        -> WidgetColors.primary
    }

internal fun QuotaLevel.barColor(): GlanceColorProvider =
    when (this) {
        QuotaLevel.Normal -> WidgetColors.accent
        QuotaLevel.Caution,
        QuotaLevel.Warning,
        -> WidgetColors.warning
        QuotaLevel.Depleted -> WidgetColors.danger
        QuotaLevel.Unknown -> WidgetColors.tertiary
    }

/** Root of every widget: a flat surface clipped to the launcher's own corner radius. */
@Composable
internal fun WidgetSurface(
    onClick: Action,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .appWidgetBackground()
            .cornerRadius(android.R.dimen.system_app_widget_background_radius)
            .background(WidgetColors.surface)
            .clickable(onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        content()
    }
}

/** The provider's name as a small uppercase label ("CODEX"); text, never another company's logo. */
@Composable
internal fun WidgetProviderLabel(providerName: String, modifier: GlanceModifier = GlanceModifier) {
    Text(
        text = providerName.uppercase(Locale.ROOT),
        modifier = modifier,
        style = TextStyle(color = WidgetColors.secondary, fontSize = 10.sp, fontWeight = FontWeight.Medium),
        maxLines = 1,
    )
}

/** Share left as a thin bar; [level] Unknown draws a muted fill for figures that are not current. */
@Composable
internal fun WidgetUsageBar(percentLeft: Int?, level: QuotaLevel, modifier: GlanceModifier = GlanceModifier) {
    LinearProgressIndicator(
        progress = (percentLeft ?: 0).coerceIn(0, 100) / 100f,
        modifier = modifier.fillMaxWidth().height(4.dp),
        color = level.barColor(),
        backgroundColor = WidgetColors.track,
    )
}

/**
 * A refresh button that fetches every account in the background, then a status text. The button
 * comes first so a text too long for the space is what gets cut, never the button.
 */
@Composable
internal fun WidgetRefreshStatus(
    context: Context,
    text: String,
    color: GlanceColorProvider,
    modifier: GlanceModifier = GlanceModifier,
    maxLines: Int = 1,
    fontSize: TextUnit = 10.sp,
) {
    Row(modifier = modifier, verticalAlignment = Alignment.Vertical.CenterVertically) {
        Image(
            provider = ImageProvider(R.drawable.ic_action_refresh),
            contentDescription = context.getString(R.string.widget_refresh_content_description),
            modifier = GlanceModifier.size(16.dp).clickable(actionRunCallback<WidgetRefreshAction>()),
            colorFilter = ColorFilter.tint(WidgetColors.tertiary),
        )
        Spacer(GlanceModifier.width(4.dp))
        Text(
            text = text,
            style = TextStyle(color = color, fontSize = fontSize, fontWeight = FontWeight.Medium),
            maxLines = maxLines,
        )
    }
}

/** Opens the app from a widget tap, on Home or on the add-account screen. */
internal fun widgetLaunchIntent(context: Context, destination: CodexMeterLaunchDestination): Intent =
    Intent(context, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        putExtra(CodexMeterRoute.EXTRA_LAUNCH_DESTINATION, destination.value)
    }

/**
 * Time only when the instant is today, weekday + time within a week of today (a weekly reset, data
 * from a few days ago), otherwise short month/day + time; never the year. The hour cycle follows
 * the phone's 12/24-hour setting; getBestDateTimePattern keeps the locale's field order.
 */
internal fun Instant.formatWidgetTime(context: Context): String {
    val locale = context.resources.configuration.locales[0]
    val zone = ZoneId.systemDefault()
    val hours = if (DateFormat.is24HourFormat(context)) "Hm" else "hm"
    val days = ChronoUnit.DAYS.between(LocalDate.now(zone), atZone(zone).toLocalDate())
    val skeleton = when {
        days == 0L -> hours
        days in -6L..6L -> "EEE$hours"
        else -> "Md$hours"
    }
    val pattern = DateFormat.getBestDateTimePattern(locale, skeleton)
    return DateTimeFormatter.ofPattern(pattern, locale).withZone(zone).format(this)
}
