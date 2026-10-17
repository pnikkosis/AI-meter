package com.kmnexus.codexmeter.ui.settings

import com.kmnexus.codexmeter.R
import com.kmnexus.codexmeter.core.i18n.AppLocaleController
import com.kmnexus.codexmeter.domain.settings.LanguagePreference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
// Robolectric SDK 36 currently requires Java 21, while this project is pinned to Java 17.
@Config(sdk = [35])
class SettingsAboutCopyTest {
    @Test
    fun `english about copy names the app, the servers it talks to and the absence of telemetry`() {
        val context = AppLocaleController.localizedContext(
            context = RuntimeEnvironment.getApplication(),
            preference = LanguagePreference.English,
        )

        val version = context.getString(R.string.settings_about_version_format, "1.0.0", "release")
        val privacy = context.getString(R.string.settings_about_privacy)

        assertEquals("AIMeter 1.0.0", version)
        assertTrue(privacy.contains("Android Keystore"))
        assertTrue(privacy.contains("OpenAI for Codex"))
        assertTrue(privacy.contains("Anthropic for Claude"))
        assertTrue(privacy.contains("no developer server"))
    }

    @Test
    fun `about section credits the upstream project and ships its mit notice`() {
        val context = AppLocaleController.localizedContext(
            context = RuntimeEnvironment.getApplication(),
            preference = LanguagePreference.English,
        )

        assertEquals("Licenses", context.getString(R.string.settings_about_licenses_label))
        assertTrue(context.getString(R.string.settings_about_based_on).contains("CodexMeter by KyoMio"))
        val notice = context.getString(R.string.license_codexmeter_mit)
        assertTrue(notice.startsWith("MIT License"))
        assertTrue(notice.contains("Copyright (c) 2026 KyoMio"))
        assertTrue(notice.contains("THE SOFTWARE IS PROVIDED \"AS IS\""))
    }

    @Test
    fun `simplified chinese about copy keeps the app name and local encryption statement`() {
        val context = AppLocaleController.localizedContext(
            context = RuntimeEnvironment.getApplication(),
            preference = LanguagePreference.SimplifiedChinese,
        )

        val version = context.getString(R.string.settings_about_version_format, "1.0.0", "release")
        val privacy = context.getString(R.string.settings_about_privacy)

        assertEquals("AIMeter 1.0.0", version)
        assertTrue(privacy.contains("Android Keystore"))
        assertEquals("许可证", context.getString(R.string.settings_about_licenses_label))
    }
}
