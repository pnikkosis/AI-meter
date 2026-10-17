package com.kmnexus.codexmeter.ui.quota

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.abs

/**
 * A moment as short as it can be read, relative to [now]: "19:29" on the same day, "Thu 06:21"
 * within a week either way (a coming reset, a recent update), "3 Nov 13:59" further away.
 * [use24Hour] follows the phone's clock setting.
 */
fun shortMomentText(
    moment: Instant,
    now: Instant,
    zone: ZoneId,
    locale: Locale,
    use24Hour: Boolean,
): String {
    val at = moment.atZone(zone)
    val days = abs(ChronoUnit.DAYS.between(now.atZone(zone).toLocalDate(), at.toLocalDate()))
    val time = DateTimeFormatter.ofPattern(if (use24Hour) "HH:mm" else "h:mm a", locale).format(at)
    val day = when {
        days == 0L -> null
        days < 7L -> DateTimeFormatter.ofPattern("EEE", locale).format(at)
        else -> DateTimeFormatter.ofPattern("d MMM", locale).format(at)
    }?.replace(".", "")
    return if (day == null) time else "$day $time"
}
