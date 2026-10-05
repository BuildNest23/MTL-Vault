package com.example.replacement

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReplacementEngineTest {

    @Test
    fun testRuleLimits() {
        // Generate 600 rules
        val ruleLines = StringBuilder()
        for (i in 1..600) {
            ruleLines.append("word$i => replacement$i\n")
        }
        val rules = ReplacementEngine.parseRules(ruleLines.toString())
        assertEquals("Rules list must be capped at MAX_RULES (500)", 500, rules.size)

        // Test max pattern length (200 characters)
        val validPattern = "a".repeat(200)
        val oversizedPattern = "b".repeat(201)
        val testInput = "$validPattern => short\n$oversizedPattern => short"
        val parsed = ReplacementEngine.parseRules(testInput)
        assertEquals("Should only parse the rule with length <= 200", 1, parsed.size)
        assertEquals(validPattern, parsed[0].find)

        // Empty find pattern skipped
        val emptyPattern = " => replacement"
        val parsedEmpty = ReplacementEngine.parseRules(emptyPattern)
        assertTrue("Empty pattern must be skipped", parsedEmpty.isEmpty())
    }

    @Test
    fun testCatastrophicRegexRejection() {
        val dangerousPatterns = listOf(
            "(a+)+",
            "(.*a){2,}",
            "(a|b+)*",
            "(a*)*",
            "a++",
            "([a-z]+)+",
            "(.*)+",
            "(.+){2,}"
        )

        for (pattern in dangerousPatterns) {
            assertFalse(
                "Pattern '$pattern' should be identified as unsafe for catastrophic backtracking",
                ReplacementEngine.isSafeRegex(pattern)
            )
        }

        // Safe patterns
        val safePatterns = listOf(
            "\\d+",
            "[A-Z][a-z]+",
            "\\bChapter\\s+\\d+\\b",
            "hello|world",
            "^Line\\s+[0-9]+$"
        )

        for (pattern in safePatterns) {
            assertTrue(
                "Pattern '$pattern' should be accepted as safe",
                ReplacementEngine.isSafeRegex(pattern)
            )
        }

        // Unsafe regex rule falls back to literal match
        val ruleText = "[re] (a+)+ => replacement"
        val parsedRules = ReplacementEngine.parseRules(ruleText)
        assertEquals(1, parsedRules.size)
        assertFalse(
            "Unsafe regex rule must fall back to literal match (isRegex = false)",
            parsedRules[0].isRegex
        )
    }

    @Test
    fun testTextNormalization() {
        // 1. CRLF and CR to LF
        val crlfText = "Line 1\r\nLine 2\rLine 3\nLine 4"
        val normalizedCrlf = ReplacementEngine.normalizeText(crlfText)
        assertEquals("Line 1\nLine 2\nLine 3\nLine 4", normalizedCrlf)

        // 2. Collapse 3+ newlines to 2
        val excessiveNewlines = "Paragraph 1\n\n\n\n\nParagraph 2\n\n\nParagraph 3"
        val normalizedNewlines = ReplacementEngine.normalizeText(excessiveNewlines)
        assertEquals("Paragraph 1\n\nParagraph 2\n\nParagraph 3", normalizedNewlines)

        // 3. Strip zero-width characters (U+200B..U+200D, U+FEFF)
        val zeroWidthText = "Hello\u200B \u200CWorld\u200D!\uFEFF"
        val normalizedZeroWidth = ReplacementEngine.normalizeText(zeroWidthText)
        assertEquals("Hello World!", normalizedZeroWidth)

        // 4. Non-breaking space (U+00A0) to standard space
        val nbspText = "First\u00A0Second\u00A0Third"
        val normalizedNbsp = ReplacementEngine.normalizeText(nbspText)
        assertEquals("First Second Third", normalizedNbsp)
    }

    @Test
    fun testLargeTextProcessingWithoutOOM() {
        // Create 600k characters text (> 500k threshold)
        val paragraph = "The Protagonist walked through the ancient forest. He knew his destiny was calling.\n"
        val repeatCount = (600_000 / paragraph.length) + 10
        val largeText = paragraph.repeat(repeatCount)
        assertTrue("Test text should exceed 500k characters", largeText.length > 500_000)

        val rules = listOf(
            ReplacementRule(find = "Protagonist", replace = "Cultivator", caseSensitive = false, wholeWord = true),
            ReplacementRule(find = "ancient", replace = "mystic", caseSensitive = false, wholeWord = true)
        )

        val result = ReplacementEngine.applyRules(largeText, rules)
        assertTrue("Result should be non-empty", result.isNotEmpty())
        assertFalse("Old word should be replaced", result.contains("Protagonist"))
        assertTrue("New word should be present", result.contains("Cultivator"))
        assertTrue("Second replacement should be present", result.contains("mystic"))
        assertFalse("Second old word should be replaced", result.contains("ancient"))
    }

    @Test
    fun testRulesCachingAndPatternPrecompilation() {
        val rulesText = "Lord = Sovereign\n[cs] Dao = The Way\n[re] \\bvol\\b = Volume"
        val rules1 = ReplacementEngine.parseRules(rulesText)
        assertEquals(3, rules1.size)
        // Verify compiledPattern is precompiled
        assertNotNull("Compiled pattern should be precompiled", rules1[0].compiledPattern)
        assertNotNull("Compiled pattern should be precompiled", rules1[1].compiledPattern)
        assertNotNull("Compiled pattern should be precompiled", rules1[2].compiledPattern)

        // Parse again with identical input - should return cached list
        val rules2 = ReplacementEngine.parseRules(rulesText)
        org.junit.Assert.assertSame("Should return cached rules list instance", rules1, rules2)
    }

    @Test
    fun testRenderedContentCacheAndInvalidation() {
        ReplacementEngine.invalidateCache()

        val chapterId = "ch_test_cache"
        val rulesHash = 12345
        val contentHash = 67890
        val renderedContent = "Rendered content with cached replacements."

        // Initially not in cache
        val initial = ReplacementEngine.getCachedRenderedContent(chapterId, rulesHash, contentHash)
        org.junit.Assert.assertNull("Should be null initially", initial)

        // Store in cache
        ReplacementEngine.cacheRenderedContent(chapterId, rulesHash, contentHash, renderedContent)

        // Retrieve with exact keys
        val retrieved = ReplacementEngine.getCachedRenderedContent(chapterId, rulesHash, contentHash)
        assertEquals(renderedContent, retrieved)

        // Mismatched rules hash -> cache miss
        val missRules = ReplacementEngine.getCachedRenderedContent(chapterId, 99999, contentHash)
        org.junit.Assert.assertNull("Mismatched rules hash should result in cache miss", missRules)

        // Mismatched content hash -> cache miss
        val missContent = ReplacementEngine.getCachedRenderedContent(chapterId, rulesHash, 11111)
        org.junit.Assert.assertNull("Mismatched content hash should result in cache miss", missContent)

        // Invalidate specific chapter
        ReplacementEngine.invalidateChapter(chapterId)
        val afterChapterInvalidate = ReplacementEngine.getCachedRenderedContent(chapterId, rulesHash, contentHash)
        org.junit.Assert.assertNull("Chapter should be invalidated", afterChapterInvalidate)

        // Re-cache and global invalidate
        ReplacementEngine.cacheRenderedContent(chapterId, rulesHash, contentHash, renderedContent)
        assertEquals(renderedContent, ReplacementEngine.getCachedRenderedContent(chapterId, rulesHash, contentHash))
        ReplacementEngine.invalidateCache()
        org.junit.Assert.assertNull("Global cache invalidation should clear all entries", ReplacementEngine.getCachedRenderedContent(chapterId, rulesHash, contentHash))
    }
}
