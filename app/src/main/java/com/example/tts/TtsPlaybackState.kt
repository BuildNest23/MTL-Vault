package com.example.tts

enum class TtsState {
    IDLE,
    INITIALIZING,
    PLAYING,
    PAUSED,
    ERROR
}

data class TtsPlaybackInfo(
    val state: TtsState = TtsState.IDLE,
    val novelId: String? = null,
    val chapterId: String? = null,
    val chapterTitle: String = "",
    val totalChunks: Int = 0,
    val currentChunkIndex: Int = 0,
    val errorMessage: String? = null
)
