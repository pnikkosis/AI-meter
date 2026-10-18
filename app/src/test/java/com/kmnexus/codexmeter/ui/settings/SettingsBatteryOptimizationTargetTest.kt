package com.kmnexus.codexmeter.ui.settings

import android.provider.Settings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
// Robolectric SDK 36 currently requires Java 21, while this project is pinned to Java 17.
@Config(sdk = [35])
class SettingsBatteryOptimizationTargetTest {
    @Test
    fun `battery hint opens the system list of optimized apps, not the exemption request`() {
        val intent = SettingsBatteryOptimizationTarget.batterySettingsIntent()

        assertEquals(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS, intent.action)
        assertNull(intent.data)
    }
}
