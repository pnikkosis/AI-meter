package com.kmnexus.codexmeter.app

import com.kmnexus.codexmeter.data.local.dao.ProviderAccountDao
import com.kmnexus.codexmeter.data.local.dao.QuotaSnapshotDao
import com.kmnexus.codexmeter.data.local.dao.RefreshAttemptDao
import com.kmnexus.codexmeter.data.repository.toDomain
import com.kmnexus.codexmeter.domain.model.AccountStatus
import com.kmnexus.codexmeter.domain.model.LocalAccountId
import com.kmnexus.codexmeter.domain.model.ProviderAccount
import com.kmnexus.codexmeter.domain.model.ProviderId
import com.kmnexus.codexmeter.domain.model.QuotaWindowId
import com.kmnexus.codexmeter.domain.quota.CurrentQuotaState
import com.kmnexus.codexmeter.domain.quota.CurrentQuotaStateFactory
import com.kmnexus.codexmeter.domain.settings.DefaultPrimaryQuotaWindowPreferenceReader
import com.kmnexus.codexmeter.domain.settings.PrimaryQuotaWindowPreferenceReader
import java.time.Clock

internal class CurrentQuotaStateRepository(
    private val providerAccountDao: ProviderAccountDao,
    private val quotaSnapshotDao: QuotaSnapshotDao,
    private val refreshAttemptDao: RefreshAttemptDao,
    private val currentQuotaStateFactory: CurrentQuotaStateFactory = CurrentQuotaStateFactory(),
    private val primaryQuotaWindowPreferenceReader: PrimaryQuotaWindowPreferenceReader =
        DefaultPrimaryQuotaWindowPreferenceReader,
    private val clock: Clock,
) {
    suspend fun loadAccountState(
        providerId: ProviderId,
        localAccountId: LocalAccountId,
        primaryWindowId: QuotaWindowId,
    ): CurrentQuotaState {
        val account = providerAccountDao.getById(localAccountId.value)
            ?.toDomain()
            ?.takeIf { it.providerId == providerId }
            ?: return unauthenticatedState()
        return loadState(
            account = account,
            primaryWindowId = primaryWindowId,
        )
    }

    /** Every saved account's stored state, in saved order; deleted accounts are left out. */
    suspend fun loadAllAccountStates(): List<CurrentQuotaState> {
        val primaryWindowId = primaryQuotaWindowPreferenceReader.primaryQuotaWindowId()
        return providerAccountDao.listAll()
            .map { it.toDomain() }
            .filter { it.status != AccountStatus.Deleted }
            .map { loadState(account = it, primaryWindowId = primaryWindowId) }
    }

    /**
     * What to publish after a change that did not refresh quota: every saved account's state, or the
     * signed-out state when there is none, so widgets and the notification still catch up.
     */
    suspend fun loadStatesToRepublish(): List<CurrentQuotaState> =
        loadAllAccountStates().ifEmpty { listOf(unauthenticatedState()) }

    private suspend fun loadState(
        account: ProviderAccount,
        primaryWindowId: QuotaWindowId,
    ): CurrentQuotaState {
        val latestSnapshot = quotaSnapshotDao.getLatestForAccount(
            providerId = account.providerId.value,
            localAccountId = account.localAccountId.value,
        )?.toDomain()
        val latestAttempt = refreshAttemptDao.getLatestForAccount(
            providerId = account.providerId.value,
            localAccountId = account.localAccountId.value,
        )?.toDomain()

        return currentQuotaStateFactory.create(
            account = account,
            latestSnapshot = latestSnapshot,
            latestAttempt = latestAttempt,
            now = clock.instant(),
            primaryWindowId = primaryWindowId,
        )
    }

    private fun unauthenticatedState(): CurrentQuotaState =
        currentQuotaStateFactory.create(
            account = null,
            latestSnapshot = null,
            latestAttempt = null,
            now = clock.instant(),
        )
}
