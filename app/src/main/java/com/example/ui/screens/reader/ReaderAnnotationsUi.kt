package com.example.ui.screens.reader

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.database.entities.NovelAnnotationEntity
import com.example.data.model.ChapterSummary
import com.example.ui.components.EmptyState
import com.example.ui.theme.EditorialHighlightSwatches
import com.example.ui.theme.LoraFontFamily
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class AnnotationFilter(val label: String) {
    ALL("All"),
    HIGHLIGHTS("Highlights"),
    BOOKMARKS("Bookmarks")
}

/**
 * Editorial bottom sheet for creating or editing a highlight/bookmark and its reading note.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnnotationEditorSheet(
    title: String,
    quoteText: String,
    isBookmark: Boolean,
    initialColorKey: String,
    initialNote: String,
    canDelete: Boolean,
    onSave: (colorKey: String, note: String) -> Unit,
    onDelete: (() -> Unit)? = null,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selectedColor by remember(initialColorKey) { mutableStateOf(initialColorKey) }
    var noteText by remember(initialNote) { mutableStateOf(initialNote) }

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
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (canDelete && onDelete != null) {
                    TextButton(
                        onClick = onDelete,
                        modifier = Modifier.testTag("annotation_editor_delete")
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteOutline,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Remove", color = MaterialTheme.colorScheme.error)
                    }
                }
            }

            if (quoteText.isNotBlank()) {
                val accentColor = if (isBookmark) {
                    MaterialTheme.colorScheme.secondary
                } else {
                    highlightIndicatorColor(selectedColor)
                }
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                    border = BorderStroke(1.dp, accentColor.copy(alpha = 0.45f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .width(3.dp)
                                .height(40.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(accentColor)
                        )
                        Text(
                            text = "“$quoteText”",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontFamily = LoraFontFamily,
                                fontStyle = FontStyle.Italic
                            ),
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 4,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            if (!isBookmark) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "HIGHLIGHT COLOR",
                        style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        EditorialHighlightSwatches.forEach { swatch ->
                            val isSelected = selectedColor == swatch.key
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (isSelected) {
                                    swatch.indicator.copy(alpha = 0.16f)
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                                },
                                border = BorderStroke(
                                    width = if (isSelected) 1.5.dp else 1.dp,
                                    color = if (isSelected) swatch.indicator else MaterialTheme.colorScheme.outlineVariant
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("annotation_color_${swatch.key}")
                                    .clickable { selectedColor = swatch.key }
                            ) {
                                Row(
                                    modifier = Modifier.padding(vertical = 10.dp, horizontal = 6.dp),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(16.dp)
                                            .clip(CircleShape)
                                            .background(swatch.indicator),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (isSelected) {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = null,
                                                tint = Color.White,
                                                modifier = Modifier.size(11.dp)
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(5.dp))
                                    Text(
                                        text = swatch.label,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }
                }
            }

            OutlinedTextField(
                value = noteText,
                onValueChange = { noteText = it },
                label = { Text("Reading note (optional)") },
                placeholder = { Text("Write a thought, translation note, or reminder…") },
                minLines = 3,
                maxLines = 6,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("annotation_note_input")
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Cancel")
                }
                Button(
                    onClick = { onSave(selectedColor, noteText) },
                    modifier = Modifier
                        .weight(1.3f)
                        .testTag("annotation_editor_save"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Save")
                }
            }
        }
    }
}

/**
 * Shared list of bookmarks, highlights, and reading notes used in both the Reader Drawer and
 * Novel Detail's Notes sheet.
 */
@Composable
fun ReaderAnnotationsList(
    annotations: List<NovelAnnotationEntity>,
    chapters: List<ChapterSummary>,
    onAnnotationClick: (NovelAnnotationEntity) -> Unit,
    onEditAnnotation: ((NovelAnnotationEntity) -> Unit)? = null,
    onDeleteAnnotation: (NovelAnnotationEntity) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(horizontal = 14.dp, vertical = 10.dp)
) {
    var selectedFilter by remember { mutableStateOf(AnnotationFilter.ALL) }
    val chapterTitleById = remember(chapters) {
        chapters.associate { ch ->
            ch.id to ch.title.ifBlank { "Chapter ${ch.orderIndex + 1}" }
        }
    }
    val dateFormat = remember { SimpleDateFormat("MMM d", Locale.getDefault()) }

    val filtered = remember(annotations, selectedFilter) {
        when (selectedFilter) {
            AnnotationFilter.ALL -> annotations
            AnnotationFilter.HIGHLIGHTS -> annotations.filter { it.type == NovelAnnotationEntity.TYPE_HIGHLIGHT }
            AnnotationFilter.BOOKMARKS -> annotations.filter { it.type == NovelAnnotationEntity.TYPE_BOOKMARK }
        }
    }

    Column(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AnnotationFilter.entries.forEach { filter ->
                val count = when (filter) {
                    AnnotationFilter.ALL -> annotations.size
                    AnnotationFilter.HIGHLIGHTS -> annotations.count { it.type == NovelAnnotationEntity.TYPE_HIGHLIGHT }
                    AnnotationFilter.BOOKMARKS -> annotations.count { it.type == NovelAnnotationEntity.TYPE_BOOKMARK }
                }
                FilterChip(
                    selected = selectedFilter == filter,
                    onClick = { selectedFilter = filter },
                    label = {
                        Text(
                            text = if (count > 0) "${filter.label} ($count)" else filter.label,
                            style = MaterialTheme.typography.labelSmall
                        )
                    },
                    shape = RoundedCornerShape(10.dp),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                    ),
                    modifier = Modifier.testTag("annotations_filter_${filter.name.lowercase()}")
                )
            }
        }

        if (filtered.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 20.dp),
                contentAlignment = Alignment.Center
            ) {
                EmptyState(
                    icon = Icons.Default.Bookmarks,
                    title = when (selectedFilter) {
                        AnnotationFilter.ALL -> "No bookmarks or notes yet"
                        AnnotationFilter.HIGHLIGHTS -> "No highlights yet"
                        AnnotationFilter.BOOKMARKS -> "No bookmarks yet"
                    },
                    message = "Tap the bookmark icon while reading, or select any passage to highlight and attach a note."
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .fillMaxWidth(),
                contentPadding = contentPadding,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(filtered, key = { it.id }) { ann ->
                    val isBookmark = ann.type == NovelAnnotationEntity.TYPE_BOOKMARK
                    val accentColor = if (isBookmark) {
                        MaterialTheme.colorScheme.secondary
                    } else {
                        highlightIndicatorColor(ann.colorKey)
                    }
                    val chapterLabel = chapterTitleById[ann.chapterId] ?: "Chapter"
                    val dateLabel = remember(ann.createdAt) { dateFormat.format(Date(ann.createdAt)) }

                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("annotation_card_${ann.id}")
                            .clickable { onAnnotationClick(ann) },
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        imageVector = if (isBookmark) Icons.Default.Bookmark else Icons.Default.FormatQuote,
                                        contentDescription = null,
                                        tint = accentColor,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = "$chapterLabel  ·  ¶${ann.paragraphIndex + 1}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = dateLabel,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
                                    )
                                    if (onEditAnnotation != null) {
                                        IconButton(
                                            onClick = { onEditAnnotation(ann) },
                                            modifier = Modifier.size(36.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Edit,
                                                contentDescription = "Edit note",
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                    IconButton(
                                        onClick = { onDeleteAnnotation(ann) },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.DeleteOutline,
                                            contentDescription = "Delete annotation",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }

                            if (ann.quoteText.isNotBlank()) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .width(3.dp)
                                            .height(32.dp)
                                            .clip(RoundedCornerShape(2.dp))
                                            .background(accentColor)
                                    )
                                    Text(
                                        text = ann.quoteText,
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontFamily = LoraFontFamily,
                                            fontSize = 14.sp,
                                            lineHeight = 20.sp
                                        ),
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 3,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }

                            if (ann.note.isNotBlank()) {
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = ann.note,
                                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                                        color = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
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
