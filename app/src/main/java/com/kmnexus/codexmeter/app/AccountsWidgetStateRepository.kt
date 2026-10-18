package com.kmnexus.codexmeter.app

import com.kmnexus.codexmeter.domain.quota.quotaLevelThresholds
import com.kmnexus.codexmeter.domain.settings.NotificationPreferenceReader
import com.kmnexus.codexmeter.ui.home.HomeAccountsLoader
import com.kmnexus.codexmeter.widget.AccountsWidgetConfiguration
import com.kmnexus.codexmeter.widget.AccountsWidgetState
import com.kmnexus.codexmeter.widget.AccountsWidgetStateFactory
import com.kmnexus.codexmeter.widget.AccountsWidgetStateLoader

/**
 * The accounts one accounts widget lists, in Home's order: every visible account, or the ones
 * chosen for that widget (even if hidden on Home). Chosen accounts that were removed are skipped.
 */
internal class AccountsWidgetStateRepository(
    private val accounts: HomeAccountsLoader,
    private val notificationPreferences: NotificationPreferenceReader,
) : AccountsWidgetStateLoader {
    override suspend fun loadAccountsWidgetState(configuration: AccountsWidgetConfiguration): AccountsWidgetState {
        val entries = accounts.loadAccounts()
        val shown = if (configuration.followsVisibleAccounts) {
            entries.filterNot { it.hidden }
        } else {
            entries.filter { it.state.account?.localAccountId?.value in configuration.chosenAccountIds }
        }
        val thresholds = notificationPreferences.notificationPreferences().quotaLevelThresholds()
        return AccountsWidgetStateFactory(thresholds).create(
            states = shown.map { it.state },
            hasAccounts = entries.isNotEmpty(),
        )
    }
}
