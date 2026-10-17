package com.kmnexus.codexmeter.app

import com.kmnexus.codexmeter.domain.account.AccountDisplayPreferences
import com.kmnexus.codexmeter.domain.account.InMemoryAccountDisplayPreferenceStore
import com.kmnexus.codexmeter.domain.model.LocalAccountId
import com.kmnexus.codexmeter.domain.model.ProviderAccount
import com.kmnexus.codexmeter.domain.model.ProviderId
import com.kmnexus.codexmeter.domain.quota.CurrentQuotaFreshness
import com.kmnexus.codexmeter.domain.quota.CurrentQuotaState
import com.kmnexus.codexmeter.domain.quota.CurrentQuotaStatus
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class HomeAccountsRepositoryTest {
    @Test
    fun `accounts come in the chosen order with their hidden marks`() = runTest {
        val repository = HomeAccountsRepository(
            states = { listOf(state("a"), state("b"), state("c")) },
            displayPreferences = InMemoryAccountDisplayPreferenceStore(
                AccountDisplayPreferences(order = listOf(LocalAccountId("c")), hidden = setOf(LocalAccountId("a"))),
            ),
        )

        val entries = repository.loadAccounts()

        assertEquals(listOf("c", "a", "b"), entries.map { it.state.account?.localAccountId?.value })
        assertEquals(listOf(false, true, false), entries.map { it.hidden })
    }

    @Test
    fun `states without an account are left out`() = runTest {
        val repository = HomeAccountsRepository(
            states = { listOf(state(null), state("a")) },
            displayPreferences = InMemoryAccountDisplayPreferenceStore(),
        )

        assertEquals(listOf("a"), repository.loadAccounts().map { it.state.account?.localAccountId?.value })
    }

    private fun state(id: String?) = CurrentQuotaState(
        status = if (id == null) CurrentQuotaStatus.Unauthenticated else CurrentQuotaStatus.NoData,
        freshness = CurrentQuotaFreshness.Unknown,
        account = id?.let {
            ProviderAccount.createNew(
                localAccountId = LocalAccountId(it),
                providerId = ProviderId("codex"),
                providerAccountId = null,
                displayName = "Cuenta $it",
                now = Instant.EPOCH,
            )
        },
        snapshot = null,
        latestAttempt = null,
        primaryWindow = null,
        secondaryWindows = emptyList(),
        primaryWindowCanAlert = false,
        error = null,
    )
}
