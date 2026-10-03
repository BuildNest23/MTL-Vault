package com.example.ui.navigation

sealed class Screen(val route: String) {
    object Library : Screen("library")
    object Stats : Screen("stats")
    object Settings : Screen("settings")

    object NovelDetail : Screen("novel/{novelId}") {
        fun createRoute(novelId: String) = "novel/$novelId"
    }

    object Reader : Screen("reader/{novelId}/{chapterId}") {
        fun createRoute(novelId: String, chapterId: String) = "reader/$novelId/$chapterId"
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
