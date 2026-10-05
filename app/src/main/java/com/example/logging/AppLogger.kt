package com.example.logging

import android.app.ActivityManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.util.Log
import com.example.BuildConfig
import com.example.data.model.ReaderSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileWriter
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.ArrayDeque
import java.util.Date
import java.util.Locale

object AppLogger {

    const val MAX_LOG_SIZE_BYTES = 1024L * 1024L // 1 MB
    const val MAX_MEMORY_LINES = 500
    const val CRASH_MEMORY_LINES = 100

    @Volatile
    var isDetailedLogging: Boolean = false

    private val memoryLog = ArrayDeque<String>(MAX_MEMORY_LINES)
    private val logChannel = Channel<String>(capacity = 2000)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var isInitialized = false

    @Volatile
    private var logsDir: File? = null

    @Volatile
    private var currentLogFile: File? = null

    @Volatile
    private var oldLogFile: File? = null

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)

    fun init(context: Context) {
        if (isInitialized) return
        synchronized(this) {
            if (isInitialized) return
            try {
                val appCtx = context.applicationContext ?: context
                val dir = File(appCtx.filesDir, "logs")
                if (!dir.exists()) {
                    dir.mkdirs()
                }
                logsDir = dir
                currentLogFile = File(dir, "app_log.txt")
                oldLogFile = File(dir, "app_log_old.txt")

                // Start async disk writer
                startDiskWriter()

                // Register uncaught crash handler
                setupCrashHandler(appCtx)

                isInitialized = true

                // Log system header at startup
                i("AppLogger", "=== App Started ===")
                i("AppLogger", buildHeader(appCtx))
            } catch (t: Throwable) {
                // Logging initialization must never crash the app
                Log.e("AppLogger", "Failed to initialize AppLogger", t)
            }
        }
    }

    /**
     * For unit tests or custom directory setups.
     */
    fun initForTesting(baseDir: File) {
        synchronized(this) {
            logsDir = baseDir
            currentLogFile = File(baseDir, "app_log.txt")
            oldLogFile = File(baseDir, "app_log_old.txt")
            isInitialized = true
        }
    }

    private fun startDiskWriter() {
        scope.launch {
            try {
                for (entry in logChannel) {
                    writeEntryToFile(entry)
                }
            } catch (t: Throwable) {
                Log.e("AppLogger", "Error in disk writer loop", t)
            }
        }
    }

    private fun writeEntryToFile(entry: String) {
        try {
            val file = currentLogFile ?: return
            val oldFile = oldLogFile ?: File(file.parentFile, "app_log_old.txt")

            rotateIfNeeded(file, oldFile, MAX_LOG_SIZE_BYTES)

            FileWriter(file, true).use { writer ->
                writer.appendLine(entry)
                writer.flush()
            }
        } catch (t: Throwable) {
            Log.e("AppLogger", "Failed to write log entry to disk", t)
        }
    }

    /**
     * Rotates files if logFile exceeds limit.
     * Replaces oldLogFile with current logFile and creates a new logFile.
     */
    fun rotateIfNeeded(file: File, oldFile: File, limitBytes: Long): Boolean {
        return try {
            if (file.exists() && file.length() >= limitBytes) {
                if (oldFile.exists()) {
                    oldFile.delete()
                }
                val renamed = file.renameTo(oldFile)
                if (!renamed) {
                    // Fallback copy & delete if rename fails
                    file.copyTo(oldFile, overwrite = true)
                    file.delete()
                }
                file.createNewFile()
                true
            } else {
                false
            }
        } catch (t: Throwable) {
            Log.e("AppLogger", "Error rotating log files", t)
            false
        }
    }

    private fun setupCrashHandler(context: Context) {
        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                // Synchronously record crash details and recent memory lines
                writeSynchronousCrashLog(thread, throwable)

                // Mark flag so on next launch the app can notify the user
                val prefs = context.getSharedPreferences("app_logger_prefs", Context.MODE_PRIVATE)
                prefs.edit().putBoolean("crashed_last_run", true).commit()
            } catch (t: Throwable) {
                Log.e("AppLogger", "Failed to record uncaught exception", t)
            } finally {
                previousHandler?.uncaughtException(thread, throwable)
            }
        }
    }

    private fun writeSynchronousCrashLog(thread: Thread, throwable: Throwable) {
        try {
            val file = currentLogFile ?: return
            val oldFile = oldLogFile ?: File(file.parentFile, "app_log_old.txt")

            rotateIfNeeded(file, oldFile, MAX_LOG_SIZE_BYTES)

            val recentLines: List<String>
            synchronized(memoryLog) {
                val list = memoryLog.toList()
                recentLines = list.takeLast(CRASH_MEMORY_LINES)
            }

            val timestamp = synchronized(dateFormat) { dateFormat.format(Date()) }
            val stackTrace = getStackTraceString(throwable)

            FileWriter(file, true).use { writer ->
                writer.appendLine("\n==================== UNCAUGHT CRASH ====================")
                writer.appendLine("Timestamp: $timestamp")
                writer.appendLine("Thread: ${thread.name} (id: ${thread.id})")
                writer.appendLine("Exception: $stackTrace")
                writer.appendLine("--- Last ${recentLines.size} Log Entries ---")
                for (line in recentLines) {
                    writer.appendLine(line)
                }
                writer.appendLine("================== END UNCAUGHT CRASH ==================\n")
                writer.flush()
            }
        } catch (t: Throwable) {
            Log.e("AppLogger", "Error writing synchronous crash log", t)
        }
    }

    fun consumeCrashFlag(context: Context): Boolean {
        return try {
            val prefs = context.getSharedPreferences("app_logger_prefs", Context.MODE_PRIVATE)
            val hadCrash = prefs.getBoolean("crashed_last_run", false)
            if (hadCrash) {
                prefs.edit().putBoolean("crashed_last_run", false).apply()
            }
            hadCrash
        } catch (t: Throwable) {
            false
        }
    }

    fun d(tag: String, msg: String, throwable: Throwable? = null) {
        try {
            try {
                Log.d(tag, msg, throwable)
            } catch (_: Throwable) {}
            if (!isDetailedLogging) return
            logInternal("D", tag, msg, throwable)
        } catch (_: Throwable) {}
    }

    fun i(tag: String, msg: String, throwable: Throwable? = null) {
        try {
            try {
                Log.i(tag, msg, throwable)
            } catch (_: Throwable) {}
            logInternal("I", tag, msg, throwable)
        } catch (_: Throwable) {}
    }

    fun w(tag: String, msg: String, throwable: Throwable? = null) {
        try {
            try {
                Log.w(tag, msg, throwable)
            } catch (_: Throwable) {}
            logInternal("W", tag, msg, throwable)
        } catch (_: Throwable) {}
    }

    fun e(tag: String, msg: String, throwable: Throwable? = null) {
        try {
            try {
                Log.e(tag, msg, throwable)
            } catch (_: Throwable) {}
            logInternal("E", tag, msg, throwable)
        } catch (_: Throwable) {}
    }

    private fun logInternal(level: String, tag: String, msg: String, throwable: Throwable?) {
        try {
            val threadName = Thread.currentThread().name
            val timestamp = synchronized(dateFormat) { dateFormat.format(Date()) }
            val formatted = "$timestamp | $level | $tag | [$threadName] | $msg"
            val fullEntry = if (throwable != null) {
                "$formatted\n${getStackTraceString(throwable)}"
            } else {
                formatted
            }

            // Keep in memory
            synchronized(memoryLog) {
                if (memoryLog.size >= MAX_MEMORY_LINES) {
                    memoryLog.removeFirst()
                }
                memoryLog.addLast(fullEntry)
            }

            // Queue for async file writing
            if (isInitialized) {
                logChannel.trySend(fullEntry)
            }
        } catch (_: Throwable) {
            // Logging must never throw
        }
    }

    fun getMemoryEntries(): List<String> {
        return synchronized(memoryLog) {
            memoryLog.toList()
        }
    }

    fun clearMemoryEntries() {
        synchronized(memoryLog) {
            memoryLog.clear()
        }
    }

    fun getLogSizeBytes(context: Context): Long {
        return try {
            val dir = logsDir ?: File(context.filesDir, "logs")
            val current = File(dir, "app_log.txt")
            val old = File(dir, "app_log_old.txt")
            var size = 0L
            if (current.exists()) size += current.length()
            if (old.exists()) size += old.length()
            size
        } catch (t: Throwable) {
            0L
        }
    }

    fun getFormattedLogSize(context: Context): String {
        val bytes = getLogSizeBytes(context)
        return when {
            bytes <= 0L -> "0 KB"
            bytes < 1024L -> "1 KB"
            bytes < 1024L * 1024L -> "${bytes / 1024L} KB"
            else -> String.format(Locale.US, "%.1f MB", bytes.toDouble() / (1024.0 * 1024.0))
        }
    }

    fun clearLogs(context: Context): Boolean {
        return try {
            clearMemoryEntries()
            val dir = logsDir ?: File(context.filesDir, "logs")
            val current = File(dir, "app_log.txt")
            val old = File(dir, "app_log_old.txt")
            if (current.exists()) current.delete()
            if (old.exists()) old.delete()
            current.createNewFile()
            i("AppLogger", "Logs cleared by user")
            true
        } catch (t: Throwable) {
            Log.e("AppLogger", "Failed to clear logs", t)
            false
        }
    }

    fun buildHeader(context: Context, readerSettings: ReaderSettings? = null, voiceCount: Int? = null): String {
        return try {
            val pm = context.packageManager
            val pInfo = try { pm.getPackageInfo(context.packageName, 0) } catch (_: Exception) { null }
            val versionName = pInfo?.versionName ?: "1.0"
            val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                pInfo?.longVersionCode ?: 1L
            } else {
                @Suppress("DEPRECATION")
                pInfo?.versionCode?.toLong() ?: 1L
            }
            val buildType = BuildConfig.BUILD_TYPE
            val androidVer = Build.VERSION.RELEASE ?: "unknown"
            val sdkVer = Build.VERSION.SDK_INT
            val manufacturer = Build.MANUFACTURER ?: "unknown"
            val model = Build.MODEL ?: "unknown"

            val actManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            val memInfo = ActivityManager.MemoryInfo().also { actManager?.getMemoryInfo(it) }
            val availRamMb = (memInfo.availMem / (1024 * 1024))
            val totalRamMb = (memInfo.totalMem / (1024 * 1024))

            val locale = Locale.getDefault().toString()
            val defaultTts = try {
                Settings.Secure.getString(context.contentResolver, "tts_default_synth") ?: "system default"
            } catch (_: Exception) {
                "unavailable"
            }

            val installedVoices = voiceCount ?: getInstalledVoiceCount()

            val settingsDesc = readerSettings?.let {
                "theme=${it.theme}, fontSize=${it.fontSize}, lineHeight=${it.lineHeight}, font=${it.fontFamily}, ttsRate=${it.ttsRate}, ttsPitch=${it.ttsPitch}, detailedLogging=${it.detailedLogging}"
            } ?: "settings not loaded"

            """
            |======================================================================
            | MTL VAULT DIAGNOSTIC LOG
            | Date: ${synchronized(dateFormat) { dateFormat.format(Date()) }}
            | App Version: $versionName (code $versionCode) [$buildType]
            | Android OS: $androidVer (API $sdkVer)
            | Device: $manufacturer $model
            | Available RAM: ${availRamMb}MB / ${totalRamMb}MB
            | System Locale: $locale
            | Default TTS Engine: $defaultTts
            | Installed TTS Voices: $installedVoices
            | Reader Settings: $settingsDesc
            |======================================================================
            """.trimMargin()
        } catch (t: Throwable) {
            "Diagnostic Header generation failed: ${t.localizedMessage}"
        }
    }

    private fun getInstalledVoiceCount(): String {
        return try {
            val tts = android.speech.tts.TextToSpeech(null, null)
            val count = tts.voices?.size ?: 0
            tts.shutdown()
            count.toString()
        } catch (_: Throwable) {
            "N/A"
        }
    }

    fun buildFullExportText(context: Context, readerSettings: ReaderSettings? = null): String {
        val sb = StringBuilder()
        val header = buildHeader(context, readerSettings)
        sb.append(header).append("\n\n")

        val dir = logsDir ?: File(context.filesDir, "logs")
        val oldFile = File(dir, "app_log_old.txt")
        val currentFile = File(dir, "app_log.txt")

        if (oldFile.exists() && oldFile.length() > 0) {
            sb.append("--- PREVIOUS LOG (app_log_old.txt) ---\n")
            try {
                sb.append(oldFile.readText(Charsets.UTF_8))
                sb.append("\n\n")
            } catch (t: Throwable) {
                sb.append("[Error reading old log: ${t.message}]\n\n")
            }
        }

        if (currentFile.exists() && currentFile.length() > 0) {
            sb.append("--- CURRENT LOG (app_log.txt) ---\n")
            try {
                sb.append(currentFile.readText(Charsets.UTF_8))
                sb.append("\n")
            } catch (t: Throwable) {
                sb.append("[Error reading current log: ${t.message}]\n")
            }
        } else {
            // Fallback to in-memory entries if file is empty or not yet flushed
            sb.append("--- CURRENT IN-MEMORY LOG ---\n")
            val memory = getMemoryEntries()
            for (line in memory) {
                sb.append(line).append("\n")
            }
        }

        return sb.toString()
    }

    fun buildExportFile(context: Context, readerSettings: ReaderSettings? = null): File {
        val cacheLogsDir = File(context.cacheDir, "logs")
        if (!cacheLogsDir.exists()) {
            cacheLogsDir.mkdirs()
        }
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val exportFile = File(cacheLogsDir, "mtl_vault_log_$timestamp.txt")
        val fullText = buildFullExportText(context, readerSettings)
        exportFile.writeText(fullText, Charsets.UTF_8)
        return exportFile
    }

    fun getStackTraceString(t: Throwable): String {
        return try {
            val sw = StringWriter()
            val pw = PrintWriter(sw)
            t.printStackTrace(pw)
            sw.toString().trim()
        } catch (_: Throwable) {
            t.localizedMessage ?: "Unknown error"
        }
    }
}
