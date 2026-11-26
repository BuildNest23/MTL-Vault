package com.example.ui.screens.detail

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.outlined.CollectionsBookmark
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import com.example.data.database.entities.NovelAnnotationEntity
import com.example.importexport.ChapterSplitter
import com.example.replacement.ReplacementEngine
import com.example.tts.TtsPlaybackInfo
import com.example.tts.TtsState
import com.example.ui.components.ChapterSplitDialog
import com.example.ui.components.ConfirmDialog
import com.example.ui.components.EditNovelDialog
import com.example.ui.components.EmptyState
import com.example.ui.components.MtlTopBar
import com.example.ui.components.NovelSearchResultsList
import com.example.ui.components.NovelShelvesSheet
import com.example.ui.components.PromptDialog
import com.example.ui.screens.reader.AnnotationEditorSheet
import com.example.ui.screens.reader.ReaderAnnotationsList

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NovelDetailScreen(
    viewModel: NovelDetailViewModel,
    onBackClick: () -> Unit,
    onOpenReader: (String) -> Unit,
    onPlayNovel: (String) -> Unit,
    onOpenEditor: (String?) -> Unit,
    onOpenReplacements: () -> Unit,
    onOpenVoices: () -> Unit = {},
    ttsInfo: TtsPlaybackInfo = TtsPlaybackInfo(),
    onOpenReaderAtParagraph: (chapterId: String, paragraphIndex: Int) -> Unit = { chId, _ -> onOpenReader(chId) }
) {
    val context = LocalContext.current
    val novel by viewModel.novel.collectAsStateWithLifecycle()
    val chapters by viewModel.chapters.collectAsStateWithLifecycle()
    val annotations by viewModel.annotations.collectAsStateWithLifecycle()
    val shelves by viewModel.shelves.collectAsStateWithLifecycle()
    val novelShelfIds by viewModel.novelShelfIds.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val searchResults by viewModel.searchResults.collectAsStateWithLifecycle()
    val isSearching by viewModel.isSearching.collectAsStateWithLifecycle()
    val chaptersLoaded by viewModel.chaptersLoaded.collectAsStateWithLifecycle()
    val hideReadChapters by viewModel.hideReadChapters.collectAsStateWithLifecycle()
    val isSelectionMode by viewModel.isSelectionMode.collectAsStateWithLifecycle()
    val selectedChapterIds by viewModel.selectedChapterIds.collectAsStateWithLifecycle()
    val pendingImportText by viewModel.pendingImportText.collectAsStateWithLifecycle()

    var showDeleteNovelDialog by remember { mutableStateOf(false) }
    var chapterToDeleteId by remember { mutableStateOf<String?>(null) }
    var showDeleteSelectedChaptersDialog by remember { mutableStateOf(false) }
    var showEditMetaDialog by remember { mutableStateOf(false) }
    var showPasteImportDialog by remember { mutableStateOf(false) }
    var showAddChaptersSheet by remember { mutableStateOf(false) }
    var showNotesSheet by remember { mutableStateOf(false) }
    var showSearchSheet by remember { mutableStateOf(false) }
    var showShelvesSheet by remember { mutableStateOf(false) }
    var editingAnnotation by remember { mutableStateOf<NovelAnnotationEntity?>(null) }
    var sortDescending by rememberSaveable { mutableStateOf(false) }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            viewModel.startFileImport(context, uri)
        }
    }

    var pendingExportSelectedOnly by remember { mutableStateOf(false) }

    val createEpubLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/epub+zip")
    ) { uri ->
        if (uri != null) {
            viewModel.exportToEpubUri(context, uri, selectedOnly = pendingExportSelectedOnly)
        }
    }

    BackHandler {
        if (isSelectionMode) {
            viewModel.toggleSelectionMode()
        } else {
            onBackClick()
        }
    }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is DetailEvent.ShowToast -> Toast.makeText(context, event.message, Toast.LENGTH_SHORT).show()
                is DetailEvent.NovelDeleted -> onBackClick()
            }
        }
    }

    val currentNovel = novel

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            MtlTopBar(
                title = currentNovel?.title ?: "Novel Details",
                onBackClick = {
                    if (isSelectionMode) viewModel.toggleSelectionMode() else onBackClick()
                },
                actions = {
                    if (chapters.isNotEmpty()) {
                        IconButton(
                            onClick = { showSearchSheet = true },
                            modifier = Modifier.testTag("detail_search_button")
                        ) {
                            Icon(Icons.Default.Search, contentDescription = "Search inside novel")
                        }
                    }
                    IconButton(
                        onClick = { showEditMetaDialog = true },
                        modifier = Modifier.testTag("detail_edit_meta_button")
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit Novel Info")
                    }
                    var novelMenuOpen by remember { mutableStateOf(false) }
                    Box {
                        IconButton(
                            onClick = { novelMenuOpen = true },
                            modifier = Modifier.testTag("detail_novel_menu_button")
                        ) {
                            Icon(Icons.Default.MoreVert, contentDescription = "More options")
                        }
                        DropdownMenu(
                            expanded = novelMenuOpen,
                            onDismissRequest = { novelMenuOpen = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Manage shelves") },
                                leadingIcon = { Icon(Icons.Outlined.CollectionsBookmark, contentDescription = null) },
                                onClick = {
                                    novelMenuOpen = false
                                    showShelvesSheet = true
                                },
                                modifier = Modifier.testTag("detail_manage_shelves_button")
                            )
                            DropdownMenuItem(
                                text = { Text("Delete novel", color = MaterialTheme.colorScheme.error) },
                                onClick = {
                                    novelMenuOpen = false
                                    showDeleteNovelDialog = true
                                },
                                modifier = Modifier.testTag("detail_delete_novel_button")
                            )
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        if (currentNovel == null || !chaptersLoaded) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        val totalChapters = chapters.size
        val readChapters = remember(chapters) { chapters.count { it.read } }
        val totalWords = remember(chapters) { chapters.sumOf { it.wordCount.toLong() } }
        val progressPercent = if (totalChapters > 0) (readChapters * 100) / totalChapters else 0
        val rulesCount = remember(currentNovel.replacements) {
            ReplacementEngine.parseRules(currentNovel.replacements).size
        }

        val targetChapter = remember(chapters, currentNovel.lastChapterId) {
            val lastId = currentNovel.lastChapterId
            if (!lastId.isNullOrBlank()) {
                chapters.find { it.id == lastId }
            } else {
                chapters.firstOrNull { !it.read } ?: chapters.firstOrNull()
            } ?: chapters.firstOrNull()
        }

        val readButtonLabel = remember(targetChapter, chapters, currentNovel.lastChapterId) {
            if (targetChapter == null) {
                "Read"
            } else {
                val anyRead = chapters.any { it.read }
                val resuming = !targetChapter.read && targetChapter.readingParagraphIndex > 0
                when {
                    resuming -> "Continue Reading"
                    currentNovel.lastChapterId.isNullOrBlank() && !anyRead -> "Start Reading"
                    else -> "Resume Reading"
                }
            }
        }

        val ttsActive = ttsInfo.state == TtsState.PLAYING || ttsInfo.state == TtsState.PAUSED
        val isSpeakingThisNovel = ttsActive && ttsInfo.novelId == currentNovel.id

        val originalPositionMap = remember(chapters) {
            chapters.mapIndexed { idx, ch -> ch.id to (idx + 1) }.toMap()
        }

        val visibleChapters = remember(chapters, hideReadChapters, sortDescending) {
            val filtered = if (hideReadChapters) chapters.filter { !it.read } else chapters
            if (sortDescending) filtered.asReversed() else filtered
        }

        val lazyListState = rememberLazyListState()

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            LazyColumn(
                state = lazyListState,
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 840.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Book-Jacket Hero Header
                item {
                    NovelBookJacketHero(
                        novel = currentNovel,
                        totalChapters = totalChapters,
                        readChapters = readChapters,
                        totalWords = totalWords,
                        progressPercent = progressPercent,
                        readButtonLabel = readButtonLabel,
                        isSpeakingThisNovel = isSpeakingThisNovel,
                        hasChapters = chapters.isNotEmpty(),
                        onReadClick = {
                            if (targetChapter != null) {
                                onOpenReader(targetChapter.id)
                            } else {
                                Toast.makeText(context, "No chapters available to read", Toast.LENGTH_SHORT).show()
                            }
                        },
                        onPlayClick = {
                            if (targetChapter != null) {
                                onPlayNovel(targetChapter.id)
                            } else {
                                Toast.makeText(context, "No chapters available to play", Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                }

                // Editorial Utility Strip
                item {
                    NovelUtilityStrip(
                        rulesCount = rulesCount,
                        notesCount = annotations.size,
                        onAddChapters = { showAddChaptersSheet = true },
                        onOpenNotes = { showNotesSheet = true },
                        onOpenVoices = onOpenVoices,
                        onOpenReplacements = onOpenReplacements,
                        onExportEpub = {
                            val safeName = currentNovel.title.replace(Regex("[^a-zA-Z0-9.-]"), "_").trim('_')
                            pendingExportSelectedOnly = false
                            createEpubLauncher.launch("$safeName.epub")
                        }
                    )
                }

                // Assigned Custom Shelves Strip
                item {
                    val assignedShelves = remember(shelves, novelShelfIds) {
                        val idSet = novelShelfIds.toSet()
                        shelves.filter { it.id in idSet }
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        assignedShelves.forEach { shelf ->
                            FilterChip(
                                selected = true,
                                onClick = { showShelvesSheet = true },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Bookmark,
                                        contentDescription = null,
                                        modifier = Modifier.size(15.dp)
                                    )
                                },
                                label = { Text(shelf.name, style = MaterialTheme.typography.labelMedium) },
                                shape = RoundedCornerShape(10.dp),
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                    selectedLeadingIconColor = MaterialTheme.colorScheme.secondary
                                )
                            )
                        }
                        AssistChip(
                            onClick = { showShelvesSheet = true },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            label = {
                                Text(
                                    text = if (assignedShelves.isEmpty()) "Add to shelf" else "Shelves",
                                    style = MaterialTheme.typography.labelMedium
                                )
                            },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.testTag("detail_shelves_chip")
                        )
                    }
                }

                // Chapters Section Header & Controls
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Chapters",
                                style = MaterialTheme.typography.headlineSmall,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Text(
                                text = if (hideReadChapters) {
                                    "${visibleChapters.size} unread of $totalChapters"
                                } else {
                                    "$totalChapters total"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            FilterChip(
                                selected = hideReadChapters,
                                onClick = { viewModel.toggleHideReadChapters() },
                                label = { Text("Unread", style = MaterialTheme.typography.labelSmall) },
                                shape = RoundedCornerShape(10.dp),
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                ),
                                modifier = Modifier.testTag("detail_hide_read_chip")
                            )

                            IconButton(
                                onClick = { sortDescending = !sortDescending },
                                modifier = Modifier.testTag("detail_sort_chapters_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.SwapVert,
                                    contentDescription = if (sortDescending) "Sort oldest first" else "Sort newest first",
                                    tint = if (sortDescending) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            OutlinedButton(
                                onClick = { viewModel.toggleSelectionMode() },
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                modifier = Modifier.heightIn(min = 36.dp)
                            ) {
                                Text(
                                    text = if (isSelectionMode) "Done" else "Select",
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }
                        }
                    }
                }

                // Selection Mode Action Bar
                if (isSelectionMode) {
                    item {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f))
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Text(
                                    text = "${selectedChapterIds.size} chapter(s) selected",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = { viewModel.selectAllChapters(visibleChapters.map { it.id }) },
                                        modifier = Modifier
                                            .weight(1f)
                                            .heightIn(min = 40.dp)
                                            .testTag("detail_select_all_button"),
                                        shape = RoundedCornerShape(10.dp),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Text("All", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                                    }
                                    OutlinedButton(
                                        onClick = { viewModel.clearChapterSelection() },
                                        modifier = Modifier
                                            .weight(1f)
                                            .heightIn(min = 40.dp)
                                            .testTag("detail_clear_selection_button"),
                                        shape = RoundedCornerShape(10.dp),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                        enabled = selectedChapterIds.isNotEmpty()
                                    ) {
                                        Text("Clear", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                                    }
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = {
                                            val safeName = currentNovel.title.replace(Regex("[^a-zA-Z0-9.-]"), "_").trim('_')
                                            pendingExportSelectedOnly = true
                                            createEpubLauncher.launch("${safeName}_selected_${selectedChapterIds.size}.epub")
                                        },
                                        modifier = Modifier
                                            .weight(1.2f)
                                            .heightIn(min = 40.dp)
                                            .testTag("detail_export_selected_button"),
                                        shape = RoundedCornerShape(10.dp),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                        enabled = selectedChapterIds.isNotEmpty()
                                    ) {
                                        Icon(Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Export EPUB", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                                    }
                                    OutlinedButton(
                                        onClick = { showDeleteSelectedChaptersDialog = true },
                                        modifier = Modifier
                                            .weight(1f)
                                            .heightIn(min = 40.dp)
                                            .testTag("detail_delete_selected_button"),
                                        shape = RoundedCornerShape(10.dp),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                        enabled = selectedChapterIds.isNotEmpty(),
                                        colors = ButtonDefaults.outlinedButtonColors(
                                            contentColor = MaterialTheme.colorScheme.error
                                        ),
                                        border = BorderStroke(
                                            1.dp,
                                            if (selectedChapterIds.isNotEmpty()) MaterialTheme.colorScheme.error.copy(alpha = 0.5f)
                                            else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                                        )
                                    ) {
                                        Icon(
                                            Icons.Default.Delete,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp),
                                            tint = if (selectedChapterIds.isNotEmpty()) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            "Delete",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            color = if (selectedChapterIds.isNotEmpty()) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Chapters Content
                if (chapters.isEmpty()) {
                    item {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            EmptyState(
                                icon = Icons.Default.Book,
                                title = "No chapters yet",
                                message = "Import an EPUB/TXT file, paste translated text, or write your first chapter.",
                                actionText = "Add Chapters",
                                onActionClick = { showAddChaptersSheet = true }
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            OutlinedButton(
                                onClick = { viewModel.addSampleChapters() },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.heightIn(min = 48.dp)
                            ) {
                                Icon(Icons.Default.Book, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Load Example Chapters")
                            }
                        }
                    }
                } else if (visibleChapters.isEmpty()) {
                    item {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "All chapters have been read",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                TextButton(
                                    onClick = { viewModel.setHideReadChapters(false) },
                                    modifier = Modifier.testTag("show_read_chapters_button")
                                ) {
                                    Text("Show all")
                                }
                            }
                        }
                    }
                } else {
                    itemsIndexed(
                        items = visibleChapters,
                        key = { _, ch -> ch.id }
                    ) { index, ch ->
                        val isSelected = selectedChapterIds.contains(ch.id)
                        val isCurrent = currentNovel.lastChapterId == ch.id
                        val isSpeaking = isSpeakingThisNovel && ttsInfo.chapterId == ch.id
                        val originalPos = originalPositionMap[ch.id] ?: (index + 1)

                        EditorialChapterRow(
                            chapter = ch,
                            displayIndex = originalPos,
                            isSelectionMode = isSelectionMode,
                            isSelected = isSelected,
                            isCurrent = isCurrent,
                            isSpeaking = isSpeaking,
                            ttsInfo = ttsInfo,
                            onClick = {
                                if (isSelectionMode) {
                                    viewModel.toggleChapterSelection(ch.id)
                                } else {
                                    onOpenReader(ch.id)
                                }
                            },
                            onLongClick = {
                                if (!isSelectionMode) {
                                    viewModel.enterSelectionMode(ch.id)
                                }
                            },
                            onToggleSelect = { viewModel.toggleChapterSelection(ch.id) },
                            onToggleRead = { viewModel.toggleChapterRead(ch.id) },
                            onEdit = { onOpenEditor(ch.id) },
                            onDelete = { chapterToDeleteId = ch.id }
                        )
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }
    }

    if (showAddChaptersSheet) {
        AddChaptersBottomSheet(
            onWriteChapter = {
                showAddChaptersSheet = false
                onOpenEditor(null)
            },
            onImportFile = {
                showAddChaptersSheet = false
                filePickerLauncher.launch("*/*")
            },
            onPasteText = {
                showAddChaptersSheet = false
                showPasteImportDialog = true
            },
            onDismiss = { showAddChaptersSheet = false }
        )
    }

    if (showDeleteNovelDialog) {
        ConfirmDialog(
            title = "Delete Novel?",
            message = "This will permanently delete \"${currentNovel?.title}\" and all of its ${chapters.size} chapter(s). This cannot be undone.",
            confirmText = "Delete Novel",
            isDestructive = true,
            onConfirm = {
                showDeleteNovelDialog = false
                viewModel.deleteNovel(context)
            },
            onDismiss = { showDeleteNovelDialog = false }
        )
    }

    chapterToDeleteId?.let { cid ->
        ConfirmDialog(
            title = "Delete Chapter?",
            message = "Are you sure you want to delete this chapter?",
            confirmText = "Delete",
            isDestructive = true,
            onConfirm = {
                chapterToDeleteId = null
                viewModel.deleteChapter(cid)
            },
            onDismiss = { chapterToDeleteId = null }
        )
    }

    if (showDeleteSelectedChaptersDialog) {
        val message = if (chapters.isNotEmpty() && selectedChapterIds.size == chapters.size) {
            "Delete all chapters? This cannot be undone."
        } else {
            "Delete ${selectedChapterIds.size} selected chapter(s)? This cannot be undone."
        }
        ConfirmDialog(
            title = "Delete Chapters?",
            message = message,
            confirmText = "Delete",
            isDestructive = true,
            onConfirm = {
                showDeleteSelectedChaptersDialog = false
                viewModel.deleteSelectedChapters()
            },
            onDismiss = { showDeleteSelectedChaptersDialog = false }
        )
    }

    if (showEditMetaDialog && currentNovel != null) {
        EditNovelDialog(
            title = currentNovel.title,
            author = currentNovel.author,
            translator = currentNovel.translator,
            description = currentNovel.description,
            coverUri = currentNovel.coverUri,
            onSave = { newTitle, newAuthor, newTranslator, newDescription, newCover, removeCover ->
                showEditMetaDialog = false
                viewModel.saveNovelDetails(
                    context = context,
                    title = newTitle,
                    author = newAuthor,
                    translator = newTranslator,
                    description = newDescription,
                    newCover = newCover,
                    removeCover = removeCover
                )
            },
            onDismiss = { showEditMetaDialog = false }
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
                    viewModel.startTextImport(text)
                }
            },
            onDismiss = { showPasteImportDialog = false }
        )
    }

    pendingImportText?.let { rawText ->
        val importWordCount = remember(rawText) { ChapterSplitter.countWords(rawText) }
        ChapterSplitDialog(
            totalWords = importWordCount,
            onSelectSplitMode = { mode, customMarker ->
                viewModel.completeImport(mode, customMarker)
            },
            onDismiss = { viewModel.cancelImport() }
        )
    }

    if (showNotesSheet) {
        val notesSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showNotesSheet = false },
            sheetState = notesSheetState,
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(bottom = 16.dp)
            ) {
                Text(
                    text = "Bookmarks & Notes",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 4.dp)
                )
                Text(
                    text = "${annotations.size} saved in ${currentNovel?.title ?: "this novel"}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 2.dp)
                )
                Spacer(modifier = Modifier.height(6.dp))
                ReaderAnnotationsList(
                    annotations = annotations,
                    chapters = chapters,
                    onAnnotationClick = { ann ->
                        showNotesSheet = false
                        onOpenReaderAtParagraph(ann.chapterId, ann.paragraphIndex)
                    },
                    onEditAnnotation = { ann -> editingAnnotation = ann },
                    onDeleteAnnotation = { ann -> viewModel.deleteAnnotation(ann.id) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 520.dp)
                )
            }
        }
    }

    editingAnnotation?.let { ann ->
        val isBookmark = ann.type == NovelAnnotationEntity.TYPE_BOOKMARK
        AnnotationEditorSheet(
            title = if (isBookmark) "Edit Bookmark Note" else "Edit Highlight & Note",
            quoteText = ann.quoteText,
            isBookmark = isBookmark,
            initialColorKey = ann.colorKey,
            initialNote = ann.note,
            canDelete = true,
            onSave = { colorKey, note ->
                viewModel.updateAnnotation(ann.copy(colorKey = colorKey, note = note.trim()))
                editingAnnotation = null
            },
            onDelete = {
                viewModel.deleteAnnotation(ann.id)
                editingAnnotation = null
            },
            onDismiss = { editingAnnotation = null }
        )
    }

    if (showSearchSheet) {
        val searchSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showSearchSheet = false },
            sheetState = searchSheetState,
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(bottom = 16.dp)
            ) {
                Text(
                    text = "Search in Novel",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 4.dp)
                )
                Text(
                    text = currentNovel?.title ?: "",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 18.dp)
                )
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { viewModel.updateSearchQuery(it) },
                    placeholder = { Text("Search chapters, character names, or phrases…") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { viewModel.clearSearch() }) {
                                Icon(Icons.Default.Close, contentDescription = "Clear search")
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .testTag("detail_search_input")
                )
                Spacer(modifier = Modifier.height(6.dp))
                NovelSearchResultsList(
                    query = searchQuery,
                    results = searchResults,
                    isSearching = isSearching,
                    showNovelTitle = false,
                    emptyPrompt = "Search across all chapters of this novel and jump straight to the matching paragraph.",
                    onHitClick = { hit ->
                        showSearchSheet = false
                        onOpenReaderAtParagraph(hit.chapterId, hit.paragraphIndex)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 460.dp)
                )
            }
        }
    }

    if (showShelvesSheet && currentNovel != null) {
        NovelShelvesSheet(
            novelTitle = currentNovel.title,
            shelves = shelves,
            assignedShelfIds = novelShelfIds.toSet(),
            onToggleShelf = { shelfId -> viewModel.toggleShelf(shelfId) },
            onCreateShelfAndAssign = { name -> viewModel.createShelfAndAssign(name) },
            onDismiss = { showShelvesSheet = false }
        )
    }
}
