package com.example.audio

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.Player
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import android.os.Bundle
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
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

    @Suppress("DEPRECATION")
    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    override fun onCreate() {
        super.onCreate()
        controller = (application as MtlVaultApplication).audioController

        // Android requires a service started with startForegroundService() to show a notification
        // within a few seconds. The channel is made here and the same id is given to Media3's own
        // notification, so the real player notification replaces the temporary one below.
        createNotificationChannel()
        setMediaNotificationProvider(
            DefaultMediaNotificationProvider.Builder(this)
                .setNotificationId(NOTIFICATION_ID)
                .setChannelId(CHANNEL_ID)
                .build()
                .apply { setSmallIcon(R.drawable.ic_notification_book) }
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
            .setCallback(sessionCallback)
            .setCustomLayout(ImmutableList.of(closeButton()))
            .build()
            .also {
                // The app drives the player directly and never binds a MediaController, so
                // onGetSession() is never called. Without this, Media3 does not know the session
                // belongs to this service and never replaces the plain starting notification with
                // the real media notification (play/pause, chapters, lock-screen controls).
                addSession(it)
            }

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        watchForShake()
        watchPlayerState()
    }

    /**
     * The app starts this service with a plain intent when playback begins. Promote it to a
     * foreground service at once, with a simple notification. Without this, Android ends the app
     * with "did not call startForeground" whenever Media3 has not shown its own notification yet.
     */
    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent != null && intent.action == null) {
            val session = mediaSession
            val exo = session?.player
            if (session != null && controller.state.value.hasBook && exo != null && exo.playWhenReady) {
                // Playback has already begun, so Media3 can post its real media notification
                // (play/pause, chapters, lock screen) and move the service to the foreground
                // itself. The plain notification is skipped so the two never swap back and forth.
                onUpdateNotification(session, true)
            } else {
                // Nothing to show yet: still satisfy Android's "call startForeground quickly" rule.
                showStartingNotification()
            }
        }
        return super.onStartCommand(intent, flags, startId)
    }

    private fun showStartingNotification() {
        val title = controller.state.value.title.ifBlank { "Audiobook" }
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_book)
            .setContentTitle(title)
            .setContentText("Audiobook player")
            .setOngoing(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()
        try {
            if (Build.VERSION.SDK_INT >= 29) {
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            com.example.logging.AppLogger.e("AudioPlaybackService", "Could not start foreground: ${e.message}", e)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < 26) return
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Audiobook playback", NotificationManager.IMPORTANCE_LOW)
            )
        }
    }

    /** Takes the temporary notification away when the book is closed or cannot be played. */
    private fun watchPlayerState() {
        scope.launch {
            controller.state
                .map { if (!it.hasBook) 0 else if (it.status == PlaybackStatus.ERROR) 1 else 2 }
                .distinctUntilChanged()
                .collect { kind ->
                    when (kind) {
                        0 -> {
                            stopForeground(STOP_FOREGROUND_REMOVE)
                            stopSelf()
                        }
                        1 -> stopForeground(STOP_FOREGROUND_REMOVE)
                        else -> Unit
                    }
                }
        }
    }

    // ============================================================ Close button

    /** The X in the notification, with the same icon as the voice reader's notification. */
    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    private fun closeButton(): CommandButton =
        CommandButton.Builder(CommandButton.ICON_UNDEFINED)
            .setDisplayName("Close")
            .setSessionCommand(SessionCommand(ACTION_CLOSE, Bundle.EMPTY))
            .setCustomIconResId(R.drawable.ic_tts_close)
            .build()

    private val sessionCallback = object : MediaSession.Callback {
        /** Lets the notification's controller use the custom Close command. */
        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo
        ): MediaSession.ConnectionResult {
            val commands = MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon()
                .add(SessionCommand(ACTION_CLOSE, Bundle.EMPTY))
                .build()
            return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailableSessionCommands(commands)
                .build()
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle
        ): ListenableFuture<SessionResult> {
            if (customCommand.customAction == ACTION_CLOSE) {
                // Saves the place, stops the book and hides the notification. The state watcher
                // then shuts this service down.
                this@AudioPlaybackService.controller.close()
                return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
            }
            return super.onCustomCommand(session, controller, customCommand, args)
        }
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
        mediaSession?.run {
            removeSession(this)
            release()
        }
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

        // Android 12-16 system controls always show previous / next buttons, so those buttons
        // are made to skip by time instead of chapter: previous = skip back (15s by default),
        // next = skip forward (30s by default), using the skip times from the app's settings.
        private val skipButtons = setOf(
            Player.COMMAND_SEEK_TO_NEXT,
            Player.COMMAND_SEEK_TO_PREVIOUS,
            Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
            Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM
        )

        override fun getAvailableCommands(): Player.Commands {
            val builder = super.getAvailableCommands().buildUpon()
            if (controller.state.value.hasBook) {
                skipButtons.forEach { builder.add(it) }
                builder.add(Player.COMMAND_SEEK_BACK)
                builder.add(Player.COMMAND_SEEK_FORWARD)
            }
            return builder.build()
        }

        override fun isCommandAvailable(command: Int): Boolean =
            if ((command in skipButtons || command == Player.COMMAND_SEEK_BACK ||
                    command == Player.COMMAND_SEEK_FORWARD) && controller.state.value.hasBook) true
            else super.isCommandAvailable(command)

        override fun hasNextMediaItem(): Boolean = controller.state.value.hasBook
        override fun hasPreviousMediaItem(): Boolean = controller.state.value.hasBook

        override fun seekToNext() = controller.skipForward()
        override fun seekToNextMediaItem() = controller.skipForward()
        override fun seekToPrevious() = controller.skipBack()
        override fun seekToPreviousMediaItem() = controller.skipBack()

        override fun seekBack() = controller.skipBack()
        override fun seekForward() = controller.skipForward()
        override fun getSeekBackIncrement(): Long = controller.settings.value.skipBackSeconds * 1000L
        override fun getSeekForwardIncrement(): Long = controller.settings.value.skipForwardSeconds * 1000L

        /** Also what happens when the notification is swiped away: close the book, don't just stop the player. */
        override fun stop() = controller.close()

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
        const val ACTION_CLOSE = "com.example.audio.CLOSE"
        const val CHANNEL_ID = "mtl_vault_audiobook_playback"
        const val NOTIFICATION_ID = 7421

        /** A shake counts when the phone is pushed with about three times the force of gravity. */
        const val SHAKE_FORCE = 3.0f
        const val SHAKE_COOLDOWN_MS = 1_500L
    }
}
