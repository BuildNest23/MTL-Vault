package com.example.ui.screens.reader

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.tts.TtsManager
import com.example.ui.components.ReaderSettingsBottomSheet
import com.example.ui.components.TtsPlayerBar
import com.example.ui.theme.ReaderDarkBg
import com.example.ui.theme.ReaderDarkText
import com.example.ui.theme.ReaderLightBg
import com.example.ui.theme.ReaderLightText
import com.example.ui.theme.ReaderNightBg
import com.example.ui.theme.ReaderNightText
import com.example.ui.theme.ReaderSepiaBg
import com.example.ui.theme.ReaderSepiaText

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(
    viewModel: ReaderViewModel,
    onBackClick: () -> Unit,
    onOpenReplacements: () -> Unit
) {
    val context = LocalContext.current
    val chapter by viewModel.currentChapter.collectAsStateWithLifecycle()
    val renderedContent by viewModel.renderedContent.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val ttsInfo by viewModel.ttsPlaybackInfo.collectAsStateWithLifecycle()
    val chaptersList by viewModel.chaptersList.collectAsStateWithLifecycle()

    var showSettingsSheet by remember { mutableStateOf(false) }
    var showChapterPicker by remember { mutableStateOf(false) }
    var controlsVisible by remember { mutableStateOf(true) }

    val scrollState = rememberScrollState()

    BackHandler {
        onBackClick()
    }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is ReaderEvent.ShowToast -> Toast.makeText(context, event.message, Toast.LENGTH_SHORT).show()
                is ReaderEvent.NavigateToChapter -> viewModel.loadChapter(event.chapterId)
            }
        }
    }

    // Restore scroll position on chapter change
    LaunchedEffect(chapter?.id) {
        chapter?.let { ch ->
            if (ch.scrollPosition > 0) {
                scrollState.scrollTo(ch.scrollPosition)
            } else {
                scrollState.scrollTo(0)
            }
        }
    }

    // Track reading progress
    val scrollProgress by remember {
        derivedStateOf {
            val max = scrollState.maxValue
            if (max > 0) (scrollState.value.toFloat() / max.toFloat()).coerceIn(0f, 1f) else 0f
        }
    }

    LaunchedEffect(scrollState.value) {
        viewModel.updateScrollPosition(scrollState.value, scrollProgress)
    }

    // Determine colors based on reading theme
    val (bgColor, textColor, topBarBg) = when (settings.theme) {
        "sepia" -> Triple(ReaderSepiaBg, ReaderSepiaText, Color(0xFFEFE4C8))
        "dark" -> Triple(ReaderDarkBg, ReaderDarkText, Color(0xFF232019))
        "night" -> Triple(ReaderNightBg, ReaderNightText, Color(0xFF0A0A0A))
        else -> Triple(ReaderLightBg, ReaderLightText, Color(0xFFFFFFFF))
    }

    val readerFontFamily = if (settings.fontFamily == "sans") FontFamily.SansSerif else FontFamily.Serif

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bgColor)
    ) {
        // Content Area
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .clickable { controlsVisible = !controlsVisible }
                .padding(horizontal = 20.dp, vertical = 60.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(24.dp))

            // Text constrained by settings.maxWidth for tablet/foldable readability
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = settings.maxWidth.dp)
            ) {
                Text(
                    text = chapter?.title ?: "Chapter",
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontFamily = readerFontFamily,
                        fontWeight = FontWeight.Bold,
                        color = textColor,
                        textAlign = TextAlign.Center
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(28.dp))

                val paragraphs = remember(renderedContent) {
                    TtsManager.parseParagraphs(renderedContent)
                }

                paragraphs.forEachIndexed { paraIndex, paragraph ->
                    val isReadingThis = (ttsInfo.state == com.example.tts.TtsState.PLAYING || ttsInfo.state == com.example.tts.TtsState.PAUSED) &&
                            ttsInfo.currentChunkIndex == paraIndex &&
                            ttsInfo.chapterId == chapter?.id

                    val highlightBg = when (settings.theme) {
                        "sepia" -> Color(0xFFECD7B2)
                        "dark" -> Color(0xFF3B352A)
                        "night" -> Color(0xFF282828)
                        else -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                    }

                    @OptIn(ExperimentalFoundationApi::class)
                    val paraModifier = if (isReadingThis) {
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(highlightBg)
                            .border(
                                width = 2.dp,
                                color = MaterialTheme.colorScheme.primary,
                                shape = RoundedCornerShape(8.dp)
                            )
                            .combinedClickable(
                                onClick = { controlsVisible = !controlsVisible },
                                onDoubleClick = { viewModel.playFromParagraph(paraIndex) }
                            )
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    } else {
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .combinedClickable(
                                onClick = { controlsVisible = !controlsVisible },
                                onDoubleClick = { viewModel.playFromParagraph(paraIndex) }
                            )
                            .padding(horizontal = 4.dp, vertical = 4.dp)
                    }

                    Box(modifier = paraModifier) {
                        Row(
                            verticalAlignment = Alignment.Top,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (isReadingThis) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                                    contentDescription = "Currently Reading",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier
                                        .size(20.dp)
                                        .padding(top = 2.dp, end = 6.dp)
                                )
                            }
                            Text(
                                text = paragraph,
                                style = MaterialTheme.typography.bodyLarge.copy(
                                    fontFamily = readerFontFamily,
                                    fontSize = settings.fontSize.sp,
                                    lineHeight = (settings.fontSize * settings.lineHeight).sp,
                                    color = textColor
                                ),
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                }

                Spacer(modifier = Modifier.height(40.dp))

                // End of chapter navigation buttons
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    TextButton(
                        onClick = { viewModel.prevChapter() },
                        modifier = Modifier.testTag("reader_end_prev_button")
                    ) {
                        Text("← Prev Chapter", color = textColor)
                    }

                    TextButton(
                        onClick = { viewModel.nextChapter() },
                        modifier = Modifier.testTag("reader_end_next_button")
                    ) {
                        Text("Next Chapter →", color = textColor)
                    }
                }

                Spacer(modifier = Modifier.height(100.dp))
            }
        }

        // Animated Top Navigation Bar
        AnimatedVisibility(
            visible = controlsVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            TopAppBar(
                title = {
                    Text(
                        text = chapter?.title ?: "Chapter",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = textColor
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .clickable { showChapterPicker = true }
                            .padding(vertical = 4.dp)
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBackClick,
                        modifier = Modifier.testTag("reader_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = textColor
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = onOpenReplacements,
                        modifier = Modifier.testTag("reader_replacements_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.SwapHoriz,
                            contentDescription = "Text Replacements",
                            tint = textColor
                        )
                    }

                    FilledIconButton(
                        onClick = { viewModel.shareToVoice(context) },
                        modifier = Modifier
                            .size(36.dp)
                            .testTag("reader_quick_voice_button"),
                        shape = RoundedCornerShape(8.dp),
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Play in @Voice",
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    IconButton(
                        onClick = { showSettingsSheet = true },
                        modifier = Modifier.testTag("reader_typography_settings_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.FormatSize,
                            contentDescription = "Appearance Settings",
                            tint = textColor
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = topBarBg.copy(alpha = 0.95f)
                )
            )
        }

        // Fixed bottom reading progress bar
        LinearProgressIndicator(
            progress = { scrollProgress },
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .align(Alignment.BottomCenter),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            strokeCap = StrokeCap.Square
        )

        // Floating Compact TTS Control Bar (collapsible or hides with controls)
        AnimatedVisibility(
            visible = controlsVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 12.dp)
        ) {
            TtsPlayerBar(
                playbackInfo = ttsInfo,
                speechRate = settings.ttsRate,
                speechPitch = settings.ttsPitch,
                onPlayPause = { viewModel.toggleTts() },
                onStop = { viewModel.stopTts() },
                onPrevChapter = { viewModel.prevChapter() },
                onNextChapter = { viewModel.nextChapter() },
                onRateChange = { viewModel.setTtsRate(it) },
                onPitchChange = { viewModel.setTtsPitch(it) }
            )
        }
    }

    // Appearance Modal Bottom Sheet
    if (showSettingsSheet) {
        ReaderSettingsBottomSheet(
            settings = settings,
            onUpdateSettings = { viewModel.updateSettings(it) },
            onShareVoiceClick = { viewModel.shareToVoice(context) },
            onDismiss = { showSettingsSheet = false }
        )
    }

    // Chapter Picker Dialog
    if (showChapterPicker) {
        var query by remember { mutableStateOf("") }
        val filteredChapters = remember(query, chaptersList) {
            if (query.isBlank()) chaptersList else chaptersList.filter { it.title.contains(query, ignoreCase = true) }
        }

        AlertDialog(
            onDismissRequest = { showChapterPicker = false },
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Select Chapter", style = MaterialTheme.typography.titleLarge)
                    IconButton(onClick = { showChapterPicker = false }) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = { Text("Search chapters...") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(350.dp)
                    ) {
                        itemsIndexed(filteredChapters) { _, ch ->
                            val isCurrent = ch.id == chapter?.id
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        showChapterPicker = false
                                        viewModel.loadChapter(ch.id)
                                    },
                                color = if (isCurrent) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "${ch.orderIndex + 1}. ${ch.title}",
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                                            color = if (isCurrent) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                        ),
                                        modifier = Modifier.weight(1f)
                                    )
                                    if (ch.read) {
                                        Text(
                                            text = "✓",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showChapterPicker = false }) {
                    Text("Close")
                }
            },
            shape = RoundedCornerShape(16.dp)
        )
    }
}
