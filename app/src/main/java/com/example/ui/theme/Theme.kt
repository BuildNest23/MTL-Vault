package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = JadePrimaryDark,
    onPrimary = JadeOnPrimaryDark,
    primaryContainer = JadePrimaryContainerDark,
    onPrimaryContainer = JadeOnPrimaryContainerDark,
    secondary = OchreSecondaryDark,
    onSecondary = OchreOnSecondaryDark,
    secondaryContainer = OchreSecondaryContainerDark,
    onSecondaryContainer = OchreOnSecondaryContainerDark,
    tertiary = InkTertiaryDark,
    onTertiary = InkOnTertiaryDark,
    tertiaryContainer = InkTertiaryContainerDark,
    onTertiaryContainer = InkOnTertiaryContainerDark,
    background = DarkBackground,
    onBackground = DarkText,
    surface = DarkSurface,
    onSurface = DarkText,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkMuted,
    surfaceContainerLowest = DarkBackground,
    surfaceContainerLow = DarkSurfaceLow,
    surfaceContainer = DarkSurface,
    surfaceContainerHigh = DarkSurfaceVariant,
    surfaceContainerHighest = DarkSurfaceHigh,
    outline = DarkBorder,
    outlineVariant = DarkBorderSubtle,
    error = AccentRedDark,
    errorContainer = AccentRedContainerDark
)

private val LightColorScheme = lightColorScheme(
    primary = JadePrimary,
    onPrimary = JadeOnPrimary,
    primaryContainer = JadePrimaryContainer,
    onPrimaryContainer = JadeOnPrimaryContainer,
    secondary = OchreSecondary,
    onSecondary = OchreOnSecondary,
    secondaryContainer = OchreSecondaryContainer,
    onSecondaryContainer = OchreOnSecondaryContainer,
    tertiary = InkTertiary,
    onTertiary = InkOnTertiary,
    tertiaryContainer = InkTertiaryContainer,
    onTertiaryContainer = InkOnTertiaryContainer,
    background = WarmBackground,
    onBackground = WarmText,
    surface = WarmSurface,
    onSurface = WarmText,
    surfaceVariant = WarmSurfaceVariant,
    onSurfaceVariant = WarmMuted,
    surfaceContainerLowest = WarmSurface,
    surfaceContainerLow = WarmSurfaceLow,
    surfaceContainer = WarmBackground,
    surfaceContainerHigh = WarmSurfaceVariant,
    surfaceContainerHighest = WarmSurfaceHigh,
    outline = WarmBorder,
    outlineVariant = WarmBorderSubtle,
    error = AccentRed,
    errorContainer = AccentRedContainer
)

@Composable
fun MtlVaultTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
