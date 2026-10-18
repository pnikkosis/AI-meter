package com.kmnexus.codexmeter.app

import com.kmnexus.codexmeter.domain.account.AccountDisplayPreferenceStore
import com.kmnexus.codexmeter.domain.account.AccountDisplayPreferences
import com.kmnexus.codexmeter.domain.account.AccountSurfacesRefresher
import kotlinx.coroutines.CancellationException

/**
 * Redraws the status notification and the widgets after accounts are hidden, shown or reordered,
 * since they list the visible accounts in the user's order. No quota alert or account error is
 * posted again for a change of order.
 */
internal class RepublishingAccountDisplayPreferenceStore(
    private val delegate: AccountDisplayPreferenceStore,
    private val accountSurfacesRefresher: AccountSurfacesRefresher,
) : AccountDisplayPreferenceStore {
    override suspend fun accountDisplayPreferences(): AccountDisplayPreferences =
        delegate.accountDisplayPreferences()

    override suspend fun updateAccountDisplayPreferences(
        transform: (AccountDisplayPreferences) -> AccountDisplayPreferences,
    ): AccountDisplayPreferences {
        val updated = delegate.updateAccountDisplayPreferences(transform)
        try {
            accountSurfacesRefresher.refreshAccountSurfaces()
        } catch (exception: CancellationException) {
            throw exception
        } catch (_: Exception) {
            // The choice is stored; the notification and widgets catch up on the next refresh.
        }
        return updated
    }
}
