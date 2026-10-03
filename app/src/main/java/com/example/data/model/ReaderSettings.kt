package com.example.data.model

data class ReaderSettings(
    val theme: String = "light", // "light", "sepia", "dark", "night"
    val fontSize: Int = 18,
    val lineHeight: Float = 1.75f,
    val fontFamily: String = "serif", // "serif" or "sans"
    val maxWidth: Int = 720,
    val ttsRate: Float = 1.0f,
    val ttsPitch: Float = 1.0f,
    val ttsVoice: String = "",
    val ttsEngine: String = "",
    val continueReading: Boolean = true,
    val replacementsEnabled: Boolean = true
)
