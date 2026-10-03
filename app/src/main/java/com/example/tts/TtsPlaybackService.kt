package com.example.tts

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.MtlVaultApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class TtsPlaybackService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())
    private var observerJob: Job? = null

    companion object {
        const val CHANNEL_ID = "tts_playback_channel"
        const val NOTIFICATION_ID = 1001

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
        observeTtsManager()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val ttsManager = (application as? MtlVaultApplication)?.ttsManager

        when (intent?.action) {
            ACTION_START -> {
                val notification = buildNotification(ttsManager?.playbackInfo?.value ?: TtsPlaybackInfo())
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
                } else {
                    startForeground(NOTIFICATION_ID, notification)
                }
            }
            ACTION_TOGGLE -> {
                val state = ttsManager?.playbackInfo?.value?.state
                if (state == TtsState.PLAYING) {
                    ttsManager.pause()
                } else if (state == TtsState.PAUSED) {
                    ttsManager.resume()
                }
            }
            ACTION_PLAY -> {
                ttsManager?.resume()
            }
            ACTION_PAUSE -> {
                ttsManager?.pause()
            }
            ACTION_PREV -> {
                ttsManager?.previousChunk()
            }
            ACTION_NEXT -> {
                ttsManager?.nextChunk()
            }
            ACTION_STOP -> {
                ttsManager?.stop()
                stopForegroundNotification()
                stopSelf()
            }
            ACTION_UPDATE -> {
                ttsManager?.playbackInfo?.value?.let { updateNotification(it) }
            }
        }
        return START_NOT_STICKY
    }

    private fun observeTtsManager() {
        val ttsManager = (application as? MtlVaultApplication)?.ttsManager ?: return
        observerJob?.cancel()
        observerJob = serviceScope.launch {
            ttsManager.playbackInfo.collectLatest { info ->
                if (info.state == TtsState.PLAYING || info.state == TtsState.PAUSED) {
                    updateNotification(info)
                } else if (info.state == TtsState.IDLE || info.state == TtsState.ERROR) {
                    stopForegroundNotification()
                    stopSelf()
                }
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "TTS Playback",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows playback controls for Text-to-Speech"
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

        val nextPendingIntent = PendingIntent.getService(
            this,
            3,
            Intent(this, TtsPlaybackService::class.java).apply { action = ACTION_NEXT },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopPendingIntent = PendingIntent.getService(
            this,
            4,
            Intent(this, TtsPlaybackService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val isPlaying = info.state == TtsState.PLAYING
        val title = if (info.chapterTitle.isNotBlank()) info.chapterTitle else "MTL Vault Reader"
        val subtitle = if (info.totalChunks > 0) {
            "Paragraph ${info.currentChunkIndex + 1} of ${info.totalChunks}"
        } else {
            "Reading novel"
        }

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle(title)
            .setContentText(subtitle)
            .setContentIntent(openAppPendingIntent)
            .setOngoing(isPlaying)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(android.R.drawable.ic_media_previous, "Prev", prevPendingIntent)
            .addAction(
                if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
                if (isPlaying) "Pause" else "Play",
                togglePendingIntent
            )
            .addAction(android.R.drawable.ic_media_next, "Next", nextPendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", stopPendingIntent)

        return builder.build()
    }

    private fun stopForegroundNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        observerJob?.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
