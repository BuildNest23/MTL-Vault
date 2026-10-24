package com.example.ui.screens.library

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.importexport.ChapterSplitter
import com.example.ui.components.ChapterSplitDialog
import com.example.ui.components.MtlActionButton
import com.example.ui.components.MtlButtonStyle
import com.example.ui.components.EmptyState
import com.example.ui.components.MtlTopBar
import com.example.ui.components.NovelCoverCard
import com.example.ui.components.PromptDialog

private enum class LibraryFilter(val label: String) {
    ALL("All"),
    UNREAD("Unread"),
    RULES("Has rules")
}

private enum class LibrarySort(val label: String) {
    LAST_READ("Last read"),
    MOST_UNREAD("Most unread"),
    TITLE("Title A–Z")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    viewModel: LibraryViewModel,
    onOpenNovel: (String) -> Unit
) {
    val context = LocalContext.current
    val novels by viewModel.novels.collectAsStateWithLifecycle()
    val pendingImportText by viewModel.pendingImportText.collectAsStateWithLifecycle()

    var showCreateNovelDialog by remember { mutableStateOf(false) }
    var showPasteImportDialog by remember { mutableStateOf(false) }
    var filter by rememberSaveable { mutableStateOf(LibraryFilter.ALL) }
    var sort by rememberSaveable { mutableStateOf(LibrarySort.LAST_READ) }
    var showSortMenu by remember { mutableStateOf(false) }

    val visibleNovels = remember(novels, filter, sort) {
        val filtered = when (filter) {
            LibraryFilter.ALL -> novels
            LibraryFilter.UNREAD -> novels.filter { it.unreadChapters > 0 }
            LibraryFilter.RULES -> novels.filter { it.hasRules }
        }
        when (sort) {
            LibrarySort.LAST_READ -> filtered.sortedByDescending { it.updatedAt }
            LibrarySort.MOST_UNREAD -> filtered.sortedByDescending { it.unreadChapters }
            LibrarySort.TITLE -> filtered.sortedBy { it.title.lowercase() }
        }
    }
    val fullSpan: (androidx.compose.foundation.lazy.grid.LazyGridItemSpanScope.() -> GridItemSpan) =
        { GridItemSpan(maxLineSpan) }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            viewModel.startFileImport(context, null, uri)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is LibraryEvent.ShowToast -> Toast.makeText(context, event.message, Toast.LENGTH_SHORT).show()
                is LibraryEvent.OpenNovel -> onOpenNovel(event.novelId)
            }
        }
    }

    Scaffold(
        topBar = {
            MtlTopBar(
                title = "MTL Vault",
                subtitle = "Save • Read • Listen"
            )
        }
    ) { innerPadding ->
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item(span = fullSpan) {
                Spacer(modifier = Modifier.height(4.dp))
            }

            item(span = fullSpan) {
                // Actions Header Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Library (${novels.size})",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontFamily = FontFamily.Serif,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 18.sp
                                )
                            )

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                TextButton(
                                    onClick = { viewModel.fetchMissingInfo(context) },
                                    modifier = Modifier.testTag("library_fetch_info_button")
                                ) {
                                    Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(15.dp))
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text("Fetch covers", fontSize = 12.sp)
                                }
                                TextButton(
                                    onClick = { viewModel.loadSampleNovel() }
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(15.dp))
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text("Example", fontSize = 12.sp)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            MtlActionButton(
                                text = "Novel",
                                icon = Icons.Default.Add,
                                onClick = { showCreateNovelDialog = true },
                                style = MtlButtonStyle.Primary,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("library_new_novel_button")
                            )
                            MtlActionButton(
                                text = "Select file",
                                icon = Icons.Default.FolderOpen,
                                onClick = { filePickerLauncher.launch("*/*") },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("library_import_file_button")
                            )
                            MtlActionButton(
                                text = "Paste",
                                icon = Icons.Default.ContentPaste,
                                onClick = { showPasteImportDialog = true },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("library_paste_import_button")
                            )
                        }
                    }
                }
            }

            if (novels.isNotEmpty()) {
                item(span = fullSpan) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            LibraryFilter.values().forEach { option ->
                                FilterChip(
                                    selected = filter == option,
                                    onClick = { filter = option },
                                    label = { Text(option.label, fontSize = 12.sp) }
                                )
                            }
                        }
                        Box {
                            TextButton(onClick = { showSortMenu = true }) {
                                Icon(
                                    Icons.AutoMirrored.Filled.Sort,
                                    contentDescription = "Sort",
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(sort.label, fontSize = 12.sp, maxLines = 1)
                            }
                            DropdownMenu(
                                expanded = showSortMenu,
                                onDismissRequest = { showSortMenu = false }
                            ) {
                                LibrarySort.values().forEach { option ->
                                    DropdownMenuItem(
                                        text = { Text(option.label) },
                                        onClick = {
                                            sort = option
                                            showSortMenu = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (novels.isEmpty()) {
                item(span = fullSpan) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        EmptyState(
                            icon = Icons.Default.AutoStories,
                            title = "No novels yet",
                            message = "Tap '+ Novel' to create your first book, or load a sample novel to check out the reader.",
                            actionText = "+ New Novel",
                            onActionClick = { showCreateNovelDialog = true }
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedButton(
                            onClick = { viewModel.loadSampleNovel() },
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.AutoStories, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Load Example Novel")
                        }
                    }
                }
            } else if (visibleNovels.isEmpty()) {
                item(span = fullSpan) {
                    Text(
                        text = "No novels match this filter.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            } else {
                items(
                    items = visibleNovels,
                    key = { it.id }
                ) { novel ->
                    NovelCoverCard(
                        novel = novel,
                        onClick = { onOpenNovel(novel.id) }
                    )
                }
            }

            item(span = fullSpan) {
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    if (showCreateNovelDialog) {
        PromptDialog(
            title = "New Novel",
            message = "Enter a title for your new novel:",
            placeholder = "e.g. Martial Peak, Lord of the Mysteries",
            confirmText = "Create",
            onConfirm = { title ->
                showCreateNovelDialog = false
                viewModel.createNovel(title)
            },
            onDismiss = { showCreateNovelDialog = false }
        )
    }

    if (showPasteImportDialog) {
        PromptDialog(
            title = "Paste Chapter Text",
            message = "Paste raw translated text below. You can split it into chapters in the next step.",
            placeholder = "Paste chapters here...",
            multiline = true,
            confirmText = "Next",
            onConfirm = { text ->
                showPasteImportDialog = false
                if (text.isNotBlank()) {
                    viewModel.startTextImport(null, text)
                }
            },
            onDismiss = { showPasteImportDialog = false }
        )
    }

    pendingImportText?.let { rawText ->
        ChapterSplitDialog(
            totalWords = ChapterSplitter.countWords(rawText),
            onSelectSplitMode = { mode, customMarker ->
                viewModel.completeImport(mode, customMarker)
            },
            onDismiss = { viewModel.cancelImport() }
        )
    }
}
