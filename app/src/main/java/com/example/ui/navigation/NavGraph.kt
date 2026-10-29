package com.example.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import com.example.tts.TtsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.MtlVaultApplication
import com.example.ui.components.AudioMiniPlayer
import com.example.ui.components.MtlBottomBar
import com.example.ui.screens.audiobooks.AddAudiobookScreen
import com.example.ui.screens.audiobooks.AddAudiobookViewModel
import com.example.ui.screens.audiobooks.AudioPlayerScreen
import com.example.ui.screens.audiobooks.AudioPlayerViewModel
import com.example.ui.screens.audiobooks.AudiobooksScreen
import com.example.ui.screens.audiobooks.AudiobooksViewModel
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

import android.content.Intent

/**
 * ViewModels are now owned by each navigation back-stack entry (not by `remember`), so they survive
 * rotation / theme changes and are cleared (onCleared) when their screen is really removed.
 */
private fun <T : ViewModel> vmFactory(build: () -> T): ViewModelProvider.Factory =
    object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <V : ViewModel> create(modelClass: Class<V>): V = build() as V
    }

@Composable
fun MtlVaultNavGraph(
    application: MtlVaultApplication,
    intent: Intent? = null,
    onIntentConsumed: () -> Unit = {}
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    // Cold start (e.g. app was swiped away from recents while TTS kept playing): instead of the Library,
    // open the novel's chapter list with the reader for the chapter being spoken on top of it.
    val restoredOnLaunch = rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (restoredOnLaunch.value) return@LaunchedEffect
        restoredOnLaunch.value = true
        if (intent?.getBooleanExtra("OPEN_READER", false) == true) return@LaunchedEffect
        val info = application.ttsManager.playbackInfo.value
        val ttsActive = info.state == TtsState.PLAYING || info.state == TtsState.PAUSED
        val novelId = info.novelId
        val chapterId = info.chapterId
        if (ttsActive && !novelId.isNullOrBlank() && !chapterId.isNullOrBlank()) {
            try {
                navController.navigate(Screen.NovelDetail.createRoute(novelId))
                navController.navigate(Screen.Reader.createRoute(novelId, chapterId))
            } catch (e: Exception) {
                com.example.logging.AppLogger.w("NavGraph", "Could not restore playing chapter: ${e.message}")
            }
        }
    }

    LaunchedEffect(intent) {
        if (intent?.getBooleanExtra("OPEN_READER", false) == true) {
            val novelId = intent.getStringExtra("NOVEL_ID")
            val chapterId = intent.getStringExtra("CHAPTER_ID")
            if (!novelId.isNullOrBlank() && !chapterId.isNullOrBlank()) {
                // If that very chapter is already on screen there is nothing to open
                // (the reader scrolls itself to the spoken paragraph on resume)
                val entry = navController.currentBackStackEntry
                val alreadyThere = entry?.destination?.route == Screen.Reader.route &&
                    entry.arguments?.getString("chapterId") == chapterId
                if (!alreadyThere) {
                    navController.navigate(Screen.Reader.createRoute(novelId, chapterId)) {
                        popUpTo(Screen.Library.route)
                        launchSingleTop = true
                    }
                }
            }
            // Handle a notification intent only once (otherwise a rotation re-opens the reader)
            onIntentConsumed()
        }
    }

    // If the reader was on screen when the app went to the background and TTS is still active, make sure
    // the reader (not the novel page) is what the user sees when they come back.
    val readerWasOpenWhenLeft = rememberSaveable { mutableStateOf(false) }
    val lifecycleOwner = LocalContext.current as? LifecycleOwner
    DisposableEffect(lifecycleOwner, navController) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE, Lifecycle.Event.ON_STOP -> {
                    readerWasOpenWhenLeft.value =
                        navController.currentBackStackEntry?.destination?.route == Screen.Reader.route
                }
                Lifecycle.Event.ON_START -> {
                    if (readerWasOpenWhenLeft.value) {
                        readerWasOpenWhenLeft.value = false
                        val entry = navController.currentBackStackEntry
                        val info = application.ttsManager.playbackInfo.value
                        val ttsActive = info.state == TtsState.PLAYING || info.state == TtsState.PAUSED
                        val novelId = info.novelId
                        val chapterId = info.chapterId
                        if (entry != null && entry.destination.route != Screen.Reader.route &&
                            ttsActive && novelId != null && chapterId != null
                        ) {
                            try {
                                navController.navigate(Screen.Reader.createRoute(novelId, chapterId)) {
                                    launchSingleTop = true
                                }
                            } catch (e: Exception) {
                                com.example.logging.AppLogger.w("NavGraph", "Could not restore reader: ${e.message}")
                            }
                        }
                    }
                }
                else -> Unit
            }
        }
        lifecycleOwner?.lifecycle?.addObserver(observer)
        onDispose { lifecycleOwner?.lifecycle?.removeObserver(observer) }
    }

    val showBottomBar = currentRoute in listOf(
        Screen.Library.route,
        Screen.Audiobooks.route,
        Screen.Stats.route,
        Screen.Settings.route
    )

    // The small audiobook player sits above the tab bar while a book is loaded
    val audioPlayerState by application.audioController.state.collectAsState()

    Scaffold(
        bottomBar = {
            Column {
                if (showBottomBar) {
                    AudioMiniPlayer(
                        state = audioPlayerState,
                        onOpen = {
                            audioPlayerState.bookId?.let { id ->
                                navController.navigate(Screen.AudioPlayer.createRoute(id, autoPlay = false))
                            }
                        },
                        onPlayPause = { application.audioController.toggle() },
                        onClose = { application.audioController.close() }
                    )
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
                val viewModel: LibraryViewModel = viewModel(
                    factory = vmFactory { LibraryViewModel(application.novelRepository) }
                )
                LibraryScreen(
                    viewModel = viewModel,
                    onOpenNovel = { novelId ->
                        navController.navigate(Screen.NovelDetail.createRoute(novelId))
                    }
                )
            }

            // Tab 2: Audiobooks
            composable(Screen.Audiobooks.route) {
                val viewModel: AudiobooksViewModel = viewModel(
                    factory = vmFactory {
                        AudiobooksViewModel(
                            application.audioRepository,
                            application.audioController,
                            application.audioImporter
                        )
                    }
                )
                AudiobooksScreen(
                    viewModel = viewModel,
                    onOpenBook = { bookId ->
                        navController.navigate(Screen.AudioPlayer.createRoute(bookId, autoPlay = true))
                    },
                    onAddBook = { navController.navigate(Screen.AddAudiobook.route) }
                )
            }

            // Tab 3: Stats
            composable(Screen.Stats.route) {
                val viewModel: StatsViewModel = viewModel(
                    factory = vmFactory {
                        StatsViewModel(application.novelRepository, application.audioRepository)
                    }
                )
                StatsScreen(viewModel = viewModel)
            }

            // Tab 4: Settings
            composable(Screen.Settings.route) {
                val viewModel: SettingsViewModel = viewModel(
                    factory = vmFactory {
                        SettingsViewModel(
                            repository = application.novelRepository,
                            settingsDataStore = application.settingsDataStore,
                            ttsManager = application.ttsManager
                        )
                    }
                )
                SettingsScreen(viewModel = viewModel)
            }

            // Subview: Add audiobook
            composable(Screen.AddAudiobook.route) {
                val viewModel: AddAudiobookViewModel = viewModel(
                    factory = vmFactory {
                        AddAudiobookViewModel(application.audioRepository, application.audioImporter)
                    }
                )
                AddAudiobookScreen(
                    viewModel = viewModel,
                    onBack = { navController.popBackStack() },
                    onSaved = { bookId ->
                        // Replace the import screen with the player of the new book
                        navController.navigate(Screen.AudioPlayer.createRoute(bookId, autoPlay = true)) {
                            popUpTo(Screen.Audiobooks.route)
                        }
                    }
                )
            }

            // Subview: Audiobook player
            composable(
                route = Screen.AudioPlayer.route,
                arguments = listOf(
                    navArgument("bookId") { type = NavType.StringType },
                    navArgument("autoPlay") {
                        type = NavType.BoolType
                        defaultValue = false
                    }
                )
            ) { backStackEntry ->
                val bookId = backStackEntry.arguments?.getString("bookId") ?: ""
                val autoPlay = backStackEntry.arguments?.getBoolean("autoPlay") ?: false
                val viewModel: AudioPlayerViewModel = viewModel(
                    factory = vmFactory {
                        AudioPlayerViewModel(
                            bookId = bookId,
                            autoPlay = autoPlay,
                            repository = application.audioRepository,
                            controller = application.audioController,
                            context = application
                        )
                    }
                )
                AudioPlayerScreen(
                    viewModel = viewModel,
                    onBack = { navController.popBackStack() }
                )
            }

            // Subview: Novel Detail
            composable(
                route = Screen.NovelDetail.route,
                arguments = listOf(navArgument("novelId") { type = NavType.StringType })
            ) { backStackEntry ->
                val novelId = backStackEntry.arguments?.getString("novelId") ?: ""
                val viewModel: NovelDetailViewModel = viewModel(
                    factory = vmFactory {
                        NovelDetailViewModel(novelId, application.novelRepository, application.settingsDataStore)
                    }
                )
                val ttsInfo by application.ttsManager.playbackInfo.collectAsState()
                NovelDetailScreen(
                    viewModel = viewModel,
                    ttsInfo = ttsInfo,
                    onBackClick = { navController.popBackStack() },
                    onOpenReader = { chapterId ->
                        navController.navigate(Screen.Reader.createRoute(novelId, chapterId, autoPlay = false))
                    },
                    onPlayNovel = { chapterId ->
                        navController.navigate(Screen.Reader.createRoute(novelId, chapterId, autoPlay = true))
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
                    navArgument("chapterId") { type = NavType.StringType },
                    navArgument("autoPlay") {
                        type = NavType.BoolType
                        defaultValue = false
                    }
                )
            ) { backStackEntry ->
                val novelId = backStackEntry.arguments?.getString("novelId") ?: ""
                val chapterId = backStackEntry.arguments?.getString("chapterId") ?: ""
                val autoPlay = backStackEntry.arguments?.getBoolean("autoPlay") ?: false
                val viewModel: ReaderViewModel = viewModel(
                    factory = vmFactory {
                        ReaderViewModel(
                            novelId = novelId,
                            initialChapterId = chapterId,
                            repository = application.novelRepository,
                            settingsDataStore = application.settingsDataStore,
                            ttsManager = application.ttsManager,
                            autoPlayTtsOnLoad = autoPlay
                        )
                    }
                )
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
                val viewModel: ChapterEditorViewModel = viewModel(
                    factory = vmFactory {
                        ChapterEditorViewModel(
                            novelId = novelId,
                            chapterId = chapterId,
                            repository = application.novelRepository
                        )
                    }
                )
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
                val viewModel: ReplacementsViewModel = viewModel(
                    factory = vmFactory { ReplacementsViewModel(novelId, application.novelRepository) }
                )
                ReplacementsScreen(
                    viewModel = viewModel,
                    onBackClick = { navController.popBackStack() }
                )
            }
        }
    }
}
