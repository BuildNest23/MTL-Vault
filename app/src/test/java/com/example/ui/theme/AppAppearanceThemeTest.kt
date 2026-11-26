package com.example.ui.theme

import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontFamily
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppAppearanceThemeTest {

    @Test
    fun defaultsUseMatteCharcoalAndEditorialFont() {
        val defaults = AppAppearance()
        assertEquals(AppThemeMode.SYSTEM, defaults.themeMode)
        assertEquals(MattePalette.CHARCOAL, defaults.mattePalette)
        assertEquals(AppFontPreset.EDITORIAL, defaults.fontPreset)
        assertEquals(false, defaults.dynamicColor)
    }

    @Test
    fun matteCharcoalIsLiftedAbovePureBlackAndDistinctFromSurface() {
        val charcoal = colorSchemeFor(MattePalette.CHARCOAL)
        assertTrue(
            "Matte charcoal background should be noticeably lighter than near-black",
            charcoal.background.luminance() > 0.015f
        )
        assertTrue(
            "Matte charcoal card surface should be elevated above background",
            charcoal.surface.luminance() > charcoal.background.luminance()
        )
        assertNotEquals(charcoal.background, charcoal.surface)
    }

    @Test
    fun allMattePalettesHaveUniqueBackgroundsAndRoundTripKeys() {
        val backgrounds = MattePalette.entries.map { colorSchemeFor(it).background }.toSet()
        assertEquals(MattePalette.entries.size, backgrounds.size)

        MattePalette.entries.forEach { palette ->
            assertEquals(palette, MattePalette.fromKey(palette.key))
        }
        assertEquals(MattePalette.CHARCOAL, MattePalette.fromKey("unknown_key"))
        assertEquals(MattePalette.CHARCOAL, MattePalette.fromKey(null))
    }

    @Test
    fun appThemeModeRoundTripsAndFallsBackToSystem() {
        AppThemeMode.entries.forEach { mode ->
            assertEquals(mode, AppThemeMode.fromKey(mode.key))
        }
        assertEquals(AppThemeMode.SYSTEM, AppThemeMode.fromKey("invalid"))
        assertEquals(AppThemeMode.SYSTEM, AppThemeMode.fromKey(null))
    }

    @Test
    fun appFontPresetsBuildDistinctTypographyAndRoundTripKeys() {
        AppFontPreset.entries.forEach { preset ->
            assertEquals(preset, AppFontPreset.fromKey(preset.key))
            val typo = typographyFor(preset)
            assertEquals(preset.displayFamily, typo.titleLarge.fontFamily)
            assertEquals(preset.subheadFamily, typo.titleMedium.fontFamily)
            assertEquals(preset.bodyFamily, typo.bodyLarge.fontFamily)
        }
        assertEquals(AppFontPreset.EDITORIAL, AppFontPreset.fromKey("missing"))
        assertEquals(FontFamily.Default, typographyFor(AppFontPreset.SYSTEM).bodyLarge.fontFamily)
    }
}
