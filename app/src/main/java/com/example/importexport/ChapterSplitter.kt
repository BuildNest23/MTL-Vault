package com.example.importexport

import java.util.regex.Pattern

data class SplitChapterResult(
    val title: String,
    val content: String,
    val wordCount: Int
)

object ChapterSplitter {

    enum class SplitMode {
        AUTO_CHAPTER,
        CUSTOM_MARKER,
        SINGLE_CHAPTER
    }

    private val AUTO_CHAPTER_REGEX = Pattern.compile(
        // Heading words must stand alone ("Chapter 5", "Chapter One"); the short forms "ch"/"c."
        // need a number. This stops ordinary lines such as "Carefully he..." from matching.
        "^\\s*(?:(?:chapter|chapitre|capítulo|capitulo|अध्याय|باب)(?![\\p{L}\\p{M}\\p{N}]).*|(?:ch\\.?|c\\.)\\s*[0-9]+[a-zA-Z]?\\b.*|第.*)$",
        Pattern.CASE_INSENSITIVE or Pattern.UNICODE_CASE
    )

    fun splitText(
        text: String,
        mode: SplitMode,
        customMarker: String = "",
        fallbackTitlePrefix: String = "Chapter ",
        startingIndex: Int = 1
    ): List<SplitChapterResult> {
        val cleanText = text.trim()
        if (cleanText.isEmpty()) return emptyList()

        return when (mode) {
            SplitMode.SINGLE_CHAPTER -> {
                val wc = countWords(cleanText)
                listOf(
                    SplitChapterResult(
                        title = "$fallbackTitlePrefix$startingIndex",
                        content = cleanText,
                        wordCount = wc
                    )
                )
            }
            SplitMode.CUSTOM_MARKER -> {
                splitByMarker(cleanText, customMarker, fallbackTitlePrefix, startingIndex)
            }
            SplitMode.AUTO_CHAPTER -> {
                val autoResults = splitByRegex(cleanText, AUTO_CHAPTER_REGEX, fallbackTitlePrefix, startingIndex)
                if (autoResults.isEmpty()) {
                    // Fallback to single chapter if no matches
                    val wc = countWords(cleanText)
                    listOf(
                        SplitChapterResult(
                            title = "$fallbackTitlePrefix$startingIndex",
                            content = cleanText,
                            wordCount = wc
                        )
                    )
                } else {
                    autoResults
                }
            }
        }
    }

    private fun splitByRegex(
        text: String,
        pattern: Pattern,
        fallbackTitlePrefix: String,
        startingIndex: Int
    ): List<SplitChapterResult> {
        val lines = text.lines()
        val results = mutableListOf<SplitChapterResult>()
        var currentTitle = ""
        val currentLines = mutableListOf<String>()
        var foundAny = false

        for (line in lines) {
            val matcher = pattern.matcher(line)
            if (matcher.matches()) {
                if (currentLines.isNotEmpty() || currentTitle.isNotEmpty()) {
                    val content = currentLines.joinToString("\n").trim()
                    val title = if (currentTitle.isNotBlank()) {
                        currentTitle
                    } else {
                        "Prologue"
                    }
                    // Keep a real heading even if empty; drop an untitled, empty preface
                    if (content.isNotBlank() || currentTitle.isNotBlank()) {
                        results.add(SplitChapterResult(title, content, countWords(content)))
                    }
                    currentLines.clear()
                }
                currentTitle = line.trim()
                foundAny = true
            } else {
                currentLines.add(line)
            }
        }

        if (currentLines.isNotEmpty() || currentTitle.isNotEmpty()) {
            val content = currentLines.joinToString("\n").trim()
            val title = if (currentTitle.isNotBlank()) {
                currentTitle
            } else if (results.isEmpty()) {
                "Prologue"
            } else {
                "$fallbackTitlePrefix${results.size + startingIndex}"
            }
            if (content.isNotBlank() || currentTitle.isNotBlank()) {
                results.add(SplitChapterResult(title, content, countWords(content)))
            }
        }

        return if (foundAny) results else emptyList()
    }

    private fun splitByMarker(
        text: String,
        marker: String,
        fallbackTitlePrefix: String,
        startingIndex: Int
    ): List<SplitChapterResult> {
        if (marker.isBlank()) return emptyList()
        val markerPattern = Pattern.compile(
            "^\\s*" + Pattern.quote(marker.trim()) + ".*$",
            Pattern.CASE_INSENSITIVE or Pattern.UNICODE_CASE
        )
        return splitByRegex(text, markerPattern, fallbackTitlePrefix, startingIndex)
    }

    fun countWords(text: String): Int {
        if (text.isBlank()) return 0
        // Handle both Western whitespace words and CJK characters
        var count = 0
        var inWord = false
        for (i in 0 until text.length) {
            val c = text[i]
            if (Character.isWhitespace(c)) {
                inWord = false
            } else if (isCjk(c)) {
                count++
                inWord = false
            } else {
                if (!inWord) {
                    count++
                    inWord = true
                }
            }
        }
        return count
    }

    private fun isCjk(c: Char): Boolean {
        val block = Character.UnicodeBlock.of(c)
        return block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS ||
                block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A ||
                block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_B ||
                block == Character.UnicodeBlock.HIRAGANA ||
                block == Character.UnicodeBlock.KATAKANA ||
                block == Character.UnicodeBlock.HANGUL_SYLLABLES
    }
}
