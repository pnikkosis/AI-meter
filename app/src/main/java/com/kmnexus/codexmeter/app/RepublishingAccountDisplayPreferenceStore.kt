package com.kmnexus.codexmeter.app

import com.kmnexus.codexmeter.domain.account.AccountDisplayPreferenceStore
import com.kmnexus.codexmeter.domain.account.AccountDisplayPreferences
import com.kmnexus.codexmeter.notification.StatusNotificationRefresher
import kotlinx.coroutines.CancellationException

/**
 * Rebuilds the status notification after accounts are hidden, shown or reordered, since it lists the
 * visible accounts in the user's order. Only the notification is redone: no quota alert or account
 * error is posted again for a change of order.
 */
internal class RepublishingAccountDisplayPreferenceStore(
    private val delegate: AccountDisplayPreferenceStore,
    private val statusNotificationRefresher: StatusNotificationRefresher,
) : AccountDisplayPreferenceStore {
    override suspend fun accountDisplayPreferences(): AccountDisplayPreferences =
        delegate.accountDisplayPreferences()

    override suspend fun updateAccountDisplayPreferences(
        transform: (AccountDisplayPreferences) -> AccountDisplayPreferences,
    ): AccountDisplayPreferences {
        val updated = delegate.updateAccountDisplayPreferences(transform)
        try {
            statusNotificationRefresher.refreshStatusNotification()
        } catch (exception: CancellationException) {
            throw exception
        } catch (_: Exception) {
            // The choice is stored; the notification catches up on the next refresh.
        }
        return updated
    }
}
