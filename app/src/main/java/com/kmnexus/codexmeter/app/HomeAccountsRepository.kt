package com.kmnexus.codexmeter.app

import com.kmnexus.codexmeter.domain.account.AccountDisplayPreferenceReader
import com.kmnexus.codexmeter.domain.quota.CurrentQuotaState
import com.kmnexus.codexmeter.ui.home.HomeAccountEntry
import com.kmnexus.codexmeter.ui.home.HomeAccountsLoader

/** Every saved account's stored state for Home, arranged and marked by the user's display choices. */
internal class HomeAccountsRepository(
    private val states: suspend () -> List<CurrentQuotaState>,
    private val displayPreferences: AccountDisplayPreferenceReader,
) : HomeAccountsLoader {
    override suspend fun loadAccounts(): List<HomeAccountEntry> {
        val preferences = displayPreferences.accountDisplayPreferences()
        val byAccount = states().mapNotNull { state -> state.account?.let { it.localAccountId to state } }
        return preferences.arrange(byAccount) { it.first }
            .map { (id, state) -> HomeAccountEntry(state = state, hidden = preferences.isHidden(id)) }
    }
}
