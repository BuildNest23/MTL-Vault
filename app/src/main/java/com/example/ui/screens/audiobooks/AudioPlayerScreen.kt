package com.example.ui.screens.audiobooks

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.audio.AudioFormat
import com.example.audio.PlaybackStatus
import com.example.audio.SleepKind
import com.example.ui.components.AudioCover
import com.example.ui.components.bookplateColorsFor

private enum class PlayerSheet { NONE, SPEED, SLEEP, CHAPTERS, BOOKMARKS, SETTINGS }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioPlayerScreen(
    viewModel: AudioPlayerViewModel,
    onBack: () -> Unit
) {
    val state by viewModel.player.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val book by viewModel.book.collectAsStateWithLifecycle()
    val chapters by viewModel.chapters.collectAsStateWithLifecycle()
    val bookmarks by viewModel.bookmarks.collectAsStateWithLifecycle()

    var sheet by remember { mutableStateOf(PlayerSheet.NONE) }
    var menuOpen by remember { mutableStateOf(false) }
    val snackbarHost = remember { SnackbarHostState() }

    LaunchedEffect(viewModel) {
        viewModel.messages.collect { snackbarHost.showSnackbar(it) }
    }

    val relinkPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.relinkMissingFile(uri)
    }

    val title = state.title.ifBlank { book?.title.orEmpty() }
    val author = state.author.ifBlank { book?.author.orEmpty() }
    val coverUri = state.coverUri ?: book?.coverUri
    val (atmosphereTint, _) = remember(title) { bookplateColorsFor(title.ifBlank { "Audiobook" }) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        atmosphereTint.copy(alpha = 0.22f),
                        MaterialTheme.colorScheme.background,
                        MaterialTheme.colorScheme.background
                    )
                )
            )
    ) {
        Scaffold(
            containerColor = Color.Transparent,
            snackbarHost = { SnackbarHost(snackbarHost) },
            topBar = {
                TopAppBar(
                    title = {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "NOW PLAYING",
                                style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.4.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (state.chapterCount > 1) {
                                Text(
                                    text = "Chapter ${state.chapterIndex + 1} of ${state.chapterCount}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack, modifier = Modifier.testTag("audio_player_back")) {
                            Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Close player")
                        }
                    },
                    actions = {
                        Box {
                            IconButton(
                                onClick = { menuOpen = true },
                                modifier = Modifier.testTag("audio_player_menu")
                            ) {
                                Icon(Icons.Default.MoreVert, contentDescription = "More")
                            }
                            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                DropdownMenuItem(
                                    text = { Text("Player settings") },
                                    onClick = {
                                        menuOpen = false
                                        sheet = PlayerSheet.SETTINGS
                                    }
                                )
                                val finished = book?.finished == true
                                DropdownMenuItem(
                                    text = { Text(if (finished) "Mark as not finished" else "Mark as finished") },
                                    onClick = {
                                        menuOpen = false
                                        viewModel.markFinished(!finished)
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Start from the beginning") },
                                    onClick = {
                                        menuOpen = false
                                        viewModel.restartBook()
                                    }
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        titleContentColor = MaterialTheme.colorScheme.onBackground,
                        navigationIconContentColor = MaterialTheme.colorScheme.onBackground,
                        actionIconContentColor = MaterialTheme.colorScheme.onBackground
                    )
                )
            }
        ) { padding ->
            BoxWithConstraints(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize(),
                contentAlignment = Alignment.TopCenter
            ) {
                val coverSize = minOf(maxWidth - 72.dp, maxHeight * 0.34f).coerceIn(136.dp, 280.dp)

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 560.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 24.dp)
                        .navigationBarsPadding()
                        .testTag("audio_player_screen"),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Spacer(modifier = Modifier.height(12.dp))

                    AudioCover(
                        title = title,
                        coverUri = coverUri,
                        modifier = Modifier
                            .size(coverSize)
                            .shadow(12.dp, RoundedCornerShape(22.dp), clip = false),
                        cornerRadius = 22.dp,
                        initialsSize = 54
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    Text(
                        text = title,
                        style = MaterialTheme.typography.headlineLarge,
                        color = MaterialTheme.colorScheme.onBackground,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (author.isNotBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = author,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    if (state.chapterCount > 0) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)
                        ) {
                            Text(
                                text = state.chapterTitle.ifBlank { "Chapter ${state.chapterIndex + 1}" },
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                textAlign = TextAlign.Center,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .padding(horizontal = 12.dp, vertical = 5.dp)
                                    .testTag("audio_player_chapter_title")
                            )
                        }
                    }

                    if (state.missingFileName != null) {
                        Spacer(modifier = Modifier.height(14.dp))
                        MissingFileNotice(
                            fileName = state.missingFileName.orEmpty(),
                            onRelink = { relinkPicker.launch(arrayOf("audio/*", "application/octet-stream")) }
                        )
                    } else if (state.status == PlaybackStatus.ERROR && !state.error.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = state.error.orEmpty(),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.testTag("audio_player_error")
                        )
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    ChapterSeekBar(
                        elapsedMs = state.chapterElapsedMs,
                        durationMs = state.chapterDurationMs,
                        onSeek = viewModel::seekInChapter
                    )

                    if (state.bookDurationMs > 0L) {
                        Text(
                            text = "${AudioFormat.duration(state.bookRemainingAtSpeedMs)} left at ${AudioFormat.speed(state.speed)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 6.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(22.dp))

                    // Transport Controls
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        IconButton(
                            onClick = viewModel::previousChapter,
                            modifier = Modifier.testTag("audio_player_prev_chapter")
                        ) {
                            Icon(Icons.Default.SkipPrevious, contentDescription = "Previous chapter", modifier = Modifier.size(30.dp))
                        }
                        SkipButton(
                            seconds = settings.skipBackSeconds,
                            icon = Icons.Default.Replay,
                            description = "Back ${settings.skipBackSeconds} seconds",
                            tag = "audio_player_skip_back",
                            onClick = viewModel::skipBack
                        )
                        FilledIconButton(
                            onClick = viewModel::playPause,
                            modifier = Modifier
                                .size(74.dp)
                                .shadow(6.dp, CircleShape, clip = false)
                                .testTag("audio_player_play_pause"),
                            shape = CircleShape,
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            )
                        ) {
                            if (state.status == PlaybackStatus.LOADING) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(28.dp),
                                    strokeWidth = 3.dp,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                            } else {
                                Icon(
                                    imageVector = if (state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = if (state.isPlaying) "Pause" else "Play",
                                    modifier = Modifier.size(40.dp)
                                )
                            }
                        }
                        SkipButton(
                            seconds = settings.skipForwardSeconds,
                            icon = Icons.Default.FastForward,
                            description = "Forward ${settings.skipForwardSeconds} seconds",
                            tag = "audio_player_skip_forward",
                            onClick = viewModel::skipForward
                        )
                        IconButton(
                            onClick = viewModel::nextChapter,
                            modifier = Modifier.testTag("audio_player_next_chapter")
                        ) {
                            Icon(Icons.Default.SkipNext, contentDescription = "Next chapter", modifier = Modifier.size(30.dp))
                        }
                    }

                    Spacer(modifier = Modifier.height(28.dp))

                    // Editorial Quick Control Pills
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        QuickControlPill(
                            icon = Icons.Default.Speed,
                            label = AudioFormat.speed(state.speed),
                            tag = "audio_player_speed",
                            onClick = { sheet = PlayerSheet.SPEED },
                            modifier = Modifier.weight(1f)
                        )
                        QuickControlPill(
                            icon = Icons.Default.Bedtime,
                            label = if (state.sleep.kind == SleepKind.OFF) "Sleep" else AudioFormat.sleep(state.sleep),
                            highlighted = state.sleep.kind != SleepKind.OFF,
                            tag = "audio_player_sleep",
                            onClick = { sheet = PlayerSheet.SLEEP },
                            modifier = Modifier.weight(1f)
                        )
                        QuickControlPill(
                            icon = Icons.Default.FormatListNumbered,
                            label = "Chapters",
                            tag = "audio_player_chapters",
                            onClick = { sheet = PlayerSheet.CHAPTERS },
                            modifier = Modifier.weight(1f)
                        )
                        QuickControlPill(
                            icon = Icons.Default.BookmarkBorder,
                            label = if (bookmarks.isEmpty()) "Marks" else "Marks (${bookmarks.size})",
                            tag = "audio_player_bookmarks",
                            onClick = { sheet = PlayerSheet.BOOKMARKS },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(28.dp))
                }
            }
        }
    }

    val close = { sheet = PlayerSheet.NONE }
    when (sheet) {
        PlayerSheet.NONE -> Unit
        PlayerSheet.SPEED -> AudioSpeedSheet(
            speed = state.speed,
            onSpeedChange = viewModel::setSpeed,
            onDismiss = close
        )
        PlayerSheet.SLEEP -> AudioSleepSheet(
            sleep = state.sleep,
            onStartTimed = viewModel::startSleepTimer,
            onEndOfChapter = viewModel::startSleepAtChapterEnd,
            onCancel = viewModel::cancelSleepTimer,
            onDismiss = close
        )
        PlayerSheet.CHAPTERS -> AudioChaptersSheet(
            chapters = chapters,
            currentIndex = state.chapterIndex,
            isPlaying = state.isPlaying,
            onPlayChapter = viewModel::playChapter,
            onRename = viewModel::renameChapter,
            onDelete = viewModel::removeChapter,
            onAddHere = viewModel::addChapterHere,
            onDismiss = close
        )
        PlayerSheet.BOOKMARKS -> AudioBookmarksSheet(
            bookmarks = bookmarks,
            onAddHere = viewModel::addBookmarkHere,
            onOpen = viewModel::openBookmark,
            onEditNote = viewModel::editBookmarkNote,
            onMakeChapter = viewModel::bookmarkToChapter,
            onDelete = viewModel::deleteBookmark,
            onDismiss = close
        )
        PlayerSheet.SETTINGS -> AudioPlayerSettingsSheet(
            settings = settings,
            onChange = viewModel::updateSettings,
            onDismiss = close
        )
    }
}

@Composable
private fun ChapterSeekBar(
    elapsedMs: Long,
    durationMs: Long,
    onSeek: (Long) -> Unit
) {
    var dragFraction by remember { mutableStateOf<Float?>(null) }
    val playedFraction = if (durationMs > 0L) (elapsedMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    val shown = dragFraction ?: playedFraction
    val shownElapsed = if (dragFraction != null) (durationMs * shown).toLong() else elapsedMs

    Column(modifier = Modifier.fillMaxWidth()) {
        Slider(
            value = shown,
            onValueChange = { dragFraction = it },
            onValueChangeFinished = {
                dragFraction?.let { onSeek((durationMs * it).toLong()) }
                dragFraction = null
            },
            enabled = durationMs > 0L,
            modifier = Modifier.testTag("audio_player_seek")
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = AudioFormat.clock(shownElapsed),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = AudioFormat.clockRemaining((durationMs - shownElapsed).coerceAtLeast(0L)),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SkipButton(
    seconds: Int,
    icon: ImageVector,
    description: String,
    tag: String,
    onClick: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        IconButton(onClick = onClick, modifier = Modifier.testTag(tag)) {
            Icon(icon, contentDescription = description, modifier = Modifier.size(30.dp))
        }
        Text(
            text = "${seconds}s",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun QuickControlPill(
    icon: ImageVector,
    label: String,
    tag: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    highlighted: Boolean = false
) {
    Surface(
        modifier = modifier
            .testTag(tag)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        color = if (highlighted) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            width = 1.dp,
            color = if (highlighted) MaterialTheme.colorScheme.secondary.copy(alpha = 0.45f)
            else MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (highlighted) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = if (highlighted) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun MissingFileNotice(fileName: String, onRelink: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("audio_player_missing_file"),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.4f))
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Can't find \"$fileName\". It may have been moved or deleted.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center
            )
            Button(onClick = onRelink, modifier = Modifier.padding(top = 10.dp)) {
                Text("Pick the file again")
            }
        }
    }
}
