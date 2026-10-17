package com.kmnexus.codexmeter

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MainActivityFontPolicyTest {
    @Test
    fun `main activity uses the single app typography`() {
        val source = sourceFile("src/main/java/com/kmnexus/codexmeter/MainActivity.kt").readText()

        assertTrue(
            "MainActivity should apply CodexMeterTheme with the theme mode only.",
            source.contains("CodexMeterTheme(themeMode = themeMode)"),
        )
        assertFalse(
            "There is one typography (Geist with tabular numbers); no font scheme may be passed or collected.",
            source.contains("fontScheme") || source.contains("FontScheme"),
        )
    }

    private fun sourceFile(path: String): File {
        val moduleFile = File(path)
        if (moduleFile.exists()) return moduleFile

        val rootFile = File("app", path)
        if (rootFile.exists()) return rootFile

        error("Cannot locate $path from ${File(".").absolutePath}")
    }
}
