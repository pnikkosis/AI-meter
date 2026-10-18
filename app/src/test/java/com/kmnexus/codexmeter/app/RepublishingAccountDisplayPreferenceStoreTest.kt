package com.kmnexus.codexmeter.app

import com.kmnexus.codexmeter.domain.account.AccountDisplayPreferences
import com.kmnexus.codexmeter.domain.account.AccountSurfacesRefresher
import com.kmnexus.codexmeter.domain.account.InMemoryAccountDisplayPreferenceStore
import com.kmnexus.codexmeter.domain.model.LocalAccountId
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class RepublishingAccountDisplayPreferenceStoreTest {
    @Test
    fun `hiding an account stores the choice and redraws the notification and widgets`() = runTest {
        var refreshes = 0
        val store = RepublishingAccountDisplayPreferenceStore(
            delegate = InMemoryAccountDisplayPreferenceStore(),
            accountSurfacesRefresher = AccountSurfacesRefresher { refreshes++ },
        )

        val updated = store.updateAccountDisplayPreferences { it.withHidden(LocalAccountId("a"), hidden = true) }

        assertEquals(AccountDisplayPreferences(hidden = setOf(LocalAccountId("a"))), updated)
        assertEquals(updated, store.accountDisplayPreferences())
        assertEquals(1, refreshes)
    }

    @Test
    fun `a failing redraw keeps the stored choice`() = runTest {
        val store = RepublishingAccountDisplayPreferenceStore(
            delegate = InMemoryAccountDisplayPreferenceStore(),
            accountSurfacesRefresher = AccountSurfacesRefresher { error("notification service gone") },
        )

        store.updateAccountDisplayPreferences { it.withHidden(LocalAccountId("a"), hidden = true) }

        assertEquals(setOf(LocalAccountId("a")), store.accountDisplayPreferences().hidden)
    }
}
