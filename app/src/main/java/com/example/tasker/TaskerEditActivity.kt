package com.example.tasker

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.MtlVaultApplication
import com.example.data.model.NovelSummary
import com.example.ui.theme.MtlVaultTheme
import kotlinx.coroutines.flow.first

/**
 * The settings screen Tasker opens when you add or edit the "MTL Vault" action.
 * Pick the novel the chapters should go to and, if you like, change the variable that holds the text.
 */
class TaskerEditActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val repository = (application as MtlVaultApplication).novelRepository
        val previous = intent?.getBundleExtra(TaskerPlugin.EXTRA_BUNDLE)
        val initialNovelId = previous?.getString(TaskerPlugin.KEY_NOVEL_ID)
        val initialText = previous?.getString(TaskerPlugin.KEY_TEXT)
            ?.takeIf { it.isNotBlank() } ?: TaskerPlugin.DEFAULT_TEXT_VARIABLE

        val initialChapter = if (previous != null && previous.containsKey(TaskerPlugin.KEY_CHAPTER)) {
            previous.getString(TaskerPlugin.KEY_CHAPTER).orEmpty()
        } else {
            TaskerPlugin.DEFAULT_CHAPTER_VARIABLE
        }

        setContent {
            MtlVaultTheme {
                PickNovelDialog(
                    loadNovels = { repository.getNovelSummaries().first() },
                    initialNovelId = initialNovelId,
                    initialText = initialText,
                    initialChapter = initialChapter,
                    onConfirm = { novel, text, chapter -> finishWithResult(novel, text, chapter) },
                    onCancel = {
                        setResult(RESULT_CANCELED)
                        finish()
                    }
                )
            }
        }
    }

    private fun finishWithResult(novel: NovelSummary, text: String, chapter: String) {
        val result = Intent().apply {
            putExtra(TaskerPlugin.EXTRA_BUNDLE, TaskerPlugin.buildBundle(novel.id, novel.title, text, chapter))
            putExtra(TaskerPlugin.EXTRA_STRING_BLURB, TaskerPlugin.blurb(novel.title))
        }
        setResult(RESULT_OK, result)
        finish()
    }
}

@Composable
private fun PickNovelDialog(
    loadNovels: suspend () -> List<NovelSummary>,
    initialNovelId: String?,
    initialText: String,
    initialChapter: String,
    onConfirm: (NovelSummary, String, String) -> Unit,
    onCancel: () -> Unit
) {
    var novels by remember { mutableStateOf<List<NovelSummary>?>(null) }
    var selectedId by remember { mutableStateOf(initialNovelId) }
    var text by remember { mutableStateOf(initialText) }
    var chapter by remember { mutableStateOf(initialChapter) }

    LaunchedEffect(Unit) {
        val list = try {
            loadNovels()
        } catch (e: Exception) {
            emptyList()
        }
        novels = list
        if (list.none { it.id == selectedId }) selectedId = list.firstOrNull()?.id
    }

    val list = novels
    val selected = list?.firstOrNull { it.id == selectedId }

    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("Save chapter to novel") },
        text = {
            Column {
                when {
                    list == null -> Text("Loading…")
                    list.isEmpty() -> Text("No novels yet. Create a novel in MTL Vault first, then edit this action again.")
                    else -> {
                        Text("Novel", style = MaterialTheme.typography.labelLarge)
                        LazyColumn(modifier = Modifier.heightIn(max = 240.dp)) {
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
                                        Text(novel.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(
                                            "${novel.totalChapters} chapters",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        OutlinedTextField(
                            value = text,
                            onValueChange = { text = it },
                            label = { Text("Chapter text (Tasker variable)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = chapter,
                            onValueChange = { chapter = it },
                            label = { Text("Chapter number (Tasker variable, optional)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "With a chapter number the chapter is titled \"Chapter N\" and skipped if the novel already has it. Leave it empty and the app uses the next number after the highest chapter.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = selected != null && text.isNotBlank(),
                onClick = { selected?.let { onConfirm(it, text.trim(), chapter.trim()) } }
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onCancel) { Text("Cancel") } }
    )
}
