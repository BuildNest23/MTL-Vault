package com.example.tts.edge

/** A neural voice from the Edge Read Aloud service. */
data class EdgeVoice(val shortName: String, val label: String)

object EdgeVoices {
    const val DEFAULT = "en-US-AvaMultilingualNeural"

    /** Curated list. Any other voice name from the Edge catalog also works if you add it here. */
    val all: List<EdgeVoice> = listOf(
        EdgeVoice("en-US-AvaMultilingualNeural", "Ava (US, female)"),
        EdgeVoice("en-US-AndrewMultilingualNeural", "Andrew (US, male)"),
        EdgeVoice("en-US-EmmaMultilingualNeural", "Emma (US, female)"),
        EdgeVoice("en-US-BrianMultilingualNeural", "Brian (US, male)"),
        EdgeVoice("en-US-AriaNeural", "Aria (US, female)"),
        EdgeVoice("en-US-JennyNeural", "Jenny (US, female)"),
        EdgeVoice("en-US-GuyNeural", "Guy (US, male)"),
        EdgeVoice("en-GB-SoniaNeural", "Sonia (UK, female)"),
        EdgeVoice("en-GB-LibbyNeural", "Libby (UK, female)"),
        EdgeVoice("en-GB-RyanNeural", "Ryan (UK, male)"),
        EdgeVoice("en-AU-NatashaNeural", "Natasha (Australia, female)"),
        EdgeVoice("en-AU-WilliamNeural", "William (Australia, male)")
    )

    fun labelFor(shortName: String): String =
        all.firstOrNull { it.shortName == shortName }?.label ?: shortName.ifBlank { DEFAULT }
}
