package com.example.ui.components

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The window for editing everything about a novel: title, author, translator, description
 * and the cover picture.
 *
 * Nothing is saved until Save is pressed. A cover chosen from the gallery is handed back as a
 * [Uri] in [onSave]'s `newCover`, and the caller copies it into the app. `removeCover` is true when
 * the user pressed "Remove cover" and did not choose a new one.
 *
 * @param coverUri the novel's current cover, or null
 */
@Composable
fun EditNovelDialog(
    title: String,
    author: String,
    translator: String,
    description: String,
    coverUri: String?,
    onSave: (
        title: String,
        author: String,
        translator: String,
        description: String,
        newCover: Uri?,
        removeCover: Boolean
    ) -> Unit,
    onDismiss: () -> Unit
) {
    var titleText by remember { mutableStateOf(title) }
    var authorText by remember { mutableStateOf(author) }
    var translatorText by remember { mutableStateOf(translator) }
    var descriptionText by remember { mutableStateOf(description) }
    var pickedCover by remember { mutableStateOf<Uri?>(null) }
    var coverRemoved by remember { mutableStateOf(false) }

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            pickedCover = uri
            coverRemoved = false
        }
    }

    // What the preview shows: the newly chosen picture, else the current cover unless it was removed
    val previewSource: String? = pickedCover?.toString() ?: if (coverRemoved) null else coverUri?.takeIf { it.isNotBlank() }
    val hasCover = previewSource != null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = "Edit novel", style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    CoverPreview(
                        source = previewSource,
                        modifier = Modifier
                            .size(width = 80.dp, height = 120.dp)
                            .testTag("edit_novel_cover")
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedButton(
                            onClick = { imagePicker.launch("image/*") },
                            modifier = Modifier.testTag("edit_novel_choose_cover")
                        ) {
                            Text(if (hasCover) "Change cover" else "Choose cover")
                        }
                        if (hasCover) {
                            TextButton(
                                onClick = {
                                    pickedCover = null
                                    coverRemoved = true
                                },
                                modifier = Modifier.testTag("edit_novel_remove_cover")
                            ) {
                                Text("Remove cover", color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }

                OutlinedTextField(
                    value = titleText,
                    onValueChange = { titleText = it },
                    label = { Text("Title") },
                    singleLine = true,
                    isError = titleText.isBlank(),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("edit_novel_title")
                )
                OutlinedTextField(
                    value = authorText,
                    onValueChange = { authorText = it },
                    label = { Text("Author") },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("edit_novel_author")
                )
                OutlinedTextField(
                    value = translatorText,
                    onValueChange = { translatorText = it },
                    label = { Text("Translator") },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("edit_novel_translator")
                )
                OutlinedTextField(
                    value = descriptionText,
                    onValueChange = { descriptionText = it },
                    label = { Text("Description") },
                    minLines = 4,
                    maxLines = 10,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("edit_novel_description")
                )
                Spacer(modifier = Modifier.height(2.dp))
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(titleText, authorText, translatorText, descriptionText, pickedCover, coverRemoved && pickedCover == null)
                },
                enabled = titleText.isNotBlank(),
                modifier = Modifier.testTag("edit_novel_save")
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

/** Small cover picture for the editor. Shows a book icon when there is no cover or it can't be read. */
@Composable
private fun CoverPreview(source: String?, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val picture by produceState<ImageBitmap?>(initialValue = null, source) {
        value = if (source == null) {
            null
        } else {
            withContext(Dispatchers.IO) {
                try {
                    val uri = Uri.parse(source)
                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    context.contentResolver.openInputStream(uri)?.use {
                        BitmapFactory.decodeStream(it, null, bounds)
                    }
                    var sample = 1
                    while (bounds.outWidth / (sample * 2) >= 480) sample *= 2
                    val options = BitmapFactory.Options().apply { inSampleSize = sample }
                    context.contentResolver.openInputStream(uri)?.use {
                        BitmapFactory.decodeStream(it, null, options)
                    }?.asImageBitmap()
                } catch (e: Exception) {
                    null
                } catch (e: OutOfMemoryError) {
                    null
                }
            }
        }
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        val image = picture
        if (image != null) {
            Image(
                bitmap = image,
                contentDescription = "Cover",
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize()
            )
        } else {
            Icon(
                Icons.Default.AutoStories,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(32.dp)
            )
        }
    }
}
