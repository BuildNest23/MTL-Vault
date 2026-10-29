package com.example.ui.navigation

sealed class Screen(val route: String) {
    object Library : Screen("library")
    object Stats : Screen("stats")
    object Settings : Screen("settings")
    object Audiobooks : Screen("audiobooks")
    object AddAudiobook : Screen("audiobooks/add")

    /** The full audiobook player. Opened from the shelf with autoPlay = true, from the mini-player with false. */
    object AudioPlayer : Screen("audioplayer/{bookId}?autoPlay={autoPlay}") {
        fun createRoute(bookId: String, autoPlay: Boolean = false) =
            "audioplayer/$bookId?autoPlay=$autoPlay"
    }

    object NovelDetail : Screen("novel/{novelId}") {
        fun createRoute(novelId: String) = "novel/$novelId"
    }

    object Reader : Screen("reader/{novelId}/{chapterId}?autoPlay={autoPlay}") {
        fun createRoute(novelId: String, chapterId: String, autoPlay: Boolean = false) =
            "reader/$novelId/$chapterId?autoPlay=$autoPlay"
    }

    object ChapterEditor : Screen("editor/{novelId}?chapterId={chapterId}") {
        fun createRoute(novelId: String, chapterId: String? = null): String {
            return if (chapterId != null) "editor/$novelId?chapterId=$chapterId" else "editor/$novelId"
        }
    }

    object Replacements : Screen("replacements/{novelId}") {
        fun createRoute(novelId: String) = "replacements/$novelId"
    }
}
