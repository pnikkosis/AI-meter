package com.kmnexus.codexmeter.widget

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element

class WidgetProviderMetadataTest {
    @Test
    fun `account widget resizes from three by one to four by two and asks for its account when placed`() {
        val provider = xml("src/main/res/xml/codexmeter_widget_info.xml")

        assertEquals("180dp", provider.androidAttribute("minWidth"))
        assertEquals("40dp", provider.androidAttribute("minHeight"))
        assertEquals("horizontal|vertical", provider.androidAttribute("resizeMode"))
        assertEquals("3", provider.androidAttribute("targetCellWidth"))
        assertEquals("1", provider.androidAttribute("targetCellHeight"))
        // Without configuration_optional the launcher opens the dialog: the widget needs an account.
        assertEquals("reconfigurable", provider.androidAttribute("widgetFeatures"))
        assertEquals("@layout/widget_preview_account", provider.androidAttribute("previewLayout"))
    }

    @Test
    fun `accounts widget can be placed without its dialog and starts at three by two`() {
        val provider = xml("src/main/res/xml/accounts_widget_info.xml")

        assertEquals("3", provider.androidAttribute("targetCellWidth"))
        assertEquals("2", provider.androidAttribute("targetCellHeight"))
        assertEquals("reconfigurable|configuration_optional", provider.androidAttribute("widgetFeatures"))
        assertEquals("@layout/widget_preview_accounts", provider.androidAttribute("previewLayout"))
        assertEquals(
            "com.kmnexus.codexmeter.widget.AccountsWidgetConfigurationActivity",
            provider.androidAttribute("configure"),
        )
    }

    @Test
    fun `both widgets are registered with their own label and configuration activity`() {
        val manifest = xml("src/main/AndroidManifest.xml")
        val receivers = manifest.elements("receiver").associateBy { it.androidAttribute("name") }
        val activities = manifest.elements("activity").map { it.androidAttribute("name") }

        assertEquals("@string/widget_label_account", receivers.getValue(".widget.CodexMeterWidgetReceiver").androidAttribute("label"))
        assertEquals("@string/widget_label_accounts", receivers.getValue(".widget.AccountsWidgetReceiver").androidAttribute("label"))
        assertTrue(".widget.CodexMeterWidgetConfigurationActivity" in activities)
        assertTrue(".widget.AccountsWidgetConfigurationActivity" in activities)
    }

    private fun Element.elements(tag: String): List<Element> {
        val nodes = getElementsByTagName(tag)
        return (0 until nodes.length).map { nodes.item(it) as Element }
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
