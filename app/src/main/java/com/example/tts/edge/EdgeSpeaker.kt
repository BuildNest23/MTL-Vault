package com.example.tts.edge

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.PowerManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.io.File
import java.security.MessageDigest
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.math.roundToInt

/**
 * Speaks one sentence at a time with Edge neural voices, a bit like Android's TextToSpeech.speak():
 * calling [speak] cancels whatever was playing, and the callback reports start / done / error.
 *
 * Audio is downloaded as MP3, cached on disk (so re-reading a sentence is instant and works
 * offline) and played with MediaPlayer. [prefetch] downloads upcoming sentences ahead of time so
 * there are no gaps between sentences.
 */
class EdgeSpeaker(context: Context) {

    interface Callback {
        fun onStart(id: String)
        fun onDone(id: String)
        fun onError(id: String, message: String)
    }

    private data class Params(val voice: String, val rate: String, val pitch: String)

    private val appContext = context.applicationContext
    private val client = EdgeTtsClient()
    private val mainScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val netGate = Semaphore(4)
    private val cacheDir = File(appContext.cacheDir, "edge_tts").apply { mkdirs() }
    private val inFlight = HashMap<String, Deferred<File>>()

    @Volatile
    private var generation = 0
    private var job: Job? = null
    private var player: MediaPlayer? = null

    init {
        ioScope.launch { pruneCache() }
    }

    /** Speaks [text]. Anything that is currently playing is stopped first. */
    fun speak(id: String, text: String, voice: String, rate: Float, pitch: Float, callback: Callback) {
        stop()
        val gen = generation

        if (text.none { it.isLetterOrDigit() }) {
            // Nothing the voice could say (just punctuation): treat as instantly finished.
            mainScope.launch { if (gen == generation) callback.onDone(id) }
            return
        }

        val params = Params(voice, rateString(rate), pitchString(pitch))
        val extraSpeed = if (rate > 2f) rate / 2f else 1f

        job = mainScope.launch {
            try {
                val file = awaitAudio(text, params)
                if (gen != generation) return@launch
                play(file, id, gen, extraSpeed, callback)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (gen == generation) {
                    callback.onError(id, e.message ?: e.javaClass.simpleName)
                }
            }
        }
    }

    /** Downloads upcoming sentences in the background so they are ready when needed. */
    fun prefetch(texts: List<String>, voice: String, rate: Float, pitch: Float) {
        val params = Params(voice, rateString(rate), pitchString(pitch))
        for (text in texts) {
            if (text.none { it.isLetterOrDigit() }) continue
            val file = cacheFile(text, params)
            if (file.exists() && file.length() > 0L) continue
            startSynthesis(text, params)
        }
    }

    /** Stops playback immediately. Safe to call at any time. */
    fun stop() {
        generation++
        job?.cancel()
        job = null
        val current = player
        if (current != null) releasePlayer(current)
    }

    fun release() {
        stop()
        mainScope.cancel()
        ioScope.cancel()
    }

    // ------------------------------------------------------------------ audio files

    private suspend fun awaitAudio(text: String, params: Params): File {
        val file = cacheFile(text, params)
        if (file.exists() && file.length() > 0L) {
            file.setLastModified(System.currentTimeMillis())
            return file
        }
        return startSynthesis(text, params).await()
    }

    private fun startSynthesis(text: String, params: Params): Deferred<File> {
        val file = cacheFile(text, params)
        val key = file.name
        return synchronized(inFlight) {
            val existing = inFlight[key]
            if (existing != null && existing.isActive) {
                existing
            } else {
                val deferred = ioScope.async {
                    try {
                        netGate.withPermit {
                            if (file.exists() && file.length() > 0L) {
                                file
                            } else {
                                val bytes = client.synthesize(text, params.voice, params.rate, params.pitch)
                                val tmp = File(cacheDir, "$key.tmp")
                                tmp.writeBytes(bytes)
                                if (!tmp.renameTo(file)) {
                                    file.writeBytes(bytes)
                                    tmp.delete()
                                }
                                file
                            }
                        }
                    } finally {
                        synchronized(inFlight) { inFlight.remove(key) }
                    }
                }
                inFlight[key] = deferred
                deferred
            }
        }
    }

    private fun cacheFile(text: String, params: Params): File {
        val raw = params.voice + "|" + params.rate + "|" + params.pitch + "|" + text
        val digest = MessageDigest.getInstance("SHA-256").digest(raw.toByteArray(Charsets.UTF_8))
        val hex = StringBuilder(digest.size * 2)
        for (b in digest) hex.append(String.format(Locale.US, "%02x", b.toInt() and 0xFF))
        return File(cacheDir, hex.toString() + ".mp3")
    }

    private fun pruneCache() {
        try {
            val files = cacheDir.listFiles() ?: return
            for (f in files) {
                if (f.name.endsWith(".tmp")) f.delete()
            }
            val mp3s = files.filter { it.name.endsWith(".mp3") && it.exists() }
            var total = mp3s.sumOf { it.length() }
            val maxBytes = 150L * 1024L * 1024L
            val targetBytes = 100L * 1024L * 1024L
            if (total <= maxBytes) return
            for (f in mp3s.sortedBy { it.lastModified() }) {
                if (total <= targetBytes) break
                val size = f.length()
                if (f.delete()) total -= size
            }
        } catch (e: Exception) {
            // Cache cleanup is best effort only.
        }
    }

    /** Deletes all downloaded sentence audio. */
    fun clearCache() {
        ioScope.launch {
            try {
                cacheDir.listFiles()?.forEach { it.delete() }
            } catch (e: Exception) {
                // ignore
            }
        }
    }

    // ------------------------------------------------------------------ playback

    private suspend fun play(file: File, id: String, gen: Int, speed: Float, callback: Callback) {
        suspendCancellableCoroutine<Unit> { cont ->
            val mp = MediaPlayer()
            player = mp

            fun finish() {
                releasePlayer(mp)
                if (cont.isActive) cont.resume(Unit)
            }

            try {
                mp.setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                mp.setWakeMode(appContext, PowerManager.PARTIAL_WAKE_LOCK)
                mp.setDataSource(file.absolutePath)
                mp.setOnPreparedListener { prepared ->
                    if (gen != generation) {
                        finish()
                    } else {
                        prepared.start()
                        if (speed != 1f) {
                            try {
                                prepared.playbackParams = prepared.playbackParams.setSpeed(speed)
                            } catch (e: Exception) {
                                // Speed above the service maximum is optional; play at normal speed.
                            }
                        }
                        callback.onStart(id)
                    }
                }
                mp.setOnCompletionListener {
                    finish()
                    if (gen == generation) callback.onDone(id)
                }
                mp.setOnErrorListener { _, what, extra ->
                    finish()
                    if (gen == generation) callback.onError(id, "Audio playback error ($what/$extra)")
                    true
                }
                mp.prepareAsync()
            } catch (e: Exception) {
                finish()
                // A broken cached file would fail forever, so remove it.
                file.delete()
                if (gen == generation) callback.onError(id, e.message ?: "Could not play audio")
            }
        }
    }

    private fun releasePlayer(mp: MediaPlayer) {
        try {
            mp.setOnPreparedListener(null)
            mp.setOnCompletionListener(null)
            mp.setOnErrorListener(null)
            mp.reset()
        } catch (e: Exception) {
            // already released
        }
        try {
            mp.release()
        } catch (e: Exception) {
            // already released
        }
        if (player === mp) player = null
    }

    // ------------------------------------------------------------------ rate and pitch

    /** App rate 0.5x..2.5x -> Edge percent. The service handles up to +100%; the rest is done by the player. */
    private fun rateString(rate: Float): String {
        val percent = ((rate - 1f) * 100f).roundToInt().coerceIn(-50, 100)
        return String.format(Locale.US, "%+d%%", percent)
    }

    /** App pitch 0.5..2.0 (1.0 = normal) -> Edge Hertz offset. */
    private fun pitchString(pitch: Float): String {
        val hertz = ((pitch - 1f) * 50f).roundToInt().coerceIn(-100, 100)
        return String.format(Locale.US, "%+dHz", hertz)
    }
}
