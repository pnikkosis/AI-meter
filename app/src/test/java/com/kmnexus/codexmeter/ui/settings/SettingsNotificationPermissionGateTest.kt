package com.kmnexus.codexmeter.ui.settings

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsNotificationPermissionGateTest {
    @Test
    fun `android 13 enable without permission requests runtime permission`() {
        assertTrue(
            SettingsNotificationPermissionGate.shouldRequestPermission(
                sdkInt = 33,
                notificationPermissionGranted = false,
                requestedEnabled = true,
            ),
        )
    }

    @Test
    fun `disabling notifications never requests runtime permission`() {
        assertFalse(
            SettingsNotificationPermissionGate.shouldRequestPermission(
                sdkInt = 35,
                notificationPermissionGranted = false,
                requestedEnabled = false,
            ),
        )
    }

    @Test
    fun `android 12 does not request notification runtime permission`() {
        assertFalse(
            SettingsNotificationPermissionGate.shouldRequestPermission(
                sdkInt = 32,
                notificationPermissionGranted = false,
                requestedEnabled = true,
            ),
        )
    }

    @Test
    fun `a switch reads on only while android lets the app post notifications`() {
        assertTrue(SettingsNotificationPermissionGate.shownAsEnabled(preferenceEnabled = true, notificationsEnabled = true))
        assertFalse(SettingsNotificationPermissionGate.shownAsEnabled(preferenceEnabled = true, notificationsEnabled = false))
        assertFalse(SettingsNotificationPermissionGate.shownAsEnabled(preferenceEnabled = false, notificationsEnabled = true))
    }

    @Test
    fun `blocked notifications without a permission dialog open the system settings`() {
        // Android 12 has no runtime permission; notifications were turned off in the system settings.
        assertTrue(
            SettingsNotificationPermissionGate.shouldOpenSystemSettings(
                sdkInt = 32,
                notificationPermissionGranted = false,
                notificationsEnabled = false,
                requestedEnabled = true,
            ),
        )
        // Android 13+: the permission is granted but the app's notifications are off.
        assertTrue(
            SettingsNotificationPermissionGate.shouldOpenSystemSettings(
                sdkInt = 35,
                notificationPermissionGranted = true,
                notificationsEnabled = false,
                requestedEnabled = true,
            ),
        )
    }

    @Test
    fun `a missing permission is asked for instead of opening the system settings`() {
        assertFalse(
            SettingsNotificationPermissionGate.shouldOpenSystemSettings(
                sdkInt = 35,
                notificationPermissionGranted = false,
                notificationsEnabled = false,
                requestedEnabled = true,
            ),
        )
    }

    @Test
    fun `allowed notifications or turning a switch off never open the system settings`() {
        assertFalse(
            SettingsNotificationPermissionGate.shouldOpenSystemSettings(
                sdkInt = 35,
                notificationPermissionGranted = true,
                notificationsEnabled = true,
                requestedEnabled = true,
            ),
        )
        assertFalse(
            SettingsNotificationPermissionGate.shouldOpenSystemSettings(
                sdkInt = 32,
                notificationPermissionGranted = false,
                notificationsEnabled = false,
                requestedEnabled = false,
            ),
        )
    }

    @Test
    fun `only a refusal without the system dialog leads to the system settings`() {
        // Already denied for good: Android answers without showing its dialog.
        assertTrue(
            SettingsNotificationPermissionGate.shouldOpenSystemSettingsAfterDenial(
                rationaleBeforeRequest = false,
                rationaleAfterRequest = false,
            ),
        )
        // First refusal in the dialog.
        assertFalse(
            SettingsNotificationPermissionGate.shouldOpenSystemSettingsAfterDenial(
                rationaleBeforeRequest = false,
                rationaleAfterRequest = true,
            ),
        )
        // Second refusal in the dialog.
        assertFalse(
            SettingsNotificationPermissionGate.shouldOpenSystemSettingsAfterDenial(
                rationaleBeforeRequest = true,
                rationaleAfterRequest = false,
            ),
        )
    }
}
