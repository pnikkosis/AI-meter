package com.kmnexus.codexmeter.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * AIMeter "Sobria" palette (DESIGN.md): flat surfaces, one blue accent, and a coral that only marks
 * a window with nothing left. Text roles: [primary] > [secondary] > [tertiary].
 */
data class CodexMeterColorPalette(
    val isDark: Boolean,
    val primary: Color, val secondary: Color, val tertiary: Color,
    val neutral: Color, val neutralAlt: Color, val surface: Color, val surfaceSoft: Color,
    val border: Color, val borderStrong: Color, val track: Color,
    val accent: Color, val accentSoft: Color,
    val success: Color, val successSoft: Color,
    val warning: Color, val warningSoft: Color,
    val danger: Color, val dangerSoft: Color,
)

val LightCodexMeterColors = CodexMeterColorPalette(
    isDark = false,
    primary = Color(0xFF14171C), secondary = Color(0xFF5B6472), tertiary = Color(0xFF8A93A0),
    neutral = Color(0xFFF4F5F7), neutralAlt = Color(0xFFECEEF1), surface = Color(0xFFFFFFFF), surfaceSoft = Color(0xFFF7F8FA),
    border = Color(0xFFE2E5EA), borderStrong = Color(0xFFC9CED6), track = Color(0xFFE3E7ED),
    accent = Color(0xFF2F6BEF), accentSoft = Color(0xFFEAF0FE),
    success = Color(0xFF1F9D63), successSoft = Color(0xFFE6F5EE),
    warning = Color(0xFFB7791F), warningSoft = Color(0xFFFBF1E1),
    danger = Color(0xFFD9452F), dangerSoft = Color(0xFFFCEBE8),
)

val DarkCodexMeterColors = CodexMeterColorPalette(
    isDark = true,
    primary = Color(0xFFEDEFF3), secondary = Color(0xFFA3ABB7), tertiary = Color(0xFF747C88),
    neutral = Color(0xFF0E1013), neutralAlt = Color(0xFF0B0D10), surface = Color(0xFF171A1F), surfaceSoft = Color(0xFF1D2127),
    border = Color(0xFF23272E), borderStrong = Color(0xFF3A404A), track = Color(0xFF2A3039),
    accent = Color(0xFF6E9BFF), accentSoft = Color(0xFF1A2436),
    success = Color(0xFF4CC38A), successSoft = Color(0xFF13261C),
    warning = Color(0xFFF2B24C), warningSoft = Color(0xFF2B2212),
    danger = Color(0xFFFF7A66), dangerSoft = Color(0xFF2D1814),
)

internal val LocalCodexMeterColors = staticCompositionLocalOf { LightCodexMeterColors }

/** Same-name object + @Composable fun coexist (different namespaces), mirroring MaterialTheme. */
object CodexMeterTheme {
    val colors: CodexMeterColorPalette
        @Composable @ReadOnlyComposable get() = LocalCodexMeterColors.current
}
