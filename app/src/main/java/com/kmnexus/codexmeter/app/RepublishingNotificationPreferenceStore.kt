package com.kmnexus.codexmeter.app

import com.kmnexus.codexmeter.domain.quota.CurrentQuotaState
import com.kmnexus.codexmeter.domain.settings.NotificationPreferenceStore
import com.kmnexus.codexmeter.domain.settings.NotificationPreferences
import com.kmnexus.codexmeter.refresh.CurrentQuotaStatePublisher
import kotlinx.coroutines.CancellationException

/** Republishes every account's stored state after a write, so thresholds and toggles apply at once. */
internal class RepublishingNotificationPreferenceStore(
    private val delegate: NotificationPreferenceStore,
    private val states: suspend () -> List<CurrentQuotaState>,
    private val currentQuotaStatePublisher: CurrentQuotaStatePublisher,
) : NotificationPreferenceStore {
    override suspend fun notificationPreferences(): NotificationPreferences =
        delegate.notificationPreferences()

    override suspend fun updateNotificationPreferences(preferences: NotificationPreferences) {
        delegate.updateNotificationPreferences(preferences)
        try {
            states().forEach { currentQuotaStatePublisher.publish(it) }
        } catch (exception: CancellationException) {
            throw exception
        } catch (_: Exception) {
            // Preference writes are durable; presentation refresh can recover on the next state update.
        }
    }
}
