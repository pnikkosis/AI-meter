package com.kmnexus.codexmeter.data.preferences

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import com.kmnexus.codexmeter.domain.account.AccountDisplayPreferences
import com.kmnexus.codexmeter.domain.model.LocalAccountId
import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class AccountDisplayPreferencesDataStoreTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val a = LocalAccountId("acc-a")
    private val b = LocalAccountId("acc-b")

    private val dataStore by lazy {
        PreferenceDataStoreFactory.create(produceFile = { File(folder.root, "display.preferences_pb") })
    }
    private val store by lazy { AccountDisplayPreferencesDataStore(dataStore) }

    @Test
    fun `nothing stored reads as the defaults`() = runTest {
        assertEquals(AccountDisplayPreferences(), store.accountDisplayPreferences())
    }

    @Test
    fun `order and hidden accounts round trip`() = runTest {
        val written = store.updateAccountDisplayPreferences { it.copy(order = listOf(b, a), hidden = setOf(b)) }

        assertEquals(AccountDisplayPreferences(order = listOf(b, a), hidden = setOf(b)), written)
        assertEquals(written, store.accountDisplayPreferences())
    }

    @Test
    fun `each update starts from what is stored`() = runTest {
        store.updateAccountDisplayPreferences { it.withHidden(a, hidden = true) }
        store.updateAccountDisplayPreferences { it.withHidden(b, hidden = true) }

        assertEquals(setOf(a, b), store.accountDisplayPreferences().hidden)
    }

    @Test
    fun `clearing everything removes the stored values`() = runTest {
        store.updateAccountDisplayPreferences { it.copy(order = listOf(a), hidden = setOf(a)) }
        store.updateAccountDisplayPreferences { it.without(a) }

        assertEquals(AccountDisplayPreferences(), store.accountDisplayPreferences())
    }

    @Test
    fun `an unreadable order reads as no order instead of failing`() = runTest {
        dataStore.edit { it[AccountDisplayPreferencesDataStore.Keys.ORDER] = "not a list" }

        assertEquals(emptyList<LocalAccountId>(), store.accountDisplayPreferences().order)
    }
}
