package com.example.ui.screens.audiobooks

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.audio.AudioFormat
import com.example.audio.DraftFile

/**
 * The "Add audiobook" screen. Step one picks a folder or files, step two lets the user check
 * and edit the book (title, order of files, chapters), and Save stores it.
 *
 * @param onBack called when the user leaves the screen without saving
 * @param onSaved called with the new book's id once it is saved
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddAudiobookScreen(
    viewModel: AddAudiobookViewModel,
    onBack: () -> Unit,
    onSaved: (bookId: String) -> Unit
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(viewModel) {
        viewModel.saved.collect { onSaved(it) }
    }

    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) viewModel.scanFolder(uri)
    }
    val filesPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        viewModel.scanFiles(uris)
    }

    val inReview = state.phase == ImportPhase.REVIEW || state.phase == ImportPhase.SAVING
    // Back from the review page returns to the first step instead of leaving the screen
    BackHandler(enabled = inReview) { viewModel.cancel() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (inReview) "Check your book" else "Add audiobook") },
                navigationIcon = {
                    IconButton(
                        onClick = { if (inReview) viewModel.cancel() else onBack() },
                        modifier = Modifier.testTag("add_audio_back")
                    ) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        bottomBar = {
            if (inReview) {
                Surface(tonalElevation = 3.dp) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        state.error?.let {
                            Text(
                                text = it,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier
                                    .padding(bottom = 8.dp)
                                    .testTag("add_audio_error")
                            )
                        }
                        Button(
                            onClick = viewModel::save,
                            enabled = state.canSave,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("add_audio_save")
                        ) {
                            if (state.phase == ImportPhase.SAVING) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                                Spacer(modifier = Modifier.size(8.dp))
                                Text("Saving…")
                            } else {
                                Text("Add to Audiobooks")
                            }
                        }
                    }
                }
            }
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            when (state.phase) {
                ImportPhase.PICK -> PickStep(
                    error = state.error,
                    onPickFolder = { folderPicker.launch(null) },
                    onPickFiles = { filesPicker.launch(arrayOf("*/*")) }
                )
                ImportPhase.SCANNING -> ScanningStep()
                ImportPhase.REVIEW, ImportPhase.SAVING -> ReviewStep(state, viewModel)
            }
        }
    }
}

// ============================================================================ Step 1

@Composable
private fun PickStep(
    error: String?,
    onPickFolder: () -> Unit,
    onPickFiles: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Default.Headphones,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Add an audiobook",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Pick a folder with all the parts of a book, or pick the audio files yourself. " +
                "The audio stays where it is. Nothing is copied.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        if (error != null) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = error,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.testTag("add_audio_error")
            )
        }

        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = onPickFolder,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("add_audio_pick_folder")
        ) {
            Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.size(8.dp))
            Text("Choose a folder")
        }
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedButton(
            onClick = onPickFiles,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("add_audio_pick_files")
        ) {
            Icon(Icons.Default.LibraryMusic, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.size(8.dp))
            Text("Choose files")
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Works with MP3, M4B, M4A, AAC, OGG, Opus, FLAC and WAV. " +
                "WMA and copy-protected files can't be played.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

// ============================================================================ Step 2

@Composable
private fun ScanningStep() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator(modifier = Modifier.testTag("add_audio_scanning"))
        Spacer(modifier = Modifier.height(16.dp))
        Text("Looking at your audio files…", style = MaterialTheme.typography.bodyLarge)
        Text(
            text = "A big book can take a few seconds.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// ============================================================================ Step 3

@Composable
private fun ReviewStep(state: AddAudiobookUiState, viewModel: AddAudiobookViewModel) {
    val draft = state.draft ?: return
    val busy = state.phase == ImportPhase.SAVING

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("add_audio_review"),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Title, author, narrator
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = draft.title,
                    onValueChange = viewModel::setTitle,
                    label = { Text("Title") },
                    singleLine = true,
                    enabled = !busy,
                    isError = draft.title.isBlank(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("add_audio_title")
                )
                OutlinedTextField(
                    value = draft.author,
                    onValueChange = viewModel::setAuthor,
                    label = { Text("Author") },
                    singleLine = true,
                    enabled = !busy,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("add_audio_author")
                )
                OutlinedTextField(
                    value = draft.narrator,
                    onValueChange = viewModel::setNarrator,
                    label = { Text("Narrator") },
                    singleLine = true,
                    enabled = !busy,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("add_audio_narrator")
                )
            }
        }

        // Summary line
        item {
            val fileWord = if (draft.files.size == 1) "file" else "files"
            val chapterWord = if (state.chapterCount == 1) "chapter" else "chapters"
            Text(
                text = "${draft.files.size} $fileWord  ·  ${AudioFormat.duration(draft.totalDurationMs)}  ·  ${state.chapterCount} $chapterWord",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.testTag("add_audio_summary")
            )
        }

        // Things worth knowing
        val notes = buildList {
            if (draft.skippedNames.isNotEmpty()) {
                add("Left out because they can't be played: ${draft.skippedNames.take(3).joinToString()}" +
                    if (draft.skippedNames.size > 3) " and ${draft.skippedNames.size - 3} more." else ".")
            }
            if (draft.truncated) add("This folder is very large. Only the first part was read.")
            if (state.filesWithoutLength > 0) {
                add("${state.filesWithoutLength} file(s) have no readable length. They may be damaged. The book total will be short until they play.")
            }
        }
        if (notes.isNotEmpty()) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        for (note in notes) {
                            Text(note, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }

        // Chapter choices
        item { ChapterChoices(state, viewModel, enabled = !busy) }

        // Files
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Files, in playing order",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                if (draft.files.size > 1) {
                    TextButton(onClick = viewModel::sortByName, enabled = !busy, modifier = Modifier.testTag("add_audio_sort")) {
                        Text("Sort by name")
                    }
                }
            }
        }
        itemsIndexed(draft.files, key = { index, file -> "$index|${file.uri}" }) { index, file ->
            FileRow(
                index = index,
                file = file,
                isFirst = index == 0,
                isLast = index == draft.files.lastIndex,
                canRemove = draft.files.size > 1,
                enabled = !busy,
                onUp = { viewModel.moveFile(index, -1) },
                onDown = { viewModel.moveFile(index, 1) },
                onRemove = { viewModel.removeFile(index) }
            )
        }
    }
}

@Composable
private fun ChapterChoices(state: AddAudiobookUiState, viewModel: AddAudiobookViewModel, enabled: Boolean) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Chapters", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)

            if (!state.isSingleFile) {
                Text(
                    text = "Each file becomes one chapter. A file that has chapters inside it keeps them.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                return@Column
            }

            if (state.showEmbeddedSwitch) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Use the ${state.embeddedChapterCount} chapters found in the file",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Recommended. Turn off to choose below instead.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = state.useEmbeddedChapters,
                        onCheckedChange = viewModel::setUseEmbeddedChapters,
                        enabled = enabled,
                        modifier = Modifier.testTag("add_audio_use_embedded")
                    )
                }
            } else {
                Text(
                    text = "No chapters were found inside this file.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (state.showSplitOption) {
                ChoiceRow(
                    selected = !state.splitByTime,
                    title = "Keep as one file",
                    subtitle = "Recommended. One chapter, and you can add your own chapters or bookmarks while listening.",
                    enabled = enabled,
                    tag = "add_audio_keep_whole",
                    onClick = { viewModel.setSplitByTime(false) }
                )
                ChoiceRow(
                    selected = state.splitByTime,
                    title = "Split into parts of equal length",
                    subtitle = "Makes next and previous chapter jump by a set time. The file itself is not changed.",
                    enabled = enabled,
                    tag = "add_audio_split",
                    onClick = { viewModel.setSplitByTime(true) }
                )
                if (state.splitByTime) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 40.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        for (minutes in AddAudiobookUiState.SPLIT_OPTIONS.take(4)) {
                            FilterChip(
                                selected = state.splitMinutes == minutes,
                                onClick = { viewModel.setSplitMinutes(minutes) },
                                enabled = enabled,
                                label = { Text("$minutes min") }
                            )
                        }
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 40.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        for (minutes in AddAudiobookUiState.SPLIT_OPTIONS.drop(4)) {
                            FilterChip(
                                selected = state.splitMinutes == minutes,
                                onClick = { viewModel.setSplitMinutes(minutes) },
                                enabled = enabled,
                                label = { Text("$minutes min") }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChoiceRow(
    selected: Boolean,
    title: String,
    subtitle: String,
    enabled: Boolean,
    tag: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onClick)
            .testTag(tag),
        verticalAlignment = Alignment.Top
    ) {
        RadioButton(selected = selected, onClick = null, enabled = enabled, modifier = Modifier.padding(top = 4.dp, end = 12.dp))
        Column {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun FileRow(
    index: Int,
    file: DraftFile,
    isFirst: Boolean,
    isLast: Boolean,
    canRemove: Boolean,
    enabled: Boolean,
    onUp: () -> Unit,
    onDown: () -> Unit,
    onRemove: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("add_audio_file_$index")
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, top = 4.dp, end = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${index + 1}",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(end = 12.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = file.name,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = if (file.durationMs > 0L) AudioFormat.duration(file.durationMs) else "Length unknown",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onUp, enabled = enabled && !isFirst) {
                Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Move up")
            }
            IconButton(onClick = onDown, enabled = enabled && !isLast) {
                Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Move down")
            }
            if (canRemove) {
                IconButton(onClick = onRemove, enabled = enabled) {
                    Icon(Icons.Default.Close, contentDescription = "Leave this file out")
                }
            }
        }
    }
}
