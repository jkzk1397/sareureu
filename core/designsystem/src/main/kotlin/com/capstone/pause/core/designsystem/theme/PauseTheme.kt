package com.capstone.pause.core.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf

private val LocalPauseColors = staticCompositionLocalOf { LightPauseColors }
private val LocalPauseType = staticCompositionLocalOf { DefaultPauseType }
private val LocalPauseShape = staticCompositionLocalOf { PauseShape() }
private val LocalPauseSpacing = staticCompositionLocalOf { PauseSpacing() }

/** 앱 테마. 화면은 [PauseTheme.colors] 등 토큰만 쓴다. */
@Composable
fun PauseTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val colors = if (darkTheme) DarkPauseColors else LightPauseColors
    val type = DefaultPauseType
    CompositionLocalProvider(
        LocalPauseColors provides colors,
        LocalPauseType provides type,
        LocalPauseShape provides PauseShape(),
        LocalPauseSpacing provides PauseSpacing(),
    ) {
        MaterialTheme(
            colorScheme = colors.toColorScheme(),
            typography = type.toTypography(),
            shapes = Shapes(
                small = PauseShape().small,
                medium = PauseShape().small,
                large = PauseShape().card,
            ),
            content = content,
        )
    }
}

/** 토큰 접근점. */
object PauseTheme {
    /** 색 토큰. */
    val colors: PauseColors
        @Composable @ReadOnlyComposable
        get() = LocalPauseColors.current

    /** 글자 토큰. */
    val type: PauseType
        @Composable @ReadOnlyComposable
        get() = LocalPauseType.current

    /** 모양 토큰. */
    val shapes: PauseShape
        @Composable @ReadOnlyComposable
        get() = LocalPauseShape.current

    /** 간격 토큰. */
    val spacing: PauseSpacing
        @Composable @ReadOnlyComposable
        get() = LocalPauseSpacing.current
}

/** Material 3 색 체계 매핑. */
fun PauseColors.toColorScheme(): ColorScheme {
    val base = if (isDark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = primary,
        onPrimary = onPrimary,
        primaryContainer = accentContainer,
        onPrimaryContainer = onAccentContainer,
        secondary = social,
        onSecondary = surface,
        tertiary = accent,
        onTertiary = BuddyInkOnLime,
        background = bg,
        onBackground = ink,
        surface = surface,
        onSurface = ink,
        surfaceVariant = surfaceAlt,
        onSurfaceVariant = ink2,
        surfaceContainer = surface,
        surfaceContainerLow = surface,
        surfaceContainerHigh = surfaceAlt,
        surfaceContainerHighest = surfaceAlt,
        outline = line,
        outlineVariant = line,
        error = nudge,
        onError = surface,
        scrim = scrim,
    )
}

private val BuddyInkOnLime = PauseColors.BuddyInk

private fun PauseType.toTypography(): Typography = Typography(
    displayLarge = display,
    displayMedium = display,
    displaySmall = display,
    headlineLarge = headline,
    headlineMedium = headline,
    headlineSmall = title,
    titleLarge = title,
    titleMedium = body.copy(fontWeight = title.fontWeight),
    titleSmall = label,
    bodyLarge = body,
    bodyMedium = bodySmall,
    bodySmall = caption,
    labelLarge = label,
    labelMedium = label,
    labelSmall = caption,
)
