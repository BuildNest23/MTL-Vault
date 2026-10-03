package com.example.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.MtlVaultApplication
import com.example.ui.components.MtlBottomBar
import com.example.ui.screens.detail.NovelDetailScreen
import com.example.ui.screens.detail.NovelDetailViewModel
import com.example.ui.screens.editor.ChapterEditorScreen
import com.example.ui.screens.editor.ChapterEditorViewModel
import com.example.ui.screens.library.LibraryScreen
import com.example.ui.screens.library.LibraryViewModel
import com.example.ui.screens.reader.ReaderScreen
import com.example.ui.screens.reader.ReaderViewModel
import com.example.ui.screens.replacements.ReplacementsScreen
import com.example.ui.screens.replacements.ReplacementsViewModel
import com.example.ui.screens.stats.StatsScreen
import com.example.ui.screens.stats.StatsViewModel
import com.example.ui.screens.settings.SettingsScreen
import com.example.ui.screens.settings.SettingsViewModel

@Composable
fun MtlVaultNavGraph(
    application: MtlVaultApplication
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val showBottomBar = currentRoute in listOf(
        Screen.Library.route,
        Screen.Stats.route,
        Screen.Settings.route
    )

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                MtlBottomBar(
                    currentRoute = currentRoute,
                    onNavigate = { route ->
                        navController.navigate(route) {
                            popUpTo(Screen.Library.route) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Library.route,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Tab 1: Library
            composable(Screen.Library.route) {
                val viewModel = remember {
                    LibraryViewModel(application.novelRepository)
                }
                LibraryScreen(
                    viewModel = viewModel,
                    onOpenNovel = { novelId ->
                        navController.navigate(Screen.NovelDetail.createRoute(novelId))
                    }
                )
            }

            // Tab 2: Stats
            composable(Screen.Stats.route) {
                val viewModel = remember {
                    StatsViewModel(application.novelRepository)
                }
                StatsScreen(viewModel = viewModel)
            }

            // Tab 3: Settings
            composable(Screen.Settings.route) {
                val viewModel = remember {
                    SettingsViewModel(
                        repository = application.novelRepository,
                        settingsDataStore = application.settingsDataStore
                    )
                }
                SettingsScreen(viewModel = viewModel)
            }

            // Subview: Novel Detail
            composable(
                route = Screen.NovelDetail.route,
                arguments = listOf(navArgument("novelId") { type = NavType.StringType })
            ) { backStackEntry ->
                val novelId = backStackEntry.arguments?.getString("novelId") ?: ""
                val viewModel = remember(novelId) {
                    NovelDetailViewModel(novelId, application.novelRepository)
                }
                NovelDetailScreen(
                    viewModel = viewModel,
                    onBackClick = { navController.popBackStack() },
                    onOpenReader = { chapterId ->
                        navController.navigate(Screen.Reader.createRoute(novelId, chapterId))
                    },
                    onOpenEditor = { chapterId ->
                        navController.navigate(Screen.ChapterEditor.createRoute(novelId, chapterId))
                    },
                    onOpenReplacements = {
                        navController.navigate(Screen.Replacements.createRoute(novelId))
                    }
                )
            }

            // Subview: Reader
            composable(
                route = Screen.Reader.route,
                arguments = listOf(
                    navArgument("novelId") { type = NavType.StringType },
                    navArgument("chapterId") { type = NavType.StringType }
                )
            ) { backStackEntry ->
                val novelId = backStackEntry.arguments?.getString("novelId") ?: ""
                val chapterId = backStackEntry.arguments?.getString("chapterId") ?: ""
                val viewModel = remember(novelId, chapterId) {
                    ReaderViewModel(
                        novelId = novelId,
                        initialChapterId = chapterId,
                        repository = application.novelRepository,
                        settingsDataStore = application.settingsDataStore,
                        ttsManager = application.ttsManager
                    )
                }
                ReaderScreen(
                    viewModel = viewModel,
                    onBackClick = { navController.popBackStack() },
                    onOpenReplacements = {
                        navController.navigate(Screen.Replacements.createRoute(novelId))
                    }
                )
            }

            // Subview: Chapter Editor
            composable(
                route = Screen.ChapterEditor.route,
                arguments = listOf(
                    navArgument("novelId") { type = NavType.StringType },
                    navArgument("chapterId") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    }
                )
            ) { backStackEntry ->
                val novelId = backStackEntry.arguments?.getString("novelId") ?: ""
                val chapterId = backStackEntry.arguments?.getString("chapterId")
                val viewModel = remember(novelId, chapterId) {
                    ChapterEditorViewModel(
                        novelId = novelId,
                        chapterId = chapterId,
                        repository = application.novelRepository
                    )
                }
                ChapterEditorScreen(
                    viewModel = viewModel,
                    onBackClick = { navController.popBackStack() }
                )
            }

            // Subview: Text Replacements
            composable(
                route = Screen.Replacements.route,
                arguments = listOf(
                    navArgument("novelId") { type = NavType.StringType }
                )
            ) { backStackEntry ->
                val novelId = backStackEntry.arguments?.getString("novelId") ?: ""
                val viewModel = remember(novelId) {
                    ReplacementsViewModel(novelId, application.novelRepository)
                }
                ReplacementsScreen(
                    viewModel = viewModel,
                    onBackClick = { navController.popBackStack() }
                )
            }
        }
    }
}
