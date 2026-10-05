package com.example.logging

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class AppLoggerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun testLogRotationWhenExceedingLimit() {
        val rootDir = tempFolder.newFolder("logs")
        val logFile = File(rootDir, "app_log.txt")
        val oldLogFile = File(rootDir, "app_log_old.txt")

        // 1. Initial write below threshold
        val smallText = "Initial log content\n"
        logFile.writeText(smallText, Charsets.UTF_8)

        val rotatedSmall = AppLogger.rotateIfNeeded(logFile, oldLogFile, 1024L)
        assertFalse("Should not rotate if file length is below limit", rotatedSmall)
        assertFalse("Old file should not exist yet", oldLogFile.exists())

        // 2. Write content exceeding 1024 bytes limit
        val largeContent = StringBuilder()
        for (i in 1..100) {
            largeContent.append("Line $i: Diagnostic log statement to simulate heavy logging\n")
        }
        logFile.writeText(largeContent.toString(), Charsets.UTF_8)
        val originalSize = logFile.length()
        assertTrue("Log file should exceed 1024 bytes", originalSize > 1024L)

        // 3. Perform rotation
        val rotated = AppLogger.rotateIfNeeded(logFile, oldLogFile, 1024L)
        assertTrue("rotateIfNeeded should return true when limit is exceeded", rotated)
        assertTrue("Old log file should now exist", oldLogFile.exists())
        assertEquals("Old log file length should match rotated content", originalSize, oldLogFile.length())
        assertTrue("Current log file should exist as a new file", logFile.exists())
        assertEquals("New log file should be empty", 0L, logFile.length())

        // 4. Exceed limit again and verify old file is replaced
        val secondLargeContent = "Second generation large content\n".repeat(100)
        logFile.writeText(secondLargeContent, Charsets.UTF_8)
        val secondSize = logFile.length()

        val rotatedSecond = AppLogger.rotateIfNeeded(logFile, oldLogFile, 1024L)
        assertTrue("Second rotation should succeed", rotatedSecond)
        assertEquals("Old file should be replaced with second log content", secondSize, oldLogFile.length())
        assertEquals("New log file should be reset to empty", 0L, logFile.length())
    }

    @Test
    fun testLoggingNeverThrows() {
        // Test that logging methods never throw under any circumstance
        try {
            AppLogger.isDetailedLogging = true
            AppLogger.d("TestTag", "Debug message")
            AppLogger.i("TestTag", "Info message")
            AppLogger.w("TestTag", "Warn message", RuntimeException("Warning exception"))
            AppLogger.e("TestTag", "Error message", IllegalStateException("Error state"))

            // Test with detailed logging off
            AppLogger.isDetailedLogging = false
            AppLogger.d("TestTag", "Should be ignored when detailed logging is off")
            AppLogger.i("TestTag", "Info message with detailed off")

            // Test with extreme inputs
            AppLogger.e("TestTag", "", null)
            AppLogger.w("NullCheck", "Testing with empty throwable", Throwable())
        } catch (t: Throwable) {
            org.junit.Assert.fail("AppLogger logging methods must never throw exceptions: ${t.message}")
        }
    }

    @Test
    fun testMemoryBufferLimitsAt500Lines() {
        AppLogger.clearMemoryEntries()
        AppLogger.isDetailedLogging = true

        for (i in 1..600) {
            AppLogger.i("LoopTag", "Entry number $i")
        }

        val entries = AppLogger.getMemoryEntries()
        assertEquals("Memory buffer must not exceed MAX_MEMORY_LINES (500)", AppLogger.MAX_MEMORY_LINES, entries.size)
        assertTrue("Latest entry should be retained", entries.last().contains("Entry number 600"))
        assertFalse("Old entry beyond 500 should be evicted", entries.first().contains("Entry number 100"))
    }

    @Test
    fun testLogFormatStructure() {
        AppLogger.clearMemoryEntries()
        AppLogger.isDetailedLogging = true

        AppLogger.i("FormatTag", "Verifying log line formatting")
        val entries = AppLogger.getMemoryEntries()
        assertTrue("Should have at least 1 entry", entries.isNotEmpty())

        val entry = entries.last()
        assertTrue("Entry should contain level I", entry.contains("| I |"))
        assertTrue("Entry should contain tag FormatTag", entry.contains("FormatTag"))
        assertTrue("Entry should contain thread brackets", entry.contains("["))
        assertTrue("Entry should contain message", entry.contains("Verifying log line formatting"))
    }
}
