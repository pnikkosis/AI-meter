package com.kmnexus.codexmeter.ui.settings

import android.os.Build

object SettingsNotificationPermissionGate {
    fun shouldRequestPermission(
        sdkInt: Int,
        notificationPermissionGranted: Boolean,
        requestedEnabled: Boolean,
    ): Boolean =
        requestedEnabled &&
            sdkInt >= Build.VERSION_CODES.TIRAMISU &&
            !notificationPermissionGranted

    /**
     * A notification switch reads ON only while Android lets the app post notifications: the status
     * notification is on by default, and on Android 13+ nothing appears until the permission is granted.
     */
    fun shownAsEnabled(preferenceEnabled: Boolean, notificationsEnabled: Boolean): Boolean =
        preferenceEnabled && notificationsEnabled

    /**
     * Turning a switch on while Android blocks the app's notifications with no permission dialog to
     * show (Android 12, or notifications turned off in the system settings): only the app's system
     * notification settings can allow them.
     */
    fun shouldOpenSystemSettings(
        sdkInt: Int,
        notificationPermissionGranted: Boolean,
        notificationsEnabled: Boolean,
        requestedEnabled: Boolean,
    ): Boolean =
        requestedEnabled &&
            !notificationsEnabled &&
            !shouldRequestPermission(sdkInt, notificationPermissionGranted, requestedEnabled)

    /**
     * A refused permission request where Android skipped its dialog (the permission was already
     * denied for good): the rationale flag is false both before and after the request only then.
     * After a first or second refusal in the dialog the flag changes, and the choice is respected.
     */
    fun shouldOpenSystemSettingsAfterDenial(
        rationaleBeforeRequest: Boolean,
        rationaleAfterRequest: Boolean,
    ): Boolean = !rationaleBeforeRequest && !rationaleAfterRequest
}
