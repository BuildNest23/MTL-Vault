package com.example.tts

data class ParagraphChunk(
    val text: String,
    val originalParagraphIndex: Int,
    val isFirstChunkOfParagraph: Boolean,
    val isLastChunkOfParagraph: Boolean
)

object ParagraphSplitter {

    const val HARD_CAP = 3500

    private val ABBREVIATIONS = setOf(
        "mr", "mrs", "ms", "dr", "prof", "st", "vs", "etc", "eg", "ie",
        "jr", "sr", "no", "vol", "ch", "gen", "col", "lt", "capt", "dept", "approx"
    )

    /**
     * Splits chapter content into paragraph chunks honoring maxChars and HARD_CAP (3500 chars).
     */
    fun parseParagraphChunks(content: String, maxChars: Int = Int.MAX_VALUE): List<ParagraphChunk> {
        if (content.isBlank()) return emptyList()

        val rawParagraphs = content.split(Regex("(\r?\n){2,}"))
            .map { it.trim() }
            .filter { it.isNotBlank() }

        val allChunks = mutableListOf<ParagraphChunk>()

        for ((originalIndex, rawParagraph) in rawParagraphs.withIndex()) {
            val splitTexts = splitSingleParagraph(rawParagraph, maxChars)
            val count = splitTexts.size
            for ((subIndex, text) in splitTexts.withIndex()) {
                allChunks.add(
                    ParagraphChunk(
                        text = text,
                        originalParagraphIndex = originalIndex,
                        isFirstChunkOfParagraph = subIndex == 0,
                        isLastChunkOfParagraph = subIndex == count - 1
                    )
                )
            }
        }
        return allChunks
    }

    /**
     * Splits a single paragraph into chunks respecting sentence boundaries, maxChars, and HARD_CAP.
     */
    fun splitSingleParagraph(paragraph: String, maxChars: Int): List<String> {
        val trimmed = paragraph.trim()
        if (trimmed.isEmpty()) return emptyList()

        val effectiveLimit = if (maxChars <= 0) HARD_CAP else maxChars

        // If paragraph length <= effectiveLimit, only check hard cap
        if (trimmed.length <= effectiveLimit && trimmed.length <= HARD_CAP) {
            return listOf(trimmed)
        }

        // Split paragraph into sentences
        val sentences = splitIntoSentences(trimmed)
        if (sentences.isEmpty()) {
            return enforceHardCap(trimmed)
        }

        // If paragraph has only one sentence
        if (sentences.size == 1) {
            // Keep whole if <= HARD_CAP; otherwise enforce hard cap
            return enforceHardCap(sentences[0])
        }

        // Group sentences greedily up to effectiveLimit
        val rawChunks = mutableListOf<String>()
        var currentChunk = StringBuilder()

        for (sentence in sentences) {
            if (currentChunk.isEmpty()) {
                currentChunk.append(sentence)
            } else {
                val candidateLength = currentChunk.length + 1 + sentence.length
                if (candidateLength <= effectiveLimit) {
                    currentChunk.append(" ").append(sentence)
                } else {
                    rawChunks.add(currentChunk.toString().trim())
                    currentChunk = StringBuilder(sentence)
                }
            }
        }
        if (currentChunk.isNotEmpty()) {
            rawChunks.add(currentChunk.toString().trim())
        }

        // Enforce hard cap (3500 chars) on each chunk
        return rawChunks.flatMap { enforceHardCap(it) }
    }

    /**
     * Splits text into individual sentences respecting:
     * - Sentence terminators (. ! ? … \u2026)
     * - Closing quotes / brackets immediately following terminators
     * - Excluding abbreviations (Mr., Mrs., Dr., etc.) and single-letter initials (A., B.)
     * - Excluding numbers/decimals (e.g. 3.14)
     * - Preserving patterns like A-14
     */
    fun splitIntoSentences(text: String): List<String> {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return emptyList()

        val sentences = mutableListOf<String>()
        var start = 0
        val len = trimmed.length
        var i = 0

        while (i < len) {
            val c = trimmed[i]
            val isTerminator = c == '.' || c == '!' || c == '?' || c == '…' || c == '\u2026'

            if (isTerminator) {
                // If it's an ellipsis like "...", advance to the last dot/ellipsis
                var termEnd = i
                while (termEnd + 1 < len && (trimmed[termEnd + 1] == '.' || trimmed[termEnd + 1] == '…' || trimmed[termEnd + 1] == '\u2026')) {
                    termEnd++
                }

                // Check for closing quotation marks or brackets immediately after terminator
                var quoteEnd = termEnd
                while (quoteEnd + 1 < len && isClosingPunctuation(trimmed[quoteEnd + 1])) {
                    quoteEnd++
                }

                // Sentence break must be followed by whitespace or end of string
                val isAtBoundary = (quoteEnd + 1 >= len) || trimmed[quoteEnd + 1].isWhitespace()

                if (isAtBoundary) {
                    var isFalseAlarm = false

                    // If terminator was a single dot (not ellipsis)
                    if (c == '.' && termEnd == i) {
                        // Check if decimal: digit before dot AND digit after dot
                        if (i > 0 && trimmed[i - 1].isDigit() && i + 1 < len && trimmed[i + 1].isDigit()) {
                            isFalseAlarm = true
                        }

                        // Check abbreviation before dot
                        if (!isFalseAlarm && i > 0) {
                            var wordStart = i - 1
                            while (wordStart >= start && trimmed[wordStart].isLetter()) {
                                wordStart--
                            }
                            val word = trimmed.substring(wordStart + 1, i)
                            if (word.length == 1 && word[0].isLetter()) {
                                // Single initial like "A."
                                isFalseAlarm = true
                            } else if (ABBREVIATIONS.contains(word.lowercase())) {
                                isFalseAlarm = true
                            }
                        }
                    }

                    // Check if ending with a quote followed by a lowercase dialogue tag (e.g. "Is that true?" asked Lin Ming)
                    if (!isFalseAlarm && quoteEnd > termEnd) {
                        var nextNonWs = quoteEnd + 1
                        while (nextNonWs < len && trimmed[nextNonWs].isWhitespace()) {
                            nextNonWs++
                        }
                        if (nextNonWs < len && trimmed[nextNonWs].isLowerCase()) {
                            isFalseAlarm = true
                        }
                    }

                    if (!isFalseAlarm) {
                        val sentence = trimmed.substring(start, quoteEnd + 1).trim()
                        if (sentence.isNotEmpty()) {
                            sentences.add(sentence)
                        }
                        var nextStart = quoteEnd + 1
                        while (nextStart < len && trimmed[nextStart].isWhitespace()) {
                            nextStart++
                        }
                        start = nextStart
                        i = nextStart
                        continue
                    }
                }
                i = quoteEnd + 1
            } else {
                i++
            }
        }

        if (start < len) {
            val remaining = trimmed.substring(start).trim()
            if (remaining.isNotEmpty()) {
                sentences.add(remaining)
            }
        }

        return if (sentences.isEmpty() && trimmed.isNotEmpty()) listOf(trimmed) else sentences
    }

    private fun isClosingPunctuation(c: Char): Boolean {
        return c == '"' || c == '”' || c == '\'' || c == '’' || c == '»' || c == ')' || c == ']' || c == '}'
    }

    /**
     * Enforces the hard cap of 3500 characters on a chunk.
     * Splits first at sentence boundaries; if a sentence still exceeds 3500 chars,
     * splits at the last space before 3500 chars.
     */
    fun enforceHardCap(text: String, hardCap: Int = HARD_CAP): List<String> {
        if (text.length <= hardCap) return listOf(text)

        val sentences = splitIntoSentences(text)
        if (sentences.size > 1) {
            val result = mutableListOf<String>()
            var cur = StringBuilder()
            for (s in sentences) {
                if (cur.isEmpty()) {
                    cur.append(s)
                } else if (cur.length + 1 + s.length <= hardCap) {
                    cur.append(" ").append(s)
                } else {
                    result.add(cur.toString().trim())
                    cur = StringBuilder(s)
                }
            }
            if (cur.isNotEmpty()) {
                result.add(cur.toString().trim())
            }
            return result.flatMap { enforceHardCapBySpace(it, hardCap) }
        } else {
            return enforceHardCapBySpace(text, hardCap)
        }
    }

    private fun enforceHardCapBySpace(text: String, hardCap: Int): List<String> {
        if (text.length <= hardCap) return listOf(text)
        val result = mutableListOf<String>()
        var remaining = text.trim()

        while (remaining.length > hardCap) {
            val lastSpace = remaining.lastIndexOf(' ', hardCap)
            val splitIdx = if (lastSpace > 0) lastSpace else hardCap
            val part = remaining.substring(0, splitIdx).trim()
            if (part.isNotEmpty()) {
                result.add(part)
            }
            remaining = remaining.substring(splitIdx).trim()
        }
        if (remaining.isNotEmpty()) {
            result.add(remaining)
        }
        return result
    }
}
