package com.example.tts

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.widget.RemoteViews
import com.example.MainActivity
import com.example.MtlVaultApplication
import com.example.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class TtsPlaybackService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())
    private var observerJob: Job? = null
    private var mediaSession: MediaSession? = null

    private var isBecomingNoisyRegistered = false
    private val becomingNoisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) {
                val ttsManager = (application as? MtlVaultApplication)?.ttsManager
                ttsManager?.pause()
            }
        }
    }

    private fun registerBecomingNoisy() {
        if (!isBecomingNoisyRegistered) {
            val filter = IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(becomingNoisyReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                registerReceiver(becomingNoisyReceiver, filter)
            }
            isBecomingNoisyRegistered = true
        }
    }

    private fun unregisterBecomingNoisy() {
        if (isBecomingNoisyRegistered) {
            try {
                unregisterReceiver(becomingNoisyReceiver)
            } catch (e: Exception) {
                // ignore
            }
            isBecomingNoisyRegistered = false
        }
    }

    companion object {
        const val CHANNEL_ID = "tts_media_channel"
        const val NOTIFICATION_ID = 1001

        const val CHUNK_UNIT_MS = 10_000L

        const val ACTION_START = "com.example.tts.ACTION_START"
        const val ACTION_PLAY = "com.example.tts.ACTION_PLAY"
        const val ACTION_PAUSE = "com.example.tts.ACTION_PAUSE"
        const val ACTION_TOGGLE = "com.example.tts.ACTION_TOGGLE"
        const val ACTION_PREV = "com.example.tts.ACTION_PREV"
        const val ACTION_NEXT = "com.example.tts.ACTION_NEXT"
        const val ACTION_STOP = "com.example.tts.ACTION_STOP"
        const val ACTION_UPDATE = "com.example.tts.ACTION_UPDATE"

        fun start(context: Context) {
            val intent = Intent(context, TtsPlaybackService::class.java).apply {
                action = ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, TtsPlaybackService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        setupMediaSession()
        observeTtsManager()
    }

    private fun setupMediaSession() {
        val ttsManager = (application as? MtlVaultApplication)?.ttsManager
        mediaSession = MediaSession(this, "MtlVaultMediaSession").apply {
            setCallback(object : MediaSession.Callback() {
                override fun onPlay() {
                    ttsManager?.resume()
                }

                override fun onPause() {
                    ttsManager?.pause()
                }

                override fun onSkipToNext() {
                    ttsManager?.skipToNextChapter()
                }

                override fun onSkipToPrevious() {
                    ttsManager?.skipToPreviousChapter()
                }

                override fun onStop() {
                    ttsManager?.stop()
                    stopForegroundNotification()
                    stopSelf()
                }

                override fun onSeekTo(pos: Long) {
                    val info = ttsManager?.playbackInfo?.value ?: return
                    val totalChunks = info.totalChunks.coerceAtLeast(1)
                    val durationMs = totalChunks * CHUNK_UNIT_MS
                    val targetIndex = ((pos.toDouble() / durationMs.toDouble()) * totalChunks).toInt().coerceIn(0, totalChunks - 1)
                    ttsManager.seekToChunk(targetIndex)
                }

                override fun onCustomAction(action: String, extras: Bundle?) {
                    if (action == ACTION_STOP) {
                        ttsManager?.stop()
                        stopForegroundNotification()
                        stopSelf()
                    }
                }
            })
            isActive = true
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val ttsManager = (application as? MtlVaultApplication)?.ttsManager

        when (intent?.action) {
            ACTION_START -> {
                val info = ttsManager?.playbackInfo?.value ?: TtsPlaybackInfo()
                updateMediaSessionState(info)
                val notification = buildNotification(info)
                startForegroundCompat(notification)
            }
            ACTION_TOGGLE -> {
                val state = ttsManager?.playbackInfo?.value?.state
                if (state == TtsState.PLAYING) {
                    ttsManager.pause()
                } else if (state == TtsState.PAUSED) {
                    ttsManager.resume()
                } else {
                    ttsManager?.resume()
                }
            }
            ACTION_PLAY -> {
                ttsManager?.resume()
            }
            ACTION_PAUSE -> {
                ttsManager?.pause()
            }
            ACTION_PREV -> {
                ttsManager?.skipToPreviousChapter()
            }
            ACTION_NEXT -> {
                ttsManager?.skipToNextChapter()
            }
            ACTION_STOP -> {
                ttsManager?.stop()
                stopForegroundNotification()
                stopSelf()
            }
            ACTION_UPDATE -> {
                ttsManager?.playbackInfo?.value?.let { info ->
                    updateMediaSessionState(info)
                    updateNotification(info)
                }
            }
        }
        return START_NOT_STICKY
    }

    private fun startForegroundCompat(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun observeTtsManager() {
        val ttsManager = (application as? MtlVaultApplication)?.ttsManager ?: return
        observerJob?.cancel()
        observerJob = serviceScope.launch {
            ttsManager.playbackInfo.collectLatest { info ->
                updateMediaSessionState(info)
                if (info.state == TtsState.PLAYING) {
                    registerBecomingNoisy()
                    val notif = buildNotification(info)
                    startForegroundCompat(notif)
                } else if (info.state == TtsState.PAUSED) {
                    unregisterBecomingNoisy()
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        stopForeground(STOP_FOREGROUND_DETACH)
                    } else {
                        @Suppress("DEPRECATION")
                        stopForeground(false)
                    }
                    updateNotification(info)
                } else if (info.state == TtsState.IDLE || info.state == TtsState.ERROR) {
                    unregisterBecomingNoisy()
                    stopForegroundNotification()
                    stopSelf()
                }
            }
        }
    }

    private fun updateMediaSessionState(info: TtsPlaybackInfo) {
        val session = mediaSession ?: return
        val state = when (info.state) {
            TtsState.PLAYING -> PlaybackState.STATE_PLAYING
            TtsState.PAUSED -> PlaybackState.STATE_PAUSED
            TtsState.IDLE -> PlaybackState.STATE_STOPPED
            else -> PlaybackState.STATE_NONE
        }

        val actions = PlaybackState.ACTION_PLAY or
                PlaybackState.ACTION_PAUSE or
                PlaybackState.ACTION_PLAY_PAUSE or
                PlaybackState.ACTION_SKIP_TO_NEXT or
                PlaybackState.ACTION_SKIP_TO_PREVIOUS or
                PlaybackState.ACTION_STOP or
                PlaybackState.ACTION_SEEK_TO

        val customCloseAction = PlaybackState.CustomAction.Builder(
            ACTION_STOP,
            "✕",
            R.drawable.ic_tts_close
        ).build()

        val totalChunks = info.totalChunks.coerceAtLeast(1)
        val durationMs = totalChunks * CHUNK_UNIT_MS
        val positionMs = (info.currentChunkIndex.coerceIn(0, totalChunks - 1)) * CHUNK_UNIT_MS

        // Fixed 0.0f speed stops phantom auto-advance past chapter start
        val playbackState = PlaybackState.Builder()
            .setActions(actions)
            .addCustomAction(customCloseAction)
            .setState(state, positionMs, 0.0f)
            .build()

        session.setPlaybackState(playbackState)

        val title = if (info.chapterTitle.isNotBlank()) info.chapterTitle else "MTL Vault Reader"
        val subtitle = if (info.totalChunks > 0) {
            "Paragraph ${info.currentChunkIndex + 1} of ${info.totalChunks}"
        } else {
            "Audiobook & Novel Reader"
        }

        val metadata = MediaMetadata.Builder()
            .putString(MediaMetadata.METADATA_KEY_TITLE, title)
            .putString(MediaMetadata.METADATA_KEY_ARTIST, "MTL Vault")
            .putString(MediaMetadata.METADATA_KEY_ALBUM, "Novel Audio")
            .putString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE, title)
            .putString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE, subtitle)
            .putLong(MediaMetadata.METADATA_KEY_DURATION, durationMs)
            .build()

        session.setMetadata(metadata)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Audiobook & TTS Playback",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows real Android system media player controls and lock-screen controls"
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun updateNotification(info: TtsPlaybackInfo) {
        val notification = buildNotification(info)
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        manager?.notify(NOTIFICATION_ID, notification)
    }

    private fun buildNotification(info: TtsPlaybackInfo): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("OPEN_READER", true)
            putExtra("NOVEL_ID", info.novelId)
            putExtra("CHAPTER_ID", info.chapterId)
        }
        val openAppPendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val prevPendingIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, TtsPlaybackService::class.java).apply { action = ACTION_PREV },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val togglePendingIntent = PendingIntent.getService(
            this,
            2,
            Intent(this, TtsPlaybackService::class.java).apply { action = ACTION_TOGGLE },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopPendingIntent = PendingIntent.getService(
            this,
            3,
            Intent(this, TtsPlaybackService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val nextPendingIntent = PendingIntent.getService(
            this,
            4,
            Intent(this, TtsPlaybackService::class.java).apply { action = ACTION_NEXT },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val isPlaying = info.state == TtsState.PLAYING
        val title = if (info.chapterTitle.isNotBlank()) info.chapterTitle else "MTL Vault Reader"
        val subtitle = if (info.totalChunks > 0) {
            "Paragraph ${info.currentChunkIndex + 1} of ${info.totalChunks}"
        } else {
            "Audiobook & Novel Reader"
        }

        // Custom RemoteViews matching in-app dark pill design in Image 2
        val customView = RemoteViews(packageName, R.layout.notification_tts_player).apply {
            setTextViewText(R.id.tts_notif_title, title)
            setTextViewText(R.id.tts_notif_subtitle, subtitle)
            setImageViewResource(
                R.id.tts_btn_play_pause,
                if (isPlaying) R.drawable.ic_tts_pause else R.drawable.ic_tts_play
            )
            setOnClickPendingIntent(R.id.tts_btn_prev, prevPendingIntent)
            setOnClickPendingIntent(R.id.tts_btn_play_pause, togglePendingIntent)
            setOnClickPendingIntent(R.id.tts_btn_stop, stopPendingIntent)
            setOnClickPendingIntent(R.id.tts_btn_next, nextPendingIntent)
            setOnClickPendingIntent(R.id.tts_notif_root, openAppPendingIntent)
        }

        val totalChunks = info.totalChunks.coerceAtLeast(1)
        val customBigView = RemoteViews(packageName, R.layout.notification_tts_player_big).apply {
            setTextViewText(R.id.tts_notif_title, title)
            setTextViewText(R.id.tts_notif_subtitle, subtitle)
            val progressPercent = ((info.currentChunkIndex.toFloat() / totalChunks) * 100).toInt()
            setProgressBar(R.id.tts_notif_progress, 100, progressPercent, false)
            setImageViewResource(
                R.id.tts_btn_play_pause,
                if (isPlaying) R.drawable.ic_tts_pause else R.drawable.ic_tts_play
            )
            setOnClickPendingIntent(R.id.tts_btn_prev, prevPendingIntent)
            setOnClickPendingIntent(R.id.tts_btn_play_pause, togglePendingIntent)
            setOnClickPendingIntent(R.id.tts_btn_stop, stopPendingIntent)
            setOnClickPendingIntent(R.id.tts_btn_next, nextPendingIntent)
            setOnClickPendingIntent(R.id.tts_notif_root, openAppPendingIntent)
        }

        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }

        builder
            .setSmallIcon(R.drawable.ic_notification_book)
            .setContentTitle(title)
            .setContentText(subtitle)
            .setContentIntent(openAppPendingIntent)
            .setDeleteIntent(stopPendingIntent)
            .setOngoing(isPlaying)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setCustomContentView(customView)
            .setCustomBigContentView(customBigView)

        // Attach system MediaStyle with active MediaSession token
        val sessionToken = mediaSession?.sessionToken
        if (sessionToken != null) {
            val mediaStyle = Notification.MediaStyle()
                .setMediaSession(sessionToken)
                .setShowActionsInCompactView(0, 1, 2)
            builder.style = mediaStyle
        }

        // Actions: 0 = Prev (⏮), 1 = Play/Pause (⏯), 2 = Next (⏭), 3 = Close (✕)
        builder.addAction(
            Notification.Action.Builder(
                R.drawable.ic_tts_prev,
                "Previous",
                prevPendingIntent
            ).build()
        )

        val playPauseIcon = if (isPlaying) R.drawable.ic_tts_pause else R.drawable.ic_tts_play
        val playPauseTitle = if (isPlaying) "Pause" else "Play"
        builder.addAction(
            Notification.Action.Builder(
                playPauseIcon,
                playPauseTitle,
                togglePendingIntent
            ).build()
        )

        builder.addAction(
            Notification.Action.Builder(
                R.drawable.ic_tts_next,
                "Next",
                nextPendingIntent
            ).build()
        )

        builder.addAction(
            Notification.Action.Builder(
                R.drawable.ic_tts_close,
                "✕",
                stopPendingIntent
            ).build()
        )

        return builder.build()
    }

    private fun stopForegroundNotification() {
        mediaSession?.isActive = false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        manager?.cancel(NOTIFICATION_ID)
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterBecomingNoisy()
        observerJob?.cancel()
        mediaSession?.isActive = false
        mediaSession?.release()
        mediaSession = null
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
