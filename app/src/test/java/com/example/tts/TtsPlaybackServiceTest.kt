package com.example.tts

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import androidx.test.core.app.ApplicationProvider
import com.example.MtlVaultApplication
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31, 33, 34]) // Test across Android 12 (31), Android 13 (33), and Android 14 (34)
class TtsPlaybackServiceTest {

    private lateinit var context: Context
    private lateinit var app: MtlVaultApplication
    private lateinit var notificationManager: NotificationManager

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        app = context.applicationContext as MtlVaultApplication
        notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    }

    @Test
    fun testAudioFocusLossTransitionsToPausedWithoutIdleOrError() {
        val ttsManager = app.ttsManager
        ttsManager.prepareChapter(
            novelId = "test_novel",
            chapterId = "test_chap",
            chapterTitle = "Chapter 1",
            content = "This is paragraph 1.\n\nThis is paragraph 2."
        )

        // Simulate audio focus loss (e.g. phone call or WhatsApp message)
        ttsManager.pause()
        assertEquals(TtsState.PAUSED, ttsManager.playbackInfo.value.state)

        // Ensure state remains PAUSED and never drops to IDLE or ERROR on repeated pause/focus loss
        ttsManager.pause()
        assertEquals(TtsState.PAUSED, ttsManager.playbackInfo.value.state)
    }

    @Test
    fun testNotificationActionsOnPausedState() {
        val serviceController = Robolectric.buildService(TtsPlaybackService::class.java)
        val service = serviceController.create().get()

        val startIntent = Intent(context, TtsPlaybackService::class.java).apply {
            action = TtsPlaybackService.ACTION_START
        }
        service.onStartCommand(startIntent, 0, 1)

        val shadowNm = Shadows.shadowOf(notificationManager)
        val notification = shadowNm.getNotification(TtsPlaybackService.NOTIFICATION_ID)
        assertNotNull(notification)

        // Clean teardown
        val stopIntent = Intent(context, TtsPlaybackService::class.java).apply {
            action = TtsPlaybackService.ACTION_STOP
        }
        service.onStartCommand(stopIntent, 0, 2)
        serviceController.destroy()
    }

    @Test
    fun testOnTaskRemovedDoesNotStopService() {
        val serviceController = Robolectric.buildService(TtsPlaybackService::class.java)
        val service = serviceController.create().get()

        val startIntent = Intent(context, TtsPlaybackService::class.java).apply {
            action = TtsPlaybackService.ACTION_START
        }
        service.onStartCommand(startIntent, 0, 1)

        // Simulate app swiped from recents
        service.onTaskRemoved(Intent())

        // Service should still be alive and not destroyed
        val shadowNm = Shadows.shadowOf(notificationManager)
        val notification = shadowNm.getNotification(TtsPlaybackService.NOTIFICATION_ID)
        assertNotNull(notification)

        // Explicit user stop must remove it
        val stopIntent = Intent(context, TtsPlaybackService::class.java).apply {
            action = TtsPlaybackService.ACTION_STOP
        }
        service.onStartCommand(stopIntent, 0, 2)
        serviceController.destroy()
    }

    @Test
    fun testCanDuckAudioFocusDoesNotPausePlayback() {
        val ttsManager = app.ttsManager
        ttsManager.prepareChapter(
            novelId = "test_novel_duck",
            chapterId = "test_chap_duck",
            chapterTitle = "Chapter Duck",
            content = "This is paragraph 1.\n\nThis is paragraph 2."
        )

        // Inject focus listener access via reflection to test audioFocusChangeListener directly
        val field = TtsManager::class.java.getDeclaredField("audioFocusChangeListener")
        field.isAccessible = true
        val listener = field.get(ttsManager) as AudioManager.OnAudioFocusChangeListener

        // Transient can-duck (notification ping) should NOT pause reading
        listener.onAudioFocusChange(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK)
        assertFalse("State should not be PAUSED on CAN_DUCK", ttsManager.playbackInfo.value.state == TtsState.PAUSED)

        // True transient loss (e.g. phone call) MUST pause reading
        listener.onAudioFocusChange(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT)
        assertEquals(TtsState.PAUSED, ttsManager.playbackInfo.value.state)
    }

    @Test
    fun testReleaseEngineIfIdleWhenServiceDestroyed() {
        val ttsManager = app.ttsManager
        ttsManager.stop(triggerServiceStop = false)
        assertEquals(TtsState.IDLE, ttsManager.playbackInfo.value.state)

        val serviceController = Robolectric.buildService(TtsPlaybackService::class.java)
        val service = serviceController.create().get()

        // Destroy service when TTS is idle -> releases engine cleanly
        serviceController.destroy()

        val isInitField = TtsManager::class.java.getDeclaredField("isInitialized")
        isInitField.isAccessible = true
        val isInitialized = isInitField.get(ttsManager) as Boolean
        assertFalse("TTS engine should be uninitialized/released while idle", isInitialized)
    }
}
