package com.example.ui.screens.audiobooks

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.audio.AudioFormat
import com.example.data.database.entities.AudioBookEntity
import com.example.ui.components.AudioCover
import com.example.ui.components.EmptyState
import com.example.ui.components.MtlTopBar

private fun progressOf(book: AudioBookEntity): Float = when {
    book.finished -> 1f
    book.totalDurationMs <= 0L -> 0f
    else -> (book.progressMs.toFloat() / book.totalDurationMs).coerceIn(0f, 1f)
}

private fun statusLine(book: AudioBookEntity): String = when {
    book.finished -> "Finished"
    book.progressMs <= 0L -> AudioFormat.duration(book.totalDurationMs)
    else -> {
        val left = (book.totalDurationMs - book.progressMs).coerceAtLeast(0L)
        val speed = if (book.playbackSpeed > 0f) book.playbackSpeed else 1f
        "${AudioFormat.duration((left / speed).toLong())} left"
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AudiobooksScreen(
    viewModel: AudiobooksViewModel,
    onOpenBook: (bookId: String) -> Unit,
    onAddBook: () -> Unit
) {
    val shelf by viewModel.uiState.collectAsStateWithLifecycle()
    val continueBook by viewModel.continueBook.collectAsStateWithLifecycle()
    val nowPlaying by viewModel.nowPlaying.collectAsStateWithLifecycle()

    val snackbarHost = remember { SnackbarHostState() }
    LaunchedEffect(viewModel) {
        viewModel.messages.collect { snackbarHost.showSnackbar(it) }
    }

    var menuBook by remember { mutableStateOf<AudioBookEntity?>(null) }
    var editBook by remember { mutableStateOf<AudioBookEntity?>(null) }
    var removeBook by remember { mutableStateOf<AudioBookEntity?>(null) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            MtlTopBar(
                title = "Audiobooks",
                subtitle = if (shelf.books.isEmpty()) "LISTENING ROOM" else "${shelf.books.size} AUDIOBOOK${if (shelf.books.size == 1) "" else "S"}"
            )
        },
        snackbarHost = { SnackbarHost(snackbarHost) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAddBook,
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("Add", style = MaterialTheme.typography.labelLarge) },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.testTag("audiobooks_add_button")
            )
        }
    ) { innerPadding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            val columns = if (maxWidth >= 600.dp) 3 else 2

            LazyVerticalGrid(
                columns = GridCells.Fixed(columns),
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("audiobooks_screen"),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 104.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                val resume = continueBook
                if (resume != null) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        ContinueListeningHeroCard(
                            book = resume,
                            onClick = { onOpenBook(resume.id) }
                        )
                    }
                }

                if (shelf.books.isNotEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Your Shelf",
                                style = MaterialTheme.typography.headlineSmall,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Text(
                                text = "${shelf.books.size} title${if (shelf.books.size == 1) "" else "s"}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                if (shelf.loaded && shelf.books.isEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        EmptyState(
                            icon = Icons.Default.Headphones,
                            title = "No audiobooks yet",
                            message = "Add a folder of audio chapters or a single M4B / MP3 audiobook from your device.",
                            actionText = "Add your first audiobook",
                            onActionClick = onAddBook,
                            modifier = Modifier
                                .padding(top = 12.dp)
                                .testTag("audiobooks_empty")
                        )
                    }
                }

                items(shelf.books, key = { it.id }) { book ->
                    val isCurrent = nowPlaying.bookId == book.id
                    Box {
                        EditorialAudioBookCard(
                            book = book,
                            isPlaying = isCurrent && nowPlaying.isPlaying,
                            onClick = { onOpenBook(book.id) },
                            onLongClick = { menuBook = book }
                        )
                        DropdownMenu(expanded = menuBook?.id == book.id, onDismissRequest = { menuBook = null }) {
                            DropdownMenuItem(
                                text = { Text(if (book.finished) "Mark as not finished" else "Mark as finished") },
                                onClick = {
                                    menuBook = null
                                    viewModel.setFinished(book.id, !book.finished)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Start from the beginning") },
                                onClick = {
                                    menuBook = null
                                    viewModel.restart(book.id)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Edit title and author") },
                                onClick = {
                                    menuBook = null
                                    editBook = book
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Remove Audiobook", color = MaterialTheme.colorScheme.error) },
                                onClick = {
                                    menuBook = null
                                    removeBook = book
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    editBook?.let { book ->
        EditDetailsDialog(
            book = book,
            onSave = { title, author ->
                viewModel.editDetails(book, title, author)
                editBook = null
            },
            onDismiss = { editBook = null }
        )
    }

    removeBook?.let { book ->
        AlertDialog(
            onDismissRequest = { removeBook = null },
            title = { Text("Remove \"${book.title}\"?") },
            text = {
                Text(
                    "The book, its bookmarks and your place in it are removed from the app. " +
                        "Your audio files stay on the phone."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteBook(book)
                        removeBook = null
                    },
                    modifier = Modifier.testTag("audiobooks_confirm_remove")
                ) {
                    Text("Remove", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { removeBook = null }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun ContinueListeningHeroCard(book: AudioBookEntity, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("audiobooks_continue"),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shadowElevation = 2.dp
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.35f),
                            MaterialTheme.colorScheme.surface
                        )
                    )
                )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                AudioCover(
                    title = book.title,
                    coverUri = book.coverUri,
                    modifier = Modifier
                        .size(84.dp)
                        .shadow(4.dp, RoundedCornerShape(12.dp), clip = false),
                    cornerRadius = 12.dp,
                    initialsSize = 26
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "CONTINUE LISTENING",
                        style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.1.sp),
                        color = MaterialTheme.colorScheme.secondary
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = book.title,
                        style = MaterialTheme.typography.titleLarge.copy(fontSize = 18.sp, lineHeight = 23.sp),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${statusLine(book)}  •  ${AudioFormat.percent(progressOf(book))}%",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    LinearProgressIndicator(
                        progress = { progressOf(book) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = MaterialTheme.colorScheme.secondary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                        strokeCap = StrokeCap.Round
                    )
                }
                FilledIconButton(
                    onClick = onClick,
                    modifier = Modifier.size(48.dp),
                    shape = CircleShape,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Resume audiobook",
                        modifier = Modifier.size(26.dp)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun EditorialAudioBookCard(
    book: AudioBookEntity,
    isPlaying: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .testTag("audiobook_tile_${book.id}"),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            width = 1.dp,
            color = if (isPlaying) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.8f)
        ),
        shadowElevation = 1.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .alpha(if (book.finished) 0.78f else 1f)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .padding(8.dp)
            ) {
                AudioCover(
                    title = book.title,
                    coverUri = book.coverUri,
                    modifier = Modifier
                        .fillMaxSize()
                        .shadow(3.dp, RoundedCornerShape(11.dp), clip = false),
                    cornerRadius = 11.dp,
                    initialsSize = 30
                )
                if (book.finished) {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(6.dp),
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Finished",
                            tint = Color.White,
                            modifier = Modifier
                                .padding(4.dp)
                                .size(13.dp)
                        )
                    }
                }
                if (isPlaying) {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(6.dp),
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.secondary
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.GraphicEq,
                                contentDescription = "Playing now",
                                tint = MaterialTheme.colorScheme.onSecondary,
                                modifier = Modifier.size(13.dp)
                            )
                            Text(
                                text = "Playing",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                color = MaterialTheme.colorScheme.onSecondary
                            )
                        }
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 10.dp, end = 10.dp, top = 2.dp, bottom = 11.dp)
            ) {
                Text(
                    text = book.title,
                    style = MaterialTheme.typography.titleMedium.copy(fontSize = 14.sp, lineHeight = 19.sp),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = statusLine(book),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (!book.finished && book.progressMs > 0L) {
                    Spacer(modifier = Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = { progressOf(book) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp)),
                        color = MaterialTheme.colorScheme.secondary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                        strokeCap = StrokeCap.Round
                    )
                }
            }
        }
    }
}

@Composable
private fun EditDetailsDialog(
    book: AudioBookEntity,
    onSave: (title: String, author: String) -> Unit,
    onDismiss: () -> Unit
) {
    var title by remember { mutableStateOf(book.title) }
    var author by remember { mutableStateOf(book.author) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Audiobook", style = MaterialTheme.typography.headlineSmall) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Title") },
                    singleLine = true,
                    isError = title.isBlank(),
                    modifier = Modifier.testTag("audiobooks_edit_title")
                )
                OutlinedTextField(
                    value = author,
                    onValueChange = { author = it },
                    label = { Text("Author") },
                    singleLine = true,
                    modifier = Modifier.testTag("audiobooks_edit_author")
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(title, author) }, enabled = title.isNotBlank()) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
