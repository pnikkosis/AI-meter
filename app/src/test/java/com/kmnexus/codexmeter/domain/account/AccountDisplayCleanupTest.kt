package com.kmnexus.codexmeter.domain.account

import com.kmnexus.codexmeter.domain.model.LocalAccountId
import com.kmnexus.codexmeter.domain.model.ProviderId
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class AccountDisplayCleanupTest {
    private val a = LocalAccountId("a")
    private val b = LocalAccountId("b")
    private val codex = ProviderId("codex")

    @Test
    fun `deleting an account drops it from the order and the hidden set`() = runTest {
        val store = InMemoryAccountDisplayPreferenceStore(AccountDisplayPreferences(order = listOf(b, a), hidden = setOf(b)))

        DisplayPreferencesDeletedAccountCleaner(store).clearDeletedAccountState(codex, b)

        assertEquals(AccountDisplayPreferences(order = listOf(a)), store.accountDisplayPreferences())
    }

    @Test
    fun `restoring after a failed delete puts the account back where it was`() = runTest {
        val before = AccountDisplayPreferences(order = listOf(b, a), hidden = setOf(b))
        val store = InMemoryAccountDisplayPreferenceStore(before)

        DisplayPreferencesDeletedAccountCleaner(store).clearDeletedAccountState(codex, b).restore()

        assertEquals(before, store.accountDisplayPreferences())
    }

    @Test
    fun `a composite cleaner clears every part and restores them in reverse order`() = runTest {
        val events = mutableListOf<String>()
        fun recording(name: String) = object : DeletedAccountStateCleaner {
            override suspend fun clearDeletedAccountState(providerId: ProviderId, localAccountId: LocalAccountId) =
                DeletedAccountStateCleanup { events += "restore $name" }.also { events += "clear $name" }
        }

        CompositeDeletedAccountStateCleaner(listOf(recording("widgets"), recording("display")))
            .clearDeletedAccountState(codex, a)
            .restore()

        assertEquals(listOf("clear widgets", "clear display", "restore display", "restore widgets"), events)
    }
}
