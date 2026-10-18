package com.kmnexus.codexmeter.data.repository

import com.kmnexus.codexmeter.data.local.dao.ProviderAccountDao
import com.kmnexus.codexmeter.data.local.dao.QuotaSnapshotDao
import com.kmnexus.codexmeter.domain.account.AccountListResult
import com.kmnexus.codexmeter.domain.account.AccountListUseCase

class AccountListRepository(
    private val providerAccountDao: ProviderAccountDao,
    private val quotaSnapshotDao: QuotaSnapshotDao,
) : AccountListUseCase {
    override suspend fun loadAccounts(): AccountListResult {
        val rawAccounts = providerAccountDao.listAll().map { it.toDomain() }

        val latestQuotaSnapshots = rawAccounts.mapNotNull { account ->
            quotaSnapshotDao.getLatestForAccount(
                providerId = account.providerId.value,
                localAccountId = account.localAccountId.value,
            )?.toDomain()?.let { account.localAccountId to it }
        }.toMap()

        // The refresh pipeline persists snapshots, not the account's last-refresh timestamp, so derive
        // it from the latest snapshot's `fetchedAt` when the account row hasn't recorded one. Mirrors
        // the COALESCE fallback in ProviderAccountDao.listByProvider so newly-added providers don't
        // show "No successful refresh yet" despite having fresh quota.
        val accounts = rawAccounts.map { account ->
            if (account.lastSuccessfulRefreshAt != null) {
                account
            } else {
                latestQuotaSnapshots[account.localAccountId]
                    ?.let { account.copy(lastSuccessfulRefreshAt = it.fetchedAt) }
                    ?: account
            }
        }

        return AccountListResult(
            accounts = accounts,
            latestQuotaSnapshots = latestQuotaSnapshots,
        )
    }
}
