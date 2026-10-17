package com.kmnexus.codexmeter.app

import com.kmnexus.codexmeter.data.local.dao.ProviderAccountDao
import com.kmnexus.codexmeter.data.local.dao.RefreshAttemptDao
import com.kmnexus.codexmeter.data.local.entity.RefreshAttemptEntity
import com.kmnexus.codexmeter.data.repository.toDomain
import com.kmnexus.codexmeter.domain.model.AccountStatus
import com.kmnexus.codexmeter.ui.settings.SettingsBackgroundRefreshStatus
import com.kmnexus.codexmeter.ui.settings.SettingsBackgroundRefreshStatusReader

/** The latest background refresh of every saved account, summed up in one Settings label. */
internal class SettingsBackgroundRefreshStatusRepository(
    private val providerAccountDao: ProviderAccountDao,
    private val refreshAttemptDao: RefreshAttemptDao,
) : SettingsBackgroundRefreshStatusReader {
    override suspend fun latestBackgroundRefreshStatus(): SettingsBackgroundRefreshStatus {
        val accounts = providerAccountDao.listAll().filter { it.toDomain().status != AccountStatus.Deleted }
        if (accounts.isEmpty()) return SettingsBackgroundRefreshStatus.NoAccounts
        val latest = accounts.mapNotNull { account ->
            refreshAttemptDao.getLatestForAccountByTrigger(
                providerId = account.providerId,
                localAccountId = account.localAccountId,
                trigger = PERIODIC_TRIGGER,
            )?.toBackgroundRefreshStatus()
        }
        return combinedBackgroundRefreshStatus(latest)
    }

    private fun RefreshAttemptEntity.toBackgroundRefreshStatus(): SettingsBackgroundRefreshStatus =
        when (status) {
            "success" -> SettingsBackgroundRefreshStatus.Success
            "failed" -> if (retryable == true && userActionRequired != true) {
                SettingsBackgroundRefreshStatus.Retrying
            } else {
                SettingsBackgroundRefreshStatus.Failed
            }
            "skipped" -> SettingsBackgroundRefreshStatus.Skipped
            "cancelled" -> SettingsBackgroundRefreshStatus.Cancelled
            else -> SettingsBackgroundRefreshStatus.Failed
        }

    private companion object {
        const val PERIODIC_TRIGGER = "periodic"
    }
}

/**
 * One label for several accounts: the result that needs attention most wins, so "Succeeded" means
 * every account's latest background refresh succeeded. Accounts never refreshed in the background
 * are left out; none at all reads as no attempts yet.
 */
internal fun combinedBackgroundRefreshStatus(
    latest: List<SettingsBackgroundRefreshStatus>,
): SettingsBackgroundRefreshStatus =
    latest.maxByOrNull { ATTENTION_ORDER.indexOf(it) } ?: SettingsBackgroundRefreshStatus.NoAttempts

private val ATTENTION_ORDER = listOf(
    SettingsBackgroundRefreshStatus.Success,
    SettingsBackgroundRefreshStatus.Skipped,
    SettingsBackgroundRefreshStatus.Cancelled,
    SettingsBackgroundRefreshStatus.Retrying,
    SettingsBackgroundRefreshStatus.Failed,
)
