package com.example.tts

data class SentenceSpan(
    val text: String,
    val start: Int,
    val end: Int
)

object SentenceSplitter {

    private val ABBREVIATIONS = hashSetOf(
        "mr", "mrs", "ms", "dr", "prof", "sr", "jr", "st", "vs", "etc",
        "e.g", "i.e", "vol", "no", "ch", "approx", "dept", "est", "min", "sec",
        "gen", "col", "lt", "sgt", "capt", "gov", "sen", "rep", "rev", "hon",
        "ave", "rd", "blvd"
    )

    private fun isClosingPunctuation(c: Char): Boolean {
        return c == '"' || c == '\'' || c == '”' || c == '’' || c == '»' ||
                c == ')' || c == ']' || c == '}' || c == '>' || c == '」' || c == '』'
    }

    /**
     * Splits [text] into sentences while preserving exact character offsets.
     * Does NOT split on:
     * - Abbreviations (e.g. Mr., Mrs., Dr., St., vs., etc.)
     * - Decimals (e.g. 3.14)
     * - Patterns like "A-14", "F-16"
     * - Single letter initials (e.g. "A.")
     *
     * Handles:
     * - Terminators: . ! ? … \u2026
     * - Ellipsis: ...
     * - Closing quotes / brackets immediately following terminators (." !” ?”)
     */
    fun splitIntoSentences(text: String): List<SentenceSpan> {
        if (text.isEmpty()) return emptyList()

        val results = mutableListOf<SentenceSpan>()
        val len = text.length
        var sentenceStart = 0

        // Skip leading whitespace
        while (sentenceStart < len && text[sentenceStart].isWhitespace()) {
            sentenceStart++
        }
        if (sentenceStart >= len) return emptyList()

        var i = sentenceStart
        while (i < len) {
            val c = text[i]
            val isTerminator = c == '.' || c == '!' || c == '?' || c == '…' || c == '\u2026'

            if (isTerminator) {
                // Advance past repeated terminators like "..." or "!?!"
                var termEnd = i
                while (termEnd + 1 < len && (text[termEnd + 1] == '.' || text[termEnd + 1] == '!' || text[termEnd + 1] == '?' || text[termEnd + 1] == '…' || text[termEnd + 1] == '\u2026')) {
                    termEnd++
                }

                // Check for closing quotation marks or brackets immediately after terminator
                var quoteEnd = termEnd
                while (quoteEnd + 1 < len && isClosingPunctuation(text[quoteEnd + 1])) {
                    quoteEnd++
                }

                // Sentence break must be followed by whitespace or end of string
                val isAtBoundary = (quoteEnd + 1 >= len) || text[quoteEnd + 1].isWhitespace()

                if (isAtBoundary) {
                    var isFalseAlarm = false

                    // If terminator was a single dot (not ellipsis or exclamation/question)
                    if (c == '.' && termEnd == i) {
                        // 1. Check if decimal: digit before dot AND digit after dot (e.g. 3.14)
                        if (i > 0 && text[i - 1].isDigit() && i + 1 < len && text[i + 1].isDigit()) {
                            isFalseAlarm = true
                        }

                        // 2. Check abbreviation or initial before dot
                        if (!isFalseAlarm && i > 0) {
                            var wordStart = i - 1
                            while (wordStart >= sentenceStart && (text[wordStart].isLetter() || text[wordStart] == '.')) {
                                wordStart--
                            }
                            val word = text.substring(wordStart + 1, i).lowercase()
                            if (word.length == 1 && word[0].isLetter()) {
                                // Single initial like "A."
                                isFalseAlarm = true
                            } else if (ABBREVIATIONS.contains(word)) {
                                if (word == "st") {
                                    // "St." can be Saint (title) or Street (address).
                                    // If preceded by a street name/number (e.g. "Baker St.") and followed by an uppercase letter,
                                    // it terminates the sentence.
                                    var nextChar: Char? = null
                                    var n = quoteEnd + 1
                                    while (n < len && text[n].isWhitespace()) n++
                                    if (n < len) nextChar = text[n]

                                    val hasPrecedingWord = wordStart > sentenceStart
                                    if (hasPrecedingWord && nextChar != null && nextChar.isUpperCase()) {
                                        isFalseAlarm = false
                                    } else {
                                        isFalseAlarm = true
                                    }
                                } else {
                                    isFalseAlarm = true
                                }
                            }
                        }

                        // 3. Patterns like "A-14"
                        if (!isFalseAlarm && i > 2 && text[i - 1].isLetterOrDigit() && text[i - 2] == '-') {
                            // Check if inside a hyphenated token not intended as a sentence ending
                            if (quoteEnd + 1 < len && !text[quoteEnd + 1].isWhitespace()) {
                                isFalseAlarm = true
                            }
                        }
                    }

                    // 4. Check if ending with a quote followed by a lowercase dialogue tag (e.g. "Stop!" he shouted.)
                    if (!isFalseAlarm && quoteEnd > termEnd) {
                        var nextNonWs = quoteEnd + 1
                        while (nextNonWs < len && text[nextNonWs].isWhitespace()) {
                            nextNonWs++
                        }
                        if (nextNonWs < len && text[nextNonWs].isLowerCase()) {
                            isFalseAlarm = true
                        }
                    }

                    if (!isFalseAlarm) {
                        val sentenceEnd = quoteEnd + 1
                        val sText = text.substring(sentenceStart, sentenceEnd)
                        results.add(SentenceSpan(sText, sentenceStart, sentenceEnd))

                        // Advance sentenceStart to next non-whitespace character
                        var nextStart = sentenceEnd
                        while (nextStart < len && text[nextStart].isWhitespace()) {
                            nextStart++
                        }
                        sentenceStart = nextStart
                        i = nextStart
                        continue
                    }
                }
                i = quoteEnd + 1
            } else {
                i++
            }
        }

        // Add remaining trailing text if no trailing terminator
        if (sentenceStart < len) {
            val sText = text.substring(sentenceStart, len)
            if (sText.isNotBlank()) {
                results.add(SentenceSpan(sText, sentenceStart, len))
            }
        }

        return results
    }

    /**
     * Finds the sentence containing [charOffset].
     * If the offset falls in whitespace between sentences or outside, returns the closest sentence.
     */
    fun findSentence(sentences: List<SentenceSpan>, charOffset: Int): SentenceSpan? {
        if (sentences.isEmpty()) return null
        if (charOffset <= sentences.first().start) return sentences.first()
        if (charOffset >= sentences.last().end) return sentences.last()

        return sentences.find { charOffset in it.start..it.end }
            ?: sentences.minByOrNull {
                if (charOffset < it.start) it.start - charOffset else charOffset - it.end
            }
    }
}
