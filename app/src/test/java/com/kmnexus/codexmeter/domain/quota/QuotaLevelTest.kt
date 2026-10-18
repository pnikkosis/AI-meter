package com.kmnexus.codexmeter.domain.quota

import com.kmnexus.codexmeter.domain.model.QuotaWindowId
import com.kmnexus.codexmeter.domain.settings.NotificationPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class QuotaLevelTest {
    private val thresholds = QuotaLevelThresholds(cautionPercent = 30, warningPercent = 10)

    @Test
    fun `percent windows fall into the alert bands by what is left`() {
        assertEquals(QuotaLevel.Normal, percent(used = 40).quotaLevel(thresholds))
        assertEquals(QuotaLevel.Caution, percent(used = 70).quotaLevel(thresholds))
        assertEquals(QuotaLevel.Caution, percent(used = 89).quotaLevel(thresholds))
        assertEquals(QuotaLevel.Warning, percent(used = 90).quotaLevel(thresholds))
        assertEquals(QuotaLevel.Warning, percent(used = 99).quotaLevel(thresholds))
    }

    @Test
    fun `nothing left is depleted whether the provider flags it or reports 0 percent`() {
        assertEquals(QuotaLevel.Depleted, percent(used = 100).quotaLevel(thresholds))
        assertEquals(
            QuotaLevel.Depleted,
            percent(used = 100, availability = QuotaWindowAvailability.Depleted).quotaLevel(thresholds),
        )
    }

    @Test
    fun `a window without a figure is unknown, never zero`() {
        assertEquals(QuotaLevel.Unknown, percent(used = null, availability = QuotaWindowAvailability.Missing).quotaLevel(thresholds))
        assertEquals(QuotaLevel.Unknown, percent(used = 50, availability = QuotaWindowAvailability.DecodeFailed).quotaLevel(thresholds))
        assertEquals(QuotaLevel.Unknown, percent(used = null).quotaLevel(thresholds))
    }

    @Test
    fun `balances use the balance bands`() {
        val bands = QuotaLevelThresholds(balanceCaution = 5.0, balanceWarning = 1.0)
        assertEquals(QuotaLevel.Normal, balance("12.00").quotaLevel(bands))
        assertEquals(QuotaLevel.Caution, balance("4.99").quotaLevel(bands))
        assertEquals(QuotaLevel.Warning, balance("0.50").quotaLevel(bands))
        assertEquals(QuotaLevel.Depleted, balance("0").quotaLevel(bands))
        assertEquals(QuotaLevel.Unknown, balance(null).quotaLevel(bands))
    }

    @Test
    fun `thresholds come from the notification preferences`() {
        val preferences = NotificationPreferences(cautionThreshold = 40, warningThreshold = 15)
        val fromPreferences = preferences.quotaLevelThresholds()
        assertEquals(40, fromPreferences.cautionPercent)
        assertEquals(15, fromPreferences.warningPercent)
        assertEquals(preferences.balanceCautionThreshold, fromPreferences.balanceCaution, 0.0)
        assertEquals(preferences.balanceWarningThreshold, fromPreferences.balanceWarning, 0.0)
    }

    @Test
    fun `the most urgent level wins and unknown only when nothing is known`() {
        assertEquals(QuotaLevel.Depleted, listOf(QuotaLevel.Normal, QuotaLevel.Depleted, QuotaLevel.Warning).mostUrgent())
        assertEquals(QuotaLevel.Warning, listOf(QuotaLevel.Caution, QuotaLevel.Warning).mostUrgent())
        assertEquals(QuotaLevel.Normal, listOf(QuotaLevel.Unknown, QuotaLevel.Normal).mostUrgent())
        assertEquals(QuotaLevel.Unknown, emptyList<QuotaLevel>().mostUrgent())
    }

    @Test
    fun `headline window is the one with the least left`() {
        val fiveHour = percent(id = "five_hour", used = 0)
        val weekly = percent(id = "weekly", used = 100, availability = QuotaWindowAvailability.Depleted)
        assertEquals("weekly", listOf(fiveHour, weekly).headlineWindow()?.windowId?.value)

        val claudeSession = percent(id = "claude_5h_window", used = 33)
        val claudeWeekly = percent(id = "claude_7d_window", used = 13)
        assertEquals("claude_5h_window", listOf(claudeSession, claudeWeekly).headlineWindow()?.windowId?.value)
    }

    @Test
    fun `headline ignores windows without a figure and the extra-usage spend meter`() {
        val missing = percent(id = "five_hour", used = null, availability = QuotaWindowAvailability.Missing)
        val spend = percent(id = "claude_extra_usage", used = 90)
        val weekly = percent(id = "claude_7d_window", used = 20)
        assertEquals("claude_7d_window", listOf(missing, spend, weekly).headlineWindow()?.windowId?.value)
        assertNull(listOf(missing, spend).headlineWindow())
        assertNull(emptyList<QuotaWindow>().headlineWindow())
    }

    @Test
    fun `ties keep the provider's order`() {
        val first = percent(id = "a", used = 50)
        val second = percent(id = "b", used = 50)
        assertEquals("a", listOf(first, second).headlineWindow()?.windowId?.value)
    }

    private fun percent(
        id: String = "five_hour",
        used: Int?,
        availability: QuotaWindowAvailability = QuotaWindowAvailability.Available,
    ) = QuotaWindow(
        windowId = QuotaWindowId(id),
        titleKey = id,
        usedPercent = used,
        resetAt = null,
        limitWindowSeconds = null,
        isPrimaryCandidate = false,
        availability = availability,
    )

    private fun balance(amount: String?) = QuotaWindow(
        windowId = QuotaWindowId("balance"),
        titleKey = "balance",
        usedPercent = null,
        resetAt = null,
        limitWindowSeconds = null,
        isPrimaryCandidate = false,
        availability = QuotaWindowAvailability.Available,
        displayKind = QuotaWindowDisplayKind.Balance,
        balanceAmount = amount,
        balanceCurrency = "USD",
    )
}
