package com.kmnexus.codexmeter.domain.account

import com.kmnexus.codexmeter.domain.model.LocalAccountId
import com.kmnexus.codexmeter.domain.model.ProviderId

/** Forgets a disconnected account's place in the order and its hidden mark. */
class DisplayPreferencesDeletedAccountCleaner(
    private val store: AccountDisplayPreferenceStore,
) : DeletedAccountStateCleaner {
    override suspend fun clearDeletedAccountState(
        providerId: ProviderId,
        localAccountId: LocalAccountId,
    ): DeletedAccountStateCleanup {
        val before = store.accountDisplayPreferences()
        store.updateAccountDisplayPreferences { it.without(localAccountId) }
        return DeletedAccountStateCleanup {
            store.updateAccountDisplayPreferences { before }
        }
    }
}

/** Runs several cleaners; a rollback restores them in reverse order. */
class CompositeDeletedAccountStateCleaner(
    private val cleaners: List<DeletedAccountStateCleaner>,
) : DeletedAccountStateCleaner {
    override suspend fun clearDeletedAccountState(
        providerId: ProviderId,
        localAccountId: LocalAccountId,
    ): DeletedAccountStateCleanup {
        val cleanups = cleaners.map { it.clearDeletedAccountState(providerId, localAccountId) }
        return DeletedAccountStateCleanup {
            cleanups.asReversed().forEach { it.restore() }
        }
    }
}
