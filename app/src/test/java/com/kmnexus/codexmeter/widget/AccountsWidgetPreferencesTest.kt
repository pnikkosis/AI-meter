package com.kmnexus.codexmeter.widget

import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import com.kmnexus.codexmeter.domain.quota.QuotaLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AccountsWidgetPreferencesTest {
    @Test
    fun `state round trips every row in order`() {
        val state = AccountsWidgetState(
            rows = listOf(
                AccountsWidgetRow(
                    providerId = "codex",
                    localAccountId = "c1",
                    providerName = "Codex",
                    accountName = "Personal",
                    status = WidgetQuotaStatus.Fresh,
                    windows = listOf(
                        AccountsWidgetWindow(
                            windowId = "weekly",
                            limitWindowSeconds = 604_800,
                            percentLeft = 0,
                            level = QuotaLevel.Depleted,
                            resetAt = "2026-10-20T09:00:00Z",
                        ),
                    ),
                    headlineWindowId = "weekly",
                    lastUpdatedAt = "2026-10-17T13:05:00Z",
                ),
                AccountsWidgetRow(
                    providerId = "claude",
                    localAccountId = "a1",
                    providerName = "Claude",
                    accountName = "Work",
                    status = WidgetQuotaStatus.AuthRequired,
                    windows = emptyList(),
                    headlineWindowId = null,
                    errorMessageKey = "error_auth_required",
                ),
            ),
            hasAccounts = true,
        )

        val restored = mutablePreferencesOf().apply { writeAccountsWidgetState(state) }.toAccountsWidgetState()

        assertEquals(state, restored)
    }

    @Test
    fun `a widget nothing was written for yet has no state`() {
        val preferences = mutablePreferencesOf()

        assertFalse(preferences.hasAccountsWidgetState())
        assertEquals(AccountsWidgetState(), preferences.toAccountsWidgetState())
    }

    @Test
    fun `unreadable stored rows read as no rows instead of failing the widget`() {
        val preferences = mutablePreferencesOf().apply {
            writeAccountsWidgetState(AccountsWidgetState(hasAccounts = true))
            this[stringPreferencesKey("accounts_widget_rows")] = "{not json"
        }

        val state = preferences.toAccountsWidgetState()

        assertTrue(state.rows.isEmpty())
        assertTrue(state.hasAccounts)
    }

    @Test
    fun `configuration round trips the chosen accounts and defaults to every visible account`() {
        val chosen = AccountsWidgetConfiguration(chosenAccountIds = listOf("a1", "c1"))

        val restored = mutablePreferencesOf().apply { writeAccountsWidgetConfiguration(chosen) }
            .toAccountsWidgetConfiguration()
        val cleared = mutablePreferencesOf().apply {
            writeAccountsWidgetConfiguration(chosen)
            writeAccountsWidgetConfiguration(AccountsWidgetConfiguration())
        }.toAccountsWidgetConfiguration()

        assertEquals(chosen, restored)
        assertTrue(cleared.followsVisibleAccounts)
        assertTrue(mutablePreferencesOf().toAccountsWidgetConfiguration().followsVisibleAccounts)
    }
}
