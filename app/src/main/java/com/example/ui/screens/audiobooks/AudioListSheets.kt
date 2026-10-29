package com.example.ui.screens.audiobooks

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.audio.AudioFormat
import com.example.audio.ChapterSpan
import com.example.data.database.entities.AudioBookmarkEntity

// ======================================================================== Chapters

/**
 * Bottom sheet with the chapter list. The chapter being played is highlighted, tapping a
 * chapter jumps to its start, and each chapter has a menu to rename or remove it. "Add here"
 * starts a new chapter at the spot being played, which is how a book kept as one piece can be
 * given chapters.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioChaptersSheet(
    chapters: List<ChapterSpan>,
    currentIndex: Int,
    isPlaying: Boolean,
    onPlayChapter: (Int) -> Unit,
    onRename: (chapterId: String, newTitle: String) -> Unit,
    onDelete: (chapterId: String) -> Unit,
    onAddHere: (title: String) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = (currentIndex - 2).coerceAtLeast(0))

    var renaming by remember { mutableStateOf<ChapterSpan?>(null) }
    var adding by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .testTag("audio_chapters_sheet")
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Chapters (${chapters.size})",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                OutlinedButton(
                    onClick = { adding = true },
                    modifier = Modifier.testTag("audio_add_chapter")
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Add here")
                }
            }

            if (chapters.size <= 1) {
                Text(
                    text = "This book is one chapter. Use Add here to start a chapter at the spot you are listening to, or save bookmarks to mark places.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))

            LazyColumn(
                state = listState,
                modifier = Modifier.heightIn(max = 480.dp)
            ) {
                itemsIndexed(chapters, key = { _, span -> span.chapterId }) { position, span ->
                    ChapterRow(
                        span = span,
                        isCurrent = position == currentIndex,
                        isPlaying = isPlaying,
                        canRemove = chapters.size > 1,
                        onClick = {
                            onPlayChapter(position)
                            onDismiss()
                        },
                        onRename = { renaming = span },
                        onRemove = { onDelete(span.chapterId) }
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    renaming?.let { span ->
        TextInputDialog(
            title = "Rename chapter",
            label = "Title",
            initial = span.title,
            confirmText = "Save",
            onConfirm = { text ->
                onRename(span.chapterId, text)
                renaming = null
            },
            onDismiss = { renaming = null }
        )
    }

    if (adding) {
        TextInputDialog(
            title = "New chapter at this spot",
            label = "Title",
            initial = "Chapter ${chapters.size + 1}",
            confirmText = "Add",
            onConfirm = { text ->
                onAddHere(text)
                adding = false
            },
            onDismiss = { adding = false }
        )
    }
}

@Composable
private fun ChapterRow(
    span: ChapterSpan,
    isCurrent: Boolean,
    isPlaying: Boolean,
    canRemove: Boolean,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onRemove: () -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }
    val highlight = if (isCurrent) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f) else Color.Transparent

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(highlight)
            .clickable(onClick = onClick)
            .padding(start = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.width(28.dp), contentAlignment = Alignment.CenterStart) {
            if (isCurrent) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.GraphicEq else Icons.Default.PlayArrow,
                    contentDescription = "Current chapter",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        Text(
            text = span.title,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal,
            color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .padding(vertical = 12.dp)
        )
        Text(
            text = if (span.durationMs > 0L) AudioFormat.clock(span.durationMs) else "",
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 8.dp)
        )
        Box {
            IconButton(onClick = { menuOpen = true }) {
                Icon(Icons.Default.MoreVert, contentDescription = "Chapter options")
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text("Rename") },
                    onClick = {
                        menuOpen = false
                        onRename()
                    }
                )
                if (canRemove) {
                    DropdownMenuItem(
                        text = { Text("Remove chapter") },
                        onClick = {
                            menuOpen = false
                            onRemove()
                        }
                    )
                }
            }
        }
    }
}

// ======================================================================= Bookmarks

/**
 * Bottom sheet with the saved bookmarks, in book order. Tapping one jumps to it. Each has a
 * menu to edit its note, turn it into a chapter start, or delete it. "Add here" saves a new
 * bookmark at the spot being played, with an optional note.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioBookmarksSheet(
    bookmarks: List<AudioBookmarkEntity>,
    onAddHere: (note: String) -> Unit,
    onOpen: (AudioBookmarkEntity) -> Unit,
    onEditNote: (bookmarkId: String, note: String) -> Unit,
    onMakeChapter: (bookmarkId: String) -> Unit,
    onDelete: (bookmarkId: String) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var adding by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<AudioBookmarkEntity?>(null) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .testTag("audio_bookmarks_sheet")
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Bookmarks (${bookmarks.size})",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                OutlinedButton(
                    onClick = { adding = true },
                    modifier = Modifier.testTag("audio_add_bookmark")
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Add here")
                }
            }
            Spacer(modifier = Modifier.height(8.dp))

            if (bookmarks.isEmpty()) {
                Text(
                    text = "No bookmarks yet. Tap Add here to mark the spot you are listening to.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)
                )
            } else {
                LazyColumn(modifier = Modifier.heightIn(max = 480.dp)) {
                    itemsIndexed(bookmarks, key = { _, mark -> mark.id }) { _, mark ->
                        BookmarkRow(
                            bookmark = mark,
                            onClick = {
                                onOpen(mark)
                                onDismiss()
                            },
                            onEditNote = { editing = mark },
                            onMakeChapter = { onMakeChapter(mark.id) },
                            onDelete = { onDelete(mark.id) }
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    if (adding) {
        TextInputDialog(
            title = "Add bookmark",
            label = "Note (optional)",
            initial = "",
            confirmText = "Save",
            onConfirm = { text ->
                onAddHere(text)
                adding = false
            },
            onDismiss = { adding = false }
        )
    }

    editing?.let { mark ->
        TextInputDialog(
            title = "Bookmark note",
            label = "Note",
            initial = mark.note,
            confirmText = "Save",
            onConfirm = { text ->
                onEditNote(mark.id, text)
                editing = null
            },
            onDismiss = { editing = null }
        )
    }
}

@Composable
private fun BookmarkRow(
    bookmark: AudioBookmarkEntity,
    onClick: () -> Unit,
    onEditNote: () -> Unit,
    onMakeChapter: () -> Unit,
    onDelete: () -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = 20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = AudioFormat.clock(bookmark.bookPositionMs),
            style = MaterialTheme.typography.titleSmall,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.width(76.dp)
        )
        Text(
            text = bookmark.note.ifBlank { "Bookmark" },
            style = MaterialTheme.typography.bodyLarge,
            color = if (bookmark.note.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant
            else MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .padding(vertical = 12.dp)
        )
        Box {
            IconButton(onClick = { menuOpen = true }) {
                Icon(Icons.Default.MoreVert, contentDescription = "Bookmark options")
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text("Edit note") },
                    onClick = {
                        menuOpen = false
                        onEditNote()
                    }
                )
                DropdownMenuItem(
                    text = { Text("Make it a chapter") },
                    onClick = {
                        menuOpen = false
                        onMakeChapter()
                    }
                )
                DropdownMenuItem(
                    text = { Text("Delete") },
                    onClick = {
                        menuOpen = false
                        onDelete()
                    }
                )
            }
        }
    }
}

// ========================================================================= Shared

/** A small dialog with one text field. The caller closes it after [onConfirm]. */
@Composable
private fun TextInputDialog(
    title: String,
    label: String,
    initial: String,
    confirmText: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var text by remember { mutableStateOf(initial) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text(label) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text) }) { Text(confirmText) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
