package com.kmnexus.codexmeter.ui

import com.kmnexus.codexmeter.providers.ProviderRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProviderPlanDisplayNameTest {
    @Test
    fun codexUsesPro20xMappingAndTitlecasesUnknowns() {
        assertEquals("Pro 20x", providerPlanDisplayName(ProviderRegistry.CODEX, "pro"))
        assertEquals("Pro 5x", providerPlanDisplayName(ProviderRegistry.CODEX, "prolite"))
        assertEquals("Plus", providerPlanDisplayName(ProviderRegistry.CODEX, "plus"))
        assertEquals("Free", providerPlanDisplayName(ProviderRegistry.CODEX, "free"))
    }

    @Test
    fun claudeShowsAnyReportedPlanVerbatimWithoutCodexMapping() {
        // Regression guard: the Codex "Pro 20x" mapping must never leak into another provider.
        assertEquals("pro", providerPlanDisplayName(ProviderRegistry.CLAUDE, "pro"))
    }

    @Test
    fun blankOrMissingPlanIsNull() {
        assertNull(providerPlanDisplayName(ProviderRegistry.CLAUDE, null))
        assertNull(providerPlanDisplayName(ProviderRegistry.CODEX, "   "))
    }
}
