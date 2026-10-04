package com.example.ui.screens.detail

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.importexport.ChapterSplitter
import com.example.replacement.ReplacementEngine
import com.example.ui.components.ChapterSplitDialog
import com.example.ui.components.ConfirmDialog
import com.example.ui.components.EmptyState
import com.example.ui.components.MtlTopBar
import com.example.ui.components.PromptDialog

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NovelDetailScreen(
    viewModel: NovelDetailViewModel,
    onBackClick: () -> Unit,
    onOpenReader: (String) -> Unit,
    onPlayNovel: (String) -> Unit,
    onOpenEditor: (String?) -> Unit,
    onOpenReplacements: () -> Unit
) {
    val context = LocalContext.current
    val novel by viewModel.novel.collectAsStateWithLifecycle()
    val chapters by viewModel.chapters.collectAsStateWithLifecycle()
    val isSelectionMode by viewModel.isSelectionMode.collectAsStateWithLifecycle()
    val selectedChapterIds by viewModel.selectedChapterIds.collectAsStateWithLifecycle()
    val pendingImportText by viewModel.pendingImportText.collectAsStateWithLifecycle()

    var showDeleteNovelDialog by remember { mutableStateOf(false) }
    var chapterToDeleteId by remember { mutableStateOf<String?>(null) }
    var showEditMetaDialog by remember { mutableStateOf(false) }
    var showPasteImportDialog by remember { mutableStateOf(false) }

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

    // Handle back button: exit selection mode first, then go back
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
                is DetailEvent.OpenReader -> onOpenReader(event.chapterId)
                is DetailEvent.OpenEditor -> onOpenEditor(event.chapterId)
                is DetailEvent.OpenReplacements -> onOpenReplacements()
            }
        }
    }

    val currentNovel = novel

    Scaffold(
        topBar = {
            MtlTopBar(
                title = currentNovel?.title ?: "Novel Details",
                onBackClick = {
                    if (isSelectionMode) viewModel.toggleSelectionMode() else onBackClick()
                },
                actions = {
                    IconButton(
                        onClick = { showEditMetaDialog = true },
                        modifier = Modifier.testTag("detail_edit_meta_button")
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit Novel Info")
                    }
                    IconButton(
                        onClick = onOpenReplacements,
                        modifier = Modifier.testTag("detail_replacements_button")
                    ) {
                        Icon(Icons.Default.SwapHoriz, contentDescription = "Replacements")
                    }
                    IconButton(
                        onClick = { showDeleteNovelDialog = true },
                        modifier = Modifier.testTag("detail_delete_novel_button")
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete Novel", tint = MaterialTheme.colorScheme.error)
                    }
                }
            )
        }
    ) { innerPadding ->
        if (currentNovel == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Text("Loading novel...")
            }
            return@Scaffold
        }

        val totalChapters = chapters.size
        val readChapters = chapters.count { it.read }
        val totalWords = chapters.sumOf { it.wordCount.toLong() }
        val progressPercent = if (totalChapters > 0) (readChapters * 100) / totalChapters else 0
        val rulesCount = ReplacementEngine.parseRules(currentNovel.replacements).size

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Novel Metadata Card
            item {
                Spacer(modifier = Modifier.height(4.dp))
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
                            .padding(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.Top
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(width = 72.dp, height = 96.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(MaterialTheme.colorScheme.primaryContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Book,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(40.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(14.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = currentNovel.title,
                                    style = MaterialTheme.typography.titleLarge.copy(
                                        fontFamily = FontFamily.Serif,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 20.sp
                                    )
                                )

                                if (currentNovel.author.isNotBlank()) {
                                    Text(
                                        text = "by ${currentNovel.author}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                if (currentNovel.translator.isNotBlank()) {
                                    Text(
                                        text = "translated by ${currentNovel.translator}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                Spacer(modifier = Modifier.height(6.dp))

                                Text(
                                    text = "$totalChapters chapters • $readChapters read • ${String.format("%,d", totalWords)} words",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                Spacer(modifier = Modifier.height(6.dp))

                                LinearProgressIndicator(
                                    progress = { if (totalChapters > 0) progressPercent / 100f else 0f },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(6.dp)
                                        .clip(RoundedCornerShape(3.dp)),
                                    color = MaterialTheme.colorScheme.primary,
                                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                                    strokeCap = StrokeCap.Round
                                )
                            }
                        }

                        if (currentNovel.description.isNotBlank()) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = currentNovel.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 4,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                        Spacer(modifier = Modifier.height(12.dp))

                        // Quick Actions Grid / Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = { onOpenEditor(null) },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(42.dp)
                                    .testTag("detail_add_chapter_button"),
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            ) {
                                Text("+ Chapter", fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                            }

                            OutlinedButton(
                                onClick = { filePickerLauncher.launch("*/*") },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(42.dp)
                                    .testTag("detail_import_file_button"),
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp)
                            ) {
                                Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(15.dp))
                                Spacer(modifier = Modifier.width(3.dp))
                                Text("Select file", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                            }

                            Button(
                                onClick = { showPasteImportDialog = true },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(42.dp)
                                    .testTag("detail_paste_import_button"),
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp)
                            ) {
                                Icon(Icons.Default.ContentPaste, contentDescription = null, modifier = Modifier.size(15.dp))
                                Spacer(modifier = Modifier.width(3.dp))
                                Text("Paste", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = onOpenReplacements,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(42.dp)
                                    .testTag("detail_open_replacements_button"),
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                            ) {
                                Icon(Icons.Default.SwapHoriz, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Rules ($rulesCount)", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                            }

                            val targetChapter = remember(chapters, currentNovel.lastChapterId) {
                                val lastId = currentNovel.lastChapterId
                                if (!lastId.isNullOrBlank()) {
                                    chapters.find { it.id == lastId }
                                } else {
                                    chapters.firstOrNull { !it.read } ?: chapters.firstOrNull()
                                } ?: chapters.firstOrNull()
                            }

                            Button(
                                onClick = {
                                    if (targetChapter != null) {
                                        onPlayNovel(targetChapter.id)
                                    } else {
                                        Toast.makeText(context, "No chapters available to play", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                enabled = chapters.isNotEmpty(),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(42.dp)
                                    .testTag("detail_play_novel_button"),
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                )
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Play", fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                            }

                            OutlinedButton(
                                onClick = {
                                    val safeName = currentNovel.title.replace(Regex("[^a-zA-Z0-9.-]"), "_").trim('_')
                                    pendingExportSelectedOnly = false
                                    createEpubLauncher.launch("$safeName.epub")
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(42.dp)
                                    .testTag("detail_export_novel_button"),
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                            ) {
                                Icon(Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Export EPUB", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                            }
                        }
                    }
                }
            }

            // Chapter List Header
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Chapters ($totalChapters)",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                        )

                        OutlinedButton(
                            onClick = { viewModel.toggleSelectionMode() },
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.height(36.dp)
                        ) {
                            Text(if (isSelectionMode) "Done" else "Select", fontSize = 12.sp)
                        }
                    }
                }
            }

            // Selection Mode Actions Bar
            if (isSelectionMode) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "${selectedChapterIds.size} chapter(s) selected",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
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
                                        .weight(1.3f)
                                        .height(38.dp),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp),
                                    enabled = selectedChapterIds.isNotEmpty()
                                ) {
                                    Icon(Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(15.dp))
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text("Export EPUB", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                                }
                                OutlinedButton(
                                    onClick = { viewModel.selectAllChapters() },
                                    modifier = Modifier
                                        .weight(0.85f)
                                        .height(38.dp),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp)
                                ) {
                                    Text("All", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                                }
                                OutlinedButton(
                                    onClick = { viewModel.clearChapterSelection() },
                                    modifier = Modifier
                                        .weight(0.85f)
                                        .height(38.dp),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp),
                                    enabled = selectedChapterIds.isNotEmpty()
                                ) {
                                    Text("Clear", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                                }
                            }
                        }
                    }
                }
            }

            // Chapters List
            if (chapters.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        EmptyState(
                            icon = Icons.Default.Book,
                            title = "No chapters yet",
                            message = "Add your first chapter or import text from a translated .txt file.",
                            actionText = "+ Add Chapter",
                            onActionClick = { onOpenEditor(null) }
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedButton(
                            onClick = { viewModel.addSampleChapters() },
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.Book, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Load Example Chapters")
                        }
                    }
                }
            } else {
                itemsIndexed(
                    items = chapters,
                    key = { _, ch -> ch.id }
                ) { index, ch ->
                    val isSelected = selectedChapterIds.contains(ch.id)
                    val isCurrent = currentNovel.lastChapterId == ch.id

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .combinedClickable(
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
                                }
                            ),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surface
                            }
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (isSelectionMode) {
                                Checkbox(
                                    checked = isSelected,
                                    onCheckedChange = { viewModel.toggleChapterSelection(ch.id) }
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                            }

                            // Order Number badge
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "${index + 1}",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = ch.title,
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontWeight = FontWeight.SemiBold
                                        ),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f, fill = false)
                                    )

                                    if (isCurrent) {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Box(
                                            modifier = Modifier
                                                .size(8.dp)
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(MaterialTheme.colorScheme.primary)
                                        )
                                    }

                                    if (ch.read) {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Surface(
                                            shape = RoundedCornerShape(10.dp),
                                            color = MaterialTheme.colorScheme.primaryContainer
                                        ) {
                                            Text(
                                                text = "✓ Read",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.primary,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(2.dp))

                                val readMin = maxOf(1, ch.wordCount / 180)
                                Text(
                                    text = "${String.format("%,d", ch.wordCount)} words • ~$readMin min read",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            // Quick chapter actions
                            if (!isSelectionMode) {
                                IconButton(
                                    onClick = { viewModel.toggleChapterRead(ch.id) },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        imageVector = if (ch.read) Icons.Filled.CheckCircle else Icons.Outlined.CheckCircle,
                                        contentDescription = "Toggle Read Status",
                                        tint = if (ch.read) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }

                                IconButton(
                                    onClick = { onOpenEditor(ch.id) },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Edit,
                                        contentDescription = "Edit Chapter",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }

                                IconButton(
                                    onClick = { chapterToDeleteId = ch.id },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Delete Chapter",
                                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    // Dialogs
    if (showDeleteNovelDialog) {
        ConfirmDialog(
            title = "Delete Novel?",
            message = "This will permanently delete \"${currentNovel?.title}\" and all of its ${chapters.size} chapter(s). This cannot be undone.",
            confirmText = "Delete Novel",
            isDestructive = true,
            onConfirm = {
                showDeleteNovelDialog = false
                viewModel.deleteNovel()
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

    if (showEditMetaDialog && currentNovel != null) {
        PromptDialog(
            title = "Edit Title",
            initialValue = currentNovel.title,
            confirmText = "Save",
            onConfirm = { newTitle ->
                showEditMetaDialog = false
                viewModel.updateNovelInfo(
                    title = newTitle,
                    author = currentNovel.author,
                    translator = currentNovel.translator,
                    description = currentNovel.description
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
        ChapterSplitDialog(
            totalWords = ChapterSplitter.countWords(rawText),
            onSelectSplitMode = { mode, customMarker ->
                viewModel.completeImport(mode, customMarker)
            },
            onDismiss = { viewModel.cancelImport() }
        )
    }
}
