package com.example.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.R

val PlayfairDisplayFontFamily = FontFamily(
    Font(R.font.playfair_display, FontWeight.Normal),
    Font(R.font.playfair_display, FontWeight.Medium),
    Font(R.font.playfair_display, FontWeight.SemiBold),
    Font(R.font.playfair_display, FontWeight.Bold)
)

val LoraFontFamily = FontFamily(
    Font(R.font.lora, FontWeight.Normal),
    Font(R.font.lora, FontWeight.Medium),
    Font(R.font.lora, FontWeight.SemiBold),
    Font(R.font.lora, FontWeight.Bold)
)

val PlusJakartaSansFontFamily = FontFamily(
    Font(R.font.plus_jakarta_sans, FontWeight.Normal),
    Font(R.font.plus_jakarta_sans, FontWeight.Medium),
    Font(R.font.plus_jakarta_sans, FontWeight.SemiBold),
    Font(R.font.plus_jakarta_sans, FontWeight.Bold)
)

val InterFontFamily = FontFamily(
    Font(R.font.inter, FontWeight.Normal),
    Font(R.font.inter, FontWeight.Medium),
    Font(R.font.inter, FontWeight.SemiBold),
    Font(R.font.inter, FontWeight.Bold)
)

val OutfitFontFamily = FontFamily(
    Font(R.font.outfit, FontWeight.Normal),
    Font(R.font.outfit, FontWeight.Medium),
    Font(R.font.outfit, FontWeight.SemiBold),
    Font(R.font.outfit, FontWeight.Bold)
)

/**
 * User-selectable overall UI typography presets.
 * Applies across app chrome (tabs, cards, top bars, stats, settings, dialogs) while keeping
 * chapter reader typography independent.
 */
enum class AppFontPreset(
    val key: String,
    val label: String,
    val description: String
) {
    EDITORIAL(
        key = "editorial",
        label = "Editorial",
        description = "Playfair & Jakarta"
    ),
    MODERN_SANS(
        key = "modern_sans",
        label = "Modern Sans",
        description = "Inter"
    ),
    BOOKISH_SERIF(
        key = "bookish_serif",
        label = "Bookish Serif",
        description = "Lora"
    ),
    GEOMETRIC(
        key = "geometric",
        label = "Geometric",
        description = "Outfit"
    ),
    SYSTEM(
        key = "system",
        label = "System",
        description = "Device default"
    );

    val displayFamily: FontFamily
        get() = when (this) {
            EDITORIAL -> PlayfairDisplayFontFamily
            MODERN_SANS -> InterFontFamily
            BOOKISH_SERIF -> LoraFontFamily
            GEOMETRIC -> OutfitFontFamily
            SYSTEM -> FontFamily.Default
        }

    val subheadFamily: FontFamily
        get() = when (this) {
            EDITORIAL -> LoraFontFamily
            MODERN_SANS -> InterFontFamily
            BOOKISH_SERIF -> LoraFontFamily
            GEOMETRIC -> OutfitFontFamily
            SYSTEM -> FontFamily.Default
        }

    val bodyFamily: FontFamily
        get() = when (this) {
            EDITORIAL -> PlusJakartaSansFontFamily
            MODERN_SANS -> InterFontFamily
            BOOKISH_SERIF -> LoraFontFamily
            GEOMETRIC -> OutfitFontFamily
            SYSTEM -> FontFamily.Default
        }

    companion object {
        fun fromKey(key: String?): AppFontPreset =
            entries.firstOrNull { it.key == key } ?: EDITORIAL
    }
}

fun typographyFor(preset: AppFontPreset): Typography {
    val display = preset.displayFamily
    val subhead = preset.subheadFamily
    val body = preset.bodyFamily
    return Typography(
        displayLarge = TextStyle(
            fontFamily = display,
            fontWeight = FontWeight.Bold,
            fontSize = 36.sp,
            lineHeight = 44.sp,
            letterSpacing = (-0.5).sp
        ),
        displayMedium = TextStyle(
            fontFamily = display,
            fontWeight = FontWeight.Bold,
            fontSize = 30.sp,
            lineHeight = 38.sp,
            letterSpacing = (-0.25).sp
        ),
        displaySmall = TextStyle(
            fontFamily = display,
            fontWeight = FontWeight.SemiBold,
            fontSize = 26.sp,
            lineHeight = 34.sp,
            letterSpacing = 0.sp
        ),
        headlineLarge = TextStyle(
            fontFamily = display,
            fontWeight = FontWeight.Bold,
            fontSize = 24.sp,
            lineHeight = 32.sp,
            letterSpacing = (-0.2).sp
        ),
        headlineMedium = TextStyle(
            fontFamily = display,
            fontWeight = FontWeight.SemiBold,
            fontSize = 21.sp,
            lineHeight = 28.sp,
            letterSpacing = (-0.1).sp
        ),
        headlineSmall = TextStyle(
            fontFamily = subhead,
            fontWeight = FontWeight.SemiBold,
            fontSize = 19.sp,
            lineHeight = 26.sp,
            letterSpacing = 0.sp
        ),
        titleLarge = TextStyle(
            fontFamily = display,
            fontWeight = FontWeight.Bold,
            fontSize = 20.sp,
            lineHeight = 26.sp,
            letterSpacing = 0.sp
        ),
        titleMedium = TextStyle(
            fontFamily = subhead,
            fontWeight = FontWeight.SemiBold,
            fontSize = 16.sp,
            lineHeight = 22.sp,
            letterSpacing = 0.1.sp
        ),
        titleSmall = TextStyle(
            fontFamily = body,
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            letterSpacing = 0.1.sp
        ),
        bodyLarge = TextStyle(
            fontFamily = body,
            fontWeight = FontWeight.Normal,
            fontSize = 15.sp,
            lineHeight = 23.sp,
            letterSpacing = 0.15.sp
        ),
        bodyMedium = TextStyle(
            fontFamily = body,
            fontWeight = FontWeight.Normal,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            letterSpacing = 0.15.sp
        ),
        bodySmall = TextStyle(
            fontFamily = body,
            fontWeight = FontWeight.Normal,
            fontSize = 12.sp,
            lineHeight = 17.sp,
            letterSpacing = 0.2.sp
        ),
        labelLarge = TextStyle(
            fontFamily = body,
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            letterSpacing = 0.1.sp
        ),
        labelMedium = TextStyle(
            fontFamily = body,
            fontWeight = FontWeight.Medium,
            fontSize = 12.sp,
            lineHeight = 16.sp,
            letterSpacing = 0.4.sp
        ),
        labelSmall = TextStyle(
            fontFamily = body,
            fontWeight = FontWeight.SemiBold,
            fontSize = 11.sp,
            lineHeight = 14.sp,
            letterSpacing = 0.6.sp
        )
    )
}

val Typography: Typography = typographyFor(AppFontPreset.EDITORIAL)
