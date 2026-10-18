package com.kmnexus.codexmeter.app

import com.kmnexus.codexmeter.domain.model.LocalAccountId
import com.kmnexus.codexmeter.domain.model.ProviderAccount
import com.kmnexus.codexmeter.domain.model.ProviderId
import com.kmnexus.codexmeter.domain.quota.CurrentQuotaFreshness
import com.kmnexus.codexmeter.domain.quota.CurrentQuotaState
import com.kmnexus.codexmeter.domain.quota.CurrentQuotaStatus
import com.kmnexus.codexmeter.domain.settings.NotificationPreferenceReader
import com.kmnexus.codexmeter.domain.settings.NotificationPreferences
import com.kmnexus.codexmeter.ui.home.HomeAccountEntry
import com.kmnexus.codexmeter.ui.home.HomeAccountsLoader
import com.kmnexus.codexmeter.widget.AccountsWidgetConfiguration
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AccountsWidgetStateRepositoryTest {
    // Home's order: c, b (hidden), a.
    private val entries = listOf(entry("c"), entry("b", hidden = true), entry("a"))

    @Test
    fun `by default the widget shows every account visible on Home, in its order`() = runTest {
        val state = repository(entries).loadAccountsWidgetState(AccountsWidgetConfiguration())

        assertEquals(listOf("c", "a"), state.rows.map { it.localAccountId })
        assertTrue(state.hasAccounts)
    }

    @Test
    fun `chosen accounts are shown in Home's order, hidden ones included`() = runTest {
        val state = repository(entries).loadAccountsWidgetState(
            AccountsWidgetConfiguration(chosenAccountIds = listOf("a", "b")),
        )

        assertEquals(listOf("b", "a"), state.rows.map { it.localAccountId })
    }

    @Test
    fun `chosen accounts that were removed are skipped`() = runTest {
        val state = repository(entries).loadAccountsWidgetState(
            AccountsWidgetConfiguration(chosenAccountIds = listOf("gone")),
        )

        assertTrue(state.rows.isEmpty())
        assertTrue(state.hasAccounts)
    }

    @Test
    fun `without any saved account the widget knows there is nothing to choose`() = runTest {
        val state = repository(emptyList()).loadAccountsWidgetState(AccountsWidgetConfiguration())

        assertTrue(state.rows.isEmpty())
        assertFalse(state.hasAccounts)
    }

    private fun repository(entries: List<HomeAccountEntry>) = AccountsWidgetStateRepository(
        accounts = HomeAccountsLoader { entries },
        notificationPreferences = object : NotificationPreferenceReader {
            override suspend fun notificationPreferences() = NotificationPreferences()
        },
    )

    private fun entry(id: String, hidden: Boolean = false) = HomeAccountEntry(
        state = CurrentQuotaState(
            status = CurrentQuotaStatus.NoData,
            freshness = CurrentQuotaFreshness.Unknown,
            account = ProviderAccount.createNew(
                localAccountId = LocalAccountId(id),
                providerId = ProviderId("codex"),
                providerAccountId = null,
                displayName = "Cuenta $id",
                now = Instant.EPOCH,
            ),
            snapshot = null,
            latestAttempt = null,
            primaryWindow = null,
            secondaryWindows = emptyList(),
            primaryWindowCanAlert = false,
            error = null,
        ),
        hidden = hidden,
    )
}
