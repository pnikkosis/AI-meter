package com.kmnexus.codexmeter.app

import androidx.room.Room
import com.kmnexus.codexmeter.data.local.db.CodexMeterDatabase
import com.kmnexus.codexmeter.data.local.entity.ProviderAccountEntity
import com.kmnexus.codexmeter.data.local.entity.QuotaSnapshotEntity
import com.kmnexus.codexmeter.domain.model.LocalAccountId
import com.kmnexus.codexmeter.domain.model.ProviderId
import com.kmnexus.codexmeter.domain.model.QuotaWindowId
import com.kmnexus.codexmeter.domain.quota.CurrentQuotaStatus
import com.kmnexus.codexmeter.domain.settings.PrimaryQuotaWindowPreferenceReader
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
// Robolectric SDK 36 currently requires Java 21, while this project is pinned to Java 17.
@Config(sdk = [35])
class CurrentQuotaStateRepositoryTest {
    @Test
    fun `without saved accounts the states to republish are the signed-out state`() = runTest {
        withRepository { _, repository ->
            assertEquals(emptyList<CurrentQuotaStatus>(), repository.loadAllAccountStates().map { it.status })
            assertEquals(
                listOf(CurrentQuotaStatus.Unauthenticated),
                repository.loadStatesToRepublish().map { it.status },
            )
        }
    }

    @Test
    fun `every saved account loads its latest snapshot`() = runTest {
        withRepository { db, repository ->
            db.providerAccountDao().upsert(account(localAccountId = "local-1", displayName = "Work"))
            db.providerAccountDao().upsert(account(localAccountId = "local-2", displayName = "Personal"))
            db.quotaSnapshotDao().insert(snapshot(snapshotId = "snapshot-1", localAccountId = "local-1"))

            val states = repository.loadAllAccountStates().associateBy { it.account?.displayName }

            assertEquals(setOf("Work", "Personal"), states.keys)
            assertEquals(CurrentQuotaStatus.Fresh, states.getValue("Work").status)
            assertEquals(62, states.getValue("Work").primaryWindow?.usedPercent)
            assertEquals(CurrentQuotaStatus.NoData, states.getValue("Personal").status)
            assertEquals(states.values.toSet(), repository.loadStatesToRepublish().toSet())
        }
    }

    @Test
    fun `account states use the persisted primary quota window`() = runTest {
        withRepository(primaryWindowId = QuotaWindowId("weekly")) { db, repository ->
            db.providerAccountDao().upsert(account(status = "active"))
            db.quotaSnapshotDao().insert(snapshot(fetchedAt = Instant.parse("2026-05-23T11:50:00Z")))

            val state = repository.loadAllAccountStates().single()

            assertEquals(QuotaWindowId("weekly"), state.primaryWindow?.windowId)
            assertEquals(41, state.primaryWindow?.usedPercent)
            assertEquals(listOf(QuotaWindowId("five_hour")), state.secondaryWindows.map { it.windowId })
        }
    }

    @Test
    fun `an account that needs a new sign-in loads the auth required state`() = runTest {
        withRepository { db, repository ->
            db.providerAccountDao().upsert(account(status = "needs_reauth"))

            assertEquals(CurrentQuotaStatus.AuthRequired, repository.loadAllAccountStates().single().status)
        }
    }

    @Test
    fun `deleted accounts are left out`() = runTest {
        withRepository { db, repository ->
            db.providerAccountDao().upsert(account(localAccountId = "local-1", status = "deleted"))
            db.providerAccountDao().upsert(account(localAccountId = "local-2", displayName = "Kept"))

            assertEquals(listOf("Kept"), repository.loadAllAccountStates().map { it.account?.displayName })
        }
    }

    @Test
    fun `requested account state uses supplied account and window`() = runTest {
        withRepository { db, repository ->
            db.providerAccountDao().upsert(account(localAccountId = "local-1", displayName = "Other"))
            db.providerAccountDao().upsert(account(localAccountId = "local-2", displayName = "Requested"))
            db.quotaSnapshotDao().insert(snapshot(snapshotId = "snapshot-1", localAccountId = "local-1"))
            db.quotaSnapshotDao().insert(snapshot(snapshotId = "snapshot-2", localAccountId = "local-2"))

            val state = repository.loadAccountState(
                providerId = ProviderId("codex"),
                localAccountId = LocalAccountId("local-2"),
                primaryWindowId = QuotaWindowId("weekly"),
            )

            assertEquals("Requested", state.account?.displayName)
            assertEquals(QuotaWindowId("weekly"), state.primaryWindow?.windowId)
            assertEquals(41, state.primaryWindow?.usedPercent)
        }
    }

    private suspend fun withRepository(
        primaryWindowId: QuotaWindowId = QuotaWindowId("five_hour"),
        block: suspend (CodexMeterDatabase, CurrentQuotaStateRepository) -> Unit,
    ) {
        val db = Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(),
            CodexMeterDatabase::class.java,
        ).build()
        val repository = CurrentQuotaStateRepository(
            providerAccountDao = db.providerAccountDao(),
            quotaSnapshotDao = db.quotaSnapshotDao(),
            refreshAttemptDao = db.refreshAttemptDao(),
            primaryQuotaWindowPreferenceReader = StaticPrimaryQuotaWindowPreferenceReader(primaryWindowId),
            clock = Clock.fixed(Instant.parse("2026-05-23T12:00:00Z"), ZoneOffset.UTC),
        )

        try {
            block(db, repository)
        } finally {
            db.close()
        }
    }

    private class StaticPrimaryQuotaWindowPreferenceReader(
        private val windowId: QuotaWindowId,
    ) : PrimaryQuotaWindowPreferenceReader {
        override suspend fun primaryQuotaWindowId(): QuotaWindowId = windowId
    }

    private fun account(
        status: String = "active",
        localAccountId: String = "local-1",
        displayName: String = "Work",
    ) = ProviderAccountEntity(
        localAccountId = localAccountId,
        providerId = "codex",
        providerAccountId = "acct-$localAccountId",
        displayName = displayName,
        avatarInitial = displayName.first().uppercase(),
        avatarColorKey = localAccountId,
        status = status,
        createdAt = Instant.parse("2026-05-23T11:00:00Z").toEpochMilli(),
        updatedAt = Instant.parse("2026-05-23T11:30:00Z").toEpochMilli(),
        lastSuccessfulRefreshAt = null,
    )

    private fun snapshot(
        fetchedAt: Instant = Instant.parse("2026-05-23T11:50:00Z"),
        snapshotId: String = "snapshot-1",
        localAccountId: String = "local-1",
    ) = QuotaSnapshotEntity(
        snapshotId = snapshotId,
        providerId = "codex",
        localAccountId = localAccountId,
        providerAccountId = "acct-$localAccountId",
        fetchedAt = fetchedAt.toEpochMilli(),
        source = "manualRefresh",
        planType = "plus",
        windowsJson = """[{"windowId":"five_hour","titleKey":"quota_window_five_hour","usedPercent":62,"resetAt":1779555600000,"limitWindowSeconds":18000,"isPrimaryCandidate":true,"availability":"Available"},{"windowId":"weekly","titleKey":"quota_window_weekly","usedPercent":41,"resetAt":1780012800000,"limitWindowSeconds":604800,"isPrimaryCandidate":true,"availability":"Available"}]""",
        creditsJson = null,
        responseDigest = "safe-digest",
    )
}
