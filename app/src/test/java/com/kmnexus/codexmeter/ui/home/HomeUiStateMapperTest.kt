package com.kmnexus.codexmeter.ui.home

import com.kmnexus.codexmeter.R
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeUiStateMapperTest {
    private val mapper = HomeUiStateMapper(QuotaLevelThresholds(cautionPercent = 30, warningPercent = 10))
    private val fetched = Instant.parse("2026-10-17T19:09:00Z")

    @Test
    fun `every visible account becomes a card in the given order and hidden ones are only counted`() {
        val codex = entry(account("codex-1", "codex", "Personal"), windows = listOf(percent("weekly", 50)))
        val claude = entry(account("claude-1", "claude", "Trabajo"), windows = listOf(percent("claude_5h_window", 33)))
        val hidden = entry(account("codex-2", "codex", "Otra"), windows = listOf(percent("weekly", 10)), hidden = true)

        val state = mapper.map(listOf(claude, hidden, codex), isRefreshing = false)

        assertEquals(listOf("claude-1", "codex-1"), state.accounts.map { it.localAccountId })
        assertEquals(1, state.hiddenAccountCount)
        assertEquals(listOf("Claude", "Codex"), state.accounts.map { it.providerName })
        assertEquals("Trabajo", state.accounts.first().displayName)
    }

    @Test
    fun `a spent window reads as depleted with nothing left, and a slot the provider did not send is dropped`() {
        val weekly = percent("weekly", used = 100, availability = QuotaWindowAvailability.Depleted, seconds = 604_800)
        val fiveHour = percent("five_hour", used = null, availability = QuotaWindowAvailability.Missing)
        val card = mapper.map(listOf(entry(account("c", "codex", "Personal"), windows = listOf(fiveHour, weekly))), false)
            .accounts.single()

        val window = card.windows.single()
        assertEquals("weekly", window.windowId)
        assertEquals(0, window.percentLeft)
        assertEquals(QuotaLevel.Depleted, window.level)
        assertEquals(R.string.account_quota_weekly_label, window.labelResId)
    }

    @Test
    fun `levels follow the thresholds`() {
        val card = mapper.map(
            listOf(entry(account("c", "claude", "P"), windows = listOf(percent("claude_5h_window", 95), percent("claude_7d_window", 75), percent("claude_7d_opus_window", 20)))),
            false,
        ).accounts.single()
        assertEquals(listOf(QuotaLevel.Warning, QuotaLevel.Caution, QuotaLevel.Normal), card.windows.map { it.level })
        assertEquals(listOf(5, 25, 80), card.windows.map { it.percentLeft })
    }

    @Test
    fun `claude extra usage keeps its spend figure`() {
        val spend = percent("claude_extra_usage", used = 25).copy(subLabel = "\$25.00 / \$100.00")
        val window = mapper.map(listOf(entry(account("c", "claude", "P"), windows = listOf(spend))), false)
            .accounts.single().windows.single()
        assertEquals(75, window.percentLeft)
        assertEquals("\$25.00 / \$100.00", window.valueText)
        assertEquals(R.string.window_label_extra_usage, window.labelResId)
        assertNull(window.labelName)
    }

    @Test
    fun `a model-specific weekly limit carries the provider's name for it`() {
        val scoped = percent("claude_7d_scoped_fable", used = 40).copy(subLabel = "Fable")
        val window = mapper.map(listOf(entry(account("c", "claude", "P"), windows = listOf(scoped))), false)
            .accounts.single().windows.single()
        assertEquals("Fable", window.labelName)
    }

    @Test
    fun `account status says how much the figures can be trusted`() {
        fun statusOf(status: CurrentQuotaStatus, error: QuotaError? = null) =
            mapper.map(listOf(entry(account("c", "codex", "P"), windows = listOf(percent("weekly", 50)), status = status, error = error)), false)
                .accounts.single()

        assertEquals(HomeAccountStatus.Fresh, statusOf(CurrentQuotaStatus.Fresh).status)
        assertEquals(HomeAccountStatus.Stale, statusOf(CurrentQuotaStatus.PossiblyStale).status)
        assertEquals(HomeAccountStatus.Expired, statusOf(CurrentQuotaStatus.Expired).status)
        assertEquals(HomeAccountStatus.NeedsSignIn, statusOf(CurrentQuotaStatus.AuthRequired).status)

        val failed = statusOf(CurrentQuotaStatus.ErrorWithLastKnownGood, QuotaError.Network(diagnosticsDigest = null))
        assertEquals(HomeAccountStatus.RefreshFailed, failed.status)
        assertEquals(R.string.error_network, failed.errorMessageResId)
        assertEquals(1, failed.windows.size)
    }

    @Test
    fun `an account without data shows no figures, never a zero`() {
        val card = mapper.map(
            listOf(entry(account("c", "codex", "P"), windows = null, status = CurrentQuotaStatus.NoData)),
            false,
        ).accounts.single()
        assertEquals(HomeAccountStatus.NoData, card.status)
        assertTrue(card.windows.isEmpty())
        assertNull(card.lastUpdatedAt)
    }

    @Test
    fun `the header time is the newest update among visible accounts`() {
        val older = entry(account("a", "codex", "A"), windows = listOf(percent("weekly", 50)), fetchedAt = fetched.minusSeconds(3600))
        val newer = entry(account("b", "claude", "B"), windows = listOf(percent("claude_5h_window", 50)), fetchedAt = fetched)
        val newestHidden = entry(account("c", "claude", "C"), windows = listOf(percent("claude_5h_window", 50)), fetchedAt = fetched.plusSeconds(60), hidden = true)

        val state = mapper.map(listOf(older, newer, newestHidden), isRefreshing = true)
        assertEquals(fetched, state.lastUpdatedAt)
        assertTrue(state.isRefreshing)
    }

    @Test
    fun `codex plans get their display names`() {
        val card = mapper.map(listOf(entry(account("c", "codex", "P"), windows = listOf(percent("weekly", 50)), plan = "pro")), false)
            .accounts.single()
        assertEquals("Pro 20x", card.planName)
    }

    private fun account(id: String, provider: String, name: String) = ProviderAccount.createNew(
        localAccountId = LocalAccountId(id),
        providerId = ProviderId(provider),
        providerAccountId = null,
        displayName = name,
        now = Instant.EPOCH,
    )

    private fun percent(
        id: String,
        used: Int?,
        availability: QuotaWindowAvailability = QuotaWindowAvailability.Available,
        seconds: Int? = null,
    ) = QuotaWindow(
        windowId = QuotaWindowId(id),
        titleKey = id,
        usedPercent = used,
        resetAt = fetched.plusSeconds(3600),
        limitWindowSeconds = seconds,
        isPrimaryCandidate = false,
        availability = availability,
    )

    private fun entry(
        account: ProviderAccount,
        windows: List<QuotaWindow>?,
        status: CurrentQuotaStatus = CurrentQuotaStatus.Fresh,
        error: QuotaError? = null,
        hidden: Boolean = false,
        fetchedAt: Instant = fetched,
        plan: String? = null,
    ): HomeAccountEntry {
        val snapshot = windows?.let {
            QuotaSnapshot(
                snapshotId = SnapshotId("s-${account.localAccountId.value}"),
                providerId = account.providerId,
                localAccountId = account.localAccountId,
                providerAccountId = null,
                fetchedAt = fetchedAt,
                source = QuotaSnapshotSource.ManualRefresh,
                planType = plan,
                windows = it,
                credits = null,
                responseDigest = null,
            )
        }
        return HomeAccountEntry(
            state = CurrentQuotaState(
                status = status,
                freshness = CurrentQuotaFreshness.Fresh,
                account = account,
                snapshot = snapshot,
                latestAttempt = null,
                primaryWindow = null,
                secondaryWindows = emptyList(),
                primaryWindowCanAlert = false,
                error = error,
            ),
            hidden = hidden,
        )
    }
}
