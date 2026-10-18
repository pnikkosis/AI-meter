package com.kmnexus.codexmeter.data.repository

import com.kmnexus.codexmeter.data.local.dao.ProviderAccountDao
import com.kmnexus.codexmeter.domain.account.AccountRenameUseCase
import com.kmnexus.codexmeter.domain.account.AccountStateRepublisher
import com.kmnexus.codexmeter.domain.account.NoopAccountStateRepublisher
import com.kmnexus.codexmeter.domain.model.LocalAccountId
import com.kmnexus.codexmeter.domain.model.ProviderAccount
import com.kmnexus.codexmeter.domain.model.ProviderId
import java.time.Clock
import kotlinx.coroutines.CancellationException

class AccountMutationRepository(
    private val providerAccountDao: ProviderAccountDao,
    private val accountStateRepublisher: AccountStateRepublisher = NoopAccountStateRepublisher,
    private val clock: Clock,
) : AccountRenameUseCase {
    override suspend fun renameAccount(
        providerId: ProviderId,
        localAccountId: LocalAccountId,
        displayName: String,
    ): ProviderAccount? {
        val existing = providerAccountDao.getById(localAccountId.value)
            ?.takeIf { it.providerId == providerId.value }
            ?: return null
        val renamed = existing.toDomain().renamedTo(displayName = displayName, updatedAt = clock.instant())
        val updatedRows = providerAccountDao.updateDisplayName(
            providerId = renamed.providerId.value,
            localAccountId = renamed.localAccountId.value,
            displayName = renamed.displayName,
            avatarInitial = renamed.avatarInitial,
            updatedAt = renamed.updatedAt.toEpochMilli(),
        )
        if (updatedRows == 0) return null

        val renamedAccount = providerAccountDao.getById(localAccountId.value)?.toDomain()
            ?: return null
        // Every account can appear in the notification and in widgets, so any rename is republished.
        runPostMutationEffect {
            accountStateRepublisher.republishAccountState(renamedAccount)
        }

        return renamedAccount
    }

    private suspend fun runPostMutationEffect(effect: suspend () -> Unit) {
        try {
            effect()
        } catch (exception: CancellationException) {
            throw exception
        } catch (_: Exception) {
            // Persistence already succeeded; republish failures are recovered by the next app open
            // or refresh and must not roll back the user-visible mutation.
        }
    }
}
