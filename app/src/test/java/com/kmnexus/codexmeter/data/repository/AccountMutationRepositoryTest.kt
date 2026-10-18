package com.kmnexus.codexmeter.data.repository

import androidx.room.Room
import com.kmnexus.codexmeter.data.local.db.CodexMeterDatabase
import com.kmnexus.codexmeter.data.local.entity.ProviderAccountEntity
import com.kmnexus.codexmeter.domain.account.AccountStateRepublisher
import com.kmnexus.codexmeter.domain.model.LocalAccountId
import com.kmnexus.codexmeter.domain.model.ProviderAccount
import com.kmnexus.codexmeter.domain.model.ProviderId
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
// Robolectric SDK 36 currently requires Java 21, while this project is pinned to Java 17.
@Config(sdk = [35])
class AccountMutationRepositoryTest {
    @Test
    fun `renaming existing account persists display name avatar initial and updated time`() = runTest {
        withRepository { db, repository ->
            db.providerAccountDao().upsert(account("local-1", displayName = "Codex Main"))

            val renamed = repository.renameAccount(
                providerId = ProviderId("codex"),
                localAccountId = LocalAccountId("local-1"),
                displayName = "  Work  ",
            )

            val entity = db.providerAccountDao().getById("local-1")
            assertEquals("Work", renamed?.displayName)
            assertEquals("W", renamed?.avatarInitial)
            assertEquals(UPDATED_AT, renamed?.updatedAt)
            assertEquals("Work", entity?.displayName)
            assertEquals("W", entity?.avatarInitial)
            assertEquals(UPDATED_AT.toEpochMilli(), entity?.updatedAt)
            assertEquals("provider-local-1", entity?.providerAccountId)
            assertEquals("active", entity?.status)
            assertEquals(CREATED_AT.toEpochMilli(), entity?.createdAt)
        }
    }

    @Test
    fun `renaming an account republishes it with the new name`() = runTest {
        val republisher = RecordingAccountStateRepublisher()
        withRepository(
            accountStateRepublisher = republisher,
        ) { db, repository ->
            db.providerAccountDao().upsert(account("local-1", displayName = "Codex Main"))

            repository.renameAccount(
                providerId = ProviderId("codex"),
                localAccountId = LocalAccountId("local-1"),
                displayName = "Work",
            )

            assertEquals(listOf(LocalAccountId("local-1")), republisher.republishedAccountIds)
            assertEquals("Work", republisher.republishedAccounts.single().displayName)
        }
    }

    @Test
    fun `renaming missing account returns null`() = runTest {
        withRepository { _, repository ->
            val renamed = repository.renameAccount(
                providerId = ProviderId("codex"),
                localAccountId = LocalAccountId("missing"),
                displayName = "Work",
            )

            assertNull(renamed)
        }
    }

    private suspend fun withRepository(
        accountStateRepublisher: AccountStateRepublisher = RecordingAccountStateRepublisher(),
        block: suspend (CodexMeterDatabase, AccountMutationRepository) -> Unit,
    ) {
        val db = Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(),
            CodexMeterDatabase::class.java,
        ).build()
        val repository = AccountMutationRepository(
            providerAccountDao = db.providerAccountDao(),
            accountStateRepublisher = accountStateRepublisher,
            clock = Clock.fixed(UPDATED_AT, ZoneOffset.UTC),
        )

        try {
            block(db, repository)
        } finally {
            db.close()
        }
    }

    private class RecordingAccountStateRepublisher : AccountStateRepublisher {
        val republishedAccounts = mutableListOf<ProviderAccount>()
        val republishedAccountIds: List<LocalAccountId>
            get() = republishedAccounts.map { it.localAccountId }

        override suspend fun republishAccountState(account: ProviderAccount) {
            republishedAccounts += account
        }
    }

    private fun account(localAccountId: String, displayName: String = localAccountId) = ProviderAccountEntity(
        localAccountId = localAccountId,
        providerId = "codex",
        providerAccountId = "provider-$localAccountId",
        displayName = displayName,
        avatarInitial = displayName.first().uppercase(),
        avatarColorKey = localAccountId,
        status = "active",
        createdAt = CREATED_AT.toEpochMilli(),
        updatedAt = CREATED_AT.toEpochMilli(),
        lastSuccessfulRefreshAt = null,
    )

    private companion object {
        val CREATED_AT: Instant = Instant.parse("2026-05-22T00:00:00Z")
        val UPDATED_AT: Instant = Instant.parse("2026-05-24T12:00:00Z")
    }
}
