package com.example.tts

import org.json.JSONObject

/** One paragraph of a chapter, with the speaker tag removed and the speaker (if any) kept apart. */
data class ParsedParagraph(
    /** The text to show and speak, without the tag. */
    val text: String,
    /** Normalised speaker key ("zeus"), or null for narration. */
    val speaker: String?,
    /** The speaker's name as written in the tag ("Zeus"), or null for narration. */
    val speakerDisplay: String?
)

/**
 * Speaker tags: a paragraph that starts with `@Name:` is spoken by that character.
 * Examples: `@Zeus: "Hello."`   `@Lin Feng (thought): This is bad.`
 * Paragraphs with no tag (or tagged `@Narrator:`) are narration.
 */
object SpeakerTags {

    // @ + name (letters, digits, spaces, dots, apostrophes, hyphens) + optional "(thought)" + colon
    private val TAG = Regex("""^@([\p{L}\p{N}][\p{L}\p{N} .'’\-]{0,39}?)(?:\s*\([^)\n]{1,20}\))?\s*[:：]\s*""")

    private val NARRATION_NAMES = setOf("narrator", "narration", "unknown")

    fun normalizeKey(name: String): String = name.trim().replace(Regex("\\s+"), " ").lowercase()

    /** Splits the tag off one paragraph. Returns null when nothing is left to say. */
    fun parse(paragraph: String): ParsedParagraph? {
        val trimmed = paragraph.trim()
        if (trimmed.isEmpty()) return null
        val match = TAG.find(trimmed) ?: return ParsedParagraph(trimmed, null, null)
        val rest = trimmed.substring(match.range.last + 1).trim()
        if (rest.isEmpty()) return null
        val display = match.groupValues[1].trim().replace(Regex("\\s+"), " ")
        val key = normalizeKey(display)
        return if (key in NARRATION_NAMES) {
            ParsedParagraph(rest, null, null)
        } else {
            ParsedParagraph(rest, key, display)
        }
    }

    /**
     * A block of text with line breaks but no blank lines (common in pasted translations) can hold
     * several tagged lines. When any line carries a tag, every line becomes its own paragraph so
     * each speaker gets only their own words.
     */
    fun splitBlock(block: String): List<String> {
        if (!block.contains('@') || !block.contains('\n')) return listOf(block)
        val lines = block.lines().map { it.trim() }.filter { it.isNotEmpty() }
        return if (lines.size > 1 && lines.any { TAG.containsMatchIn(it) }) lines else listOf(block)
    }

    /** Removes speaker tags from a whole text, leaving everything else as it was (used for exports). */
    fun strip(text: String): String {
        if (!text.contains('@')) return text
        return text.lines().joinToString("\n") { line ->
            val t = line.trimStart()
            val m = TAG.find(t)
            if (m == null) line else t.substring(m.range.last + 1)
        }
    }

    /** All character names used in a text, first spelling kept, keyed by normalised name. */
    fun findNames(text: String, into: MutableMap<String, String>) {
        if (!text.contains('@')) return
        for (line in text.lineSequence()) {
            val t = line.trimStart()
            if (!t.startsWith("@")) continue
            val m = TAG.find(t) ?: continue
            val display = m.groupValues[1].trim().replace(Regex("\\s+"), " ")
            val key = normalizeKey(display)
            if (key !in NARRATION_NAMES && key !in into) into[key] = display
        }
    }
}

/** Which voice each character of one novel uses. Voice names are the phone engine's voice names. */
data class VoiceCast(
    /** Voice for narration; blank = the normal voice from Settings. */
    val narrator: String = "",
    /** Character key -> voice name (blank = same as narrator). */
    val voices: Map<String, String> = emptyMap(),
    /** Character key -> name as written, for display. */
    val names: Map<String, String> = emptyMap()
) {
    fun toJson(): String {
        val o = JSONObject()
        o.put("narrator", narrator)
        o.put("voices", JSONObject(voices))
        o.put("names", JSONObject(names))
        return o.toString()
    }

    companion object {
        fun fromJson(json: String?): VoiceCast {
            if (json.isNullOrBlank()) return VoiceCast()
            return try {
                val o = JSONObject(json)
                fun map(key: String): Map<String, String> {
                    val m = o.optJSONObject(key) ?: return emptyMap()
                    val out = LinkedHashMap<String, String>()
                    for (k in m.keys()) out[k] = m.optString(k, "")
                    return out
                }
                VoiceCast(o.optString("narrator", ""), map("voices"), map("names"))
            } catch (e: Exception) {
                VoiceCast()
            }
        }
    }
}

/** Saves the voice cast of each novel on the phone. */
class VoiceCastStore(context: android.content.Context) {
    private val prefs = context.applicationContext.getSharedPreferences("voice_cast", android.content.Context.MODE_PRIVATE)

    fun load(novelId: String): VoiceCast = VoiceCast.fromJson(prefs.getString("cast_$novelId", null))

    fun save(novelId: String, cast: VoiceCast) {
        prefs.edit().putString("cast_$novelId", cast.toJson()).apply()
    }
}
