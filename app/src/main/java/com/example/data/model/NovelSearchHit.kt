package com.example.data.model

import com.example.tts.TtsManager

data class NovelSearchHit(
    val novelId: String,
    val novelTitle: String,
    val chapterId: String,
    val chapterTitle: String,
    val chapterOrder: Int,
    val paragraphIndex: Int,
    val snippet: String,
    val matchStart: Int,
    val matchEnd: Int
)

object NovelSearchEngine {

    /**
     * Escapes SQL LIKE wildcards (%, _, \) so literal user queries match safely.
     */
    fun toLikePattern(query: String): String {
        val escaped = query
            .replace("\\", "\\\\")
            .replace("%", "\\%")
            .replace("_", "\\_")
        return "%$escaped%"
    }

    /**
     * Extracts contextual snippet hits from a rendered chapter using the exact same paragraph
     * parser as the Reader so tapping a hit scrolls directly to [paragraphIndex].
     */
    fun searchChapterText(
        novelId: String,
        novelTitle: String,
        chapterId: String,
        chapterTitle: String,
        chapterOrder: Int,
        renderedContent: String,
        query: String,
        maxHitsPerChapter: Int = 5
    ): List<NovelSearchHit> {
        val needle = query.trim()
        if (needle.isEmpty()) return emptyList()

        val hits = mutableListOf<NovelSearchHit>()
        val paragraphs = TtsManager.parseParagraphs(renderedContent)

        for (paraIdx in paragraphs.indices) {
            if (hits.size >= maxHitsPerChapter) break
            val paragraph = paragraphs[paraIdx]
            val matchIdx = paragraph.indexOf(needle, ignoreCase = true)
            if (matchIdx >= 0) {
                val (snippet, start, end) = buildSnippet(paragraph, matchIdx, needle.length)
                hits.add(
                    NovelSearchHit(
                        novelId = novelId,
                        novelTitle = novelTitle,
                        chapterId = chapterId,
                        chapterTitle = chapterTitle.ifBlank { "Chapter ${chapterOrder + 1}" },
                        chapterOrder = chapterOrder,
                        paragraphIndex = paraIdx,
                        snippet = snippet,
                        matchStart = start,
                        matchEnd = end
                    )
                )
            }
        }

        // If no paragraph matched (for example, the query matched the chapter title itself), add a title hit.
        if (hits.isEmpty() && chapterTitle.contains(needle, ignoreCase = true)) {
            val matchIdx = chapterTitle.indexOf(needle, ignoreCase = true)
            hits.add(
                NovelSearchHit(
                    novelId = novelId,
                    novelTitle = novelTitle,
                    chapterId = chapterId,
                    chapterTitle = chapterTitle.ifBlank { "Chapter ${chapterOrder + 1}" },
                    chapterOrder = chapterOrder,
                    paragraphIndex = 0,
                    snippet = chapterTitle,
                    matchStart = matchIdx,
                    matchEnd = (matchIdx + needle.length).coerceAtMost(chapterTitle.length)
                )
            )
        }

        return hits
    }

    internal fun buildSnippet(
        text: String,
        matchIndex: Int,
        matchLength: Int,
        contextBefore: Int = 46,
        contextAfter: Int = 74
    ): Triple<String, Int, Int> {
        val rawStart = (matchIndex - contextBefore).coerceAtLeast(0)
        val rawEnd = (matchIndex + matchLength + contextAfter).coerceAtMost(text.length)

        // Snap slightly to word boundaries when trimming
        val sliceStart = if (rawStart > 0) {
            val spaceAfter = text.indexOf(' ', rawStart)
            if (spaceAfter in rawStart until matchIndex) spaceAfter + 1 else rawStart
        } else 0

        val sliceEnd = if (rawEnd < text.length) {
            val spaceBefore = text.lastIndexOf(' ', rawEnd)
            if (spaceBefore > matchIndex + matchLength) spaceBefore else rawEnd
        } else text.length

        val prefix = if (sliceStart > 0) "…" else ""
        val suffix = if (sliceEnd < text.length) "…" else ""
        val core = text.substring(sliceStart, sliceEnd).replace('\n', ' ')
        val snippet = "$prefix$core$suffix"
        val start = prefix.length + (matchIndex - sliceStart)
        val end = (start + matchLength).coerceAtMost(snippet.length)
        return Triple(snippet, start.coerceAtLeast(0), end.coerceAtLeast(0))
    }
}
