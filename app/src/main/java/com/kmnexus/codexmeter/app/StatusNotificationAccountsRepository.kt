package com.kmnexus.codexmeter.app

import com.kmnexus.codexmeter.domain.account.AccountDisplayPreferenceReader
import com.kmnexus.codexmeter.domain.quota.CurrentQuotaState
import com.kmnexus.codexmeter.domain.settings.NotificationPreferenceReader
import com.kmnexus.codexmeter.notification.StatusNotificationStatesLoader

/**
 * The accounts the persistent notification shows: the one chosen in Settings (even if hidden from
 * Home), or else every visible account in the user's order.
 */
internal class StatusNotificationAccountsRepository(
    private val states: suspend () -> List<CurrentQuotaState>,
    private val displayPreferences: AccountDisplayPreferenceReader,
    private val notificationPreferences: NotificationPreferenceReader,
) : StatusNotificationStatesLoader {
    override suspend fun loadStatusNotificationStates(): List<CurrentQuotaState> {
        val accountStates = states().filter { it.account != null }
        val chosen = notificationPreferences.notificationPreferences().persistentNotificationAccount
        val chosenState = chosen?.let { selection ->
            accountStates.firstOrNull {
                it.account?.providerId == selection.providerId &&
                    it.account.localAccountId == selection.localAccountId
            }
        }
        if (chosenState != null) return listOf(chosenState)
        return displayPreferences.accountDisplayPreferences()
            .visible(accountStates) { requireNotNull(it.account).localAccountId }
    }
}
