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
    background = DarkBackground,
    surface = DarkSurface,
    surfaceVariant = DarkSurfaceVariant,
    onBackground = DarkText,
    onSurface = DarkText,
    onSurfaceVariant = DarkMuted,
    outline = DarkBorder,
    error = AccentRed,
    errorContainer = AccentRedContainer
)

private val LightColorScheme = lightColorScheme(
    primary = JadePrimary,
    onPrimary = JadeOnPrimary,
    primaryContainer = JadePrimaryContainer,
    onPrimaryContainer = JadeOnPrimaryContainer,
    background = WarmBackground,
    surface = WarmSurface,
    surfaceVariant = WarmSurfaceVariant,
    onBackground = WarmText,
    onSurface = WarmText,
    onSurfaceVariant = WarmMuted,
    outline = WarmBorder,
    error = AccentRed,
    errorContainer = AccentRedContainer
)

@Composable
fun MtlVaultTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false, // Use our signature ClassGrid Forest Jade by default
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
