package com.kmnexus.codexmeter.ui.auth

import com.kmnexus.codexmeter.domain.model.LocalAccountId
import com.kmnexus.codexmeter.domain.model.ProviderId
import com.kmnexus.codexmeter.providers.ProviderAuthKind
import com.kmnexus.codexmeter.providers.ProviderRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderSelectionViewModelTest {
    @Test
    fun `providerSelection contains Codex and Claude only`() {
        val providers = ProviderRegistry.all.map { it.providerId.value }
        assertEquals(listOf("claude", "codex"), providers)
    }

    @Test
    fun `providerSelection Codex uses OAuthWebView auth`() {
        val codex = ProviderRegistry.all.find { it.providerId.value == "codex" }
        assertNotNull(codex)
        assertEquals(ProviderAuthKind.OAuthWebView, codex!!.authKind)
        assertEquals("Codex", codex.displayName)
    }

    @Test
    fun `entry mode from route value parses login correctly`() {
        val mode = AddAccountEntryMode.fromRouteValue("login")
        assertTrue(mode is AddAccountEntryMode.LoginToCodex)
    }

    @Test
    fun `retired api key and cookie routes fall back to provider selection`() {
        assertTrue(AddAccountEntryMode.fromRouteValue("apikey:deepseek") is AddAccountEntryMode.ProviderSelection)
        assertTrue(AddAccountEntryMode.fromRouteValue("cookie:kimi") is AddAccountEntryMode.ProviderSelection)
        assertTrue(AddAccountEntryMode.fromRouteValue("devicecode:grok") is AddAccountEntryMode.ProviderSelection)
    }

    @Test
    fun `claude pkce entry mode round-trips with and without relogin account`() {
        val add = AddAccountEntryMode.WebViewOAuthPkce(ProviderId("claude"))
        assertEquals(add, AddAccountEntryMode.fromRouteValue(add.routeValue))

        val relogin = AddAccountEntryMode.WebViewOAuthPkce(ProviderId("claude"), LocalAccountId("claude-1"))
        assertEquals(relogin, AddAccountEntryMode.fromRouteValue(relogin.routeValue))
    }

    @Test
    fun `entry mode from route value falls back to ProviderSelection`() {
        val mode = AddAccountEntryMode.fromRouteValue(null)
        assertTrue(mode is AddAccountEntryMode.ProviderSelection)

        val mode2 = AddAccountEntryMode.fromRouteValue("unknown")
        assertTrue(mode2 is AddAccountEntryMode.ProviderSelection)
    }

    @Test
    fun `entry mode Choose alias equals ProviderSelection`() {
        val choose = AddAccountEntryMode.Choose
        assertTrue(choose is AddAccountEntryMode.ProviderSelection)
    }
}
