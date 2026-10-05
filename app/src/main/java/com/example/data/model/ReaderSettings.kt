package com.example.data.model

enum class ParagraphSplitMode(val maxChars: Int, val label: String) {
    OFF(Int.MAX_VALUE, "Off"),
    SHORT(250, "Short"),
    MEDIUM(450, "Medium"),
    LONG(700, "Long");

    companion object {
        fun fromString(value: String?): ParagraphSplitMode {
            return entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: MEDIUM
        }
    }
}

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
    val replacementsEnabled: Boolean = true,
    val detailedLogging: Boolean = false,
    val splitLongParagraphs: String = "medium", // "off", "short", "medium", "long"
    val openAtFirstUnread: Boolean = true
)
