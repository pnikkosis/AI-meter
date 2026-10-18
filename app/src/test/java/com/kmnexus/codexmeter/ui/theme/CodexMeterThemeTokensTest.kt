package com.kmnexus.codexmeter.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CodexMeterThemeTokensTest {
    @Test
    fun `sobria light color tokens match design values`() {
        val c = LightCodexMeterColors
        assertFalse(c.isDark)
        assertEquals(Color(0xFF14171C), c.primary)
        assertEquals(Color(0xFF5B6472), c.secondary)
        assertEquals(Color(0xFF686F79), c.tertiary)
        assertEquals(Color(0xFFF4F5F7), c.neutral)
        assertEquals(Color(0xFFECEEF1), c.neutralAlt)
        assertEquals(Color(0xFFFFFFFF), c.surface)
        assertEquals(Color(0xFFF7F8FA), c.surfaceSoft)
        assertEquals(Color(0xFFE2E5EA), c.border)
        assertEquals(Color(0xFFC9CED6), c.borderStrong)
        assertEquals(Color(0xFFE3E7ED), c.track)
        assertEquals(Color(0xFF2F6BEF), c.accent)
        assertEquals(Color(0xFFEAF0FE), c.accentSoft)
        assertEquals(Color(0xFF187D4F), c.success)
        assertEquals(Color(0xFFE6F5EE), c.successSoft)
        assertEquals(Color(0xFF966319), c.warning)
        assertEquals(Color(0xFFFBF1E1), c.warningSoft)
        assertEquals(Color(0xFFBE3C29), c.danger)
        assertEquals(Color(0xFFFCEBE8), c.dangerSoft)
    }

    @Test
    fun `sobria dark color tokens match design values`() {
        val c = DarkCodexMeterColors
        assertTrue(c.isDark)
        assertEquals(Color(0xFFEDEFF3), c.primary)
        assertEquals(Color(0xFFA3ABB7), c.secondary)
        assertEquals(Color(0xFF7C838F), c.tertiary)
        assertEquals(Color(0xFF0E1013), c.neutral)
        assertEquals(Color(0xFF0B0D10), c.neutralAlt)
        assertEquals(Color(0xFF171A1F), c.surface)
        assertEquals(Color(0xFF1D2127), c.surfaceSoft)
        assertEquals(Color(0xFF23272E), c.border)
        assertEquals(Color(0xFF3A404A), c.borderStrong)
        assertEquals(Color(0xFF2A3039), c.track)
        assertEquals(Color(0xFF6E9BFF), c.accent)
        assertEquals(Color(0xFF1A2436), c.accentSoft)
        assertEquals(Color(0xFF4CC38A), c.success)
        assertEquals(Color(0xFF13261C), c.successSoft)
        assertEquals(Color(0xFFF2B24C), c.warning)
        assertEquals(Color(0xFF2B2212), c.warningSoft)
        assertEquals(Color(0xFFFF7A66), c.danger)
        assertEquals(Color(0xFF2D1814), c.dangerSoft)
    }

    @Test
    fun `spacing tokens match design values`() {
        assertEquals(4.dp, CodexMeterSpacing.xs)
        assertEquals(8.dp, CodexMeterSpacing.sm)
        assertEquals(12.dp, CodexMeterSpacing.md)
        assertEquals(16.dp, CodexMeterSpacing.lg)
        assertEquals(20.dp, CodexMeterSpacing.xl)
        assertEquals(24.dp, CodexMeterSpacing.xxl)
    }

    @Test
    fun `shape tokens match design values`() {
        assertEquals(RoundedCornerShape(6.dp), CodexMeterShapes.xs)
        assertEquals(RoundedCornerShape(12.dp), CodexMeterShapes.sm)
        assertEquals(RoundedCornerShape(16.dp), CodexMeterShapes.md)
        assertEquals(RoundedCornerShape(22.dp), CodexMeterShapes.lg)
        assertEquals(RoundedCornerShape(28.dp), CodexMeterShapes.xl)
        assertEquals(RoundedCornerShape(999.dp), CodexMeterShapes.pill)
    }

    @Test
    fun `typography tokens match design values`() {
        assertEquals(28.sp, CodexMeterTypography.display.fontSize)
        assertEquals(FontWeight.SemiBold, CodexMeterTypography.display.fontWeight)
        assertEquals(17.sp, CodexMeterTypography.title.fontSize)
        assertEquals(FontWeight.SemiBold, CodexMeterTypography.title.fontWeight)
        assertEquals(14.sp, CodexMeterTypography.body.fontSize)
        assertEquals(FontWeight.Normal, CodexMeterTypography.body.fontWeight)
        assertEquals(13.sp, CodexMeterTypography.label.fontSize)
        assertEquals(FontWeight.Medium, CodexMeterTypography.label.fontWeight)
        assertEquals(34.sp, CodexMeterTypography.number.fontSize)
        assertEquals(FontWeight.SemiBold, CodexMeterTypography.number.fontWeight)
    }

    @Test
    fun `one sans family everywhere and tabular figures for numbers`() {
        val t = CodexMeterTypography
        assertNotEquals(FontFamily.Default, t.display.fontFamily)
        listOf(t.title, t.body, t.label, t.number).forEach { style ->
            assertEquals(t.display.fontFamily, style.fontFamily)
        }
        assertEquals("tnum", t.number.fontFeatureSettings)
    }

    @Test
    fun `typography keeps zero letter spacing`() {
        assertEquals(0.sp, CodexMeterTypography.display.letterSpacing)
        assertEquals(0.sp, CodexMeterTypography.title.letterSpacing)
        assertEquals(0.sp, CodexMeterTypography.body.letterSpacing)
        assertEquals(0.sp, CodexMeterTypography.label.letterSpacing)
        assertEquals(0.sp, CodexMeterTypography.number.letterSpacing)
    }

    @Test
    fun `material typography roles map to codexmeter tokens`() {
        val material = CodexMeterTypography.material

        assertEquals(CodexMeterTypography.display, material.headlineMedium)
        assertEquals(CodexMeterTypography.title, material.titleMedium)
        assertEquals(CodexMeterTypography.body, material.bodyMedium)
        assertEquals(CodexMeterTypography.label, material.labelMedium)
    }

    @Test
    fun `material color roles map to codexmeter tokens`() {
        val light = materialScheme(LightCodexMeterColors, dark = false)
        assertEquals(LightCodexMeterColors.accent, light.primary)
        assertEquals(LightCodexMeterColors.neutral, light.background)
        assertEquals(LightCodexMeterColors.surface, light.surface)
        assertEquals(LightCodexMeterColors.secondary, light.onSurfaceVariant)
        assertEquals(LightCodexMeterColors.border, light.outlineVariant)
        assertEquals(LightCodexMeterColors.borderStrong, light.outline)
        assertEquals(LightCodexMeterColors.danger, light.error)

        val dark = materialScheme(DarkCodexMeterColors, dark = true)
        assertEquals(DarkCodexMeterColors.accent, dark.primary)
        assertEquals(DarkCodexMeterColors.neutral, dark.background)
        assertEquals(DarkCodexMeterColors.surface, dark.surface)
        assertEquals(DarkCodexMeterColors.secondary, dark.onSurfaceVariant)
        assertEquals(DarkCodexMeterColors.border, dark.outlineVariant)
        assertEquals(DarkCodexMeterColors.borderStrong, dark.outline)
        assertEquals(DarkCodexMeterColors.danger, dark.error)
    }
}
