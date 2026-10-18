package com.kmnexus.codexmeter.ui.theme

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The OFL requires the license to travel with the font: in the repo and on the app's license screen. */
class GeistLicenseTest {
    @Test
    fun `the app shows the Geist license exactly as the repository ships it`() {
        val shipped = file("licenses/Geist-OFL.txt").readText().trimEnd('\n')
        val strings = file("app/src/main/res/values/strings.xml").readText()
        val raw = Regex("""<string name="license_geist_ofl" translatable="false">(.*?)</string>""", RegexOption.DOT_MATCHES_ALL)
            .find(strings)
            ?.groupValues
            ?.get(1)
            ?: error("license_geist_ofl is missing")

        assertEquals(shipped, raw.unescapeAndroidString())
    }

    @Test
    fun `the license names the copyright the font file carries`() {
        val shipped = file("licenses/Geist-OFL.txt").readText()
        val font = file("app/src/main/res/font/geist_variable.ttf").readBytes()
        val notice = "Copyright 2024 The Geist Project Authors (https://github.com/vercel/geist-font)"

        assertTrue(shipped.startsWith(notice))
        // The font's name table stores the same notice as UTF-16BE.
        assertTrue(String(font, Charsets.UTF_16BE).contains(notice) || String(font.drop(1).toByteArray(), Charsets.UTF_16BE).contains(notice))
    }

    private fun String.unescapeAndroidString(): String =
        replace("\\n", "\n")
            .replace("\\\"", "\"")
            .replace("\\'", "'")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&amp;", "&")

    private fun file(path: String): File =
        listOf(File(path), File("..", path), File(path.removePrefix("app/")))
            .firstOrNull { it.exists() }
            ?: error("Cannot locate $path from ${File(".").absolutePath}")
}
