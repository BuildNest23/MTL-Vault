package com.example

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.NovelSummary
import com.example.importexport.ChapterSplitter
import com.example.ui.theme.MtlVaultTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Receives text shared from other apps (ChatGPT, browser, notes...) and saves it as a new
 * chapter in a chosen novel.
 *
 * Works two ways:
 *  - Share sheet: Share -> "Save to MTL Vault"
 *  - Text selection menu: select text -> (...) -> "Save to MTL Vault"
 *
 * The chapter title is taken from a leading "Chapter N ..." line if present (that line is
 * removed from the body), otherwise it is auto-numbered after the novel's highest chapter.
 */
class ShareReceiverActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val rawText = extractText(intent)?.toString()?.trim().orEmpty()
        if (rawText.isBlank()) {
            Toast.makeText(this, "Nothing to save", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        val app = application as MtlVaultApplication
        val repository = app.novelRepository
        val prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val parsed = splitHeading(rawText)

        setContent {
            MtlVaultTheme {
                ShareDialog(
                    body = parsed.body,
                    detectedTitle = parsed.title,
                    loadNovels = { repository.getNovelSummaries().first() },
                    lastNovelId = prefs.getString(KEY_LAST_NOVEL, null),
                    suggestTitle = { novelId -> repository.getNextDefaultChapterTitle(novelId) },
                    onSave = { novelId, title ->
                        repository.addChapter(novelId, title, parsed.body)
                        prefs.edit().putString(KEY_LAST_NOVEL, novelId).apply()
                    },
                    onFinished = { message ->
                        if (message != null) {
                            Toast.makeText(this, message, Toast.LENGTH_LONG).show()
                        }
                        finish()
                    }
                )
            }
        }
    }

    private fun extractText(intent: Intent?): CharSequence? {
        if (intent == null) return null
        return when (intent.action) {
            Intent.ACTION_SEND -> intent.getCharSequenceExtra(Intent.EXTRA_TEXT)
            Intent.ACTION_PROCESS_TEXT -> intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)
            else -> null
        }
    }

    private data class Parsed(val title: String?, val body: String)

    /**
     * If the first non-blank line looks like "Chapter 2471: Title" (optionally wrapped in
     * markdown # or ** from ChatGPT), use it as the title and drop it from the body.
     */
    private fun splitHeading(text: String): Parsed {
        val lines = text.lines()
        val firstIdx = lines.indexOfFirst { it.isNotBlank() }
        if (firstIdx < 0) return Parsed(null, text)

        val cleaned = lines[firstIdx].trim()
            .trimStart('#', '*', ' ')
            .trimEnd('*', ' ')
        val looksLikeHeading = cleaned.length <= 150 && HEADING_REGEX.matches(cleaned)
        if (!looksLikeHeading) return Parsed(null, text)

        val body = lines.drop(firstIdx + 1).joinToString("\n").trim()
        // A lone heading with no text after it: keep everything as the body instead
        if (body.isBlank()) return Parsed(null, text)
        return Parsed(cleaned, body)
    }

    companion object {
        private const val PREFS = "share_receiver"
        private const val KEY_LAST_NOVEL = "last_novel_id"
        private val HEADING_REGEX = Regex(
            """(?i)^(?:chapter|ch\.?|chapitre|capitulo|capítulo)\s*\d+.*$"""
        )
    }
}

@Composable
private fun ShareDialog(
    body: String,
    detectedTitle: String?,
    loadNovels: suspend () -> List<NovelSummary>,
    lastNovelId: String?,
    suggestTitle: suspend (String) -> String,
    onSave: suspend (novelId: String, title: String) -> Unit,
    onFinished: (message: String?) -> Unit
) {
    val scope = rememberCoroutineScope()
    var novels by remember { mutableStateOf<List<NovelSummary>?>(null) }
    var selectedId by remember { mutableStateOf<String?>(null) }
    var title by remember { mutableStateOf(detectedTitle.orEmpty()) }
    var titleEdited by remember { mutableStateOf(detectedTitle != null) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val wordCount = remember(body) { ChapterSplitter.countWords(body) }

    LaunchedEffect(Unit) {
        val list = try {
            loadNovels()
        } catch (e: Exception) {
            emptyList()
        }
        novels = list
        // Library is sorted by last update, so the first novel is the one you added to last.
        selectedId = list.firstOrNull { it.id == lastNovelId }?.id ?: list.firstOrNull()?.id
    }

    LaunchedEffect(selectedId) {
        val id = selectedId ?: return@LaunchedEffect
        if (!titleEdited) {
            title = try {
                suggestTitle(id)
            } catch (e: Exception) {
                ""
            }
        }
    }

    val list = novels
    AlertDialog(
        onDismissRequest = { if (!saving) onFinished(null) },
        title = { Text("Save to MTL Vault") },
        text = {
            Column {
                when {
                    list == null -> Text("Loading…")
                    list.isEmpty() -> Text("No novels yet. Create a novel in MTL Vault first, then share again.")
                    else -> {
                        Text(
                            "$wordCount words",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = title,
                            onValueChange = {
                                title = it
                                titleEdited = true
                            },
                            label = { Text("Chapter title") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(12.dp))
                        Text("Novel", style = MaterialTheme.typography.labelLarge)
                        LazyColumn(modifier = Modifier.heightIn(max = 220.dp)) {
                            items(list, key = { it.id }) { novel ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { selectedId = novel.id }
                                        .padding(vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    RadioButton(
                                        selected = selectedId == novel.id,
                                        onClick = { selectedId = novel.id }
                                    )
                                    Column {
                                        Text(
                                            novel.title,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            "${novel.totalChapters} chapters",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                        error?.let {
                            Spacer(Modifier.height(8.dp))
                            Text(it, color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        },
        confirmButton = {
            val canSave = !saving && selectedId != null && !list.isNullOrEmpty()
            TextButton(
                enabled = canSave,
                onClick = {
                    val id = selectedId ?: return@TextButton
                    val novelTitle = list?.firstOrNull { it.id == id }?.title.orEmpty()
                    val finalTitle = title.trim()
                    saving = true
                    scope.launch {
                        try {
                            onSave(id, finalTitle)
                            onFinished("Saved to \"$novelTitle\"" + if (finalTitle.isNotEmpty()) ": $finalTitle" else "")
                        } catch (e: Exception) {
                            saving = false
                            error = "Could not save: ${e.message ?: "unknown error"}"
                        }
                    }
                }
            ) { Text(if (saving) "Saving…" else "Save") }
        },
        dismissButton = {
            TextButton(enabled = !saving, onClick = { onFinished(null) }) { Text("Cancel") }
        }
    )
}
