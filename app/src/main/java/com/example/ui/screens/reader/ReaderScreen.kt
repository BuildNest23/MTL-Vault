package com.example.ui.screens.reader

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Dp
import com.example.tts.SentenceSplitter
import com.example.tts.SentenceSpan
import com.example.tts.TtsHighlightState
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
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
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.TextStyle
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
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
    val initialParagraphIndex by viewModel.initialParagraphIndex.collectAsStateWithLifecycle()

    var showSettingsSheet by remember { mutableStateOf(false) }
    var showChapterPicker by remember { mutableStateOf(false) }
    var controlsVisible by remember { mutableStateOf(true) }

    val lazyListState = rememberLazyListState()

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { /* Gracefully proceed whether granted or denied */ }

    val requestNotificationPermissionIfNeeded = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

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

    LaunchedEffect(viewModel.autoPlayTtsOnLoad) {
        if (viewModel.autoPlayTtsOnLoad) {
            requestNotificationPermissionIfNeeded()
        }
    }

    val paragraphs = remember(renderedContent) {
        com.example.tts.TtsManager.parseParagraphs(renderedContent)
    }

    // Restore scroll position on chapter change / initial load to saved paragraph
    var hasRestoredPositionForChapter by remember(chapter?.id) { mutableStateOf(false) }
    LaunchedEffect(chapter?.id, paragraphs.size, initialParagraphIndex) {
        val currChapter = chapter
        if (currChapter != null && paragraphs.isNotEmpty() && !hasRestoredPositionForChapter) {
            val rawTarget = initialParagraphIndex ?: currChapter.readingParagraphIndex
            val clampedPara = rawTarget.coerceIn(0, (paragraphs.size - 1).coerceAtLeast(0))
            if (clampedPara > 0) {
                // Item 0 is header_title, item 1 is para 0, item k + 1 is para k
                val targetItemIndex = clampedPara + 1
                lazyListState.scrollToItem(targetItemIndex, 0)
            } else {
                lazyListState.scrollToItem(0, 0)
            }
            hasRestoredPositionForChapter = true
        }
    }

    // Track reading progress from LazyListState via derivedStateOf
    // Track reading progress accurately across the entire scrollable range
    val scrollProgress by remember(paragraphs.size) {
        derivedStateOf {
            val totalItems = paragraphs.size + 2
            if (totalItems <= 1 || paragraphs.isEmpty()) return@derivedStateOf 0f

            val layoutInfo = lazyListState.layoutInfo
            val visibleItems = layoutInfo.visibleItemsInfo
            if (visibleItems.isEmpty()) return@derivedStateOf 0f

            // If at the absolute bottom (cannot scroll forward or footer bottom reached)
            if (!lazyListState.canScrollForward && lazyListState.canScrollBackward) {
                return@derivedStateOf 1.0f
            }
            // If at the absolute top (cannot scroll backward and at index 0 offset 0)
            if (!lazyListState.canScrollBackward && lazyListState.firstVisibleItemIndex == 0 && lazyListState.firstVisibleItemScrollOffset == 0) {
                return@derivedStateOf 0.0f
            }

            // Check if the last item (footer nav) is visible
            val lastVisible = visibleItems.last()
            if (lastVisible.index == totalItems - 1) {
                val bottomCutoff = lastVisible.offset + lastVisible.size
                if (bottomCutoff <= layoutInfo.viewportEndOffset) {
                    return@derivedStateOf 1.0f
                }
            }

            // Calculate progress based on position in total scrollable items
            val visibleCount = visibleItems.size
            val maxScrollableIndex = (totalItems - visibleCount).coerceAtLeast(1)
            val firstIndex = lazyListState.firstVisibleItemIndex
            val firstOffset = lazyListState.firstVisibleItemScrollOffset
            val itemInfo = visibleItems.firstOrNull { it.index == firstIndex }
            val itemSize = itemInfo?.size?.takeIf { it > 0 } ?: 1
            val fractionWithinItem = (firstOffset.toFloat() / itemSize.toFloat()).coerceIn(0f, 1f)
            val currentScrollPos = firstIndex.toFloat() + fractionWithinItem

            (currentScrollPos / maxScrollableIndex.toFloat()).coerceIn(0f, 1f)
        }
    }

    // Observe scroll changes with snapshotFlow, debounce (800ms), and distinctUntilChanged
    @OptIn(FlowPreview::class)
    LaunchedEffect(lazyListState, paragraphs.size) {
        snapshotFlow {
            val firstItem = lazyListState.firstVisibleItemIndex
            val paragraphIndex = maxOf(0, firstItem - 1)
            Triple(paragraphIndex, firstItem, scrollProgress)
        }
            .distinctUntilChanged { old, new ->
                // Paragraph unchanged and progress difference is less than 1% (0.01f)
                old.first == new.first && kotlin.math.abs(old.third - new.third) < 0.01f
            }
            .debounce(800)
            .collect { (paragraphIndex, firstItem, progress) ->
                viewModel.updateReadingPosition(paragraphIndex, firstItem, progress)
            }
    }

    // Save reading position when screen leaves / on dispose
    DisposableEffect(Unit) {
        onDispose {
            viewModel.saveCurrentPositionNow()
        }
    }

    // Auto-scroll when TTS moves to a new paragraph, ensuring partially visible paragraphs also scroll up
    LaunchedEffect(ttsInfo.currentChunkIndex, ttsInfo.chapterId, chapter?.id) {
        if ((ttsInfo.state == com.example.tts.TtsState.PLAYING || ttsInfo.state == com.example.tts.TtsState.PAUSED) &&
            ttsInfo.chapterId == chapter?.id
        ) {
            val targetItemIndex = ttsInfo.currentChunkIndex + 1
            if (targetItemIndex in 0 until (paragraphs.size + 2)) {
                val layoutInfo = lazyListState.layoutInfo
                val visibleItem = layoutInfo.visibleItemsInfo.find { it.index == targetItemIndex }

                // Comfortable viewport margins accounting for top bar and bottom reader controls
                val topMargin = layoutInfo.viewportStartOffset + 50
                val bottomMargin = layoutInfo.viewportEndOffset - 140
                val availableHeight = bottomMargin - topMargin

                val isComfortablyVisible = visibleItem != null &&
                        if (visibleItem.size > availableHeight) {
                            visibleItem.offset in (topMargin - 40)..(topMargin + 100)
                        } else {
                            visibleItem.offset >= topMargin && (visibleItem.offset + visibleItem.size) <= bottomMargin
                        }

                if (!isComfortablyVisible) {
                    lazyListState.animateScrollToItem(targetItemIndex)
                }
            }
        }
    }

    // Determine colors based on reading theme
    val (bgColor, textColor, topBarBg) = when (settings.theme) {
        "sepia" -> Triple(ReaderSepiaBg, ReaderSepiaText, Color(0xFFEFE4C8))
        "dark" -> Triple(ReaderDarkBg, ReaderDarkText, Color(0xFF232019))
        "night" -> Triple(ReaderNightBg, ReaderNightText, Color(0xFF0A0A0A))
        else -> Triple(ReaderLightBg, ReaderLightText, Color(0xFFFFFFFF))
    }

    val sentenceHighlightColor = remember(settings.theme) {
        when (settings.theme) {
            "sepia" -> Color(0xFFC48B36).copy(alpha = 0.32f) // warm golden amber
            "dark" -> Color(0xFFFDD835).copy(alpha = 0.28f)  // soft yellow glow on dark
            "night" -> Color(0xFF4CAF50).copy(alpha = 0.26f) // calm emerald green on black
            else -> Color(0xFFFFD54F).copy(alpha = 0.40f)    // classic sunny yellow on light
        }
    }

    val coroutineScope = rememberCoroutineScope()

    val paragraphSentences = remember(paragraphs) {
        paragraphs.map { SentenceSplitter.splitIntoSentences(it) }
    }

    val onSentenceBoundsMeasured: (Int, Float, Float) -> Unit = remember(lazyListState) {
        { paraIdx, sTop, sBottom ->
            val targetItemIndex = paraIdx + 1
            val layoutInfo = lazyListState.layoutInfo
            val itemInfo = layoutInfo.visibleItemsInfo.find { it.index == targetItemIndex }

            val topMargin = layoutInfo.viewportStartOffset + 140
            val bottomMargin = layoutInfo.viewportEndOffset - 320

            if (itemInfo == null) {
                coroutineScope.launch {
                    lazyListState.animateScrollToItem(targetItemIndex)
                }
            } else {
                val screenTop = itemInfo.offset + sTop
                val screenBottom = itemInfo.offset + sBottom

                if (screenTop < topMargin) {
                    val delta = screenTop - topMargin - 40f
                    coroutineScope.launch {
                        lazyListState.animateScrollBy(delta)
                    }
                } else if (screenBottom > bottomMargin) {
                    val delta = screenBottom - bottomMargin + 40f
                    coroutineScope.launch {
                        lazyListState.animateScrollBy(delta)
                    }
                }
            }
        }
    }

    val readerFontFamily = if (settings.fontFamily == "sans") FontFamily.SansSerif else FontFamily.Serif

    val paragraphTextStyle = remember(readerFontFamily, settings.fontSize, settings.lineHeight, textColor) {
        TextStyle(
            fontFamily = readerFontFamily,
            fontSize = settings.fontSize.sp,
            lineHeight = (settings.fontSize * settings.lineHeight).sp,
            color = textColor
        )
    }

    val titleTextStyle = remember(readerFontFamily, textColor) {
        TextStyle(
            fontFamily = readerFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 24.sp,
            color = textColor,
            textAlign = TextAlign.Center
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bgColor)
    ) {
        // Content Area with LazyColumn for high performance
        LazyColumn(
            state = lazyListState,
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    controlsVisible = !controlsVisible
                },
            contentPadding = PaddingValues(
                horizontal = 20.dp,
                vertical = 60.dp
            ),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            item(key = "header_title") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = settings.maxWidth.dp)
                        .padding(top = 24.dp, bottom = 28.dp)
                ) {
                    Text(
                        text = chapter?.title ?: "Chapter",
                        style = titleTextStyle,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            items(
                count = paragraphs.size,
                key = { paraIndex -> "para_$paraIndex" }
            ) { paraIndex ->
                val paragraph = paragraphs[paraIndex]
                val sentences = paragraphSentences.getOrNull(paraIndex) ?: emptyList()

                ReaderParagraphItem(
                    paraIndex = paraIndex,
                    text = paragraph,
                    sentences = sentences,
                    highlightStateFlow = viewModel.ttsHighlightState,
                    highlightColor = sentenceHighlightColor,
                    textStyle = paragraphTextStyle,
                    maxWidth = settings.maxWidth.dp,
                    onClick = { controlsVisible = !controlsVisible },
                    onDoubleTapSentence = { sentenceStartOffset ->
                        requestNotificationPermissionIfNeeded()
                        viewModel.playFromSentence(paraIndex, sentenceStartOffset)
                    },
                    onSentenceBoundsMeasured = onSentenceBoundsMeasured
                )
            }

            item(key = "footer_nav") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = settings.maxWidth.dp)
                        .padding(top = 40.dp, bottom = 100.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
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
                }
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
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clickable { showChapterPicker = true }
                            .padding(vertical = 4.dp)
                    ) {
                        Text(
                            text = chapter?.title ?: "Chapter",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = textColor
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )

                        if (chapter?.read == true) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = Color(0xFFE8F5E9),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF4CAF50).copy(alpha = 0.7f)),
                                modifier = Modifier.clickable { viewModel.toggleReadStatus() }
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Read",
                                        tint = Color(0xFF2E7D32),
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text(
                                        text = "Read",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF2E7D32)
                                    )
                                }
                            }
                        }
                    }
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

        // Fixed bottom reading progress bar - turns all green when read or >= 85%
        val progressColor = if (scrollProgress >= 0.85f || chapter?.read == true) {
            Color(0xFF4CAF50)
        } else {
            MaterialTheme.colorScheme.primary
        }

        LinearProgressIndicator(
            progress = { scrollProgress },
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .align(Alignment.BottomCenter),
            color = progressColor,
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
                onPlayPause = {
                    requestNotificationPermissionIfNeeded()
                    val visiblePara = maxOf(0, lazyListState.firstVisibleItemIndex - 1)
                    viewModel.toggleTts(visiblePara)
                },
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

@Composable
private fun ReaderParagraphItem(
    paraIndex: Int,
    text: String,
    sentences: List<SentenceSpan>,
    highlightStateFlow: StateFlow<TtsHighlightState>,
    highlightColor: Color,
    textStyle: TextStyle,
    maxWidth: Dp,
    onClick: () -> Unit,
    onDoubleTapSentence: (sentenceStartOffset: Int) -> Unit,
    onSentenceBoundsMeasured: (paraIndex: Int, sentenceTop: Float, sentenceBottom: Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val highlightRange by remember(paraIndex, highlightStateFlow) {
        highlightStateFlow.map<TtsHighlightState, Pair<Int, Int>?> { state ->
            if (state.paragraphIndex == paraIndex) {
                Pair(state.sentenceStart, state.sentenceEnd)
            } else {
                null
            }
        }.distinctUntilChanged()
    }.collectAsStateWithLifecycle(initialValue = null)

    var textLayoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }

    val annotatedText = remember(text, highlightRange, highlightColor) {
        val range = highlightRange
        if (range == null || range.second <= range.first) {
            AnnotatedString(text)
        } else {
            val builder = AnnotatedString.Builder(text)
            val start = range.first.coerceIn(0, text.length)
            val end = range.second.coerceIn(start, text.length)
            if (end > start) {
                builder.addStyle(
                    SpanStyle(background = highlightColor),
                    start,
                    end
                )
            }
            builder.toAnnotatedString()
        }
    }

    LaunchedEffect(highlightRange, textLayoutResult) {
        val range = highlightRange ?: return@LaunchedEffect
        val layout = textLayoutResult ?: return@LaunchedEffect
        val start = range.first.coerceIn(0, text.length)
        val end = range.second.coerceIn(start, text.length)
        if (end > start) {
            val startLine = layout.getLineForOffset(start)
            val endLine = layout.getLineForOffset((end - 1).coerceAtLeast(start))
            val sTop = layout.getLineTop(startLine)
            val sBottom = layout.getLineBottom(endLine)
            onSentenceBoundsMeasured(paraIndex, sTop, sBottom)
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .widthIn(max = maxWidth)
            .padding(bottom = 12.dp)
            .pointerInput(text, sentences) {
                detectTapGestures(
                    onTap = { onClick() },
                    onDoubleTap = { tapOffset ->
                        val layout = textLayoutResult
                        if (layout != null) {
                            val charOffset = layout.getOffsetForPosition(tapOffset)
                            val sentence = SentenceSplitter.findSentence(sentences, charOffset)
                            val targetStart = sentence?.start ?: charOffset
                            onDoubleTapSentence(targetStart)
                        } else {
                            onDoubleTapSentence(0)
                        }
                    }
                )
            }
    ) {
        Text(
            text = annotatedText,
            style = textStyle,
            onTextLayout = { textLayoutResult = it },
            modifier = Modifier.fillMaxWidth()
        )
    }
}
