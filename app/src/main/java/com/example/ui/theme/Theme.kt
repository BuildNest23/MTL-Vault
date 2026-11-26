package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

enum class AppThemeMode(val key: String, val label: String) {
    SYSTEM("system", "System"),
    DARK("dark", "Matte Dark"),
    LIGHT("light", "Light");

    companion object {
        fun fromKey(key: String?): AppThemeMode =
            entries.firstOrNull { it.key == key } ?: SYSTEM
    }
}

enum class MattePalette(
    val key: String,
    val label: String,
    val subtitle: String,
    val previewBackground: Color,
    val previewSurface: Color,
    val previewAccent: Color,
    val previewSecondary: Color
) {
    CHARCOAL(
        key = "charcoal",
        label = "Charcoal",
        subtitle = "Soft graphite",
        previewBackground = DarkBackground,
        previewSurface = DarkSurface,
        previewAccent = JadePrimaryDark,
        previewSecondary = OchreSecondaryDark
    ),
    SLATE(
        key = "slate",
        label = "Slate",
        subtitle = "Blue-gray",
        previewBackground = MatteSlateBackground,
        previewSurface = MatteSlateSurface,
        previewAccent = MatteSlatePrimary,
        previewSecondary = MatteSlateSecondary
    ),
    FOREST(
        key = "forest",
        label = "Forest",
        subtitle = "Muted pine",
        previewBackground = MatteForestBackground,
        previewSurface = MatteForestSurface,
        previewAccent = MatteForestPrimary,
        previewSecondary = MatteForestSecondary
    ),
    ESPRESSO(
        key = "espresso",
        label = "Espresso",
        subtitle = "Warm cocoa",
        previewBackground = MatteEspressoBackground,
        previewSurface = MatteEspressoSurface,
        previewAccent = MatteEspressoPrimary,
        previewSecondary = MatteEspressoSecondary
    ),
    PLUM(
        key = "plum",
        label = "Plum",
        subtitle = "Aubergine",
        previewBackground = MattePlumBackground,
        previewSurface = MattePlumSurface,
        previewAccent = MattePlumPrimary,
        previewSecondary = MattePlumSecondary
    );

    companion object {
        fun fromKey(key: String?): MattePalette =
            entries.firstOrNull { it.key == key } ?: CHARCOAL
    }
}

data class AppAppearance(
    val themeMode: AppThemeMode = AppThemeMode.SYSTEM,
    val mattePalette: MattePalette = MattePalette.CHARCOAL,
    val fontPreset: AppFontPreset = AppFontPreset.EDITORIAL,
    val dynamicColor: Boolean = false
)

private val MatteCharcoalColorScheme = darkColorScheme(
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

private val MatteSlateColorScheme = darkColorScheme(
    primary = MatteSlatePrimary,
    onPrimary = MatteSlateOnPrimary,
    primaryContainer = MatteSlatePrimaryContainer,
    onPrimaryContainer = MatteSlateOnPrimaryContainer,
    secondary = MatteSlateSecondary,
    onSecondary = MatteSlateOnSecondary,
    secondaryContainer = MatteSlateSecondaryContainer,
    onSecondaryContainer = MatteSlateOnSecondaryContainer,
    tertiary = OchreSecondaryDark,
    onTertiary = OchreOnSecondaryDark,
    tertiaryContainer = OchreSecondaryContainerDark,
    onTertiaryContainer = OchreOnSecondaryContainerDark,
    background = MatteSlateBackground,
    onBackground = MatteSlateText,
    surface = MatteSlateSurface,
    onSurface = MatteSlateText,
    surfaceVariant = MatteSlateSurfaceVariant,
    onSurfaceVariant = MatteSlateMuted,
    surfaceContainerLowest = MatteSlateBackground,
    surfaceContainerLow = MatteSlateSurfaceLow,
    surfaceContainer = MatteSlateSurface,
    surfaceContainerHigh = MatteSlateSurfaceVariant,
    surfaceContainerHighest = MatteSlateSurfaceHigh,
    outline = MatteSlateBorder,
    outlineVariant = MatteSlateBorderSubtle,
    error = AccentRedDark,
    errorContainer = AccentRedContainerDark
)

private val MatteForestColorScheme = darkColorScheme(
    primary = MatteForestPrimary,
    onPrimary = MatteForestOnPrimary,
    primaryContainer = MatteForestPrimaryContainer,
    onPrimaryContainer = MatteForestOnPrimaryContainer,
    secondary = MatteForestSecondary,
    onSecondary = MatteForestOnSecondary,
    secondaryContainer = MatteForestSecondaryContainer,
    onSecondaryContainer = MatteForestOnSecondaryContainer,
    tertiary = InkTertiaryDark,
    onTertiary = InkOnTertiaryDark,
    tertiaryContainer = InkTertiaryContainerDark,
    onTertiaryContainer = InkOnTertiaryContainerDark,
    background = MatteForestBackground,
    onBackground = MatteForestText,
    surface = MatteForestSurface,
    onSurface = MatteForestText,
    surfaceVariant = MatteForestSurfaceVariant,
    onSurfaceVariant = MatteForestMuted,
    surfaceContainerLowest = MatteForestBackground,
    surfaceContainerLow = MatteForestSurfaceLow,
    surfaceContainer = MatteForestSurface,
    surfaceContainerHigh = MatteForestSurfaceVariant,
    surfaceContainerHighest = MatteForestSurfaceHigh,
    outline = MatteForestBorder,
    outlineVariant = MatteForestBorderSubtle,
    error = AccentRedDark,
    errorContainer = AccentRedContainerDark
)

private val MatteEspressoColorScheme = darkColorScheme(
    primary = MatteEspressoPrimary,
    onPrimary = MatteEspressoOnPrimary,
    primaryContainer = MatteEspressoPrimaryContainer,
    onPrimaryContainer = MatteEspressoOnPrimaryContainer,
    secondary = MatteEspressoSecondary,
    onSecondary = MatteEspressoOnSecondary,
    secondaryContainer = MatteEspressoSecondaryContainer,
    onSecondaryContainer = MatteEspressoOnSecondaryContainer,
    tertiary = InkTertiaryDark,
    onTertiary = InkOnTertiaryDark,
    tertiaryContainer = InkTertiaryContainerDark,
    onTertiaryContainer = InkOnTertiaryContainerDark,
    background = MatteEspressoBackground,
    onBackground = MatteEspressoText,
    surface = MatteEspressoSurface,
    onSurface = MatteEspressoText,
    surfaceVariant = MatteEspressoSurfaceVariant,
    onSurfaceVariant = MatteEspressoMuted,
    surfaceContainerLowest = MatteEspressoBackground,
    surfaceContainerLow = MatteEspressoSurfaceLow,
    surfaceContainer = MatteEspressoSurface,
    surfaceContainerHigh = MatteEspressoSurfaceVariant,
    surfaceContainerHighest = MatteEspressoSurfaceHigh,
    outline = MatteEspressoBorder,
    outlineVariant = MatteEspressoBorderSubtle,
    error = AccentRedDark,
    errorContainer = AccentRedContainerDark
)

private val MattePlumColorScheme = darkColorScheme(
    primary = MattePlumPrimary,
    onPrimary = MattePlumOnPrimary,
    primaryContainer = MattePlumPrimaryContainer,
    onPrimaryContainer = MattePlumOnPrimaryContainer,
    secondary = MattePlumSecondary,
    onSecondary = MattePlumOnSecondary,
    secondaryContainer = MattePlumSecondaryContainer,
    onSecondaryContainer = MattePlumOnSecondaryContainer,
    tertiary = InkTertiaryDark,
    onTertiary = InkOnTertiaryDark,
    tertiaryContainer = InkTertiaryContainerDark,
    onTertiaryContainer = InkOnTertiaryContainerDark,
    background = MattePlumBackground,
    onBackground = MattePlumText,
    surface = MattePlumSurface,
    onSurface = MattePlumText,
    surfaceVariant = MattePlumSurfaceVariant,
    onSurfaceVariant = MattePlumMuted,
    surfaceContainerLowest = MattePlumBackground,
    surfaceContainerLow = MattePlumSurfaceLow,
    surfaceContainer = MattePlumSurface,
    surfaceContainerHigh = MattePlumSurfaceVariant,
    surfaceContainerHighest = MattePlumSurfaceHigh,
    outline = MattePlumBorder,
    outlineVariant = MattePlumBorderSubtle,
    error = AccentRedDark,
    errorContainer = AccentRedContainerDark
)

fun colorSchemeFor(palette: MattePalette): ColorScheme = when (palette) {
    MattePalette.CHARCOAL -> MatteCharcoalColorScheme
    MattePalette.SLATE -> MatteSlateColorScheme
    MattePalette.FOREST -> MatteForestColorScheme
    MattePalette.ESPRESSO -> MatteEspressoColorScheme
    MattePalette.PLUM -> MattePlumColorScheme
}

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
    mattePalette: MattePalette = MattePalette.CHARCOAL,
    fontPreset: AppFontPreset = AppFontPreset.EDITORIAL,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> colorSchemeFor(mattePalette)
        else -> LightColorScheme
    }

    val typography = remember(fontPreset) { typographyFor(fontPreset) }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = typography,
        content = content
    )
}
