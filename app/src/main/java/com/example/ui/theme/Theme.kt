package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

enum class ThemeMode {
    DARK, LIGHT, SYSTEM
}

@Immutable
data class JarvisHudColors(
    val arcCyan: Color,
    val glowBlue: Color,
    val panelGlass: Color,
    val panelBorder: Color,
    val gridLine: Color,
    val onlineGreen: Color,
    val warnAmber: Color,
    val errorRed: Color,
    val codeBackground: Color,
    val isDark: Boolean
)

val LocalJarvisHudColors = staticCompositionLocalOf {
    JarvisHudColors(
        arcCyan = HudArcCyan,
        glowBlue = HudElectricCobalt,
        panelGlass = HudPanelDark,
        panelBorder = HudOutlineDark,
        gridLine = HudArcCyan.copy(alpha = 0.08f),
        onlineGreen = HudOnlineMint,
        warnAmber = HudAmberWarn,
        errorRed = HudCrimsonAlert,
        codeBackground = HudCodeSurfaceDark,
        isDark = true
    )
}

private val JarvisDarkColorScheme = darkColorScheme(
    primary = HudArcCyan,
    onPrimary = HudDeepVoid,
    primaryContainer = HudPanelElevatedDark,
    onPrimaryContainer = HudArcCyan,
    secondary = HudElectricCobalt,
    onSecondary = HudIceText,
    secondaryContainer = Color(0xFF0F2952),
    onSecondaryContainer = HudIceText,
    tertiary = HudOnlineMint,
    onTertiary = HudDeepVoid,
    background = HudDeepVoid,
    onBackground = HudIceText,
    surface = HudSurfaceDark,
    onSurface = HudIceText,
    surfaceVariant = HudPanelDark,
    onSurfaceVariant = HudMutedSteel,
    outline = HudOutlineDark,
    outlineVariant = Color(0xFF13294B),
    error = HudCrimsonAlert,
    onError = Color.White
)

private val JarvisLightColorScheme = lightColorScheme(
    primary = HudDeepCobalt,
    onPrimary = Color.White,
    primaryContainer = HudArcticElevated,
    onPrimaryContainer = HudDeepCobalt,
    secondary = HudDeepTeal,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD6F3FA),
    onSecondaryContainer = HudInkDark,
    tertiary = Color(0xFF008F5D),
    onTertiary = Color.White,
    background = HudArcticBg,
    onBackground = HudInkDark,
    surface = HudArcticSurface,
    onSurface = HudInkDark,
    surfaceVariant = HudArcticPanel,
    onSurfaceVariant = HudInkMuted,
    outline = HudOutlineLight,
    outlineVariant = Color(0xFFD0E2F2),
    error = Color(0xFFD32F2F),
    onError = Color.White
)

private val JarvisShapes = Shapes(
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(26.dp)
)

@Composable
fun JarvisTheme(
    themeMode: ThemeMode = ThemeMode.DARK,
    content: @Composable () -> Unit
) {
    val useDark = when (themeMode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }

    val colorScheme = if (useDark) JarvisDarkColorScheme else JarvisLightColorScheme
    val hudColors = if (useDark) {
        JarvisHudColors(
            arcCyan = HudArcCyan,
            glowBlue = HudElectricCobalt,
            panelGlass = HudPanelDark.copy(alpha = 0.88f),
            panelBorder = HudArcCyan.copy(alpha = 0.32f),
            gridLine = HudArcCyan.copy(alpha = 0.07f),
            onlineGreen = HudOnlineMint,
            warnAmber = HudAmberWarn,
            errorRed = HudCrimsonAlert,
            codeBackground = HudCodeSurfaceDark,
            isDark = true
        )
    } else {
        JarvisHudColors(
            arcCyan = HudDeepCobalt,
            glowBlue = HudDeepTeal,
            panelGlass = HudArcticSurface.copy(alpha = 0.94f),
            panelBorder = HudDeepCobalt.copy(alpha = 0.25f),
            gridLine = HudDeepCobalt.copy(alpha = 0.06f),
            onlineGreen = Color(0xFF008F5D),
            warnAmber = Color(0xFFD97706),
            errorRed = Color(0xFFD32F2F),
            codeBackground = HudCodeSurfaceLight,
            isDark = false
        )
    }

    CompositionLocalProvider(LocalJarvisHudColors provides hudColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = JarvisTypography,
            shapes = JarvisShapes,
            content = content
        )
    }
}
