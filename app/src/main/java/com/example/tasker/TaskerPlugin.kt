package com.example.tasker

import android.os.Bundle

/**
 * Constants and helpers for the Tasker / Locale "Action" plugin.
 *
 * Tasker shows MTL Vault in its Action > Plugin list (found through [ACTION_EDIT_SETTING]), opens
 * [TaskerEditActivity] so you can pick a novel, then later runs the action by sending
 * [ACTION_FIRE_SETTING] to [TaskerFireReceiver].
 */
object TaskerPlugin {
    const val ACTION_EDIT_SETTING = "com.twofortyfouram.locale.intent.action.EDIT_SETTING"
    const val ACTION_FIRE_SETTING = "com.twofortyfouram.locale.intent.action.FIRE_SETTING"
    const val EXTRA_BUNDLE = "com.twofortyfouram.locale.intent.extra.BUNDLE"
    const val EXTRA_STRING_BLURB = "com.twofortyfouram.locale.intent.extra.BLURB"

    /** Tells Tasker which bundle keys contain %variables it should replace before firing. */
    const val EXTRA_VARIABLE_REPLACE_KEYS = "net.dinglisch.android.tasker.extras.VARIABLE_REPLACE_KEYS"

    const val KEY_NOVEL_ID = "novel_id"
    const val KEY_NOVEL_TITLE = "novel_title"
    const val KEY_TEXT = "text"

    /** What the "Chapter text" box is filled with by default; the Tasker task sets this variable. */
    const val DEFAULT_TEXT_VARIABLE = "%nb_text"

    fun buildBundle(novelId: String, novelTitle: String, text: String): Bundle = Bundle().apply {
        putString(KEY_NOVEL_ID, novelId)
        putString(KEY_NOVEL_TITLE, novelTitle)
        putString(KEY_TEXT, text)
        putString(EXTRA_VARIABLE_REPLACE_KEYS, KEY_TEXT)
    }

    /** Short summary Tasker shows under the action in the task list. */
    fun blurb(novelTitle: String): String = "Save next chapter to: $novelTitle".take(60)

    /** True when Tasker did not replace the variable (it was never set), e.g. "%nb_text". */
    fun looksUnresolved(text: String): Boolean = UNRESOLVED_REGEX.matches(text.trim())

    /**
     * If the first non-blank line is a heading such as "Chapter 2527: Audacity" (optionally wrapped
     * in markdown # or **), it is removed, because the chapter title is numbered by the app itself.
     * Text without such a heading is returned unchanged.
     */
    fun stripHeading(text: String): String {
        val lines = text.lines()
        val firstIdx = lines.indexOfFirst { it.isNotBlank() }
        if (firstIdx < 0) return text

        val cleaned = lines[firstIdx].trim().trimStart('#', '*', ' ').trimEnd('*', ' ')
        if (cleaned.length > 150 || !HEADING_REGEX.matches(cleaned)) return text

        val body = lines.drop(firstIdx + 1).joinToString("\n").trim()
        return if (body.isBlank()) text else body
    }

    private val UNRESOLVED_REGEX = Regex("""^%[A-Za-z0-9_]+$""")
    private val HEADING_REGEX = Regex(
        """(?i)^(?:chapter|ch\.?|chapitre|capitulo|capítulo)\s*\d+.*$"""
    )
}
