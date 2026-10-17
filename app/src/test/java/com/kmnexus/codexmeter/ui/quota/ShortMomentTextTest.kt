package com.kmnexus.codexmeter.ui.quota

import java.time.Instant
import java.time.ZoneId
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class ShortMomentTextTest {
    private val zone = ZoneId.of("America/Argentina/Buenos_Aires") // UTC-3, no DST
    // Saturday 17 October 2026, 16:09 local.
    private val now = Instant.parse("2026-10-17T19:09:00Z")

    @Test
    fun `later today shows only the time`() {
        assertEquals("19:29", text("2026-10-17T22:29:00Z"))
    }

    @Test
    fun `within the next week shows the weekday and the time`() {
        assertEquals("Thu 06:21", text("2026-10-22T09:21:00Z"))
        assertEquals("Sun 01:00", text("2026-10-18T04:00:00Z"))
    }

    @Test
    fun `further away shows the date and the time`() {
        assertEquals("3 Nov 13:59", text("2026-11-03T16:59:00Z"))
    }

    @Test
    fun `twelve hour clocks follow the locale`() {
        assertEquals("7:29 PM", text("2026-10-17T22:29:00Z", use24Hour = false))
    }

    @Test
    fun `earlier today shows only the time`() {
        assertEquals("10:00", text("2026-10-17T13:00:00Z"))
    }

    @Test
    fun `an update from the last days shows its weekday`() {
        assertEquals("Fri 23:50", text("2026-10-17T02:50:00Z"))
        assertEquals("1 Oct 09:00", text("2026-10-01T12:00:00Z"))
    }

    @Test
    fun `abbreviation dots are dropped`() {
        // es: CLDR writes "jue." and "nov."; the short form reads better without the dots.
        assertEquals("jue 06:21", text("2026-10-22T09:21:00Z", locale = Locale.forLanguageTag("es-AR")))
        assertEquals("3 nov 13:59", text("2026-11-03T16:59:00Z", locale = Locale.forLanguageTag("es-AR")))
    }

    private fun text(at: String, use24Hour: Boolean = true, locale: Locale = Locale.US) =
        shortMomentText(Instant.parse(at), now, zone, locale, use24Hour)
}
