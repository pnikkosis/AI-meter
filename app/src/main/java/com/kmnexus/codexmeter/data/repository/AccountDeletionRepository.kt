package com.kmnexus.codexmeter.data.repository

import androidx.room.withTransaction
import com.kmnexus.codexmeter.data.local.db.CodexMeterDatabase
import com.kmnexus.codexmeter.data.preferences.CurrentAccountSelection
import com.kmnexus.codexmeter.data.preferences.CurrentAccountStore
import com.kmnexus.codexmeter.data.secure.SecureSessionStore
import com.kmnexus.codexmeter.data.secure.SessionRevoker
import com.kmnexus.codexmeter.domain.account.DeletedAccountStateCleanup
import com.kmnexus.codexmeter.domain.account.DeletedAccountStateCleaner
import com.kmnexus.codexmeter.domain.account.RemoteRevocation
import com.kmnexus.codexmeter.domain.model.LocalAccountId
import com.kmnexus.codexmeter.domain.model.ProviderId
import kotlinx.coroutines.CancellationException

/**
 * Disconnects an account: first asks the provider to revoke the stored sign-in (best effort, only
 * where the provider supports it), then deletes everything the app keeps for it on the device — the
 * encrypted session file, snapshots, refresh attempts, alert state, widget state and the account row.
 * The local deletion happens whether or not the revocation worked. The user's browser session with
 * the provider is never touched.
 */
class AccountDeletionRepository(
    private val database: CodexMeterDatabase,
    private val secureSessionStore: SecureSessionStore,
    private val currentAccountStore: CurrentAccountStore,
    private val deletedAccountStateCleaner: DeletedAccountStateCleaner,
    private val localDataDeleter: AccountLocalDataDeleter = RoomAccountLocalDataDeleter(database),
    private val sessionRevoker: SessionRevoker = SessionRevoker { RemoteRevocation.NotSupported },
) {
    suspend fun deleteAccount(
        providerId: ProviderId,
        localAccountId: LocalAccountId,
    ): RemoteRevocation {
        val providerValue = providerId.value
        val localValue = localAccountId.value
        val previousSelection = currentAccountStore.currentAccountSelection()
        val nextSelection = nextCurrentSelectionAfterDeleting(
            providerId = providerId,
            deletedLocalAccountId = localAccountId,
            currentSelection = previousSelection,
        )
        val shouldUpdateCurrentSelection = previousSelection?.providerId == providerId &&
            previousSelection.localAccountId == localAccountId
        val sessionBeforeDelete = secureSessionStore.load(
            providerId = providerValue,
            localAccountId = localValue,
        )
        // Revoke before deleting: the token is needed for the request and is gone afterwards.
        val revocation = if (sessionBeforeDelete == null) {
            RemoteRevocation.NothingToRevoke
        } else {
            try {
                sessionRevoker.revoke(sessionBeforeDelete)
            } catch (exception: CancellationException) {
                throw exception
            } catch (_: Exception) {
                RemoteRevocation.Failed
            }
        }

        var deletedAccountStateCleanup: DeletedAccountStateCleanup? = null

        try {
            if (shouldUpdateCurrentSelection) {
                currentAccountStore.updateCurrentAccountSelection(nextSelection)
            }
            deletedAccountStateCleanup = deletedAccountStateCleaner.clearDeletedAccountState(
                providerId = providerId,
                localAccountId = localAccountId,
            )
            secureSessionStore.delete(
                providerId = providerValue,
                localAccountId = localValue,
            )
            localDataDeleter.deleteLocalData(providerId = providerId, localAccountId = localAccountId)
        } catch (throwable: Throwable) {
            deletedAccountStateCleanup?.let { cleanup ->
                runCatching { cleanup.restore() }
            }
            if (sessionBeforeDelete != null) {
                runCatching { secureSessionStore.save(sessionBeforeDelete) }
            }
            if (shouldUpdateCurrentSelection) {
                runCatching { currentAccountStore.updateCurrentAccountSelection(previousSelection) }
            }
            throw throwable
        }
        return revocation
    }

    private suspend fun nextCurrentSelectionAfterDeleting(
        providerId: ProviderId,
        deletedLocalAccountId: LocalAccountId,
        currentSelection: CurrentAccountSelection?,
    ): CurrentAccountSelection? {
        if (currentSelection?.providerId != providerId ||
            currentSelection.localAccountId != deletedLocalAccountId
        ) {
            return currentSelection
        }

        return database.providerAccountDao()
            .listByProvider(providerId.value)
            .filterNot { it.localAccountId == deletedLocalAccountId.value }
            .firstOrNull()
            ?.let { account ->
                CurrentAccountSelection(
                    providerId = providerId,
                    localAccountId = LocalAccountId(account.localAccountId),
                )
            }
    }
}

interface AccountLocalDataDeleter {
    suspend fun deleteLocalData(
        providerId: ProviderId,
        localAccountId: LocalAccountId,
    )
}

class RoomAccountLocalDataDeleter(
    private val database: CodexMeterDatabase,
) : AccountLocalDataDeleter {
    override suspend fun deleteLocalData(
        providerId: ProviderId,
        localAccountId: LocalAccountId,
    ) {
        val providerValue = providerId.value
        val localValue = localAccountId.value
        database.withTransaction {
            val account = database.providerAccountDao().getById(localValue)
                ?.takeIf { it.providerId == providerValue }

            database.quotaSnapshotDao().deleteForAccount(
                providerId = providerValue,
                localAccountId = localValue,
            )
            database.refreshAttemptDao().deleteForAccount(
                providerId = providerValue,
                localAccountId = localValue,
            )
            database.alertStateDao().deleteForAccount(
                providerId = providerValue,
                localAccountId = localValue,
            )
            if (account != null) {
                database.providerAccountDao().delete(account)
            }
        }
    }
}
