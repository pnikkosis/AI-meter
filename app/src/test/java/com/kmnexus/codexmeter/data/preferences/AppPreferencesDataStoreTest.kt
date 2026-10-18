package com.kmnexus.codexmeter.data.preferences

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppPreferencesDataStoreTest {
    @Test
    fun `the current account older versions stored is dropped and nothing else`() = runTest {
        val other = booleanPreferencesKey("other_setting")
        val stored = mutablePreferencesOf(
            stringPreferencesKey("current_provider_id") to "codex",
            stringPreferencesKey("current_local_account_id") to "codex-1234",
            other to true,
        )

        assertTrue(DropCurrentAccountSelection.shouldMigrate(stored))
        assertEquals(mutablePreferencesOf(other to true).asMap(), DropCurrentAccountSelection.migrate(stored).asMap())
    }

    @Test
    fun `half of an old selection is dropped too`() = runTest {
        val stored = mutablePreferencesOf(stringPreferencesKey("current_local_account_id") to "claude-1")

        assertTrue(DropCurrentAccountSelection.shouldMigrate(stored))
        assertEquals(emptyMap<Any, Any>(), DropCurrentAccountSelection.migrate(stored).asMap())
    }

    @Test
    fun `a file without a current account is left alone`() = runTest {
        val stored = mutablePreferencesOf(booleanPreferencesKey("other_setting") to true)

        assertFalse(DropCurrentAccountSelection.shouldMigrate(stored))
    }
}
