package com.kmnexus.codexmeter.notification

import com.kmnexus.codexmeter.domain.model.LocalAccountId
import com.kmnexus.codexmeter.domain.model.ProviderAccount
import com.kmnexus.codexmeter.domain.model.ProviderId
import com.kmnexus.codexmeter.domain.model.QuotaWindowId
import com.kmnexus.codexmeter.domain.model.SnapshotId
import com.kmnexus.codexmeter.domain.quota.CurrentQuotaFreshness
import com.kmnexus.codexmeter.domain.quota.CurrentQuotaState
import com.kmnexus.codexmeter.domain.quota.CurrentQuotaStatus
import com.kmnexus.codexmeter.domain.quota.QuotaSnapshot
import com.kmnexus.codexmeter.domain.quota.QuotaSnapshotSource
import com.kmnexus.codexmeter.domain.quota.QuotaWindow
import com.kmnexus.codexmeter.domain.quota.QuotaWindowAvailability
import com.kmnexus.codexmeter.testing.TestStrings
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

class StatusNotificationContentTest {
    private val codexPersonal = state(
        account("c1", "codex", "Personal"),
        windows = listOf(
            window("five_hour", used = null, availability = QuotaWindowAvailability.Missing),
            window("weekly", used = 100, availability = QuotaWindowAvailability.Depleted, seconds = WEEK),
        ),
    )
    private val claudeWork = state(
        account("a1", "claude", "Work"),
        windows = listOf(
            window("claude_5h_window", used = 33, seconds = FIVE_HOURS),
            window("claude_7d_window", used = 13, seconds = WEEK),
            window("claude_extra_usage", used = 20).copy(subLabel = "\$20.00 / \$100.00"),
        ),
    )

    @Test
    fun `title gives each account the share left of the window that limits it`() {
        assertEquals("Left: Codex 0% · Claude 67%", render(StatusNotificationContent.title(listOf(codexPersonal, claudeWork))))
    }

    @Test
    fun `body has one line per account with every window`() {
        assertEquals(
            "Codex · Personal: 7-day quota used up\nClaude · Work: 5h quota 67%, 7-day quota 87%",
            render(StatusNotificationContent.body(listOf(codexPersonal, claudeWork))),
        )
    }

    @Test
    fun `two accounts of one provider are told apart by name in the title`() {
        val codexWork = state(account("c2", "codex", "Work"), windows = listOf(window("weekly", used = 60, seconds = WEEK)))
        assertEquals("Left: Codex Personal 0% · Codex Work 40%", render(StatusNotificationContent.title(listOf(codexPersonal, codexWork))))
    }

    @Test
    fun `lines say when figures may be old or the account needs attention`() {
        val stale = claudeWork.copy(status = CurrentQuotaStatus.Expired)
        val failed = claudeWork.copy(status = CurrentQuotaStatus.ErrorWithLastKnownGood)
        val signIn = claudeWork.copy(status = CurrentQuotaStatus.AuthRequired)
        assertEquals(
            "Claude · Work: 5h quota 67%, 7-day quota 87% · may be out of date",
            render(StatusNotificationContent.body(listOf(stale))),
        )
        assertEquals(
            "Claude · Work: 5h quota 67%, 7-day quota 87% · couldn't refresh",
            render(StatusNotificationContent.body(listOf(failed))),
        )
        assertEquals(
            "Claude · Work: 5h quota 67%, 7-day quota 87% · reconnect the account",
            render(StatusNotificationContent.body(listOf(signIn))),
        )
    }

    @Test
    fun `an account without figures says so instead of showing zero`() {
        val empty = state(account("c3", "codex", "New"), windows = null, status = CurrentQuotaStatus.NoData)
        assertEquals("Left: Codex –", render(StatusNotificationContent.title(listOf(empty))))
        assertEquals("Codex · New: no figures yet", render(StatusNotificationContent.body(listOf(empty))))
    }

    @Test
    fun `without accounts the notification asks to connect one`() {
        assertEquals("AIMeter", render(StatusNotificationContent.title(emptyList())))
        assertEquals(
            "Connect an account to show quota status.",
            render(StatusNotificationContent.body(emptyList())),
        )
    }

    private fun render(text: NotificationText) = TestStrings.render(text)

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
        resetAt = null,
        limitWindowSeconds = seconds,
        isPrimaryCandidate = false,
        availability = availability,
    )

    private fun state(
        account: ProviderAccount,
        windows: List<QuotaWindow>?,
        status: CurrentQuotaStatus = CurrentQuotaStatus.Fresh,
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
                fetchedAt = Instant.EPOCH,
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
        error = null,
    )

    private companion object {
        const val FIVE_HOURS = 5 * 60 * 60
        const val WEEK = 7 * 24 * 60 * 60
    }
}
