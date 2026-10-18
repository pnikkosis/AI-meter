package com.kmnexus.codexmeter.widget

import androidx.compose.ui.graphics.Color
import com.kmnexus.codexmeter.ui.theme.CodexMeterColorPalette
import com.kmnexus.codexmeter.ui.theme.DarkCodexMeterColors
import com.kmnexus.codexmeter.ui.theme.LightCodexMeterColors
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Test
import org.w3c.dom.Element

class WidgetInitialLayoutTest {
    @Test
    fun `the placeholder is the widget's own flat surface`() {
        val layout = xml("src/main/res/layout/codexmeter_widget_loading.xml")
        assertEquals("@android:id/background", layout.androidAttribute("id"))
        assertEquals("@drawable/bg_widget_loading", layout.androidAttribute("background"))
        assertEquals("@color/widget_text_secondary", layout.androidAttribute("textColor"))

        val shape = xml("src/main/res/drawable/bg_widget_loading.xml")
        val solid = shape.getElementsByTagName("solid").item(0) as Element
        assertEquals("@color/widget_surface", solid.androidAttribute("color"))
    }

    @Test
    fun `widget resource colors follow the Sobria palette by day and by night`() {
        val day = colors("src/main/res/values/colors.xml")
        val night = colors("src/main/res/values-night/colors.xml")

        val palette = mapOf(
            "widget_surface" to { colors: CodexMeterColorPalette -> colors.surface },
            "widget_text_primary" to { colors: CodexMeterColorPalette -> colors.primary },
            "widget_text_secondary" to { colors: CodexMeterColorPalette -> colors.secondary },
            "widget_text_tertiary" to { colors: CodexMeterColorPalette -> colors.tertiary },
            "widget_accent" to { colors: CodexMeterColorPalette -> colors.accent },
            "widget_danger" to { colors: CodexMeterColorPalette -> colors.danger },
            "widget_track" to { colors: CodexMeterColorPalette -> colors.track },
        )
        assertEquals(palette.keys, day.keys.filter { it.startsWith("widget_") }.toSet())
        assertEquals(palette.keys, night.keys)
        palette.forEach { (name, pick) ->
            assertEquals(name, pick(LightCodexMeterColors), Color(day.getValue(name)))
            assertEquals(name, pick(DarkCodexMeterColors), Color(night.getValue(name)))
        }
    }

    private fun colors(path: String): Map<String, Int> {
        val nodes = xml(path).getElementsByTagName("color")
        return (0 until nodes.length).associate { index ->
            val color = nodes.item(index) as Element
            val hex = color.textContent.trim().removePrefix("#")
            color.getAttribute("name") to (if (hex.length == 6) "FF$hex" else hex).toLong(16).toInt()
        }
    }

    private fun xml(path: String): Element =
        DocumentBuilderFactory.newInstance()
            .apply { isNamespaceAware = true }
            .newDocumentBuilder()
            .parse(resourceFile(path))
            .documentElement

    private fun resourceFile(path: String): File {
        val moduleFile = File(path)
        if (moduleFile.exists()) return moduleFile

        val rootFile = File("app", path)
        if (rootFile.exists()) return rootFile

        error("Cannot locate $path from ${File(".").absolutePath}")
    }

    private fun Element.androidAttribute(name: String): String =
        getAttributeNS(ANDROID_NAMESPACE, name)

    private companion object {
        const val ANDROID_NAMESPACE = "http://schemas.android.com/apk/res/android"
    }
}
