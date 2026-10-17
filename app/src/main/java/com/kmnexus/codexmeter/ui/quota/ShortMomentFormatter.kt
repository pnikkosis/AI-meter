package com.kmnexus.codexmeter.ui.quota

import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import java.time.Instant
import java.time.ZoneId
import java.util.Locale

/** [shortMomentText] with the phone's locale, time zone and 12/24-hour setting. */
@Composable
fun shortMomentFormatter(): (Instant) -> String {
    val locale = LocalConfiguration.current.locales[0] ?: Locale.getDefault()
    val use24Hour = DateFormat.is24HourFormat(LocalContext.current)
    val zone = ZoneId.systemDefault()
    val now = Instant.now()
    return { moment -> shortMomentText(moment, now, zone, locale, use24Hour) }
}
