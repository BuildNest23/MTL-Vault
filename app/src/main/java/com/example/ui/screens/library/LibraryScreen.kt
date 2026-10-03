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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.importexport.ChapterSplitter
import com.example.ui.components.ChapterSplitDialog
import com.example.ui.components.EmptyState
import com.example.ui.components.MtlTopBar
import com.example.ui.components.NovelCard
import com.example.ui.components.PromptDialog

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
                subtitle = "Translate • Read • Listen"
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Spacer(modifier = Modifier.height(4.dp))
                // Info banner matching ClassGrid / MTL Vault style
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                        .border(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                            shape = RoundedCornerShape(12.dp)
                        )
                        .padding(14.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Your offline novel vault. Create a novel, import text or files, then read or listen with native speech.",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = MaterialTheme.colorScheme.onSurface,
                                lineHeight = 18.sp
                            )
                        )
                    }
                }
            }

            item {
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
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = { showCreateNovelDialog = true },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(42.dp)
                                    .testTag("library_new_novel_button"),
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            ) {
                                Text("+ Novel", fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                            }

                            OutlinedButton(
                                onClick = { filePickerLauncher.launch("*/*") },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(42.dp)
                                    .testTag("library_import_file_button"),
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
                                    .testTag("library_paste_import_button"),
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp)
                            ) {
                                Icon(Icons.Default.ContentPaste, contentDescription = null, modifier = Modifier.size(15.dp))
                                Spacer(modifier = Modifier.width(3.dp))
                                Text("Paste", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                            }
                        }
                    }
                }
            }

            if (novels.isEmpty()) {
                item {
                    EmptyState(
                        icon = Icons.Default.AutoStories,
                        title = "No novels yet",
                        message = "Tap '+ Novel' to create your first book, or import an existing .txt file to begin reading.",
                        actionText = "+ New Novel",
                        onActionClick = { showCreateNovelDialog = true }
                    )
                }
            } else {
                items(
                    items = novels,
                    key = { it.id }
                ) { novel ->
                    NovelCard(
                        novel = novel,
                        onClick = { onOpenNovel(novel.id) }
                    )
                }
            }

            item {
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
