package com.kmnexus.codexmeter.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kmnexus.codexmeter.R
import com.kmnexus.codexmeter.domain.theme.ThemeMode
import com.kmnexus.codexmeter.domain.theme.resolveDarkAppearance

data class CodexMeterTextStyles(
    val display: TextStyle,
    val title: TextStyle,
    val body: TextStyle,
    val label: TextStyle,
    val number: TextStyle,
) {
    val material: Typography = Typography(
        displayLarge = display,
        displayMedium = display,
        displaySmall = display,
        headlineLarge = display,
        headlineMedium = display,
        headlineSmall = display,
        titleLarge = title.copy(fontSize = 20.sp, lineHeight = 26.sp),
        titleMedium = title,
        titleSmall = title.copy(fontSize = 15.sp, lineHeight = 20.sp),
        bodyLarge = body.copy(fontSize = 16.sp, lineHeight = 24.sp),
        bodyMedium = body,
        bodySmall = body.copy(fontSize = 13.sp, lineHeight = 18.sp),
        labelLarge = label.copy(fontSize = 14.sp, lineHeight = 20.sp),
        labelMedium = label,
        labelSmall = label.copy(fontSize = 12.sp, lineHeight = 16.sp),
    )
}

/** Geist (SIL OFL 1.1, see NOTICE.md) is the only typeface; numbers use its tabular figures. */
object CodexMeterFontFamilies {
    val geistSans = FontFamily(
        Font(R.font.geist_variable, weight = FontWeight.Normal),
        Font(R.font.geist_variable, weight = FontWeight.Medium),
        Font(R.font.geist_variable, weight = FontWeight.SemiBold),
        Font(R.font.geist_variable, weight = FontWeight.Bold),
    )
}

private val AppTextStyles = CodexMeterTextStyles(
    display = TextStyle(
        fontFamily = CodexMeterFontFamilies.geistSans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 28.sp,
        lineHeight = 34.sp,
        letterSpacing = 0.sp,
    ),
    title = TextStyle(
        fontFamily = CodexMeterFontFamilies.geistSans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 17.sp,
        lineHeight = 22.sp,
        letterSpacing = 0.sp,
    ),
    body = TextStyle(
        fontFamily = CodexMeterFontFamilies.geistSans,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.sp,
    ),
    label = TextStyle(
        fontFamily = CodexMeterFontFamilies.geistSans,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.sp,
    ),
    number = TextStyle(
        fontFamily = CodexMeterFontFamilies.geistSans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 34.sp,
        lineHeight = 38.sp,
        letterSpacing = 0.sp,
        // Tabular figures keep percentages from shifting width as they change.
        fontFeatureSettings = "tnum",
    ),
)

object CodexMeterTypography {
    val display: TextStyle = AppTextStyles.display
    val title: TextStyle = AppTextStyles.title
    val body: TextStyle = AppTextStyles.body
    val label: TextStyle = AppTextStyles.label
    val number: TextStyle = AppTextStyles.number
    val material: Typography = AppTextStyles.material

    val current: CodexMeterTextStyles = AppTextStyles
}

object CodexMeterShapes {
    val xs = RoundedCornerShape(6.dp)
    val sm = RoundedCornerShape(12.dp)
    val md = RoundedCornerShape(16.dp)
    val lg = RoundedCornerShape(22.dp)
    val xl = RoundedCornerShape(28.dp)
    val pill = RoundedCornerShape(999.dp)

    val material = Shapes(
        extraSmall = xs,
        small = sm,
        medium = md,
        large = lg,
        extraLarge = xl,
    )
}

private fun lightScheme(p: CodexMeterColorPalette) = lightColorScheme(
    primary = p.accent,
    onPrimary = p.surface,
    primaryContainer = p.accentSoft,
    onPrimaryContainer = p.accent,
    inversePrimary = p.accent,
    secondary = p.secondary,
    onSecondary = p.surface,
    secondaryContainer = p.neutralAlt,
    onSecondaryContainer = p.secondary,
    tertiary = p.tertiary,
    onTertiary = p.surface,
    tertiaryContainer = p.neutralAlt,
    onTertiaryContainer = p.tertiary,
    background = p.neutral,
    onBackground = p.primary,
    surface = p.surface,
    onSurface = p.primary,
    surfaceVariant = p.surfaceSoft,
    onSurfaceVariant = p.secondary,
    surfaceTint = p.accent,
    inverseSurface = p.primary,
    inverseOnSurface = p.surface,
    outline = p.borderStrong,
    outlineVariant = p.border,
    scrim = p.primary,
    surfaceBright = p.surface,
    surfaceDim = p.neutralAlt,
    surfaceContainer = p.neutral,
    surfaceContainerHigh = p.neutralAlt,
    surfaceContainerHighest = p.neutralAlt,
    surfaceContainerLow = p.surfaceSoft,
    surfaceContainerLowest = p.surface,
    error = p.danger,
    onError = p.surface,
    errorContainer = p.dangerSoft,
    onErrorContainer = p.danger,
    primaryFixed = p.accentSoft,
    primaryFixedDim = p.accentSoft,
    onPrimaryFixed = p.accent,
    onPrimaryFixedVariant = p.accent,
    secondaryFixed = p.neutralAlt,
    secondaryFixedDim = p.neutralAlt,
    onSecondaryFixed = p.primary,
    onSecondaryFixedVariant = p.secondary,
    tertiaryFixed = p.neutralAlt,
    tertiaryFixedDim = p.neutralAlt,
    onTertiaryFixed = p.primary,
    onTertiaryFixedVariant = p.tertiary,
)

private fun darkScheme(p: CodexMeterColorPalette) = darkColorScheme(
    primary = p.accent,
    onPrimary = Color(0xFF0B1426), // dark ink reads on the bright accent
    primaryContainer = p.accentSoft,
    onPrimaryContainer = p.accent,
    secondary = p.secondary,
    onSecondary = p.primary,
    secondaryContainer = p.neutralAlt,
    onSecondaryContainer = p.primary,
    tertiary = p.tertiary,
    onTertiary = p.primary,
    background = p.neutral,
    onBackground = p.primary,
    surface = p.surface,
    onSurface = p.primary,
    surfaceVariant = p.surfaceSoft,
    onSurfaceVariant = p.secondary,
    surfaceTint = p.accent,
    inverseSurface = p.primary,
    inverseOnSurface = p.neutral,
    outline = p.borderStrong,
    outlineVariant = p.border,
    scrim = Color(0xFF000000),
    error = p.danger,
    onError = Color(0xFF2B0F0F),
    errorContainer = p.dangerSoft,
    onErrorContainer = p.danger,
)

internal fun materialScheme(palette: CodexMeterColorPalette, dark: Boolean) =
    if (dark) darkScheme(palette) else lightScheme(palette)

@Composable
fun CodexMeterTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit,
) {
    val dark = resolveDarkAppearance(themeMode, isSystemInDarkTheme())
    val palette = if (dark) DarkCodexMeterColors else LightCodexMeterColors
    CompositionLocalProvider(LocalCodexMeterColors provides palette) {
        MaterialTheme(
            colorScheme = materialScheme(palette, dark),
            typography = CodexMeterTypography.material,
            shapes = CodexMeterShapes.material,
            content = content,
        )
    }
}
