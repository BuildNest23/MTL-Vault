package com.example.ui.screens.library

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.CollectionsBookmark
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.ViewModule
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.NovelSummary
import com.example.importexport.ChapterSplitter
import com.example.ui.components.ChapterSplitDialog
import com.example.ui.components.ConfirmDialog
import com.example.ui.components.EditNovelDialog
import com.example.ui.components.EditorialBookCover
import com.example.ui.components.EmptyState
import com.example.ui.components.ManageShelvesSheet
import com.example.ui.components.MtlActionButton
import com.example.ui.components.MtlButtonStyle
import com.example.ui.components.MtlTopBar
import com.example.ui.components.NovelCoverCard
import com.example.ui.components.NovelSearchResultsList
import com.example.ui.components.NovelShelvesSheet
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
    onOpenNovel: (String) -> Unit,
    onOpenReader: (novelId: String, chapterId: String) -> Unit = { _, _ -> },
    onPlayNovel: (novelId: String, chapterId: String) -> Unit = { n, c -> onOpenReader(n, c) },
    onOpenReaderAtParagraph: (novelId: String, chapterId: String, paragraphIndex: Int) -> Unit = { n, c, _ -> onOpenReader(n, c) }
) {
    val context = LocalContext.current
    val novels by viewModel.novels.collectAsStateWithLifecycle()
    val shelves by viewModel.shelves.collectAsStateWithLifecycle()
    val shelfMemberships by viewModel.shelfMemberships.collectAsStateWithLifecycle()
    val gridColumns by viewModel.gridColumns.collectAsStateWithLifecycle()
    val pendingImportText by viewModel.pendingImportText.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val searchInsideChapters by viewModel.searchInsideChapters.collectAsStateWithLifecycle()
    val chapterSearchResults by viewModel.chapterSearchResults.collectAsStateWithLifecycle()
    val isSearchingChapters by viewModel.isSearchingChapters.collectAsStateWithLifecycle()

    var showAddSheet by remember { mutableStateOf(false) }
    var showCreateNovelDialog by remember { mutableStateOf(false) }
    var showPasteImportDialog by remember { mutableStateOf(false) }
    var showTopOverflow by remember { mutableStateOf(false) }
    var showManageShelvesSheet by remember { mutableStateOf(false) }
    var isSearchOpen by rememberSaveable { mutableStateOf(false) }
    var filter by rememberSaveable { mutableStateOf(LibraryFilter.ALL) }
    var selectedShelfId by rememberSaveable { mutableStateOf<String?>(null) }
    var sort by rememberSaveable { mutableStateOf(LibrarySort.LAST_READ) }
    var showSortMenu by remember { mutableStateOf(false) }

    // Long-press menu state
    var menuNovel by remember { mutableStateOf<NovelSummary?>(null) }
    var editNovel by remember { mutableStateOf<NovelSummary?>(null) }
    var deleteNovel by remember { mutableStateOf<NovelSummary?>(null) }
    var shelvesForNovel by remember { mutableStateOf<NovelSummary?>(null) }

    // Clear selected shelf if it was deleted
    LaunchedEffect(shelves, selectedShelfId) {
        if (selectedShelfId != null && shelves.none { it.id == selectedShelfId }) {
            selectedShelfId = null
        }
    }

    val novelIdsByShelf = remember(shelfMemberships) {
        shelfMemberships.groupBy({ it.shelfId }, { it.novelId }).mapValues { it.value.toSet() }
    }
    val shelfIdsByNovel = remember(shelfMemberships) {
        shelfMemberships.groupBy({ it.novelId }, { it.shelfId }).mapValues { it.value.toSet() }
    }
    val shelfNameById = remember(shelves) {
        shelves.associate { it.id to it.name }
    }
    val novelCountByShelf = remember(novelIdsByShelf, novels) {
        val liveNovelIds = novels.mapTo(HashSet()) { it.id }
        novelIdsByShelf.mapValues { (_, ids) -> ids.count { it in liveNovelIds } }
    }

    // Most recently active novel with chapters for the editorial Continue Reading hero card
    val continueNovel = remember(novels) {
        novels.filter { it.totalChapters > 0 }
            .sortedByDescending { it.updatedAt }
            .let { active ->
                active.firstOrNull { it.unreadChapters > 0 } ?: active.firstOrNull()
            }
    }

    val activeShelf = remember(shelves, selectedShelfId) {
        shelves.firstOrNull { it.id == selectedShelfId }
    }

    val visibleNovels = remember(
        novels,
        filter,
        selectedShelfId,
        novelIdsByShelf,
        shelfIdsByNovel,
        shelfNameById,
        sort,
        isSearchOpen,
        searchQuery,
        searchInsideChapters
    ) {
        val byShelfOrFilter = if (selectedShelfId != null) {
            val allowedIds = novelIdsByShelf[selectedShelfId].orEmpty()
            novels.filter { it.id in allowedIds }
        } else {
            when (filter) {
                LibraryFilter.ALL -> novels
                LibraryFilter.UNREAD -> novels.filter { it.unreadChapters > 0 }
                LibraryFilter.RULES -> novels.filter { it.hasRules }
            }
        }
        val queryTrimmed = if (isSearchOpen && !searchInsideChapters) searchQuery.trim() else ""
        val searched = if (queryTrimmed.isNotEmpty()) {
            byShelfOrFilter.filter { novel ->
                novel.title.contains(queryTrimmed, ignoreCase = true) ||
                    novel.author.contains(queryTrimmed, ignoreCase = true) ||
                    novel.translator.contains(queryTrimmed, ignoreCase = true) ||
                    shelfIdsByNovel[novel.id].orEmpty().any { sid ->
                        shelfNameById[sid]?.contains(queryTrimmed, ignoreCase = true) == true
                    }
            }
        } else {
            byShelfOrFilter
        }
        when (sort) {
            LibrarySort.LAST_READ -> searched.sortedByDescending { it.updatedAt }
            LibrarySort.MOST_UNREAD -> searched.sortedByDescending { it.unreadChapters }
            LibrarySort.TITLE -> searched.sortedBy { it.title.lowercase() }
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
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            MtlTopBar(
                title = "MTL Vault",
                subtitle = if (novels.isEmpty()) "READING ROOM" else "${novels.size} VOLUME${if (novels.size == 1) "" else "S"} IN VAULT",
                actions = {
                    if (novels.isNotEmpty()) {
                        IconButton(
                            onClick = {
                                if (isSearchOpen) {
                                    isSearchOpen = false
                                    viewModel.clearSearch()
                                } else {
                                    isSearchOpen = true
                                }
                            },
                            modifier = Modifier.testTag("library_search_toggle_button")
                        ) {
                            Icon(
                                imageVector = if (isSearchOpen) Icons.Default.Close else Icons.Default.Search,
                                contentDescription = if (isSearchOpen) "Close search" else "Search library and chapters"
                            )
                        }
                        IconButton(
                            onClick = { viewModel.toggleGridColumns() },
                            modifier = Modifier.testTag("library_grid_toggle")
                        ) {
                            Icon(
                                imageVector = if (gridColumns == 2) Icons.Outlined.ViewModule else Icons.Outlined.GridView,
                                contentDescription = if (gridColumns == 2) "Switch to compact shelf" else "Switch to editorial cards"
                            )
                        }
                    }
                    Box {
                        IconButton(
                            onClick = { showTopOverflow = true },
                            modifier = Modifier.testTag("library_more_button")
                        ) {
                            Icon(Icons.Default.MoreVert, contentDescription = "Library tools")
                        }
                        DropdownMenu(
                            expanded = showTopOverflow,
                            onDismissRequest = { showTopOverflow = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Manage shelves") },
                                leadingIcon = { Icon(Icons.Outlined.CollectionsBookmark, contentDescription = null) },
                                onClick = {
                                    showTopOverflow = false
                                    showManageShelvesSheet = true
                                },
                                modifier = Modifier.testTag("library_manage_shelves_menu_item")
                            )
                            DropdownMenuItem(
                                text = { Text("Fetch missing covers") },
                                leadingIcon = { Icon(Icons.Default.CloudDownload, contentDescription = null) },
                                onClick = {
                                    showTopOverflow = false
                                    viewModel.fetchMissingInfo(context)
                                },
                                modifier = Modifier.testTag("library_fetch_info_button")
                            )
                            DropdownMenuItem(
                                text = { Text("Load example novel") },
                                leadingIcon = { Icon(Icons.Default.AutoStories, contentDescription = null) },
                                onClick = {
                                    showTopOverflow = false
                                    viewModel.loadSampleNovel()
                                }
                            )
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showAddSheet = true },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("Add", style = MaterialTheme.typography.labelLarge) },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.testTag("library_add_fab")
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (isSearchOpen && novels.isNotEmpty()) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 1.dp
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { viewModel.updateSearchQuery(it) },
                            placeholder = {
                                Text(
                                    if (searchInsideChapters) "Search dialogue, names, or phrases across all chapters…"
                                    else "Filter by title, author, or shelf…"
                                )
                            },
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                            trailingIcon = {
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { viewModel.updateSearchQuery("") }) {
                                        Icon(Icons.Default.Close, contentDescription = "Clear search")
                                    }
                                }
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("library_search_input")
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            FilterChip(
                                selected = !searchInsideChapters,
                                onClick = { viewModel.setSearchInsideChapters(false) },
                                label = { Text("Novels", style = MaterialTheme.typography.labelMedium) },
                                shape = RoundedCornerShape(10.dp),
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                ),
                                modifier = Modifier.testTag("library_search_mode_novels")
                            )
                            FilterChip(
                                selected = searchInsideChapters,
                                onClick = { viewModel.setSearchInsideChapters(true) },
                                label = { Text("Inside Chapters", style = MaterialTheme.typography.labelMedium) },
                                shape = RoundedCornerShape(10.dp),
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer
                                ),
                                modifier = Modifier.testTag("library_search_mode_chapters")
                            )
                        }
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }

            if (isSearchOpen && searchInsideChapters) {
                NovelSearchResultsList(
                    query = searchQuery,
                    results = chapterSearchResults,
                    isSearching = isSearchingChapters,
                    showNovelTitle = true,
                    emptyPrompt = "Search across every chapter in your library and jump straight to the matching passage.",
                    onHitClick = { hit ->
                        onOpenReaderAtParagraph(hit.novelId, hit.chapterId, hit.paragraphIndex)
                    },
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                )
            } else {
                BoxWithConstraints(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize()
                ) {
                    val isWide = maxWidth >= 600.dp
                    val effectiveColumns = when {
                        isWide && gridColumns == 2 -> 3
                        isWide -> 4
                        else -> gridColumns
                    }
                    val compactCards = gridColumns == 3

                    LazyVerticalGrid(
                        columns = GridCells.Fixed(effectiveColumns),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            start = 16.dp,
                            end = 16.dp,
                            top = 14.dp,
                            bottom = 96.dp
                        ),
                        horizontalArrangement = Arrangement.spacedBy(if (compactCards) 10.dp else 14.dp),
                        verticalArrangement = Arrangement.spacedBy(if (compactCards) 12.dp else 16.dp)
                    ) {
                        // Editorial Continue Reading Hero Card (hidden while actively filtering a shelf or searching)
                        if (continueNovel != null && selectedShelfId == null && (!isSearchOpen || searchQuery.isBlank())) {
                            item(span = fullSpan) {
                                ContinueReadingHeroCard(
                                    novel = continueNovel,
                                    onCardClick = { onOpenNovel(continueNovel.id) },
                                    onResumeRead = {
                                        viewModel.continueReading(continueNovel) { chapterId ->
                                            if (chapterId != null) onOpenReader(continueNovel.id, chapterId)
                                        }
                                    },
                                    onResumeListen = {
                                        viewModel.continueReading(continueNovel) { chapterId ->
                                            if (chapterId != null) onPlayNovel(continueNovel.id, chapterId)
                                        }
                                    }
                                )
                            }
                        }

                        // Filter, Custom Shelves & Sort Strip
                        if (novels.isNotEmpty()) {
                            item(span = fullSpan) {
                                Column {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .weight(1f)
                                                .horizontalScroll(rememberScrollState()),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            LibraryFilter.values().forEach { option ->
                                                FilterChip(
                                                    selected = selectedShelfId == null && filter == option,
                                                    onClick = {
                                                        selectedShelfId = null
                                                        filter = option
                                                    },
                                                    label = {
                                                        Text(
                                                            text = option.label,
                                                            style = MaterialTheme.typography.labelMedium
                                                        )
                                                    },
                                                    shape = RoundedCornerShape(10.dp),
                                                    colors = FilterChipDefaults.filterChipColors(
                                                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                                    )
                                                )
                                            }

                                            shelves.forEach { shelf ->
                                                val count = novelCountByShelf[shelf.id] ?: 0
                                                val isSelected = selectedShelfId == shelf.id
                                                FilterChip(
                                                    selected = isSelected,
                                                    onClick = {
                                                        selectedShelfId = if (isSelected) null else shelf.id
                                                    },
                                                    leadingIcon = {
                                                        Icon(
                                                            imageVector = Icons.Default.Bookmark,
                                                            contentDescription = null,
                                                            modifier = Modifier.size(15.dp)
                                                        )
                                                    },
                                                    label = {
                                                        Text(
                                                            text = if (count > 0) "${shelf.name} ($count)" else shelf.name,
                                                            style = MaterialTheme.typography.labelMedium
                                                        )
                                                    },
                                                    shape = RoundedCornerShape(10.dp),
                                                    colors = FilterChipDefaults.filterChipColors(
                                                        selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                                                        selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                                        selectedLeadingIconColor = MaterialTheme.colorScheme.secondary
                                                    ),
                                                    modifier = Modifier.testTag("library_shelf_chip_${shelf.id}")
                                                )
                                            }

                                            AssistChip(
                                                onClick = { showManageShelvesSheet = true },
                                                leadingIcon = {
                                                    Icon(
                                                        imageVector = Icons.Default.Add,
                                                        contentDescription = null,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                },
                                                label = {
                                                    Text(
                                                        text = if (shelves.isEmpty()) "Shelf" else "Shelves",
                                                        style = MaterialTheme.typography.labelMedium
                                                    )
                                                },
                                                shape = RoundedCornerShape(10.dp),
                                                modifier = Modifier.testTag("library_add_shelf_chip")
                                            )
                                        }

                                        Box {
                                            TextButton(
                                                onClick = { showSortMenu = true },
                                                modifier = Modifier.testTag("library_sort_button")
                                            ) {
                                                Icon(
                                                    Icons.AutoMirrored.Filled.Sort,
                                                    contentDescription = "Sort novels",
                                                    modifier = Modifier.size(16.dp)
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(
                                                    text = sort.label,
                                                    style = MaterialTheme.typography.labelMedium,
                                                    maxLines = 1
                                                )
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
                        }

                        if (novels.isEmpty()) {
                            item(span = fullSpan) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 12.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    EmptyState(
                                        icon = Icons.Default.AutoStories,
                                        title = "Your reading room awaits",
                                        message = "Import an EPUB or text file, paste translated chapters, or start a fresh volume.",
                                        actionText = "Import or create novel",
                                        onActionClick = { showAddSheet = true }
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                    OutlinedButton(
                                        onClick = { viewModel.loadSampleNovel() },
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.heightIn(min = 48.dp)
                                    ) {
                                        Icon(Icons.Default.AutoStories, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Load Example Novel", style = MaterialTheme.typography.labelLarge)
                                    }
                                }
                            }
                        } else if (visibleNovels.isEmpty()) {
                            item(span = fullSpan) {
                                val emptyHeading = when {
                                    isSearchOpen && searchQuery.isNotBlank() ->
                                        "No novels match \"${searchQuery.trim()}\""
                                    activeShelf != null ->
                                        "\"${activeShelf.name}\" has no novels yet"
                                    else ->
                                        "No novels match \"${filter.label}\""
                                }
                                Surface(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(16.dp),
                                    color = MaterialTheme.colorScheme.surface,
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(24.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text(
                                            text = emptyHeading,
                                            style = MaterialTheme.typography.titleMedium,
                                            textAlign = TextAlign.Center
                                        )
                                        if (activeShelf != null && (!isSearchOpen || searchQuery.isBlank())) {
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = "Long-press any novel card and choose \"Manage shelves\" to place it here.",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                textAlign = TextAlign.Center
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(6.dp))
                                        if (isSearchOpen && searchQuery.isNotBlank()) {
                                            TextButton(onClick = { viewModel.setSearchInsideChapters(true) }) {
                                                Text("Search inside chapters instead")
                                            }
                                        } else {
                                            TextButton(
                                                onClick = {
                                                    selectedShelfId = null
                                                    filter = LibraryFilter.ALL
                                                }
                                            ) {
                                                Text("Show all novels")
                                            }
                                        }
                                    }
                                }
                            }
                        } else {
                            items(
                                items = visibleNovels,
                                key = { it.id }
                            ) { novel ->
                                Box {
                                    NovelCoverCard(
                                        novel = novel,
                                        compact = compactCards,
                                        onClick = { onOpenNovel(novel.id) },
                                        onLongClick = { menuNovel = novel }
                                    )
                                    DropdownMenu(
                                        expanded = menuNovel?.id == novel.id,
                                        onDismissRequest = { menuNovel = null }
                                    ) {
                                        DropdownMenuItem(
                                            text = { Text("Continue reading") },
                                            onClick = {
                                                menuNovel = null
                                                viewModel.continueReading(novel) { chapterId ->
                                                    if (chapterId != null) onOpenReader(novel.id, chapterId)
                                                }
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Listen with TTS") },
                                            onClick = {
                                                menuNovel = null
                                                viewModel.continueReading(novel) { chapterId ->
                                                    if (chapterId != null) onPlayNovel(novel.id, chapterId)
                                                }
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Manage shelves") },
                                            onClick = {
                                                menuNovel = null
                                                shelvesForNovel = novel
                                            },
                                            modifier = Modifier.testTag("library_novel_shelves_item")
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Edit details") },
                                            onClick = {
                                                menuNovel = null
                                                editNovel = novel
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Mark all as read") },
                                            onClick = {
                                                menuNovel = null
                                                viewModel.setAllChaptersRead(novel, true)
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Mark all as unread") },
                                            onClick = {
                                                menuNovel = null
                                                viewModel.setAllChaptersRead(novel, false)
                                            }
                                        )
                                        HorizontalDivider()
                                        DropdownMenuItem(
                                            text = { Text("Delete novel", color = MaterialTheme.colorScheme.error) },
                                            onClick = {
                                                menuNovel = null
                                                deleteNovel = novel
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Editorial Add / Import Bottom Sheet
    if (showAddSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showAddSheet = false },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Add to Vault",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Import local books or create a new novel workspace.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(6.dp))

                AddSheetOption(
                    icon = Icons.Default.FolderOpen,
                    title = "Import EPUB or TXT file",
                    subtitle = "Automatically extracts metadata and chapters",
                    testTag = "library_import_file_button",
                    onClick = {
                        showAddSheet = false
                        filePickerLauncher.launch("*/*")
                    }
                )
                AddSheetOption(
                    icon = Icons.Default.ContentPaste,
                    title = "Paste chapter text",
                    subtitle = "Split pasted machine-translated chapters in one step",
                    testTag = "library_paste_import_button",
                    onClick = {
                        showAddSheet = false
                        showPasteImportDialog = true
                    }
                )
                AddSheetOption(
                    icon = Icons.Default.Add,
                    title = "Create blank novel",
                    subtitle = "Start a new volume and add chapters manually",
                    testTag = "library_new_novel_button",
                    onClick = {
                        showAddSheet = false
                        showCreateNovelDialog = true
                    }
                )

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 4.dp),
                    color = MaterialTheme.colorScheme.outlineVariant
                )

                AddSheetOption(
                    icon = Icons.Default.CloudDownload,
                    title = "Fetch missing covers & info",
                    subtitle = "Look up cover artwork and synopses for untitled covers",
                    testTag = "library_sheet_fetch_info",
                    onClick = {
                        showAddSheet = false
                        viewModel.fetchMissingInfo(context)
                    }
                )
                AddSheetOption(
                    icon = Icons.Default.AutoStories,
                    title = "Load example novel",
                    subtitle = "Add a short sample volume to try reading and TTS",
                    testTag = "library_sheet_sample",
                    onClick = {
                        showAddSheet = false
                        viewModel.loadSampleNovel()
                    }
                )
            }
        }
    }

    editNovel?.let { novel ->
        EditNovelDialog(
            title = novel.title,
            author = novel.author,
            translator = novel.translator,
            description = novel.description,
            coverUri = novel.coverUri,
            onSave = { newTitle, newAuthor, newTranslator, newDescription, newCover, removeCover ->
                editNovel = null
                viewModel.saveNovelDetails(
                    context = context,
                    novelId = novel.id,
                    title = newTitle,
                    author = newAuthor,
                    translator = newTranslator,
                    description = newDescription,
                    newCover = newCover,
                    removeCover = removeCover
                )
            },
            onDismiss = { editNovel = null }
        )
    }

    deleteNovel?.let { novel ->
        ConfirmDialog(
            title = "Delete novel?",
            message = "\"${novel.title}\" and all ${novel.totalChapters} of its chapters will be deleted. This can't be undone.",
            confirmText = "Delete",
            isDestructive = true,
            onConfirm = {
                deleteNovel = null
                viewModel.deleteNovel(context, novel)
            },
            onDismiss = { deleteNovel = null }
        )
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
        val totalWords = remember(rawText) { ChapterSplitter.countWords(rawText) }
        ChapterSplitDialog(
            totalWords = totalWords,
            onSelectSplitMode = { mode, customMarker ->
                viewModel.completeImport(mode, customMarker)
            },
            onDismiss = { viewModel.cancelImport() }
        )
    }

    if (showManageShelvesSheet) {
        ManageShelvesSheet(
            shelves = shelves,
            novelCountByShelf = novelCountByShelf,
            onCreateShelf = { name -> viewModel.createShelf(name) },
            onRenameShelf = { id, newName -> viewModel.renameShelf(id, newName) },
            onDeleteShelf = { id -> viewModel.deleteShelf(id) },
            onDismiss = { showManageShelvesSheet = false }
        )
    }

    shelvesForNovel?.let { novel ->
        NovelShelvesSheet(
            novelTitle = novel.title,
            shelves = shelves,
            assignedShelfIds = shelfIdsByNovel[novel.id].orEmpty(),
            onToggleShelf = { shelfId -> viewModel.toggleNovelShelf(novel.id, shelfId) },
            onCreateShelfAndAssign = { name -> viewModel.createShelfAndAssign(novel.id, name) },
            onDismiss = { shelvesForNovel = null }
        )
    }
}

@Composable
private fun ContinueReadingHeroCard(
    novel: NovelSummary,
    onCardClick: () -> Unit,
    onResumeRead: () -> Unit,
    onResumeListen: () -> Unit
) {
    val progress = if (novel.totalChapters > 0) {
        (novel.readChapters.toFloat() / novel.totalChapters.toFloat()).coerceIn(0f, 1f)
    } else 0f

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("library_continue_card")
            .clickable(onClick = onCardClick),
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
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.28f),
                            MaterialTheme.colorScheme.surface
                        )
                    )
                )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                EditorialBookCover(
                    title = novel.title,
                    author = novel.author,
                    coverUri = novel.coverUri,
                    cornerRadius = 11.dp,
                    compactBookplate = true,
                    modifier = Modifier
                        .width(84.dp)
                        .aspectRatio(2f / 3f)
                        .shadow(4.dp, RoundedCornerShape(11.dp), clip = false)
                )

                Spacer(modifier = Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (novel.unreadChapters > 0) "CONTINUE READING" else "RECENTLY FINISHED",
                        style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.1.sp),
                        color = MaterialTheme.colorScheme.secondary
                    )

                    Spacer(modifier = Modifier.height(3.dp))

                    Text(
                        text = novel.title.ifBlank { "Untitled" },
                        style = MaterialTheme.typography.titleLarge.copy(fontSize = 18.sp, lineHeight = 23.sp),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    val chapterLine = novel.lastChapterTitle?.takeIf { it.isNotBlank() }
                        ?: "${novel.readChapters} of ${novel.totalChapters} chapters read"
                    Text(
                        text = chapterLine,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp)
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier
                                .weight(1f)
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant,
                            strokeCap = StrokeCap.Round
                        )
                        Text(
                            text = "${novel.progressPercent}%",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        MtlActionButton(
                            text = if (novel.readChapters > 0 || !novel.lastChapterId.isNullOrBlank()) "Resume" else "Start",
                            icon = Icons.Default.AutoStories,
                            onClick = onResumeRead,
                            style = MtlButtonStyle.Primary,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("library_continue_read_button")
                        )

                        FilledTonalIconButton(
                            onClick = onResumeListen,
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("library_continue_listen_button"),
                            shape = RoundedCornerShape(14.dp),
                            colors = IconButtonDefaults.filledTonalIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Default.Headphones,
                                contentDescription = "Listen with TTS"
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AddSheetOption(
    icon: ImageVector,
    title: String,
    subtitle: String,
    testTag: String,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(testTag)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
