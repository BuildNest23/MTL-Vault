package com.example.ui.screens.audiobooks

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.audio.AudioFormat
import com.example.data.database.entities.AudioBookEntity
import com.example.ui.components.AudioCover
import com.example.ui.components.MtlTopBar

/** How much of the book has been heard, from 0 to 1. A finished book counts as complete. */
private fun progressOf(book: AudioBookEntity): Float = when {
    book.finished -> 1f
    book.totalDurationMs <= 0L -> 0f
    else -> (book.progressMs.toFloat() / book.totalDurationMs).coerceIn(0f, 1f)
}

/** "3h 20m left" at the book's own speed, "Finished", or the total length for a book not started. */
private fun statusLine(book: AudioBookEntity): String = when {
    book.finished -> "Finished"
    book.progressMs <= 0L -> AudioFormat.duration(book.totalDurationMs)
    else -> {
        val left = (book.totalDurationMs - book.progressMs).coerceAtLeast(0L)
        val speed = if (book.playbackSpeed > 0f) book.playbackSpeed else 1f
        "${AudioFormat.duration((left / speed).toLong())} left"
    }
}

/**
 * The Audiobooks shelf: a "Continue listening" card, then every book in a grid with its cover
 * and progress. Tap a book to play it. Press and hold for finished, restart, edit and remove.
 *
 * @param onOpenBook opens the full player for the book and starts playing
 * @param onAddBook opens the Add audiobook screen
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AudiobooksScreen(
    viewModel: AudiobooksViewModel,
    onOpenBook: (bookId: String) -> Unit,
    onAddBook: () -> Unit
) {
    val shelf by viewModel.uiState.collectAsState()
    val continueBook by viewModel.continueBook.collectAsState()
    val nowPlaying by viewModel.nowPlaying.collectAsState()

    val snackbarHost = remember { SnackbarHostState() }
    LaunchedEffect(viewModel) {
        viewModel.messages.collect { snackbarHost.showSnackbar(it) }
    }

    var menuBook by remember { mutableStateOf<AudioBookEntity?>(null) }
    var editBook by remember { mutableStateOf<AudioBookEntity?>(null) }
    var removeBook by remember { mutableStateOf<AudioBookEntity?>(null) }

    Scaffold(
        topBar = { MtlTopBar(title = "Audiobooks", subtitle = "Listen • Resume • Sleep") },
        snackbarHost = { SnackbarHost(snackbarHost) }
    ) { innerPadding ->
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .testTag("audiobooks_screen"),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Continue listening
            val resume = continueBook
            if (resume != null) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    ContinueCard(book = resume, onClick = { onOpenBook(resume.id) })
                }
            }

            // Header with the count and the add button
            item(span = { GridItemSpan(maxLineSpan) }) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Audiobooks (${shelf.books.size})",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontFamily = FontFamily.Serif,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                    )
                    TextButton(onClick = onAddBook, modifier = Modifier.testTag("audiobooks_add_button")) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.size(4.dp))
                        Text("Add book")
                    }
                }
            }

            if (shelf.loaded && shelf.books.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    EmptyShelf(onAddBook = onAddBook)
                }
            }

            items(shelf.books, key = { it.id }) { book ->
                val isCurrent = nowPlaying.bookId == book.id
                Box {
                    BookTile(
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

            item(span = { GridItemSpan(maxLineSpan) }) { Spacer(modifier = Modifier.height(96.dp)) }
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
private fun ContinueCard(book: AudioBookEntity, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("audiobooks_continue"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            AudioCover(
                title = book.title,
                coverUri = book.coverUri,
                modifier = Modifier.size(72.dp),
                cornerRadius = 10.dp,
                initialsSize = 22
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = "Continue listening",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f)
                )
                Text(
                    text = book.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${statusLine(book)}  ·  ${AudioFormat.percent(progressOf(book))}%",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f)
                )
                LinearProgressIndicator(
                    progress = { progressOf(book) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.12f)
                )
            }
            Icon(
                Icons.Default.PlayArrow,
                contentDescription = "Resume",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(36.dp)
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BookTile(
    book: AudioBookEntity,
    isPlaying: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .testTag("audiobook_tile_${book.id}")
    ) {
        Box(modifier = Modifier.fillMaxWidth().aspectRatio(1f)) {
            AudioCover(
                title = book.title,
                coverUri = book.coverUri,
                modifier = Modifier.fillMaxSize(),
                cornerRadius = 12.dp,
                initialsSize = 28
            )
            if (book.finished) {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = "Finished",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .size(22.dp)
                        .background(MaterialTheme.colorScheme.surface, CircleShape)
                )
            }
            if (isPlaying) {
                Icon(
                    Icons.Default.GraphicEq,
                    contentDescription = "Playing now",
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(6.dp)
                        .size(24.dp)
                        .background(MaterialTheme.colorScheme.primary, CircleShape)
                        .padding(3.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = book.title,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = statusLine(book),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (!book.finished && book.progressMs > 0L) {
            LinearProgressIndicator(
                progress = { progressOf(book) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
        }
    }
}

@Composable
private fun EmptyShelf(onAddBook: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 40.dp, horizontal = 16.dp)
            .testTag("audiobooks_empty"),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            Icons.Default.Headphones,
            contentDescription = null,
            modifier = Modifier.size(56.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "No audiobooks yet",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "Add your own audio files: a folder of chapters, or a whole book in one file.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 4.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onAddBook) { Text("Add your first book") }
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
        title = { Text("Edit book") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
