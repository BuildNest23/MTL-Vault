package com.example.ui.theme

import androidx.compose.ui.graphics.Color

// Primary — Deep Forest Jade (Editorial Reading Room)
val JadePrimary = Color(0xFF245940)
val JadeOnPrimary = Color(0xFFFFFFFF)
val JadePrimaryContainer = Color(0xFFDCEBE1)
val JadeOnPrimaryContainer = Color(0xFF0E3322)

val JadePrimaryDark = Color(0xFF82CCA0)
val JadeOnPrimaryDark = Color(0xFF08291A)
val JadePrimaryContainerDark = Color(0xFF1A3628)
val JadeOnPrimaryContainerDark = Color(0xFFC8EED6)

// Secondary — Burnished Ochre / Brass (Warm Editorial Accent)
val OchreSecondary = Color(0xFFB36B2C)
val OchreOnSecondary = Color(0xFFFFFFFF)
val OchreSecondaryContainer = Color(0xFFF7E6D4)
val OchreOnSecondaryContainer = Color(0xFF3E2006)

val OchreSecondaryDark = Color(0xFFE0A25E)
val OchreOnSecondaryDark = Color(0xFF3B1E04)
val OchreSecondaryContainerDark = Color(0xFF3A2817)
val OchreOnSecondaryContainerDark = Color(0xFFFADBB8)

// Tertiary — Muted Ink Slate
val InkTertiary = Color(0xFF4A5D73)
val InkOnTertiary = Color(0xFFFFFFFF)
val InkTertiaryContainer = Color(0xFFE1E8F0)
val InkOnTertiaryContainer = Color(0xFF172738)

val InkTertiaryDark = Color(0xFF9EB7D4)
val InkOnTertiaryDark = Color(0xFF132436)
val InkTertiaryContainerDark = Color(0xFF243447)
val InkOnTertiaryContainerDark = Color(0xFFD8E5F5)

// Warm Linen & Ivory Surfaces (Light)
val WarmBackground = Color(0xFFF7F3EB)
val WarmSurface = Color(0xFFFFFDF9)
val WarmSurfaceLow = Color(0xFFFBF7F0)
val WarmSurfaceVariant = Color(0xFFEFE9DC)
val WarmSurfaceHigh = Color(0xFFE8E0D0)
val WarmText = Color(0xFF1F1C17)
val WarmMuted = Color(0xFF6B6357)
val WarmBorder = Color(0xFFD5CCB9)
val WarmBorderSubtle = Color(0xFFE4DDD0)

// Candlelit Study / Espresso Obsidian Surfaces (Dark)
val DarkBackground = Color(0xFF141210)
val DarkSurface = Color(0xFF1E1B18)
val DarkSurfaceLow = Color(0xFF191614)
val DarkSurfaceVariant = Color(0xFF28231E)
val DarkSurfaceHigh = Color(0xFF322C26)
val DarkText = Color(0xFFEFE9DD)
val DarkMuted = Color(0xFFA79B85)
val DarkBorder = Color(0xFF443C33)
val DarkBorderSubtle = Color(0xFF342E28)

// Semantic Accents
val AccentRed = Color(0xFFB23A32)
val AccentRedContainer = Color(0xFFF8E4E1)
val AccentRedDark = Color(0xFFF28B82)
val AccentRedContainerDark = Color(0xFF4A1D19)

// Reader Paper Themes
val ReaderLightBg = Color(0xFFF7F3EB)
val ReaderLightText = Color(0xFF1F1C17)
val ReaderSepiaBg = Color(0xFFF3EAD5)
val ReaderSepiaText = Color(0xFF3A2D1A)
val ReaderDarkBg = Color(0xFF1A1815)
val ReaderDarkText = Color(0xFFEFE9DD)
val ReaderNightBg = Color(0xFF000000)
val ReaderNightText = Color(0xFFB8B8B8)

// Curated Editorial Bookplate Covers (Deep Cloth Binding Tones)
val EditorialBookplatePalettes = listOf(
    Pair(Color(0xFF244A39), Color(0xFF173125)), // Deep Pine Cloth
    Pair(Color(0xFF6E3B2B), Color(0xFF492418)), // Terracotta Leather
    Pair(Color(0xFF2E435A), Color(0xFF1C2B3B)), // Indigo Folio
    Pair(Color(0xFF5D3A4E), Color(0xFF3C2332)), // Plum Binding
    Pair(Color(0xFF6A542C), Color(0xFF46371B)), // Burnished Olive Brass
    Pair(Color(0xFF2B545A), Color(0xFF1A373C)), // Deep Slate Teal
    Pair(Color(0xFF5A4336), Color(0xFF3B2B22)), // Warm Umber Cloth
    Pair(Color(0xFF3D4F32), Color(0xFF26331F))  // Moss Linen
)

// Reader Highlight Swatches (key -> light/sepia tint, dark/night tint, swatch indicator)
data class HighlightSwatch(
    val key: String,
    val label: String,
    val indicator: Color,
    val lightWash: Color,
    val darkWash: Color
)

val EditorialHighlightSwatches = listOf(
    HighlightSwatch(
        key = "amber",
        label = "Amber",
        indicator = Color(0xFFD9943B),
        lightWash = Color(0x52E6A845),
        darkWash = Color(0x4DE0A25E)
    ),
    HighlightSwatch(
        key = "jade",
        label = "Sage",
        indicator = Color(0xFF4A936C),
        lightWash = Color(0x4768B88A),
        darkWash = Color(0x4782CCA0)
    ),
    HighlightSwatch(
        key = "rose",
        label = "Rose",
        indicator = Color(0xFFC96B6B),
        lightWash = Color(0x47DC7C7C),
        darkWash = Color(0x47E58A8A)
    ),
    HighlightSwatch(
        key = "slate",
        label = "Slate",
        indicator = Color(0xFF6482A3),
        lightWash = Color(0x477798BD),
        darkWash = Color(0x479EB7D4)
    )
)
