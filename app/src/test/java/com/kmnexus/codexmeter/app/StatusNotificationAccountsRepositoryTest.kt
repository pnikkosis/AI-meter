package com.kmnexus.codexmeter.app

import com.kmnexus.codexmeter.domain.account.AccountDisplayPreferences
import com.kmnexus.codexmeter.domain.account.InMemoryAccountDisplayPreferenceStore
import com.kmnexus.codexmeter.domain.model.LocalAccountId
import com.kmnexus.codexmeter.domain.model.ProviderAccount
import com.kmnexus.codexmeter.domain.model.ProviderId
import com.kmnexus.codexmeter.domain.quota.CurrentQuotaFreshness
import com.kmnexus.codexmeter.domain.quota.CurrentQuotaState
import com.kmnexus.codexmeter.domain.quota.CurrentQuotaStatus
import com.kmnexus.codexmeter.domain.settings.NotificationAccountSelection
import com.kmnexus.codexmeter.domain.settings.NotificationPreferenceReader
import com.kmnexus.codexmeter.domain.settings.NotificationPreferences
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class StatusNotificationAccountsRepositoryTest {
    private val display = AccountDisplayPreferences(
        order = listOf(LocalAccountId("c"), LocalAccountId("a")),
        hidden = setOf(LocalAccountId("b")),
    )

    @Test
    fun `without a chosen account the notification shows every visible account in the chosen order`() = runTest {
        val repository = repository(chosen = null)

        assertEquals(listOf("c", "a"), repository.loadStatusNotificationStates().ids())
    }

    @Test
    fun `a chosen account is shown alone even when it is hidden from Home`() = runTest {
        val repository = repository(chosen = NotificationAccountSelection(ProviderId("codex"), LocalAccountId("b")))

        assertEquals(listOf("b"), repository.loadStatusNotificationStates().ids())
    }

    @Test
    fun `a chosen account that is gone falls back to the visible accounts`() = runTest {
        val repository = repository(chosen = NotificationAccountSelection(ProviderId("codex"), LocalAccountId("x")))

        assertEquals(listOf("c", "a"), repository.loadStatusNotificationStates().ids())
    }

    @Test
    fun `a choice naming another provider for the same id does not match`() = runTest {
        val repository = repository(chosen = NotificationAccountSelection(ProviderId("claude"), LocalAccountId("b")))

        assertEquals(listOf("c", "a"), repository.loadStatusNotificationStates().ids())
    }

    private fun repository(chosen: NotificationAccountSelection?) = StatusNotificationAccountsRepository(
        states = { listOf(state(null), state("a"), state("b"), state("c")) },
        displayPreferences = InMemoryAccountDisplayPreferenceStore(display),
        notificationPreferences = object : NotificationPreferenceReader {
            override suspend fun notificationPreferences() =
                NotificationPreferences(persistentNotificationAccount = chosen)
        },
    )

    private fun List<CurrentQuotaState>.ids() = map { it.account?.localAccountId?.value }

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
