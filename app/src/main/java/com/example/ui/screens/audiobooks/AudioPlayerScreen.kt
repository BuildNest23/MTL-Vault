package com.example.ui.screens.audiobooks

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
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
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.audio.AudioFormat
import com.example.audio.PlaybackStatus
import com.example.audio.SleepKind
import com.example.ui.components.AudioCover

/** Which bottom sheet is open on the player screen. */
private enum class PlayerSheet { NONE, SPEED, SLEEP, CHAPTERS, BOOKMARKS, SETTINGS }

/**
 * The full audiobook player: cover, chapter title, chapter seek bar, the big play button,
 * skip and chapter buttons, and quick controls for speed, sleep timer, chapters and bookmarks.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioPlayerScreen(
    viewModel: AudioPlayerViewModel,
    onBack: () -> Unit
) {
    val state by viewModel.player.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val book by viewModel.book.collectAsState()
    val chapters by viewModel.chapters.collectAsState()
    val bookmarks by viewModel.bookmarks.collectAsState()

    var sheet by remember { mutableStateOf(PlayerSheet.NONE) }
    var menuOpen by remember { mutableStateOf(false) }
    val snackbarHost = remember { SnackbarHostState() }

    // Short notes from the view model ("Chapter added", ...)
    LaunchedEffect(viewModel) {
        viewModel.messages.collect { snackbarHost.showSnackbar(it) }
    }

    val relinkPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.relinkMissingFile(uri)
    }

    // Until the player has opened the book, show what the database knows
    val title = state.title.ifBlank { book?.title.orEmpty() }
    val author = state.author.ifBlank { book?.author.orEmpty() }
    val coverUri = state.coverUri ?: book?.coverUri

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHost) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Now playing",
                        style = MaterialTheme.typography.titleMedium
                    )
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
                }
            )
        }
    ) { padding ->
        BoxWithConstraints(
            modifier = Modifier
                .padding(padding)
                .fillMaxWidth()
        ) {
            // The cover takes the width, but never more than 40% of the height on short screens
            val coverSize = minOf(maxWidth - 64.dp, maxHeight * 0.32f).coerceAtLeast(120.dp)

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp)
                    .navigationBarsPadding()
                    .testTag("audio_player_screen"),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(8.dp))

                AudioCover(
                    title = title,
                    coverUri = coverUri,
                    modifier = Modifier.size(coverSize),
                    cornerRadius = 20.dp,
                    initialsSize = 56
                )

                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (author.isNotBlank()) {
                    Text(
                        text = author,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Chapter title with position, for example "Chapter 3 of 12"
                if (state.chapterCount > 0) {
                    Text(
                        text = state.chapterTitle.ifBlank { "Chapter ${state.chapterIndex + 1}" },
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.testTag("audio_player_chapter_title")
                    )
                    if (state.chapterCount > 1) {
                        Text(
                            text = "Chapter ${state.chapterIndex + 1} of ${state.chapterCount}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // A file that is gone, or any other playback problem
                if (state.missingFileName != null) {
                    Spacer(modifier = Modifier.height(12.dp))
                    MissingFileNotice(
                        fileName = state.missingFileName.orEmpty(),
                        onRelink = { relinkPicker.launch(arrayOf("audio/*", "application/octet-stream")) }
                    )
                } else if (state.status == PlaybackStatus.ERROR && !state.error.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = state.error.orEmpty(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.testTag("audio_player_error")
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                ChapterSeekBar(
                    elapsedMs = state.chapterElapsedMs,
                    durationMs = state.chapterDurationMs,
                    onSeek = viewModel::seekInChapter
                )

                Text(
                    text = if (state.bookDurationMs > 0L) {
                        "${AudioFormat.duration(state.bookRemainingAtSpeedMs)} left in the book at ${AudioFormat.speed(state.speed)}"
                    } else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Transport buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    IconButton(
                        onClick = viewModel::previousChapter,
                        modifier = Modifier.testTag("audio_player_prev_chapter")
                    ) {
                        Icon(Icons.Default.SkipPrevious, contentDescription = "Previous chapter", modifier = Modifier.size(32.dp))
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
                            .size(72.dp)
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
                        Icon(Icons.Default.SkipNext, contentDescription = "Next chapter", modifier = Modifier.size(32.dp))
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Quick controls
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    QuickControl(
                        icon = Icons.Default.Speed,
                        label = AudioFormat.speed(state.speed),
                        tag = "audio_player_speed",
                        onClick = { sheet = PlayerSheet.SPEED }
                    )
                    QuickControl(
                        icon = Icons.Default.Bedtime,
                        label = if (state.sleep.kind == SleepKind.OFF) "Sleep" else AudioFormat.sleep(state.sleep),
                        highlighted = state.sleep.kind != SleepKind.OFF,
                        tag = "audio_player_sleep",
                        onClick = { sheet = PlayerSheet.SLEEP }
                    )
                    QuickControl(
                        icon = Icons.Default.FormatListNumbered,
                        label = "Chapters",
                        tag = "audio_player_chapters",
                        onClick = { sheet = PlayerSheet.CHAPTERS }
                    )
                    QuickControl(
                        icon = Icons.Default.BookmarkBorder,
                        label = if (bookmarks.isEmpty()) "Bookmarks" else "Marks (${bookmarks.size})",
                        tag = "audio_player_bookmarks",
                        onClick = { sheet = PlayerSheet.BOOKMARKS }
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    // ------------------------------------------------------------------ Sheets
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

/**
 * Seek bar for the current chapter with the time played on the left and the time left on the
 * right. While the finger is down the bar shows the finger; the player only jumps on release.
 */
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

/** A skip button with the number of seconds written under its icon. */
@Composable
private fun SkipButton(
    seconds: Int,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    tag: String,
    onClick: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        IconButton(onClick = onClick, modifier = Modifier.testTag(tag)) {
            Icon(icon, contentDescription = description, modifier = Modifier.size(32.dp))
        }
        Text(
            text = "${seconds}s",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** An icon with a short label under it, used for the four quick controls. */
@Composable
private fun QuickControl(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    tag: String,
    onClick: () -> Unit,
    highlighted: Boolean = false
) {
    val color = if (highlighted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.testTag(tag)
    ) {
        IconButton(onClick = onClick) {
            Icon(icon, contentDescription = label, tint = color, modifier = Modifier.size(26.dp))
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** Shown when a file of the book can't be found any more, with a button to pick it again. */
@Composable
private fun MissingFileNotice(fileName: String, onRelink: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("audio_player_missing_file"),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Can't find \"$fileName\". It may have been moved or deleted.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
            textAlign = TextAlign.Center
        )
        Button(onClick = onRelink, modifier = Modifier.padding(top = 8.dp)) {
            Text("Pick the file again")
        }
    }
}
