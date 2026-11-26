package com.example.ui.screens.detail

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.database.entities.NovelEntity
import com.example.data.model.ChapterSummary
import com.example.tts.TtsPlaybackInfo
import com.example.tts.TtsState
import com.example.ui.components.EditorialBookCover
import com.example.ui.components.MtlActionButton
import com.example.ui.components.MtlButtonStyle
import com.example.ui.components.bookplateColorsFor

@Composable
internal fun NovelBookJacketHero(
    novel: NovelEntity,
    totalChapters: Int,
    readChapters: Int,
    totalWords: Long,
    progressPercent: Int,
    readButtonLabel: String,
    isSpeakingThisNovel: Boolean,
    hasChapters: Boolean,
    onReadClick: () -> Unit,
    onPlayClick: () -> Unit
) {
    val (clothTint, _) = remember(novel.title) { bookplateColorsFor(novel.title) }
    var synopsisExpanded by remember(novel.id) { mutableStateOf(false) }

    val estMinutes = (totalWords / 180L).toInt()
    val estTimeText = when {
        estMinutes >= 60 -> "~${String.format("%.1f", estMinutes / 60.0)} hr"
        estMinutes > 0 -> "~$estMinutes min"
        else -> ""
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shadowElevation = 2.dp
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            clothTint.copy(alpha = 0.14f),
                            MaterialTheme.colorScheme.surface
                        )
                    )
                )
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
                    EditorialBookCover(
                        title = novel.title,
                        author = novel.author,
                        coverUri = novel.coverUri,
                        cornerRadius = 12.dp,
                        compactBookplate = false,
                        modifier = Modifier
                            .width(100.dp)
                            .aspectRatio(2f / 3f)
                            .shadow(6.dp, RoundedCornerShape(12.dp), clip = false)
                    )

                    Spacer(modifier = Modifier.width(16.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = novel.title,
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        if (novel.author.isNotBlank()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "by ${novel.author}",
                                style = MaterialTheme.typography.titleMedium.copy(fontSize = 14.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        if (novel.translator.isNotBlank()) {
                            Text(
                                text = "Translated by ${novel.translator}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        val metaParts = buildList {
                            add("$totalChapters ch")
                            if (totalWords > 0) add("${String.format("%,d", totalWords)} words")
                            if (estTimeText.isNotBlank()) add(estTimeText)
                        }
                        Text(
                            text = metaParts.joinToString("  •  "),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            LinearProgressIndicator(
                                progress = { if (totalChapters > 0) progressPercent / 100f else 0f },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp)),
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                                strokeCap = StrokeCap.Round
                            )
                            Text(
                                text = "$readChapters/$totalChapters",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                if (novel.description.isNotBlank()) {
                    Spacer(modifier = Modifier.height(14.dp))
                    Column(modifier = Modifier.animateContentSize()) {
                        Text(
                            text = novel.description,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = if (synopsisExpanded) Int.MAX_VALUE else 3,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (novel.description.length > 140) {
                            TextButton(
                                onClick = { synopsisExpanded = !synopsisExpanded },
                                modifier = Modifier.align(Alignment.End)
                            ) {
                                Text(
                                    text = if (synopsisExpanded) "Show less" else "Read full synopsis",
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Dual Editorial CTAs: Read (Primary) + Listen TTS (Secondary/Tonal)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    MtlActionButton(
                        text = readButtonLabel,
                        icon = Icons.Default.AutoStories,
                        onClick = onReadClick,
                        style = MtlButtonStyle.Primary,
                        large = true,
                        enabled = hasChapters,
                        modifier = Modifier
                            .weight(1.35f)
                            .testTag("detail_read_novel_button")
                    )

                    MtlActionButton(
                        text = if (isSpeakingThisNovel) "Playing" else "Listen",
                        icon = if (isSpeakingThisNovel) Icons.Default.PlayArrow else Icons.Default.Headphones,
                        onClick = onPlayClick,
                        style = MtlButtonStyle.Secondary,
                        large = true,
                        enabled = hasChapters,
                        modifier = Modifier
                            .weight(0.95f)
                            .testTag("detail_play_novel_button")
                    )
                }
            }
        }
    }
}

@Composable
internal fun NovelUtilityStrip(
    rulesCount: Int,
    notesCount: Int = 0,
    onAddChapters: () -> Unit,
    onOpenNotes: () -> Unit = {},
    onOpenVoices: () -> Unit,
    onOpenReplacements: () -> Unit,
    onExportEpub: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        UtilityPillTile(
            icon = Icons.Default.Add,
            label = "Add",
            testTag = "detail_add_chapters_button",
            highlight = true,
            onClick = onAddChapters,
            modifier = Modifier.weight(1f)
        )
        UtilityPillTile(
            icon = Icons.Default.Bookmarks,
            label = if (notesCount > 0) "Notes ($notesCount)" else "Notes",
            testTag = "detail_open_notes_button",
            onClick = onOpenNotes,
            modifier = Modifier.weight(1f)
        )
        UtilityPillTile(
            icon = Icons.Default.RecordVoiceOver,
            label = "Voices",
            testTag = "detail_open_voices_button",
            onClick = onOpenVoices,
            modifier = Modifier.weight(1f)
        )
        UtilityPillTile(
            icon = Icons.Default.SwapHoriz,
            label = if (rulesCount > 0) "Rules ($rulesCount)" else "Rules",
            testTag = "detail_open_replacements_button",
            onClick = onOpenReplacements,
            modifier = Modifier.weight(1f)
        )
        UtilityPillTile(
            icon = Icons.Default.FileDownload,
            label = "EPUB",
            testTag = "detail_export_novel_button",
            onClick = onExportEpub,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun UtilityPillTile(
    icon: ImageVector,
    label: String,
    testTag: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    highlight: Boolean = false
) {
    Surface(
        modifier = modifier
            .testTag(testTag)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        color = if (highlight) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            width = 1.dp,
            color = if (highlight) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
            } else {
                MaterialTheme.colorScheme.outlineVariant
            }
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp, horizontal = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (highlight) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = if (highlight) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun EditorialChapterRow(
    chapter: ChapterSummary,
    displayIndex: Int,
    isSelectionMode: Boolean,
    isSelected: Boolean,
    isCurrent: Boolean,
    isSpeaking: Boolean,
    ttsInfo: TtsPlaybackInfo,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onToggleSelect: () -> Unit,
    onToggleRead: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val containerColor = when {
        isSelected -> MaterialTheme.colorScheme.primaryContainer
        isSpeaking -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f)
        isCurrent && !chapter.read -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
        else -> MaterialTheme.colorScheme.surface
    }

    val borderColor = when {
        isSelected -> MaterialTheme.colorScheme.primary
        isSpeaking -> MaterialTheme.colorScheme.secondary.copy(alpha = 0.55f)
        isCurrent && !chapter.read -> MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
        else -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.75f)
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        shape = RoundedCornerShape(14.dp),
        color = containerColor,
        border = BorderStroke(1.dp, borderColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
                .alpha(if (chapter.read && !isCurrent && !isSpeaking && !isSelected) 0.62f else 1f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Subtle vertical editorial bookmark stripe for active/speaking chapter
            if (isSpeaking || (isCurrent && !chapter.read)) {
                Box(
                    modifier = Modifier
                        .width(4.dp)
                        .fillMaxHeight()
                        .background(
                            if (isSpeaking) MaterialTheme.colorScheme.secondary
                            else MaterialTheme.colorScheme.primary
                        )
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isSelectionMode) {
                    Checkbox(
                        checked = isSelected,
                        onCheckedChange = { onToggleSelect() }
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                }

                // Chapter index badge
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            if (chapter.read) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                            else MaterialTheme.colorScheme.surfaceVariant
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (chapter.read) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Read",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                    } else {
                        Text(
                            text = "$displayIndex",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (isSpeaking) {
                            Icon(
                                imageVector = if (ttsInfo.state == TtsState.PLAYING) {
                                    Icons.Filled.PlayArrow
                                } else {
                                    Icons.Filled.Pause
                                },
                                contentDescription = "Currently being read aloud",
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                        }

                        Text(
                            text = chapter.title,
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = if (isSpeaking || isCurrent) FontWeight.Bold else FontWeight.SemiBold
                            ),
                            color = if (isSpeaking) MaterialTheme.colorScheme.secondary
                            else MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )

                        if (isCurrent && !chapter.read) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.primary
                            ) {
                                Text(
                                    text = "Reading",
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(3.dp))

                    val readMin = maxOf(1, chapter.wordCount / 180)
                    val subtitle = buildString {
                        if (isCurrent && !chapter.read && chapter.readingParagraphIndex > 0) {
                            append("¶${chapter.readingParagraphIndex + 1}  •  ")
                        }
                        append("${String.format("%,d", chapter.wordCount)} words  •  ~$readMin min")
                    }
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (!isSelectionMode) {
                    var rowMenuOpen by remember { mutableStateOf(false) }
                    Box {
                        IconButton(
                            onClick = { rowMenuOpen = true },
                            modifier = Modifier.testTag("chapter_row_menu_${chapter.id}")
                        ) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "Chapter actions",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        DropdownMenu(
                            expanded = rowMenuOpen,
                            onDismissRequest = { rowMenuOpen = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text(if (chapter.read) "Mark as unread" else "Mark as read") },
                                onClick = {
                                    rowMenuOpen = false
                                    onToggleRead()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Edit chapter") },
                                onClick = {
                                    rowMenuOpen = false
                                    onEdit()
                                }
                            )
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        "Delete chapter",
                                        color = MaterialTheme.colorScheme.error
                                    )
                                },
                                onClick = {
                                    rowMenuOpen = false
                                    onDelete()
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AddChaptersBottomSheet(
    onWriteChapter: () -> Unit,
    onImportFile: () -> Unit,
    onPasteText: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
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
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Add Chapters",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "Append chapters from a file, clipboard, or write one directly.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(6.dp))

            SheetActionRow(
                icon = Icons.Default.FolderOpen,
                title = "Import EPUB or TXT file",
                subtitle = "Append chapters from a local file into this novel",
                testTag = "detail_import_file_button",
                onClick = onImportFile
            )
            SheetActionRow(
                icon = Icons.Default.ContentPaste,
                title = "Paste chapter text",
                subtitle = "Paste raw text and auto-split into chapters",
                testTag = "detail_paste_import_button",
                onClick = onPasteText
            )
            SheetActionRow(
                icon = Icons.Default.Add,
                title = "Write a new chapter",
                subtitle = "Open the chapter editor with a blank page",
                testTag = "detail_add_chapter_button",
                onClick = onWriteChapter
            )
        }
    }
}

@Composable
private fun SheetActionRow(
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
