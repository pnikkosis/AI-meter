package com.kmnexus.codexmeter.ui.settings

import android.content.Intent
import android.provider.Settings

internal object SettingsBatteryOptimizationTarget {
    /**
     * The system's list of battery-optimized apps, where the user can exempt AIMeter themselves. The
     * app does not ask for REQUEST_IGNORE_BATTERY_OPTIMIZATIONS: Google Play keeps that permission
     * for apps whose core function needs it, and a usage widget is not one of them.
     */
    fun batterySettingsIntent(): Intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
}
