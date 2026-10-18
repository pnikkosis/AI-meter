package com.kmnexus.codexmeter.widget

import com.kmnexus.codexmeter.domain.model.LocalAccountId
import com.kmnexus.codexmeter.domain.model.ProviderAccount
import com.kmnexus.codexmeter.domain.model.ProviderId
import com.kmnexus.codexmeter.domain.model.QuotaWindowId
import com.kmnexus.codexmeter.domain.model.SnapshotId
import com.kmnexus.codexmeter.domain.quota.CurrentQuotaFreshness
import com.kmnexus.codexmeter.domain.quota.CurrentQuotaState
import com.kmnexus.codexmeter.domain.quota.CurrentQuotaStatus
import com.kmnexus.codexmeter.domain.quota.QuotaLevel
import com.kmnexus.codexmeter.domain.quota.QuotaLevelThresholds
import com.kmnexus.codexmeter.domain.quota.QuotaSnapshot
import com.kmnexus.codexmeter.domain.quota.QuotaSnapshotSource
import com.kmnexus.codexmeter.domain.quota.QuotaWindow
import com.kmnexus.codexmeter.domain.quota.QuotaWindowAvailability
import com.kmnexus.codexmeter.domain.refresh.QuotaError
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AccountsWidgetStateFactoryTest {
    private val factory = AccountsWidgetStateFactory(QuotaLevelThresholds(cautionPercent = 30, warningPercent = 10))
    private val fetchedAt = Instant.parse("2026-10-17T13:05:00Z")

    @Test
    fun `each account becomes a row with its limit windows and the one that limits it`() {
        val claude = state(
            account("a1", "claude", "Work"),
            windows = listOf(
                window("claude_5h_window", used = 33, seconds = FIVE_HOURS),
                window("claude_7d_window", used = 80, seconds = WEEK),
                window("claude_extra_usage", used = 20),
            ),
        )

        val row = factory.create(listOf(claude), hasAccounts = true).rows.single()

        assertEquals("Claude", row.providerName)
        assertEquals("Work", row.accountName)
        assertEquals(WidgetQuotaStatus.Fresh, row.status)
        assertEquals(listOf("claude_5h_window", "claude_7d_window"), row.windows.map { it.windowId })
        assertEquals(listOf(67, 20), row.windows.map { it.percentLeft })
        assertEquals(listOf(QuotaLevel.Normal, QuotaLevel.Caution), row.windows.map { it.level })
        assertEquals("claude_7d_window", row.headlineWindowId)
        assertEquals(fetchedAt.toString(), row.lastUpdatedAt)
    }

    @Test
    fun `a spent window is kept as used up and headlines the account`() {
        val codex = state(
            account("c1", "codex", "Personal"),
            windows = listOf(
                window("five_hour", used = null, availability = QuotaWindowAvailability.Missing),
                window("weekly", used = 100, availability = QuotaWindowAvailability.Depleted, seconds = WEEK),
            ),
        )

        val row = factory.create(listOf(codex), hasAccounts = true).rows.single()

        assertEquals(listOf("weekly"), row.windows.map { it.windowId })
        assertEquals(QuotaLevel.Depleted, row.windows.single().level)
        assertEquals("weekly", row.headlineWindowId)
    }

    @Test
    fun `figures that are not current keep their value but lose their level`() {
        val offline = state(
            account("a1", "claude", "Work"),
            windows = listOf(window("claude_5h_window", used = 95, seconds = FIVE_HOURS)),
            status = CurrentQuotaStatus.ErrorWithLastKnownGood,
            error = QuotaError.Network(diagnosticsDigest = "d"),
        )

        val row = factory.create(listOf(offline), hasAccounts = true).rows.single()

        assertEquals(WidgetQuotaStatus.ErrorWithLastKnownGood, row.status)
        assertEquals(5, row.windows.single().percentLeft)
        assertEquals(QuotaLevel.Unknown, row.windows.single().level)
        assertEquals("error_network", row.errorMessageKey)
        assertFalse(row.isCurrent)
    }

    @Test
    fun `an account without figures has no windows and no headline, never a zero`() {
        val fresh = state(account("c1", "codex", "New"), windows = null, status = CurrentQuotaStatus.NoData)

        val row = factory.create(listOf(fresh), hasAccounts = true).rows.single()

        assertTrue(row.windows.isEmpty())
        assertNull(row.headlineWindowId)
        assertNull(row.headline)
        assertNull(row.lastUpdatedAt)
        assertEquals(WidgetQuotaStatus.NoData, row.status)
    }

    @Test
    fun `rows keep the given order and skip states without an account`() {
        val states = listOf(
            state(account("c1", "codex", "Personal"), windows = listOf(window("weekly", used = 10, seconds = WEEK))),
            CurrentQuotaState(
                status = CurrentQuotaStatus.Unauthenticated,
                freshness = CurrentQuotaFreshness.Unknown,
                account = null,
                snapshot = null,
                latestAttempt = null,
                primaryWindow = null,
                secondaryWindows = emptyList(),
                primaryWindowCanAlert = false,
                error = null,
            ),
            state(account("a1", "claude", "Work"), windows = listOf(window("claude_5h_window", used = 10))),
        )

        val widgetState = factory.create(states, hasAccounts = true)

        assertEquals(listOf("c1", "a1"), widgetState.rows.map { it.localAccountId })
        assertTrue(widgetState.hasAccounts)
    }

    private fun account(id: String, provider: String, name: String) = ProviderAccount.createNew(
        localAccountId = LocalAccountId(id),
        providerId = ProviderId(provider),
        providerAccountId = null,
        displayName = name,
        now = Instant.EPOCH,
    )

    private fun window(
        id: String,
        used: Int?,
        availability: QuotaWindowAvailability = QuotaWindowAvailability.Available,
        seconds: Int? = null,
    ) = QuotaWindow(
        windowId = QuotaWindowId(id),
        titleKey = id,
        usedPercent = used,
        resetAt = Instant.parse("2026-10-17T18:00:00Z"),
        limitWindowSeconds = seconds,
        isPrimaryCandidate = false,
        availability = availability,
    )

    private fun state(
        account: ProviderAccount,
        windows: List<QuotaWindow>?,
        status: CurrentQuotaStatus = CurrentQuotaStatus.Fresh,
        error: QuotaError? = null,
    ) = CurrentQuotaState(
        status = status,
        freshness = CurrentQuotaFreshness.Fresh,
        account = account,
        snapshot = windows?.let {
            QuotaSnapshot(
                snapshotId = SnapshotId("s-${account.localAccountId.value}"),
                providerId = account.providerId,
                localAccountId = account.localAccountId,
                providerAccountId = null,
                fetchedAt = fetchedAt,
                source = QuotaSnapshotSource.ManualRefresh,
                planType = null,
                windows = it,
                credits = null,
                responseDigest = null,
            )
        },
        latestAttempt = null,
        primaryWindow = null,
        secondaryWindows = emptyList(),
        primaryWindowCanAlert = false,
        error = error,
    )

    private companion object {
        const val FIVE_HOURS = 5 * 60 * 60
        const val WEEK = 7 * 24 * 60 * 60
    }
}
