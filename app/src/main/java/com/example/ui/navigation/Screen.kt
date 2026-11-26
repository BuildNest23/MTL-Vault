package com.example.ui.navigation

import android.net.Uri

sealed class Screen(val route: String) {
    object Library : Screen("library")
    object Stats : Screen("stats")
    object Settings : Screen("settings")
    object Audiobooks : Screen("audiobooks")
    object AddAudiobook : Screen("audiobooks/add")

    /** The full audiobook player. Opened from the shelf with autoPlay = true, from the mini-player with false. */
    object AudioPlayer : Screen("audioplayer/{bookId}?autoPlay={autoPlay}") {
        fun createRoute(bookId: String, autoPlay: Boolean = false) =
            "audioplayer/${encode(bookId)}?autoPlay=$autoPlay"
    }

    object NovelDetail : Screen("novel/{novelId}") {
        fun createRoute(novelId: String) = "novel/${encode(novelId)}"
    }

    object Reader : Screen("reader/{novelId}/{chapterId}?autoPlay={autoPlay}&paragraph={paragraph}") {
        fun createRoute(
            novelId: String,
            chapterId: String,
            autoPlay: Boolean = false,
            paragraph: Int = -1
        ) = "reader/${encode(novelId)}/${encode(chapterId)}?autoPlay=$autoPlay&paragraph=$paragraph"
    }

    object ChapterEditor : Screen("editor/{novelId}?chapterId={chapterId}") {
        fun createRoute(novelId: String, chapterId: String? = null): String {
            val encodedNovelId = encode(novelId)
            return if (chapterId != null) {
                "editor/$encodedNovelId?chapterId=${encode(chapterId)}"
            } else {
                "editor/$encodedNovelId"
            }
        }
    }

    object Replacements : Screen("replacements/{novelId}") {
        fun createRoute(novelId: String) = "replacements/${encode(novelId)}"
    }

    object Voices : Screen("voices/{novelId}") {
        fun createRoute(novelId: String) = "voices/${encode(novelId)}"
    }

    companion object {
        private fun encode(value: String): String = Uri.encode(value)
    }
}
