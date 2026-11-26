package com.example.audio

import com.example.data.database.entities.NovelEntity
import com.example.tts.TtsPlaybackInfo
import com.example.tts.TtsState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackCoordinatorTest {

    @Test
    fun resolveReturnsNoneWhenNothingIsLoaded() {
        val state = resolveNowPlayingState(
            tts = TtsPlaybackInfo(),
            ttsNovel = null,
            ttsRate = 1.0f,
            audio = AudioPlayerUiState(),
            lastActiveSource = NowPlayingSource.NONE
        )
        assertEquals(NowPlayingSource.NONE, state.source)
        assertFalse(state.isActive)
    }

    @Test
    fun resolvePrefersPlayingTtsOverPausedAudiobook() {
        val novel = NovelEntity(id = "n1", title = "Jade Chronicles", coverUri = "file:///cover.jpg")
        val tts = TtsPlaybackInfo(
            state = TtsState.PLAYING,
            novelId = "n1",
            chapterId = "c5",
            chapterTitle = "Chapter 5",
            totalChunks = 20,
            currentChunkIndex = 4
        )
        val audio = AudioPlayerUiState(
            bookId = "b1",
            title = "Audio Saga",
            status = PlaybackStatus.PAUSED,
            bookPositionMs = 50_000L,
            bookDurationMs = 100_000L
        )

        val state = resolveNowPlayingState(
            tts = tts,
            ttsNovel = novel,
            ttsRate = 1.25f,
            audio = audio,
            lastActiveSource = NowPlayingSource.AUDIOBOOK
        )

        assertEquals(NowPlayingSource.TTS, state.source)
        assertTrue(state.isActive)
        assertTrue(state.isPlaying)
        assertEquals("Jade Chronicles", state.title)
        assertEquals("file:///cover.jpg", state.coverUri)
        assertEquals("1.25x", state.speedLabel)
        assertEquals(0.25f, state.progress, 0.001f)
        assertEquals("n1", state.novelId)
        assertEquals("c5", state.chapterId)
    }

    @Test
    fun resolvePrefersPlayingAudiobookOverPausedTts() {
        val tts = TtsPlaybackInfo(
            state = TtsState.PAUSED,
            novelId = "n1",
            chapterId = "c5",
            chapterTitle = "Chapter 5",
            totalChunks = 10,
            currentChunkIndex = 2
        )
        val audio = AudioPlayerUiState(
            bookId = "b1",
            title = "Audio Saga",
            chapterTitle = "Part 2",
            status = PlaybackStatus.PLAYING,
            bookPositionMs = 30_000L,
            bookDurationMs = 60_000L,
            speed = 1.5f
        )

        val state = resolveNowPlayingState(
            tts = tts,
            ttsNovel = null,
            ttsRate = 1.0f,
            audio = audio,
            lastActiveSource = NowPlayingSource.TTS
        )

        assertEquals(NowPlayingSource.AUDIOBOOK, state.source)
        assertTrue(state.isPlaying)
        assertEquals("Audio Saga", state.title)
        assertEquals("Part 2", state.subtitle)
        assertEquals("1.5x", state.speedLabel)
        assertEquals(0.5f, state.progress, 0.001f)
    }

    @Test
    fun resolveUsesLastActiveSourceWhenBothArePaused() {
        val tts = TtsPlaybackInfo(
            state = TtsState.PAUSED,
            novelId = "n1",
            chapterId = "c5",
            chapterTitle = "Chapter 5",
            totalChunks = 10,
            currentChunkIndex = 0
        )
        val audio = AudioPlayerUiState(
            bookId = "b1",
            title = "Audio Saga",
            status = PlaybackStatus.PAUSED
        )

        val ttsPreferred = resolveNowPlayingState(
            tts = tts,
            ttsNovel = NovelEntity(id = "n1", title = "Novel A"),
            ttsRate = 1.0f,
            audio = audio,
            lastActiveSource = NowPlayingSource.TTS
        )
        assertEquals(NowPlayingSource.TTS, ttsPreferred.source)

        val audioPreferred = resolveNowPlayingState(
            tts = tts,
            ttsNovel = NovelEntity(id = "n1", title = "Novel A"),
            ttsRate = 1.0f,
            audio = audio,
            lastActiveSource = NowPlayingSource.AUDIOBOOK
        )
        assertEquals(NowPlayingSource.AUDIOBOOK, audioPreferred.source)
    }
}
