package com.kmnexus.codexmeter.widget

import androidx.compose.ui.unit.dp
import com.kmnexus.codexmeter.domain.quota.QuotaLevel
import org.junit.Assert.assertEquals
import org.junit.Test

class AccountsWidgetLayoutTest {
    @Test
    fun `rows fill the height that is left under the header`() {
        assertEquals(1, accountsWidgetRowCapacity(height = 60.dp, wide = false))
        assertEquals(2, accountsWidgetRowCapacity(height = 130.dp, wide = false))
        assertEquals(4, accountsWidgetRowCapacity(height = 210.dp, wide = false))
        assertEquals(3, accountsWidgetRowCapacity(height = 210.dp, wide = true))
    }

    @Test
    fun `wide widgets give each row cells, very wide ones three`() {
        assertEquals(0, accountsWidgetCellCapacity(200.dp))
        assertEquals(2, accountsWidgetCellCapacity(300.dp))
        assertEquals(3, accountsWidgetCellCapacity(380.dp))
    }

    @Test
    fun `a widget too small for any row still shows one`() {
        assertEquals(1, accountsWidgetRowCapacity(height = 30.dp, wide = true))
    }

    @Test
    fun `a very tall widget stops at eight rows`() {
        assertEquals(8, accountsWidgetRowCapacity(height = 600.dp, wide = false))
    }

    @Test
    fun `cells keep the provider's order when every window fits`() {
        val row = row(windows = listOf(window("five_hour", 80), window("weekly", 40)), headline = "weekly")

        assertEquals(listOf("five_hour", "weekly"), row.cellWindows(capacity = 2).map { it.windowId })
    }

    @Test
    fun `when windows do not fit the one that limits the account is never left out`() {
        val row = row(
            windows = listOf(window("claude_5h_window", 80), window("claude_7d_window", 60), window("claude_7d_scoped_opus", 3)),
            headline = "claude_7d_scoped_opus",
        )

        assertEquals(
            listOf("claude_5h_window", "claude_7d_scoped_opus"),
            row.cellWindows(capacity = 2).map { it.windowId },
        )
        assertEquals(listOf("claude_7d_scoped_opus"), row.cellWindows(capacity = 1).map { it.windowId })
    }

    private fun window(id: String, left: Int) =
        AccountsWidgetWindow(windowId = id, percentLeft = left, level = QuotaLevel.Normal)

    private fun row(windows: List<AccountsWidgetWindow>, headline: String?) = AccountsWidgetRow(
        providerId = "claude",
        localAccountId = "a1",
        providerName = "Claude",
        accountName = "Work",
        status = WidgetQuotaStatus.Fresh,
        windows = windows,
        headlineWindowId = headline,
    )
}
