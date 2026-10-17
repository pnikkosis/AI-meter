package com.kmnexus.codexmeter.app

import com.kmnexus.codexmeter.ui.settings.SettingsBackgroundRefreshStatus.Cancelled
import com.kmnexus.codexmeter.ui.settings.SettingsBackgroundRefreshStatus.Failed
import com.kmnexus.codexmeter.ui.settings.SettingsBackgroundRefreshStatus.NoAttempts
import com.kmnexus.codexmeter.ui.settings.SettingsBackgroundRefreshStatus.Retrying
import com.kmnexus.codexmeter.ui.settings.SettingsBackgroundRefreshStatus.Skipped
import com.kmnexus.codexmeter.ui.settings.SettingsBackgroundRefreshStatus.Success
import org.junit.Assert.assertEquals
import org.junit.Test

class BackgroundRefreshStatusCombinationTest {
    @Test
    fun `no account with a background attempt reports no attempts`() {
        assertEquals(NoAttempts, combinedBackgroundRefreshStatus(emptyList()))
    }

    @Test
    fun `success only when every account's latest background refresh succeeded`() {
        assertEquals(Success, combinedBackgroundRefreshStatus(listOf(Success, Success)))
        assertEquals(Skipped, combinedBackgroundRefreshStatus(listOf(Success, Skipped)))
    }

    @Test
    fun `the account that needs attention most decides the label`() {
        assertEquals(Failed, combinedBackgroundRefreshStatus(listOf(Success, Retrying, Failed)))
        assertEquals(Retrying, combinedBackgroundRefreshStatus(listOf(Cancelled, Retrying, Success)))
        assertEquals(Cancelled, combinedBackgroundRefreshStatus(listOf(Skipped, Cancelled)))
    }
}
