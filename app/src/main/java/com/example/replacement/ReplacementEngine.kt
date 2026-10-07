package com.example.replacement

import com.example.logging.AppLogger
import java.util.regex.Pattern

data class ReplacementRule(
    val find: String,
    val replace: String,
    val caseSensitive: Boolean = false,
    val wholeWord: Boolean = true,
    val isRegex: Boolean = false,
    val compiledPattern: Pattern? = null
)

data class ContentCacheKey(
    val chapterId: String,
    val rulesHash: Int,
    val contentHash: Int
)

object ReplacementEngine {

    private val GROUP_REF = Regex("\\$(\\d|\\{\\w+\\})")

    const val MAX_RULES = 500
    const val MAX_PATTERN_LENGTH = 200
    const val LARGE_TEXT_THRESHOLD = 500_000
    private const val CHUNK_SIZE = 250_000

    private val DANGEROUS_REGEX_PATTERNS = listOf(
        // Nested quantifiers like (a+)+, (a*)*, (.*)+, (a+){2,}
        Regex("""\([^()]*(\+|\*|\{[0-9]+,?[0-9]*\})[^()]*\)\s*(\+|\*|\{[0-9]+,?[0-9]*\})"""),
        // Wildcard groups with repetition like (.*a){2,}, (.*)+
        Regex("""\([^()]*\.[*+][^()]*\)\s*(\+|\*|\{[0-9]+,?[0-9]*\})"""),
        // Alternation with quantifiers inside and outside like (a|b+)*, (a+|b)*
        Regex("""\([^()]*\|[^()]*[*+][^()]*\)\s*[*+]"""),
        Regex("""\([^()]*[*+][^()]*\|[^()]*\)\s*[*+]"""),
        // Consecutive quantifiers like a++, a**
        Regex("""(\+|\*|\{[0-9]+,?[0-9]*\})\s*(\+|\*|\{[0-9]+,?[0-9]*\})""")
    )

    // In-memory LRU cache for parsed rules
    private val parsedRulesCache = object : LinkedHashMap<String, List<ReplacementRule>>(32, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<ReplacementRule>>?): Boolean {
            return size > 30
        }
    }

    // In-memory LRU cache for processed chapter content
    private val contentCache = object : LinkedHashMap<ContentCacheKey, String>(50, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<ContentCacheKey, String>?): Boolean {
            return size > 50
        }
    }

    @Synchronized
    fun getCachedRenderedContent(chapterId: String, rulesHash: Int, contentHash: Int): String? {
        return contentCache[ContentCacheKey(chapterId, rulesHash, contentHash)]
    }

    @Synchronized
    fun cacheRenderedContent(chapterId: String, rulesHash: Int, contentHash: Int, content: String) {
        contentCache[ContentCacheKey(chapterId, rulesHash, contentHash)] = content
    }

    @Synchronized
    fun invalidateCache() {
        contentCache.clear()
        parsedRulesCache.clear()
        AppLogger.i("ReplacementEngine", "Replacement cache invalidated")
    }

    @Synchronized
    fun invalidateChapter(chapterId: String) {
        contentCache.keys.removeAll { it.chapterId == chapterId }
    }

    fun isSafeRegex(pattern: String): Boolean {
        if (pattern.isBlank()) return false
        for (dangerous in DANGEROUS_REGEX_PATTERNS) {
            if (dangerous.containsMatchIn(pattern)) {
                return false
            }
        }
        return try {
            Pattern.compile(pattern)
            true
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Normalizes text before rendering:
     * 1. Converts \r\n and \r to \n
     * 2. Collapses 3+ consecutive newlines to 2 (\n\n)
     * 3. Strips zero-width characters (U+200B..U+200D, U+FEFF)
     * 4. Replaces non-breaking space (U+00A0) with standard space
     */
    fun normalizeText(text: String): String {
        if (text.isEmpty()) return text
        // 1. Convert \r\n and \r to \n
        var s = text.replace("\r\n", "\n").replace('\r', '\n')
        // 2. Collapse 3+ consecutive newlines to 2 (\n\n)
        s = s.replace(Regex("\n{3,}"), "\n\n")
        // 3. Strip zero-width characters (U+200B..U+200D, U+FEFF)
        s = s.replace(Regex("[\u200B-\u200D\uFEFF]"), "")
        // 4. Replace non-breaking space (U+00A0) with standard space
        s = s.replace('\u00A0', ' ')
        return s
    }

    @Synchronized
    fun parseRules(text: String?): List<ReplacementRule> {
        if (text.isNullOrBlank()) return emptyList()

        // Check LRU rules cache
        val cached = parsedRulesCache[text]
        if (cached != null) {
            return cached
        }

        val rules = mutableListOf<ReplacementRule>()
        val lines = text.lines()

        for (raw in lines) {
            if (rules.size >= MAX_RULES) {
                AppLogger.w("ReplacementEngine", "Rule limit reached (max $MAX_RULES). Skipping further rules.")
                break
            }

            val line = raw.trim()
            if (line.isEmpty() || line.startsWith("#")) continue

            var cs = false
            var ww = true
            var isRegex = false
            var rest = line

            // Check for flags like [cs], [nww], [re], [regex], [cs,nww]
            while (rest.startsWith("[")) {
                val closeIdx = rest.indexOf(']')
                if (closeIdx > 0) {
                    val flagStr = rest.substring(1, closeIdx).lowercase()
                    val flags = flagStr.split(Regex("[\\s,]+")).filter { it.isNotBlank() }
                    for (f in flags) {
                        when (f) {
                            "cs", "case", "casesensitive" -> cs = true
                            "nww", "any", "sub", "substring", "partial" -> ww = false
                            "ww", "whole", "wholeword" -> ww = true
                            "re", "regex" -> isRegex = true
                        }
                    }
                    rest = rest.substring(closeIdx + 1).trim()
                } else {
                    break
                }
            }

            // Find separator: "=>", "->", or "="
            var sepIdx = -1
            var sepLen = 0
            if (isRegex) {
                // Regex rules may contain "=" inside lookarounds such as (?=...) or (?<=...)
                var k = 0
                while (k < rest.length) {
                    if (rest.startsWith("=>", k) || rest.startsWith("->", k)) {
                        sepIdx = k; sepLen = 2; break
                    }
                    if (rest[k] == '=') {
                        val isLookaround = (k >= 2 && rest.regionMatches(k - 2, "(?", 0, 2)) ||
                            (k >= 3 && rest.regionMatches(k - 3, "(?<", 0, 3))
                        if (!isLookaround) { sepIdx = k; sepLen = 1; break }
                    }
                    k++
                }
            } else {
                for (sep in listOf("=>", "->", "=")) {
                    val idx = rest.indexOf(sep)
                    if (idx >= 0 && (sepIdx == -1 || idx < sepIdx)) {
                        sepIdx = idx
                        sepLen = sep.length
                    }
                }
            }

            if (sepIdx < 0) continue

            val find = rest.substring(0, sepIdx).trim()
            val replace = rest.substring(sepIdx + sepLen).trim()

            if (find.isEmpty()) {
                AppLogger.w("ReplacementEngine", "Skipping empty find pattern in line: '$line'")
                continue
            }

            if (find.length > MAX_PATTERN_LENGTH) {
                AppLogger.w("ReplacementEngine", "Pattern exceeds max length ($MAX_PATTERN_LENGTH chars): '${find.take(30)}...'. Skipping.")
                continue
            }

            if (isRegex) {
                if (!isSafeRegex(find)) {
                    AppLogger.w("ReplacementEngine", "Unsafe regex pattern detected (catastrophic backtracking risk): '$find'. Falling back to literal match.")
                    isRegex = false
                }
            }

            // Pre-compile Pattern instance for performance
            val flags = if (cs) 0 else Pattern.CASE_INSENSITIVE or Pattern.UNICODE_CASE
            val compiled = try {
                if (isRegex) {
                    Pattern.compile(find, flags)
                } else {
                    Pattern.compile(Pattern.quote(find), flags)
                }
            } catch (e: Exception) {
                AppLogger.w("ReplacementEngine", "Failed to compile pattern for '$find': ${e.message}")
                null
            }

            rules.add(ReplacementRule(find, replace, cs, ww, isRegex, compiled))
        }

        parsedRulesCache[text] = rules
        return rules
    }

    fun applyRules(text: String, rules: List<ReplacementRule>): String {
        if (rules.isEmpty() || text.isEmpty()) return text

        // If text exceeds 500k chars, use chunked approach to avoid OOM
        if (text.length > LARGE_TEXT_THRESHOLD) {
            return applyRulesChunked(text, rules)
        }

        var result = text
        for (rule in rules) {
            result = applySingleRule(result, rule)
        }
        return result
    }

    private fun applyRulesChunked(text: String, rules: List<ReplacementRule>): String {
        AppLogger.i("ReplacementEngine", "Applying rules on large text (${text.length} chars) using chunked processing")
        val chunks = mutableListOf<String>()
        var start = 0
        while (start < text.length) {
            if (start + CHUNK_SIZE >= text.length) {
                chunks.add(text.substring(start))
                break
            }
            var end = start + CHUNK_SIZE
            val nextNewline = text.indexOf('\n', end)
            if (nextNewline != -1 && nextNewline - start < CHUNK_SIZE + 50_000) {
                end = nextNewline + 1
            }
            chunks.add(text.substring(start, end))
            start = end
        }

        val sb = StringBuilder(text.length)
        for (chunk in chunks) {
            var chunkResult = chunk
            for (rule in rules) {
                chunkResult = applySingleRule(chunkResult, rule)
            }
            sb.append(chunkResult)
        }
        return sb.toString()
    }

    private fun applySingleRule(text: String, rule: ReplacementRule): String {
        if (rule.find.isEmpty()) return text
        return try {
            val pattern = rule.compiledPattern ?: run {
                val flags = if (rule.caseSensitive) 0 else Pattern.CASE_INSENSITIVE or Pattern.UNICODE_CASE
                if (rule.isRegex) {
                    Pattern.compile(rule.find, flags)
                } else {
                    Pattern.compile(Pattern.quote(rule.find), flags)
                }
            }

            val matcher = pattern.matcher(text)
            val sb = StringBuffer()
            // Regex rules may use $1 / ${name} capture-group references in the replacement
            val useGroupRefs = rule.isRegex && GROUP_REF.containsMatchIn(rule.replace)

            while (matcher.find()) {
                val start = matcher.start()
                val end = matcher.end()

                var shouldReplace = true
                if (!rule.isRegex && rule.wholeWord) {
                    if (start > 0) {
                        val prevChar = text[start - 1]
                        if (isWordChar(prevChar)) {
                            shouldReplace = false
                        }
                    }
                    if (shouldReplace && end < text.length) {
                        val nextChar = text[end]
                        if (isWordChar(nextChar)) {
                            shouldReplace = false
                        }
                    }
                }

                if (shouldReplace) {
                    if (useGroupRefs) {
                        try {
                            matcher.appendReplacement(sb, rule.replace)
                        } catch (e: Exception) {
                            // Invalid group reference: fall back to the literal text
                            matcher.appendReplacement(sb, MatcherUtils.quoteReplacement(rule.replace))
                        }
                    } else {
                        matcher.appendReplacement(sb, MatcherUtils.quoteReplacement(rule.replace))
                    }
                } else {
                    matcher.appendReplacement(sb, MatcherUtils.quoteReplacement(matcher.group()))
                }
            }
            matcher.appendTail(sb)
            sb.toString()
        } catch (e: Exception) {
            AppLogger.e("ReplacementEngine", "Error applying rule: ${rule.find}", e)
            text
        }
    }

    private fun isWordChar(c: Char): Boolean {
        return Character.isLetterOrDigit(c) || c == '_'
    }
}

private object MatcherUtils {
    fun quoteReplacement(s: String): String {
        if (!s.contains('\\') && !s.contains('$')) return s
        val sb = StringBuilder()
        for (c in s) {
            if (c == '\\' || c == '$') {
                sb.append('\\')
            }
            sb.append(c)
        }
        return sb.toString()
    }
}
