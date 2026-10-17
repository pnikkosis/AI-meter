package com.kmnexus.codexmeter.ui.home

import com.kmnexus.codexmeter.domain.model.LocalAccountId
import com.kmnexus.codexmeter.domain.model.ProviderAccount
import com.kmnexus.codexmeter.domain.model.ProviderId
import com.kmnexus.codexmeter.domain.model.QuotaWindowId
import com.kmnexus.codexmeter.domain.model.SnapshotId
import com.kmnexus.codexmeter.domain.quota.CurrentQuotaFreshness
import com.kmnexus.codexmeter.domain.quota.CurrentQuotaState
import com.kmnexus.codexmeter.domain.quota.CurrentQuotaStatus
import com.kmnexus.codexmeter.domain.quota.QuotaLevel
import com.kmnexus.codexmeter.domain.quota.QuotaSnapshot
import com.kmnexus.codexmeter.domain.quota.QuotaSnapshotSource
import com.kmnexus.codexmeter.domain.quota.QuotaWindow
import com.kmnexus.codexmeter.domain.quota.QuotaWindowAvailability
import com.kmnexus.codexmeter.domain.settings.NotificationPreferenceReader
import com.kmnexus.codexmeter.domain.settings.NotificationPreferences
import com.kmnexus.codexmeter.ui.account.AccountRefreshAllUseCase
import java.time.Instant
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val fetched = Instant.parse("2026-10-17T19:09:00Z")

    @Before
    fun setUpMainDispatcher() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun resetMainDispatcher() {
        Dispatchers.resetMain()
    }

    @Test
    fun `starts loading and then shows every visible account without asking the providers`() = runTest(dispatcher) {
        val refresh = CountingRefresh()
        val viewModel = HomeViewModel(
            accountsLoader = { listOf(entry("a", used = 40), entry("b", used = 10, hidden = true)) },
            refreshAllUseCase = refresh,
        )
        assertTrue(viewModel.uiState.value.isLoading)

        viewModel.load()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertEquals(listOf("a"), state.accounts.map { it.localAccountId })
        assertEquals(1, state.hiddenAccountCount)
        assertEquals(0, refresh.calls)
    }

    @Test
    fun `refreshing asks every provider once and then shows the stored result`() = runTest(dispatcher) {
        var used = 40
        val gate = CompletableDeferred<Unit>()
        val refresh = CountingRefresh(gate)
        val viewModel = HomeViewModel(
            accountsLoader = { listOf(entry("a", used = used)) },
            refreshAllUseCase = refresh,
        )
        viewModel.load()
        advanceUntilIdle()

        viewModel.refreshAll()
        viewModel.refreshAll()
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.isRefreshing)

        used = 90
        gate.complete(Unit)
        advanceUntilIdle()

        assertEquals(1, refresh.calls)
        assertFalse(viewModel.uiState.value.isRefreshing)
        assertEquals(10, viewModel.uiState.value.accounts.single().windows.single().percentLeft)
    }

    @Test
    fun `a failed refresh still ends the refresh and keeps the stored figures`() = runTest(dispatcher) {
        val viewModel = HomeViewModel(
            accountsLoader = { listOf(entry("a", used = 40)) },
            refreshAllUseCase = AccountRefreshAllUseCase { error("offline") },
        )
        viewModel.refreshAll()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isRefreshing)
        assertEquals(60, viewModel.uiState.value.accounts.single().windows.single().percentLeft)
    }

    @Test
    fun `a failed load keeps what was shown before`() = runTest(dispatcher) {
        var fail = false
        val viewModel = HomeViewModel(
            accountsLoader = { if (fail) error("db") else listOf(entry("a", used = 40)) },
            refreshAllUseCase = CountingRefresh(),
        )
        viewModel.load()
        advanceUntilIdle()
        fail = true
        viewModel.load()
        advanceUntilIdle()

        assertEquals(listOf("a"), viewModel.uiState.value.accounts.map { it.localAccountId })
        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test
    fun `levels use the thresholds the user set`() = runTest(dispatcher) {
        val viewModel = HomeViewModel(
            accountsLoader = { listOf(entry("a", used = 60)) },
            refreshAllUseCase = CountingRefresh(),
            notificationPreferenceReader = object : NotificationPreferenceReader {
                override suspend fun notificationPreferences() =
                    NotificationPreferences(cautionThreshold = 50, warningThreshold = 20)
            },
        )
        viewModel.load()
        advanceUntilIdle()

        assertEquals(QuotaLevel.Caution, viewModel.uiState.value.accounts.single().windows.single().level)
    }

    private class CountingRefresh(private val gate: CompletableDeferred<Unit>? = null) : AccountRefreshAllUseCase {
        var calls = 0

        override suspend fun refreshAll() {
            calls += 1
            gate?.await()
        }
    }

    private fun entry(id: String, used: Int, hidden: Boolean = false): HomeAccountEntry {
        val account = ProviderAccount.createNew(
            localAccountId = LocalAccountId(id),
            providerId = ProviderId("codex"),
            providerAccountId = null,
            displayName = "Cuenta $id",
            now = Instant.EPOCH,
        )
        val window = QuotaWindow(
            windowId = QuotaWindowId("weekly"),
            titleKey = "weekly",
            usedPercent = used,
            resetAt = null,
            limitWindowSeconds = 604_800,
            isPrimaryCandidate = true,
            availability = QuotaWindowAvailability.Available,
        )
        return HomeAccountEntry(
            state = CurrentQuotaState(
                status = CurrentQuotaStatus.Fresh,
                freshness = CurrentQuotaFreshness.Fresh,
                account = account,
                snapshot = QuotaSnapshot(
                    snapshotId = SnapshotId("s-$id"),
                    providerId = account.providerId,
                    localAccountId = account.localAccountId,
                    providerAccountId = null,
                    fetchedAt = fetched,
                    source = QuotaSnapshotSource.ManualRefresh,
                    planType = null,
                    windows = listOf(window),
                    credits = null,
                    responseDigest = null,
                ),
                latestAttempt = null,
                primaryWindow = window,
                secondaryWindows = emptyList(),
                primaryWindowCanAlert = true,
                error = null,
            ),
            hidden = hidden,
        )
    }
}
