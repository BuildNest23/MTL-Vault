package com.example.replacement

import java.util.regex.Pattern

data class ReplacementRule(
    val find: String,
    val replace: String,
    val caseSensitive: Boolean = false,
    val wholeWord: Boolean = true
)

object ReplacementEngine {

    fun parseRules(text: String?): List<ReplacementRule> {
        if (text.isNullOrBlank()) return emptyList()
        val rules = mutableListOf<ReplacementRule>()
        val lines = text.lines()

        for (raw in lines) {
            val line = raw.trim()
            if (line.isEmpty() || line.startsWith("#")) continue

            var cs = false
            var ww = true
            var rest = line

            // Check for flags like [cs], [nww], [cs,nww], [ww]
            if (rest.startsWith("[")) {
                val closeIdx = rest.indexOf(']')
                if (closeIdx > 0) {
                    val flagStr = rest.substring(1, closeIdx).lowercase()
                    val flags = flagStr.split(Regex("[\\s,]+")).filter { it.isNotBlank() }
                    for (f in flags) {
                        when (f) {
                            "cs", "case", "casesensitive" -> cs = true
                            "nww", "any", "sub", "substring", "partial" -> ww = false
                            "ww", "whole", "wholeword" -> ww = true
                        }
                    }
                    rest = rest.substring(closeIdx + 1).trim()
                }
            }

            // Find separator: "=>", "->", or "="
            var sepIdx = -1
            var sepLen = 0
            for (sep in listOf("=>", "->", "=")) {
                val idx = rest.indexOf(sep)
                if (idx >= 0 && (sepIdx == -1 || idx < sepIdx)) {
                    sepIdx = idx
                    sepLen = sep.length
                }
            }

            if (sepIdx < 0) continue

            val find = rest.substring(0, sepIdx).trim()
            val replace = rest.substring(sepIdx + sepLen).trim()

            if (find.isNotEmpty()) {
                rules.add(ReplacementRule(find, replace, cs, ww))
            }
        }
        return rules
    }

    fun applyRules(text: String, rules: List<ReplacementRule>): String {
        if (rules.isEmpty() || text.isEmpty()) return text
        var result = text

        for (rule in rules) {
            if (rule.find.isEmpty()) continue
            try {
                val flags = if (rule.caseSensitive) 0 else Pattern.CASE_INSENSITIVE or Pattern.UNICODE_CASE
                val pattern = Pattern.compile(Pattern.quote(rule.find), flags)
                val matcher = pattern.matcher(result)
                val sb = StringBuffer()

                while (matcher.find()) {
                    val start = matcher.start()
                    val end = matcher.end()

                    var shouldReplace = true
                    if (rule.wholeWord) {
                        // Check previous character
                        if (start > 0) {
                            val prevChar = result[start - 1]
                            if (isWordChar(prevChar)) {
                                shouldReplace = false
                            }
                        }
                        // Check next character
                        if (shouldReplace && end < result.length) {
                            val nextChar = result[end]
                            if (isWordChar(nextChar)) {
                                shouldReplace = false
                            }
                        }
                    }

                    if (shouldReplace) {
                        matcher.appendReplacement(sb, MatcherUtils.quoteReplacement(rule.replace))
                    } else {
                        matcher.appendReplacement(sb, MatcherUtils.quoteReplacement(matcher.group()))
                    }
                }
                matcher.appendTail(sb)
                result = sb.toString()
            } catch (e: Exception) {
                // If any invalid regex or matching error, continue with current result
            }
        }
        return result
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
