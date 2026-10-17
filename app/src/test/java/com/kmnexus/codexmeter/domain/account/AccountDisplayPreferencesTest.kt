package com.kmnexus.codexmeter.domain.account

import com.kmnexus.codexmeter.domain.model.LocalAccountId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AccountDisplayPreferencesTest {
    private val a = LocalAccountId("a")
    private val b = LocalAccountId("b")
    private val c = LocalAccountId("c")

    @Test
    fun `without preferences accounts keep their saved order and are all visible`() {
        val prefs = AccountDisplayPreferences()
        assertEquals(listOf(a, b, c), prefs.arrange(listOf(a, b, c)) { it })
        assertEquals(listOf(a, b, c), prefs.visible(listOf(a, b, c)) { it })
    }

    @Test
    fun `the chosen order wins and new accounts go last in saved order`() {
        val prefs = AccountDisplayPreferences(order = listOf(c, a))
        assertEquals(listOf(c, a, b), prefs.arrange(listOf(a, b, c)) { it })
    }

    @Test
    fun `order entries for accounts that no longer exist are ignored`() {
        val prefs = AccountDisplayPreferences(order = listOf(LocalAccountId("gone"), b))
        assertEquals(listOf(b, a), prefs.arrange(listOf(a, b)) { it })
    }

    @Test
    fun `hidden accounts stay in the arrangement but not among the visible ones`() {
        val prefs = AccountDisplayPreferences(hidden = setOf(b))
        assertTrue(prefs.isHidden(b))
        assertFalse(prefs.isHidden(a))
        assertEquals(listOf(a, b, c), prefs.arrange(listOf(a, b, c)) { it })
        assertEquals(listOf(a, c), prefs.visible(listOf(a, b, c)) { it })
    }

    @Test
    fun `hiding and showing toggle one account`() {
        val hidden = AccountDisplayPreferences().withHidden(a, hidden = true)
        assertEquals(setOf(a), hidden.hidden)
        assertEquals(emptySet<LocalAccountId>(), hidden.withHidden(a, hidden = false).hidden)
    }

    @Test
    fun `moving swaps with the neighbour and writes the whole order`() {
        val prefs = AccountDisplayPreferences()
        val current = listOf(a, b, c)
        assertEquals(listOf(b, a, c), prefs.moved(b, offset = -1, currentOrder = current).order)
        assertEquals(listOf(a, c, b), prefs.moved(b, offset = 1, currentOrder = current).order)
    }

    @Test
    fun `moving past either end changes nothing`() {
        val prefs = AccountDisplayPreferences(order = listOf(a, b))
        assertEquals(prefs, prefs.moved(a, offset = -1, currentOrder = listOf(a, b)))
        assertEquals(prefs, prefs.moved(b, offset = 1, currentOrder = listOf(a, b)))
        assertEquals(prefs, prefs.moved(LocalAccountId("unknown"), offset = 1, currentOrder = listOf(a, b)))
    }

    @Test
    fun `a removed account leaves no trace`() {
        val prefs = AccountDisplayPreferences(order = listOf(a, b), hidden = setOf(b))
        val cleaned = prefs.without(b)
        assertEquals(listOf(a), cleaned.order)
        assertEquals(emptySet<LocalAccountId>(), cleaned.hidden)
    }
}
