package com.nextgen.caros.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.sp
import com.nextgen.caros.data.AccentColor
import com.nextgen.caros.data.AppSettings
import com.nextgen.caros.data.ThemeMode

@Immutable
data class NgColors(
    val isDark: Boolean,
    val background: Color,
    val glass: Color,
    val glassStrong: Color,
    val glassBorder: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val accent: Color,
    val accent2: Color,
    val success: Color = Color(0xFF30D158),
    val warning: Color = Color(0xFFFF9F0A),
    val danger: Color = Color(0xFFFF453A),
    val verified: Color = Color(0xFF34D399),
    val mapped: Color = Color(0xFF60A5FA),
    val unverified: Color = Color(0xFFFBBF24),
) {
    val accentBrush: Brush get() = Brush.linearGradient(listOf(accent, accent2))
}

val LocalNg = staticCompositionLocalOf { darkColors(AccentColor.CYAN) }

fun darkColors(a: AccentColor) = NgColors(
    isDark = true,
    background = Color(0xFF020306),
    glass = Color(0x14FFFFFF),
    glassStrong = Color(0x24FFFFFF),
    glassBorder = Color(0x21FFFFFF),
    textPrimary = Color(0xFFFFFFFF),
    textSecondary = Color(0x99EBEBF5),
    textTertiary = Color(0x4DEBEBF5),
    accent = Color(a.argb),
    accent2 = Color(a.argb2),
)

fun lightColors(a: AccentColor) = NgColors(
    isDark = false,
    background = Color(0xFFF2F2F7),
    glass = Color(0xCCFFFFFF),
    glassStrong = Color(0xF2FFFFFF),
    glassBorder = Color(0x14000000),
    textPrimary = Color(0xFF000000),
    textSecondary = Color(0x993C3C43),
    textTertiary = Color(0x4D3C3C43),
    accent = Color(a.argb).let { if (a == AccentColor.CYAN || a == AccentColor.ICE) Color(0xFF0A84FF) else it },
    accent2 = Color(a.argb2),
)

object NgType {
    val font = FontFamily.SansSerif
    val clockHuge = TextStyle(fontFamily = font, fontWeight = FontWeight.Thin, fontSize = 96.sp, letterSpacing = (-2).sp)
    val largeTitle = TextStyle(fontFamily = font, fontWeight = FontWeight.Bold, fontSize = 34.sp, letterSpacing = 0.4.sp)
    val speedHuge = TextStyle(fontFamily = font, fontWeight = FontWeight.Black, fontSize = 92.sp, letterSpacing = (-3).sp)
    val display = TextStyle(fontFamily = font, fontWeight = FontWeight.Light, fontSize = 56.sp, letterSpacing = (-1).sp)
    val title = TextStyle(fontFamily = font, fontWeight = FontWeight.SemiBold, fontSize = 24.sp)
    val headline = TextStyle(fontFamily = font, fontWeight = FontWeight.SemiBold, fontSize = 19.sp)
    val body = TextStyle(fontFamily = font, fontWeight = FontWeight.Normal, fontSize = 16.sp)
    val label = TextStyle(fontFamily = font, fontWeight = FontWeight.Medium, fontSize = 14.sp, letterSpacing = 0.2.sp)
    val caption = TextStyle(fontFamily = font, fontWeight = FontWeight.Medium, fontSize = 12.sp, letterSpacing = 0.6.sp)
}

/**
 * Applies the design system. Also normalises density so the UI is authored against a ~560dp
 * tall canvas: a 1024×600 head unit and a 1920×720 one render the same proportions, with wide
 * screens simply gaining horizontal space.
 */
@Composable
fun NextGenTheme(settings: AppSettings, content: @Composable () -> Unit) {
    val dark = when (settings.themeMode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    val ng = if (dark) darkColors(settings.accent) else lightColors(settings.accent)
    val scheme = if (dark) darkColorScheme(
        primary = ng.accent, secondary = ng.accent2, background = ng.background, surface = Color(0xFF0E1320),
        onPrimary = Color.Black, onBackground = ng.textPrimary, onSurface = ng.textPrimary,
        surfaceVariant = Color(0xFF1A2133), onSurfaceVariant = ng.textSecondary,
    ) else lightColorScheme(
        primary = ng.accent, secondary = ng.accent2, background = ng.background, surface = Color.White,
        onPrimary = Color.White, onBackground = ng.textPrimary, onSurface = ng.textPrimary,
    )

    val config = LocalConfiguration.current
    val base = LocalDensity.current
    val factor = (config.screenHeightDp / 560f).coerceIn(0.72f, 2.2f)
    val density = Density(base.density * factor, base.fontScale.coerceAtMost(1.15f))

    CompositionLocalProvider(LocalNg provides ng, LocalDensity provides density) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
