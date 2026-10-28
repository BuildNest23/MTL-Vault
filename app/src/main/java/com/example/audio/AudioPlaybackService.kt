package com.example.audio

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.SystemClock
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.Player
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.example.MainActivity
import com.example.MtlVaultApplication
import com.example.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlin.math.sqrt

/**
 * The background part of the audiobook player.
 *
 * While a book plays, this service keeps the app alive with the screen off and shows the
 * notification with play/pause and next/previous, the lock-screen controls, and handles
 * headset and Bluetooth buttons. It does not play anything itself: the ExoPlayer belongs to
 * [AudioPlayerController], and this service only publishes that player to the system through
 * a media session.
 *
 * The buttons are adjusted to suit audiobooks (see [ChapterPlayer]): next and previous move
 * by chapter, the back and forward keys use your skip times, and pressing play after a break
 * applies smart rewind.
 *
 * It also listens for a shake while a sleep timer is running, to add time to the timer.
 */
class AudioPlaybackService : MediaSessionService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var mediaSession: MediaSession? = null
    private var sensorManager: SensorManager? = null
    private var shakeRegistered = false

    private lateinit var controller: AudioPlayerController

    override fun onCreate() {
        super.onCreate()
        controller = (application as MtlVaultApplication).audioController

        setMediaNotificationProvider(
            DefaultMediaNotificationProvider.Builder(this).build().apply {
                setSmallIcon(R.drawable.ic_notification_book)
            }
        )

        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        mediaSession = MediaSession.Builder(this, ChapterPlayer(controller))
            .setId(SESSION_ID)
            .setSessionActivity(openApp)
            .build()

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        watchForShake()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    /** Swiping the app away keeps a playing book going, and shuts the service down if nothing is playing. */
    override fun onTaskRemoved(rootIntent: Intent?) {
        val exo = mediaSession?.player
        if (exo == null || !exo.playWhenReady || exo.mediaItemCount == 0) stopSelf()
    }

    override fun onDestroy() {
        unregisterShake()
        scope.cancel()
        // Only the session is released here. The player belongs to AudioPlayerController.
        mediaSession?.release()
        mediaSession = null
        super.onDestroy()
    }

    // ============================================================ Audiobook buttons

    /**
     * Wraps the ExoPlayer so the system's buttons act on chapters and go through the
     * controller. ExoPlayer on its own would treat "next" as "next file", which does nothing
     * for a book kept in one file.
     */
    private class ChapterPlayer(private val controller: AudioPlayerController) :
        ForwardingPlayer(controller.player) {

        private val chapterCommands = setOf(
            Player.COMMAND_SEEK_TO_NEXT,
            Player.COMMAND_SEEK_TO_PREVIOUS,
            Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
            Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM
        )

        override fun getAvailableCommands(): Player.Commands {
            val builder = super.getAvailableCommands().buildUpon()
            if (controller.state.value.hasBook) chapterCommands.forEach { builder.add(it) }
            return builder.build()
        }

        override fun isCommandAvailable(command: Int): Boolean =
            if (command in chapterCommands && controller.state.value.hasBook) true
            else super.isCommandAvailable(command)

        override fun hasNextMediaItem(): Boolean = controller.state.value.hasNextChapter
        override fun hasPreviousMediaItem(): Boolean = controller.state.value.hasBook

        override fun seekToNext() = controller.nextChapter()
        override fun seekToNextMediaItem() = controller.nextChapter()
        override fun seekToPrevious() = controller.previousChapter()
        override fun seekToPreviousMediaItem() = controller.previousChapter()

        override fun seekBack() = controller.skipBack()
        override fun seekForward() = controller.skipForward()
        override fun getSeekBackIncrement(): Long = controller.settings.value.skipBackSeconds * 1000L
        override fun getSeekForwardIncrement(): Long = controller.settings.value.skipForwardSeconds * 1000L

        override fun play() = controller.play()
        override fun pause() = controller.pause()
        override fun setPlayWhenReady(playWhenReady: Boolean) {
            if (playWhenReady) controller.play() else controller.pause()
        }
    }

    // ============================================================ Shake to extend

    private val shakeListener = object : SensorEventListener {
        private var lastShakeAt = 0L

        override fun onSensorChanged(event: SensorEvent) {
            val x = event.values[0]
            val y = event.values[1]
            val z = event.values[2]
            val force = sqrt(x * x + y * y + z * z) / SensorManager.GRAVITY_EARTH
            val now = SystemClock.elapsedRealtime()
            if (force > SHAKE_FORCE && now - lastShakeAt > SHAKE_COOLDOWN_MS) {
                lastShakeAt = now
                controller.extendSleepTimer()
            }
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
    }

    /** Listens to the motion sensor only while a book plays with a sleep timer running and the option is on. */
    private fun watchForShake() {
        scope.launch {
            combine(
                controller.state.map { it.isPlaying && it.sleep.kind != SleepKind.OFF },
                controller.settings.map { it.shakeToExtendSleep }
            ) { timerRunning, shakeOn -> timerRunning && shakeOn }
                .distinctUntilChanged()
                .collect { wanted -> if (wanted) registerShake() else unregisterShake() }
        }
    }

    private fun registerShake() {
        if (shakeRegistered) return
        val manager = sensorManager ?: return
        val sensor = manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) ?: return
        shakeRegistered = manager.registerListener(shakeListener, sensor, SensorManager.SENSOR_DELAY_UI)
    }

    private fun unregisterShake() {
        if (!shakeRegistered) return
        sensorManager?.unregisterListener(shakeListener)
        shakeRegistered = false
    }

    private companion object {
        const val SESSION_ID = "mtl_vault_audiobook"

        /** A shake counts when the phone is pushed with about three times the force of gravity. */
        const val SHAKE_FORCE = 3.0f
        const val SHAKE_COOLDOWN_MS = 1_500L
    }
}
