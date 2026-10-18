package com.kmnexus.codexmeter.app

import com.kmnexus.codexmeter.data.local.dao.ProviderAccountDao
import com.kmnexus.codexmeter.data.repository.toDomain
import com.kmnexus.codexmeter.domain.model.AccountStatus
import com.kmnexus.codexmeter.domain.model.ProviderAccount

internal class CurrentQuotaRefreshAccountStore(
    private val providerAccountDao: ProviderAccountDao,
) {
    /**
     * Every Active account across ALL providers — background refresh must keep each connected
     * provider fresh, not only Codex.
     */
    suspend fun activeAccounts(): List<ProviderAccount> =
        providerAccountDao.listAll()
            .map { it.toDomain() }
            .filter { it.status == AccountStatus.Active }

    /**
     * Accounts a user-initiated refresh may attempt: Active plus NeedsReauth. Including NeedsReauth
     * lets a manual pull-to-refresh retry a flagged account; a successful retry then clears the flag
     * (see [com.kmnexus.codexmeter.refresh.RefreshCoordinator]). Disabled/Deleted stay excluded.
     * Background (Periodic) refresh keeps using [activeAccounts] so it never re-hits failed accounts.
     */
    suspend fun manuallyRefreshableAccounts(): List<ProviderAccount> =
        providerAccountDao.listAll()
            .map { it.toDomain() }
            .filter { it.status.isManuallyRefreshable() }
}

private fun AccountStatus.isManuallyRefreshable(): Boolean =
    this == AccountStatus.Active || this == AccountStatus.NeedsReauth
