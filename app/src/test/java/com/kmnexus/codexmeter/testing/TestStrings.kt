package com.kmnexus.codexmeter.testing

import com.kmnexus.codexmeter.R
import com.kmnexus.codexmeter.notification.NotificationText
import java.io.File
import java.util.Locale

/**
 * Renders resource-backed text with the default (English) strings.xml on the plain JVM, so content
 * built from string ids can be checked without Robolectric. Matches ids by R field name.
 */
object TestStrings {
    private val templates: Map<Int, String> by lazy {
        val file = listOf(File("src/main/res/values/strings.xml"), File("app/src/main/res/values/strings.xml"))
            .first { it.exists() }
        val byName = Regex("<string name=\"([^\"]+)\"[^>]*>(.*?)</string>", RegexOption.DOT_MATCHES_ALL)
            .findAll(file.readText())
            .associate { it.groupValues[1] to unescape(it.groupValues[2]) }
        R.string::class.java.fields
            .mapNotNull { field -> byName[field.name]?.let { field.getInt(null) to it } }
            .toMap()
    }

    fun render(text: NotificationText): String {
        val args = text.formatArgs.map { if (it is NotificationText) render(it) else it }
        return String.format(Locale.ROOT, templates.getValue(text.resourceId), *args.toTypedArray())
    }

    private fun unescape(value: String): String =
        value.replace("\\'", "'")
            .replace("\\\"", "\"")
            .replace("\\n", "\n")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&amp;", "&")
}
