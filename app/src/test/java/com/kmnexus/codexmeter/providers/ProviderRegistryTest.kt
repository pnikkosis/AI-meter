package com.kmnexus.codexmeter.providers

import com.kmnexus.codexmeter.domain.model.ProviderId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderRegistryTest {
    @Test
    fun all_containsCodex() {
        val codex = ProviderRegistry.all.find { it.providerId == ProviderId("codex") }
        assertNotNull(codex)
        assertTrue(codex!!.isDefault)
    }

    @Test
    fun configFor_returnsCorrectProvider() {
        val config = ProviderRegistry.configFor(ProviderId("codex"))
        assertEquals("Codex", config.displayName)
        assertEquals(ProviderAuthKind.OAuthPkceLogin, config.authKind)
    }

    @Test
    fun defaultProvider_isCodex() {
        assertEquals(ProviderId("codex"), ProviderRegistry.defaultProvider().providerId)
    }

    /** Scope guard: the app tracks exactly the Codex and Claude subscriptions, nothing else. */
    @Test
    fun registryContainsOnlyCodexAndClaude() {
        assertEquals(
            listOf(ProviderRegistry.CLAUDE, ProviderRegistry.CODEX),
            ProviderRegistry.all.map { it.providerId },
        )
        assertTrue(ProviderRegistry.all.none { it.supportsBalance })
    }

    @Test
    fun claudeProvider_usesOAuthPkceAndIsNotDefault() {
        val config = ProviderRegistry.configFor(ProviderRegistry.CLAUDE)
        assertEquals("Claude", config.displayName)
        assertEquals(ProviderAuthKind.OAuthPkceLogin, config.authKind)
        assertFalse(config.isDefault)
        assertEquals(com.kmnexus.codexmeter.R.string.provider_claude_description, config.descriptionResId)
    }

    @Test
    fun codexProviderIsDescribedInWords() {
        val config = ProviderRegistry.configFor(ProviderRegistry.CODEX)
        assertEquals(com.kmnexus.codexmeter.R.string.provider_codex_description, config.descriptionResId)
    }

    @Test
    fun unknownProviderFallsBackToRawId() {
        assertEquals("deepseek", ProviderRegistry.displayNameFor(ProviderId("deepseek")))
    }
}
