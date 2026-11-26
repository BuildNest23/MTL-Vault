package com.example.ui.screens.reader

import androidx.compose.ui.graphics.Color
import com.example.data.database.entities.NovelAnnotationEntity
import com.example.ui.theme.EditorialHighlightSwatches

data class LocatedSelection(
    val paragraphIndex: Int,
    val startOffset: Int,
    val endOffset: Int,
    val quoteText: String
)

/**
 * Maps a selected text snippet back to the paragraph and character range where it appeared,
 * preferring paragraphs currently visible on screen so repeated phrases match the right spot.
 */
fun locateSelectionInParagraphs(
    paragraphs: List<String>,
    visibleParagraphIndices: List<Int>,
    selectedText: String
): LocatedSelection? {
    val trimmed = selectedText.trim()
    if (trimmed.isEmpty() || paragraphs.isEmpty()) return null

    // If selection spans multiple paragraphs, anchor to the first non-empty line in its paragraph.
    val primarySnippet = trimmed
        .lineSequence()
        .map { it.trim() }
        .firstOrNull { it.isNotEmpty() }
        ?: trimmed

    val validVisible = visibleParagraphIndices.filter { it in paragraphs.indices }.distinct()

    fun findIn(indices: Iterable<Int>, ignoreCase: Boolean): LocatedSelection? {
        for (idx in indices) {
            val para = paragraphs[idx]
            val pos = para.indexOf(primarySnippet, ignoreCase = ignoreCase)
            if (pos >= 0) {
                return LocatedSelection(
                    paragraphIndex = idx,
                    startOffset = pos,
                    endOffset = (pos + primarySnippet.length).coerceAtMost(para.length),
                    quoteText = primarySnippet
                )
            }
        }
        return null
    }

    findIn(validVisible, ignoreCase = false)?.let { return it }
    findIn(validVisible, ignoreCase = true)?.let { return it }
    findIn(paragraphs.indices, ignoreCase = false)?.let { return it }
    findIn(paragraphs.indices, ignoreCase = true)?.let { return it }

    val fallbackIdx = validVisible.firstOrNull() ?: 0
    return LocatedSelection(
        paragraphIndex = fallbackIdx,
        startOffset = 0,
        endOffset = paragraphs[fallbackIdx].length,
        quoteText = primarySnippet.take(300)
    )
}

/**
 * Resolves a saved highlight's character range inside [paragraphText].
 * Resilient to replacement rule changes: verifies saved offsets first, falls back to quote
 * search if offsets drifted, and highlights the whole paragraph if the text was rewritten.
 */
fun resolveHighlightRange(
    paragraphText: String,
    annotation: NovelAnnotationEntity
): IntRange? {
    if (paragraphText.isEmpty()) return null

    val start = annotation.startOffset
    val end = annotation.endOffset
    if (start in 0 until paragraphText.length && end in (start + 1)..paragraphText.length) {
        val currentSlice = paragraphText.substring(start, end)
        if (annotation.quoteText.isBlank() || currentSlice.equals(annotation.quoteText, ignoreCase = true)) {
            return start until end
        }
    }

    if (annotation.quoteText.isNotBlank()) {
        val exactIdx = paragraphText.indexOf(annotation.quoteText, ignoreCase = false)
        if (exactIdx >= 0) {
            return exactIdx until (exactIdx + annotation.quoteText.length).coerceAtMost(paragraphText.length)
        }
        val ciIdx = paragraphText.indexOf(annotation.quoteText, ignoreCase = true)
        if (ciIdx >= 0) {
            return ciIdx until (ciIdx + annotation.quoteText.length).coerceAtMost(paragraphText.length)
        }
    }

    return 0 until paragraphText.length
}

fun highlightWashColor(colorKey: String, readerTheme: String): Color {
    val swatch = EditorialHighlightSwatches.find { it.key == colorKey }
        ?: EditorialHighlightSwatches.first()
    val isDark = readerTheme == "dark" || readerTheme == "night"
    return if (isDark) swatch.darkWash else swatch.lightWash
}

fun highlightIndicatorColor(colorKey: String): Color {
    val swatch = EditorialHighlightSwatches.find { it.key == colorKey }
        ?: EditorialHighlightSwatches.first()
    return swatch.indicator
}
