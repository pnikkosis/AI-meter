package com.kmnexus.codexmeter.ui.settings

import android.content.Intent
import android.provider.Settings

internal object SettingsNotificationSettingsTarget {
    /** The system page where the user allows or blocks this app's notifications. */
    fun appNotificationSettingsIntent(packageName: String): Intent =
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
}
