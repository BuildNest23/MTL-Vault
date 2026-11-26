package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.CollectionsBookmark
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.database.entities.ShelfEntity
import com.example.data.model.NovelSearchHit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NovelShelvesSheet(
    novelTitle: String,
    shelves: List<ShelfEntity>,
    assignedShelfIds: Set<String>,
    onToggleShelf: (shelfId: String) -> Unit,
    onCreateShelfAndAssign: (name: String) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var newShelfName by remember { mutableStateOf("") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
        ) {
            Text(
                text = "Organize Shelves",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = novelTitle.ifBlank { "Untitled" },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp)
            )

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = newShelfName,
                    onValueChange = { newShelfName = it },
                    placeholder = { Text("New shelf name…") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("shelf_assign_create_input")
                )
                FilledTonalButton(
                    onClick = {
                        val trimmed = newShelfName.trim()
                        if (trimmed.isNotEmpty()) {
                            onCreateShelfAndAssign(trimmed)
                            newShelfName = ""
                        }
                    },
                    enabled = newShelfName.isNotBlank(),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .heightIn(min = 52.dp)
                        .testTag("shelf_assign_create_button")
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Add")
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            if (shelves.isEmpty()) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.CollectionsBookmark,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "No custom shelves yet",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Create your first shelf above to group novels (e.g. Xianxia, Favorites, Completed).",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 320.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(items = shelves, key = { it.id }) { shelf ->
                        val assigned = assignedShelfIds.contains(shelf.id)
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onToggleShelf(shelf.id) }
                                .testTag("shelf_assign_row_${shelf.id}"),
                            shape = RoundedCornerShape(12.dp),
                            color = if (assigned) {
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                            } else {
                                MaterialTheme.colorScheme.surfaceContainerLow
                            },
                            border = BorderStroke(
                                1.dp,
                                if (assigned) MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                                else MaterialTheme.colorScheme.outlineVariant
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 52.dp)
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = assigned,
                                    onCheckedChange = { onToggleShelf(shelf.id) }
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = shelf.name,
                                    style = MaterialTheme.typography.bodyLarge.copy(
                                        fontWeight = if (assigned) FontWeight.SemiBold else FontWeight.Normal
                                    ),
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManageShelvesSheet(
    shelves: List<ShelfEntity>,
    novelCountByShelf: Map<String, Int>,
    onCreateShelf: (name: String) -> Unit,
    onRenameShelf: (shelfId: String, newName: String) -> Unit,
    onDeleteShelf: (shelfId: String) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var newShelfName by remember { mutableStateOf("") }
    var renamingShelf by remember { mutableStateOf<ShelfEntity?>(null) }
    var deletingShelf by remember { mutableStateOf<ShelfEntity?>(null) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
        ) {
            Text(
                text = "Custom Shelves",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "Curate personal collections for your reading room.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp)
            )

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = newShelfName,
                    onValueChange = { newShelfName = it },
                    placeholder = { Text("e.g. Xianxia, Favorites, Completed") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("manage_shelves_create_input")
                )
                FilledTonalButton(
                    onClick = {
                        val trimmed = newShelfName.trim()
                        if (trimmed.isNotEmpty()) {
                            onCreateShelf(trimmed)
                            newShelfName = ""
                        }
                    },
                    enabled = newShelfName.isNotBlank(),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .heightIn(min = 52.dp)
                        .testTag("manage_shelves_create_button")
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Create")
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            if (shelves.isEmpty()) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.CollectionsBookmark,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "No custom shelves yet",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Long-press any novel card in your library or open its detail page to place it on a shelf.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 340.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(items = shelves, key = { it.id }) { shelf ->
                        val count = novelCountByShelf[shelf.id] ?: 0
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 14.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Bookmark,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = shelf.name,
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "$count volume${if (count == 1) "" else "s"}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                IconButton(
                                    onClick = { renamingShelf = shelf },
                                    modifier = Modifier.testTag("manage_shelves_rename_${shelf.id}")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Edit,
                                        contentDescription = "Rename ${shelf.name}",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                IconButton(
                                    onClick = { deletingShelf = shelf },
                                    modifier = Modifier.testTag("manage_shelves_delete_${shelf.id}")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Delete ${shelf.name}",
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    renamingShelf?.let { shelf ->
        PromptDialog(
            title = "Rename Shelf",
            message = "Enter a new name for \"${shelf.name}\":",
            initialValue = shelf.name,
            placeholder = "Shelf name",
            confirmText = "Save",
            onConfirm = { updated ->
                renamingShelf = null
                if (updated.isNotBlank()) {
                    onRenameShelf(shelf.id, updated)
                }
            },
            onDismiss = { renamingShelf = null }
        )
    }

    deletingShelf?.let { shelf ->
        ConfirmDialog(
            title = "Delete shelf?",
            message = "Remove the \"${shelf.name}\" shelf? Your novels and chapters will stay in your library.",
            confirmText = "Delete",
            isDestructive = true,
            onConfirm = {
                deletingShelf = null
                onDeleteShelf(shelf.id)
            },
            onDismiss = { deletingShelf = null }
        )
    }
}

@Composable
fun HighlightedSnippetText(
    hit: NovelSearchHit,
    modifier: Modifier = Modifier
) {
    val highlightBg = MaterialTheme.colorScheme.secondaryContainer
    val highlightFg = MaterialTheme.colorScheme.onSecondaryContainer
    val annotated = remember(hit.snippet, hit.matchStart, hit.matchEnd, highlightBg, highlightFg) {
        buildAnnotatedString {
            append(hit.snippet)
            val start = hit.matchStart.coerceIn(0, hit.snippet.length)
            val end = hit.matchEnd.coerceIn(start, hit.snippet.length)
            if (end > start) {
                addStyle(
                    style = SpanStyle(
                        background = highlightBg,
                        color = highlightFg,
                        fontWeight = FontWeight.SemiBold
                    ),
                    start = start,
                    end = end
                )
            }
        }
    }
    Text(
        text = annotated,
        style = MaterialTheme.typography.bodySmall.copy(lineHeight = 19.sp),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 3,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
    )
}

@Composable
fun NovelSearchResultsList(
    query: String,
    results: List<NovelSearchHit>,
    isSearching: Boolean,
    showNovelTitle: Boolean = false,
    emptyPrompt: String = "Search dialogue, names, or phrases across chapters.",
    onHitClick: (NovelSearchHit) -> Unit,
    modifier: Modifier = Modifier
) {
    val trimmed = query.trim()

    Column(modifier = modifier) {
        if (isSearching) {
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                color = MaterialTheme.colorScheme.primary
            )
        }

        when {
            trimmed.isEmpty() -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 28.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier.size(30.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = emptyPrompt,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            trimmed.length < 2 -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Type at least 2 characters to search chapters.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }

            !isSearching && results.isEmpty() -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 28.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No chapter matches for \"$trimmed\"",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }

            else -> {
                if (results.isNotEmpty()) {
                    Text(
                        text = "${results.size}${if (results.size >= 80) "+" else ""} match${if (results.size == 1) "" else "es"}",
                        style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.8.sp),
                        color = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f))
                }

                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(
                        items = results,
                        key = { "${it.novelId}_${it.chapterId}_${it.paragraphIndex}_${it.matchStart}" }
                    ) { hit ->
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onHitClick(hit) }
                                .testTag("search_hit_${hit.chapterId}_${hit.paragraphIndex}"),
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp)
                            ) {
                                if (showNovelTitle) {
                                    Text(
                                        text = hit.novelTitle.ifBlank { "Untitled" },
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = FontWeight.SemiBold,
                                            letterSpacing = 0.4.sp
                                        ),
                                        color = MaterialTheme.colorScheme.primary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = hit.chapterTitle,
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "¶ ${hit.paragraphIndex + 1}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                Spacer(modifier = Modifier.height(5.dp))

                                HighlightedSnippetText(hit = hit)
                            }
                        }
                    }
                }
            }
        }
    }
}
