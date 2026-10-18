package com.kmnexus.codexmeter.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AccountsWidgetConfigurationPolicyTest {
    private val accounts = listOf(option("c"), option("b", hidden = true), option("a"))

    @Test
    fun `choices keep Home's order whatever the tapping order`() {
        val afterA = toggleAccountChoice(emptyList(), "a", order = listOf("c", "b", "a"))
        val afterC = toggleAccountChoice(afterA, "c", order = listOf("c", "b", "a"))

        assertEquals(listOf("c", "a"), afterC)
        assertEquals(listOf("a"), toggleAccountChoice(afterC, "c", order = listOf("c", "b", "a")))
    }

    @Test
    fun `switching to chosen accounts starts from the ones visible on Home`() {
        val state = AccountsWidgetConfigurationScreenState(accounts = accounts)

        val choosing = state.choosingAccounts()

        assertFalse(choosing.followsVisibleAccounts)
        assertEquals(listOf("c", "a"), choosing.chosenAccountIds)
    }

    @Test
    fun `an empty choice cannot be saved, every visible account always can`() {
        val state = AccountsWidgetConfigurationScreenState(accounts = accounts)

        assertTrue(state.canSave)
        assertFalse(state.copy(followsVisibleAccounts = false, chosenAccountIds = emptyList()).canSave)
        assertEquals(AccountsWidgetConfiguration(), state.toConfiguration())
        assertEquals(
            AccountsWidgetConfiguration(chosenAccountIds = listOf("b")),
            state.copy(followsVisibleAccounts = false, chosenAccountIds = listOf("b")).toConfiguration(),
        )
    }

    @Test
    fun `a stored choice opens as chosen accounts, without ones that were removed`() {
        val state = AccountsWidgetConfigurationScreenState.from(
            accounts = accounts,
            configuration = AccountsWidgetConfiguration(chosenAccountIds = listOf("a", "gone")),
        )

        assertFalse(state.followsVisibleAccounts)
        assertEquals(listOf("a"), state.chosenAccountIds)
    }

    private fun option(id: String, hidden: Boolean = false) = AccountsWidgetAccountOption(
        localAccountId = id,
        providerName = "Codex",
        accountName = "Cuenta $id",
        hiddenOnHome = hidden,
    )
}
