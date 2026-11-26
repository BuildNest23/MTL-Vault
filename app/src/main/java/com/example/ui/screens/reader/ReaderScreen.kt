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
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Dp
import com.example.tts.SentenceSplitter
import com.example.tts.SentenceSpan
import com.example.tts.TtsHighlightState
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.TextStyle
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.rememberDrawerState
import androidx.compose.foundation.layout.fillMaxHeight
import com.example.data.database.entities.NovelAnnotationEntity
import com.example.data.model.ChapterSummary
import com.example.data.model.NovelSearchHit
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.draw.drawBehind
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.Job
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
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalTextToolbar
import com.example.ui.components.AddRuleFromSelectionDialog
import com.example.ui.components.NovelSearchResultsList
import com.example.ui.components.ReaderSelectionPopup
import com.example.ui.components.rememberReaderTextToolbar
import com.example.ui.components.ReaderSettingsBottomSheet
import com.example.ui.components.TtsPlayerBar
import com.example.ui.theme.LoraFontFamily
import com.example.ui.theme.PlayfairDisplayFontFamily
import com.example.ui.theme.PlusJakartaSansFontFamily
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
    onOpenReplacements: () -> Unit,
    onOpenVoices: () -> Unit = {}
) {
    val context = LocalContext.current
    val chapter by viewModel.currentChapter.collectAsStateWithLifecycle()
    val renderedContent by viewModel.renderedContent.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val ttsInfo by viewModel.ttsPlaybackInfo.collectAsStateWithLifecycle()
    val sleepTimer by viewModel.ttsSleepTimer.collectAsStateWithLifecycle()
    val edgeProgress by viewModel.edgeProgress.collectAsStateWithLifecycle()
    val chaptersList by viewModel.chaptersList.collectAsStateWithLifecycle()
    val novelAnnotations by viewModel.novelAnnotations.collectAsStateWithLifecycle()
    val chapterAnnotations by viewModel.chapterAnnotations.collectAsStateWithLifecycle()
    val initialParagraphIndex by viewModel.initialParagraphIndex.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val searchResults by viewModel.searchResults.collectAsStateWithLifecycle()
    val isSearching by viewModel.isSearching.collectAsStateWithLifecycle()

    var showSettingsSheet by remember { mutableStateOf(false) }
    val selectionToolbar = rememberReaderTextToolbar()
    // Text the person selected and wants to turn into a replacement rule (null = dialog closed)
    var replaceSeed by remember { mutableStateOf<String?>(null) }
    // Pending selection to save as a highlight with a reading note
    var pendingNoteSelection by remember { mutableStateOf<LocatedSelection?>(null) }
    // Existing highlight or bookmark being edited in the bottom sheet
    var editingAnnotation by remember { mutableStateOf<NovelAnnotationEntity?>(null) }
    // 0 = Chapters, 1 = Notes & Bookmarks, 2 = Full-Novel Search
    var drawerTab by remember { mutableStateOf(0) }
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val drawerScope = rememberCoroutineScope()
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

    BackHandler(enabled = !drawerState.isOpen) {
        onBackClick()
    }
    // Back closes the chapter list first
    BackHandler(enabled = drawerState.isOpen) {
        drawerScope.launch { drawerState.close() }
    }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is ReaderEvent.ShowToast -> Toast.makeText(context, event.message, Toast.LENGTH_SHORT).show()
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

    val annotationsByParagraph = remember(chapterAnnotations) {
        chapterAnnotations.groupBy { it.paragraphIndex }
    }

    val currentVisibleParagraph by remember(paragraphs.size) {
        derivedStateOf {
            val raw = maxOf(0, lazyListState.firstVisibleItemIndex - 1)
            raw.coerceIn(0, (paragraphs.size - 1).coerceAtLeast(0))
        }
    }

    val currentParagraphBookmark = remember(annotationsByParagraph, currentVisibleParagraph) {
        annotationsByParagraph[currentVisibleParagraph]
            ?.firstOrNull { it.type == NovelAnnotationEntity.TYPE_BOOKMARK }
    }

    // Jump smoothly to a paragraph within the already-open chapter (e.g., tapping a bookmark in drawer)
    LaunchedEffect(Unit) {
        viewModel.scrollToParagraphEvent.collect { targetPara ->
            if (paragraphs.isNotEmpty()) {
                val clamped = targetPara.coerceIn(0, paragraphs.lastIndex)
                lazyListState.scrollToItem(clamped + 1, 0)
            }
        }
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

    // --- Auto-scroll: ONE controller ---------------------------------------------------------
    // Before, the per-paragraph scroll and the per-sentence scroll could run at the same time and cancel
    // each other mid-animation (jitter), and nothing stopped them when the finger touched the list.
    // Now: only one auto-scroll job exists at a time, a touch cancels it immediately, and long jumps
    // (e.g. a new chapter opening far from the spoken paragraph) are instant instead of animated.
    val coroutineScope = rememberCoroutineScope()
    val autoScrollJob = remember { arrayOfNulls<Job>(1) }
    val autoScrollToken = remember { longArrayOf(0L) }
    val autoScrollRunning = remember { booleanArrayOf(false) }
    val lastUserScrollAt = remember { longArrayOf(0L) }
    val lastAutoScrollEnd = remember { longArrayOf(0L) }
    LaunchedEffect(lazyListState) {
        snapshotFlow { lazyListState.isScrollInProgress }.collect {
            // Scroll started/ended and it was not caused by our own animation => user scroll
            val now = android.os.SystemClock.uptimeMillis()
            // Ignore the state flip caused by the end of our own animation
            if (!autoScrollRunning[0] && now - lastAutoScrollEnd[0] > 300L) lastUserScrollAt[0] = now
        }
    }
    val userIsScrolling: () -> Boolean = {
        (lazyListState.isScrollInProgress && !autoScrollRunning[0]) ||
            android.os.SystemClock.uptimeMillis() - lastUserScrollAt[0] < 2500L
    }

    val runAutoScroll: (suspend () -> Unit) -> Unit = remember(lazyListState) {
        { block ->
            autoScrollJob[0]?.cancel()
            autoScrollToken[0] = autoScrollToken[0] + 1
            val token = autoScrollToken[0]
            autoScrollJob[0] = coroutineScope.launch {
                autoScrollRunning[0] = true
                try {
                    block()
                } finally {
                    if (autoScrollToken[0] == token) {
                        autoScrollRunning[0] = false
                        lastAutoScrollEnd[0] = android.os.SystemClock.uptimeMillis()
                    }
                }
            }
        }
    }

    val jumpToItem: suspend (Int) -> Unit = { item ->
        val first = lazyListState.firstVisibleItemIndex
        if (kotlin.math.abs(first - item) > 3) {
            lazyListState.scrollToItem(item) // far away: instant, no long animated sweep
        } else {
            lazyListState.animateScrollToItem(item)
        }
    }

    // Auto-scroll when TTS moves to a paragraph that is NOT on screen. Paragraphs that are already
    // visible are kept comfortably in view by the per-sentence scroller (onSentenceBoundsMeasured).
    LaunchedEffect(ttsInfo.currentChunkIndex, ttsInfo.chapterId, chapter?.id) {
        if (userIsScrolling()) return@LaunchedEffect
        val active = ttsInfo.state == com.example.tts.TtsState.PLAYING || ttsInfo.state == com.example.tts.TtsState.PAUSED
        if (active && ttsInfo.chapterId == chapter?.id) {
            val targetItemIndex = ttsInfo.currentChunkIndex + 1
            if (targetItemIndex in 0 until (paragraphs.size + 2)) {
                val layoutInfo = lazyListState.layoutInfo
                val visibleItem = layoutInfo.visibleItemsInfo.find { it.index == targetItemIndex }
                val needsScroll = if (visibleItem == null) {
                    true
                } else if (ttsInfo.state == com.example.tts.TtsState.PAUSED) {
                    // While paused no sentence highlight drives scrolling, so keep the old comfort check
                    val topMargin = layoutInfo.viewportStartOffset + 50
                    val bottomMargin = layoutInfo.viewportEndOffset - 140
                    visibleItem.offset < topMargin || (visibleItem.offset + visibleItem.size) > bottomMargin
                } else {
                    false
                }
                if (needsScroll) runAutoScroll { jumpToItem(targetItemIndex) }
            }
        }
    }

    // Coming back to the app (e.g. via the media notification): jump straight to the spoken paragraph
    val ttsInfoNow = rememberUpdatedState(ttsInfo)
    val chapterIdNow = rememberUpdatedState(chapter?.id)
    val paragraphCountNow = rememberUpdatedState(paragraphs.size)
    val activityOwner = context as? LifecycleOwner
    DisposableEffect(activityOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val info = ttsInfoNow.value
                val active = info.state == com.example.tts.TtsState.PLAYING || info.state == com.example.tts.TtsState.PAUSED
                if (active && info.chapterId == chapterIdNow.value && paragraphCountNow.value > 0) {
                    val target = (info.currentChunkIndex + 1).coerceIn(0, paragraphCountNow.value + 1)
                    coroutineScope.launch { lazyListState.scrollToItem(target) }
                }
            }
        }
        activityOwner?.lifecycle?.addObserver(observer)
        onDispose { activityOwner?.lifecycle?.removeObserver(observer) }
    }

    // Rough "time left" for the player; recalculated whenever the spoken paragraph or speed changes
    val remainingSeconds = remember(ttsInfo.currentChunkIndex, ttsInfo.totalChunks, ttsInfo.chapterId, settings.ttsRate) {
        viewModel.ttsRemainingSeconds()
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

    val onSentenceBoundsMeasured: (Int, Float, Float) -> Unit = remember(lazyListState) {
        { paraIdx, sTop, sBottom ->
          if (!userIsScrolling()) {
            val targetItemIndex = paraIdx + 1
            val layoutInfo = lazyListState.layoutInfo
            val itemInfo = layoutInfo.visibleItemsInfo.find { it.index == targetItemIndex }

            val topMargin = layoutInfo.viewportStartOffset + 140
            val bottomMargin = layoutInfo.viewportEndOffset - 320

            if (itemInfo == null) {
                runAutoScroll { jumpToItem(targetItemIndex) }
            } else {
                val screenTop = itemInfo.offset + sTop
                val screenBottom = itemInfo.offset + sBottom

                if (screenTop < topMargin) {
                    val delta = screenTop - topMargin - 40f
                    runAutoScroll {
                        lazyListState.animateScrollBy(delta, androidx.compose.animation.core.tween(300))
                    }
                } else if (screenBottom > bottomMargin) {
                    val delta = screenBottom - bottomMargin + 40f
                    runAutoScroll {
                        lazyListState.animateScrollBy(delta, androidx.compose.animation.core.tween(300))
                    }
                }
            }
          }
        }
    }

    val readerFontFamily = when (settings.fontFamily) {
        "sans" -> PlusJakartaSansFontFamily
        "system" -> FontFamily.Default
        else -> LoraFontFamily
    }

    val paragraphTextStyle = remember(readerFontFamily, settings.fontSize, settings.lineHeight, textColor) {
        TextStyle(
            fontFamily = readerFontFamily,
            fontSize = settings.fontSize.sp,
            lineHeight = (settings.fontSize * settings.lineHeight).sp,
            color = textColor
        )
    }

    val titleTextStyle = remember(textColor) {
        TextStyle(
            fontFamily = PlayfairDisplayFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 26.sp,
            lineHeight = 34.sp,
            color = textColor,
            textAlign = TextAlign.Center
        )
    }

    val effectiveMaxWidth = settings.maxWidth.dp.coerceAtMost(680.dp)
    val currentChapterPos = remember(chaptersList, chapter?.id) {
        chaptersList.indexOfFirst { it.id == chapter?.id }
    }

    // Swipe right anywhere on the page (or tap the title / list button) to open the drawer
    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = true,
        drawerContent = {
            ChapterDrawerContent(
                chapters = chaptersList,
                currentChapterId = chapter?.id,
                annotations = novelAnnotations,
                searchQuery = searchQuery,
                searchResults = searchResults,
                isSearching = isSearching,
                selectedTab = drawerTab,
                onSelectTab = { drawerTab = it },
                isOpen = drawerState.targetValue == DrawerValue.Open,
                onChapterClick = { chapterId ->
                    drawerScope.launch { drawerState.close() }
                    if (chapterId != chapter?.id) viewModel.loadChapter(chapterId)
                },
                onAnnotationClick = { ann ->
                    drawerScope.launch { drawerState.close() }
                    viewModel.jumpToAnnotation(ann)
                },
                onEditAnnotation = { ann -> editingAnnotation = ann },
                onDeleteAnnotation = { ann -> viewModel.deleteAnnotation(ann.id) },
                onSearchQueryChange = { viewModel.updateSearchQuery(it) },
                onClearSearch = { viewModel.clearSearch() },
                onSearchHitClick = { hit ->
                    drawerScope.launch { drawerState.close() }
                    viewModel.jumpToSearchHit(hit)
                }
            )
        }
    ) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bgColor)
    ) {
        // Content Area with LazyColumn for high performance.
        // Wrapped in a SelectionContainer so text can be long-pressed and selected; the floating
        // menu (Copy / Replace / Define / More) is our own, see ReaderSelectionMenu.kt.
        CompositionLocalProvider(LocalTextToolbar provides selectionToolbar) {
        SelectionContainer {
        LazyColumn(
            state = lazyListState,
            modifier = Modifier
                .fillMaxSize()
                // A finger on the list cancels any running auto-scroll right away (no fighting the user)
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            if (event.changes.any { it.pressed }) {
                                lastUserScrollAt[0] = android.os.SystemClock.uptimeMillis()
                                autoScrollJob[0]?.cancel()
                            }
                        }
                    }
                }
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    controlsVisible = !controlsVisible
                },
            contentPadding = PaddingValues(
                start = 20.dp,
                end = 20.dp,
                top = 60.dp,
                bottom = 150.dp // keeps the end of the chapter clear of the docked player
            ),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            item(key = "header_title") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = effectiveMaxWidth)
                        .padding(top = 28.dp, bottom = 30.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (currentChapterPos >= 0 && chaptersList.isNotEmpty()) {
                        Text(
                            text = "CHAPTER ${currentChapterPos + 1} OF ${chaptersList.size}",
                            style = MaterialTheme.typography.labelSmall.copy(
                                letterSpacing = 1.4.sp,
                                color = textColor.copy(alpha = 0.55f)
                            )
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                    }
                    Text(
                        text = chapter?.title ?: "Chapter",
                        style = titleTextStyle,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .width(32.dp)
                                .height(1.dp)
                                .background(textColor.copy(alpha = 0.22f))
                        )
                        Text(
                            text = "✦",
                            fontSize = 11.sp,
                            color = textColor.copy(alpha = 0.40f)
                        )
                        Box(
                            modifier = Modifier
                                .width(32.dp)
                                .height(1.dp)
                                .background(textColor.copy(alpha = 0.22f))
                        )
                    }
                }
            }

            items(
                count = paragraphs.size,
                key = { paraIndex -> "para_$paraIndex" }
            ) { paraIndex ->
                val paragraph = paragraphs[paraIndex]
                // Split one paragraph at a time, only when it is on screen. Splitting the whole chapter
                // up front blocked the main thread for a moment every time a long chapter opened.
                val sentences = remember(paragraph) { SentenceSplitter.splitIntoSentences(paragraph) }
                val paraAnnotations = annotationsByParagraph[paraIndex].orEmpty()

                ReaderParagraphItem(
                    paraIndex = paraIndex,
                    text = paragraph,
                    sentences = sentences,
                    paragraphAnnotations = paraAnnotations,
                    readerTheme = settings.theme,
                    highlightStateFlow = viewModel.ttsHighlightState,
                    highlightColor = sentenceHighlightColor,
                    textStyle = paragraphTextStyle,
                    maxWidth = effectiveMaxWidth,
                    onClick = { controlsVisible = !controlsVisible },
                    onAnnotationClick = { ann -> editingAnnotation = ann },
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
                        .widthIn(max = effectiveMaxWidth)
                        .padding(top = 36.dp, bottom = 100.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    HorizontalDivider(
                        color = textColor.copy(alpha = 0.14f),
                        modifier = Modifier.padding(bottom = 20.dp)
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .testTag("reader_end_prev_button")
                                .clickable { viewModel.prevChapter() },
                            shape = RoundedCornerShape(14.dp),
                            color = textColor.copy(alpha = 0.06f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, textColor.copy(alpha = 0.14f))
                        ) {
                            Text(
                                text = "← Previous",
                                style = MaterialTheme.typography.labelLarge,
                                color = textColor,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(vertical = 14.dp, horizontal = 12.dp)
                            )
                        }

                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .testTag("reader_end_next_button")
                                .clickable { viewModel.nextChapter() },
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.primary
                        ) {
                            Text(
                                text = "Next Chapter →",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onPrimary,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(vertical = 14.dp, horizontal = 12.dp)
                            )
                        }
                    }
                }
            }
        }
        }
        }

        ReaderSelectionPopup(
            toolbar = selectionToolbar,
            onReplace = { selected -> replaceSeed = selected },
            onHighlight = { selected, colorKey ->
                val visibleParas = lazyListState.layoutInfo.visibleItemsInfo
                    .mapNotNull { info -> (info.index - 1).takeIf { it in paragraphs.indices } }
                locateSelectionInParagraphs(paragraphs, visibleParas, selected)?.let { located ->
                    viewModel.addHighlight(
                        paragraphIndex = located.paragraphIndex,
                        quoteText = located.quoteText,
                        startOffset = located.startOffset,
                        endOffset = located.endOffset,
                        colorKey = colorKey
                    )
                }
            },
            onAddNote = { selected ->
                val visibleParas = lazyListState.layoutInfo.visibleItemsInfo
                    .mapNotNull { info -> (info.index - 1).takeIf { it in paragraphs.indices } }
                pendingNoteSelection = locateSelectionInParagraphs(paragraphs, visibleParas, selected)
            }
        )

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
                            .clickable { drawerScope.launch { drawerState.open() } }
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
                        onClick = {
                            drawerTab = 0
                            drawerScope.launch { drawerState.open() }
                        },
                        modifier = Modifier.testTag("reader_chapter_list_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.List,
                            contentDescription = "Chapters and notes drawer",
                            tint = textColor
                        )
                    }

                    IconButton(
                        onClick = {
                            val preview = paragraphs.getOrNull(currentVisibleParagraph).orEmpty()
                            viewModel.toggleBookmarkAt(currentVisibleParagraph, preview)
                        },
                        modifier = Modifier.testTag("reader_bookmark_button")
                    ) {
                        Icon(
                            imageVector = if (currentParagraphBookmark != null) {
                                Icons.Default.Bookmark
                            } else {
                                Icons.Default.BookmarkBorder
                            },
                            contentDescription = if (currentParagraphBookmark != null) {
                                "Remove paragraph bookmark"
                            } else {
                                "Bookmark current paragraph"
                            },
                            tint = if (currentParagraphBookmark != null) {
                                MaterialTheme.colorScheme.secondary
                            } else {
                                textColor
                            }
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

                    var overflowOpen by remember { mutableStateOf(false) }
                    Box {
                        IconButton(
                            onClick = { overflowOpen = true },
                            modifier = Modifier.testTag("reader_more_menu_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "More reader options",
                                tint = textColor
                            )
                        }
                        DropdownMenu(
                            expanded = overflowOpen,
                            onDismissRequest = { overflowOpen = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Search in Novel") },
                                leadingIcon = {
                                    Icon(Icons.Default.Search, contentDescription = null)
                                },
                                onClick = {
                                    overflowOpen = false
                                    drawerTab = 2
                                    drawerScope.launch { drawerState.open() }
                                },
                                modifier = Modifier.testTag("reader_menu_search")
                            )
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        if (novelAnnotations.isNotEmpty()) {
                                            "Bookmarks & Notes (${novelAnnotations.size})"
                                        } else {
                                            "Bookmarks & Notes"
                                        }
                                    )
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.Bookmarks, contentDescription = null)
                                },
                                onClick = {
                                    overflowOpen = false
                                    drawerTab = 1
                                    drawerScope.launch { drawerState.open() }
                                },
                                modifier = Modifier.testTag("reader_menu_notes")
                            )
                            DropdownMenuItem(
                                text = { Text("Character Voices") },
                                leadingIcon = {
                                    Icon(Icons.Default.RecordVoiceOver, contentDescription = null)
                                },
                                onClick = {
                                    overflowOpen = false
                                    onOpenVoices()
                                },
                                modifier = Modifier.testTag("reader_voices_button")
                            )
                            DropdownMenuItem(
                                text = { Text("Text Replacements") },
                                leadingIcon = {
                                    Icon(Icons.Default.SwapHoriz, contentDescription = null)
                                },
                                onClick = {
                                    overflowOpen = false
                                    onOpenReplacements()
                                },
                                modifier = Modifier.testTag("reader_replacements_button")
                            )
                            DropdownMenuItem(
                                text = {
                                    Text(if (chapter?.read == true) "Mark as Unread" else "Mark as Read")
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.Check, contentDescription = null)
                                },
                                onClick = {
                                    overflowOpen = false
                                    viewModel.toggleReadStatus()
                                }
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = topBarBg
                )
            )
        }

        // Floating Compact TTS Control Bar (collapsible or hides with controls)
        AnimatedVisibility(
            visible = controlsVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
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
                onPrevParagraph = { viewModel.prevParagraph() },
                onNextParagraph = { viewModel.nextParagraph() },
                onSeek = { viewModel.seekTtsToParagraph(it) },
                remainingSeconds = remainingSeconds,
                sleepTimer = sleepTimer,
                onSetSleepMinutes = { viewModel.setSleepTimerMinutes(it) },
                onSetSleepAtChapterEnd = { viewModel.setSleepAtChapterEnd() },
                onCancelSleep = { viewModel.cancelSleepTimer() },
                onRateChange = { viewModel.setTtsRate(it) },
                onPitchChange = { viewModel.setTtsPitch(it) },
                useEdge = settings.ttsEngine == "edge",
                edgeVoice = settings.edgeVoice,
                onUseEdgeChange = { viewModel.setUseEdgeVoices(it) },
                onEdgeVoiceChange = { viewModel.setEdgeVoice(it) },
                edgeProgress = edgeProgress,
                edgeWifiOnly = settings.edgeWifiOnly,
                onEdgeWifiOnlyChange = { viewModel.setEdgeWifiOnly(it) },
                onClearEdgeChapter = { viewModel.clearEdgeChapter() },
                onClearEdgeCache = { viewModel.clearEdgeCache() },
                loadEdgeCacheSize = { viewModel.edgeCacheSize() }
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
    }
    }

    replaceSeed?.let { seed ->
        AddRuleFromSelectionDialog(
            initialFind = seed,
            onDismiss = { replaceSeed = null },
            onSave = { find, replacement, caseSensitive, partial ->
                viewModel.addReplacementRule(find, replacement, caseSensitive, partial)
                replaceSeed = null
            }
        )
    }

    pendingNoteSelection?.let { located ->
        AnnotationEditorSheet(
            title = "Highlight & Add Note",
            quoteText = located.quoteText,
            isBookmark = false,
            initialColorKey = "amber",
            initialNote = "",
            canDelete = false,
            onSave = { colorKey, note ->
                viewModel.addHighlight(
                    paragraphIndex = located.paragraphIndex,
                    quoteText = located.quoteText,
                    startOffset = located.startOffset,
                    endOffset = located.endOffset,
                    colorKey = colorKey,
                    note = note
                )
                pendingNoteSelection = null
            },
            onDismiss = { pendingNoteSelection = null }
        )
    }

    editingAnnotation?.let { ann ->
        val isBookmark = ann.type == NovelAnnotationEntity.TYPE_BOOKMARK
        AnnotationEditorSheet(
            title = if (isBookmark) "Bookmark Note" else "Edit Highlight & Note",
            quoteText = ann.quoteText,
            isBookmark = isBookmark,
            initialColorKey = ann.colorKey,
            initialNote = ann.note,
            canDelete = true,
            onSave = { colorKey, note ->
                viewModel.updateAnnotation(ann.copy(colorKey = colorKey, note = note.trim()))
                editingAnnotation = null
            },
            onDelete = {
                viewModel.deleteAnnotation(ann.id)
                editingAnnotation = null
            },
            onDismiss = { editingAnnotation = null }
        )
    }

    // Appearance Modal Bottom Sheet
    if (showSettingsSheet) {
        ReaderSettingsBottomSheet(
            settings = settings,
            onUpdateSettings = { viewModel.updateSettings(it) },
            onDismiss = { showSettingsSheet = false }
        )
    }
}

/**
 * Editorial drawer sliding in from the left with Chapters, Bookmarks & Notes, and Full-Novel Search tabs.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChapterDrawerContent(
    chapters: List<ChapterSummary>,
    currentChapterId: String?,
    annotations: List<NovelAnnotationEntity>,
    searchQuery: String,
    searchResults: List<NovelSearchHit>,
    isSearching: Boolean,
    selectedTab: Int,
    onSelectTab: (Int) -> Unit,
    isOpen: Boolean,
    onChapterClick: (String) -> Unit,
    onAnnotationClick: (NovelAnnotationEntity) -> Unit,
    onEditAnnotation: (NovelAnnotationEntity) -> Unit,
    onDeleteAnnotation: (NovelAnnotationEntity) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onClearSearch: () -> Unit,
    onSearchHitClick: (NovelSearchHit) -> Unit
) {
    var searching by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var hideRead by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    val displayed = remember(chapters, query, hideRead, currentChapterId) {
        val number = query.trim().toIntOrNull()
        chapters.filter { ch ->
            val isCurrent = ch.id == currentChapterId
            val matchesQuery = query.isBlank() ||
                ch.title.contains(query.trim(), ignoreCase = true) ||
                (number != null && ch.orderIndex + 1 == number)
            val passesRead = !hideRead || !ch.read || isCurrent
            matchesQuery && passesRead
        }
    }
    val readCount = remember(chapters) { chapters.count { it.read } }

    fun currentIndex(): Int = displayed.indexOfFirst { it.id == currentChapterId }

    // Every time the Chapters tab opens, show the current chapter a few rows from the top
    LaunchedEffect(isOpen, selectedTab, currentChapterId, chapters.size) {
        if (isOpen && selectedTab == 0 && query.isBlank()) {
            val idx = currentIndex()
            if (idx >= 0) listState.scrollToItem((idx - 4).coerceAtLeast(0))
        }
    }

    ModalDrawerSheet(
        drawerContainerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(modifier = Modifier.fillMaxHeight()) {
            SecondaryTabRow(
                selectedTabIndex = selectedTab.coerceIn(0, 2),
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { onSelectTab(0) },
                    text = { Text("Chapters", maxLines = 1) },
                    modifier = Modifier.testTag("reader_drawer_tab_chapters")
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { onSelectTab(1) },
                    text = {
                        Text(
                            text = if (annotations.isNotEmpty()) "Notes (${annotations.size})" else "Notes",
                            maxLines = 1
                        )
                    },
                    modifier = Modifier.testTag("reader_drawer_tab_notes")
                )
                Tab(
                    selected = selectedTab == 2,
                    onClick = { onSelectTab(2) },
                    text = { Text("Search", maxLines = 1) },
                    modifier = Modifier.testTag("reader_drawer_tab_search")
                )
            }

            if (selectedTab == 0) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 18.dp, end = 6.dp, top = 12.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Table of Contents",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "$readCount of ${chapters.size} read",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = {
                        searching = !searching
                        if (!searching) query = ""
                    }) {
                        Icon(
                            imageVector = if (searching) Icons.Default.Close else Icons.Default.Search,
                            contentDescription = "Search chapters"
                        )
                    }
                    IconButton(onClick = {
                        query = ""
                        searching = false
                        scope.launch {
                            val idx = chapters.indexOfFirst { it.id == currentChapterId }
                            if (idx >= 0) listState.animateScrollToItem(idx)
                        }
                    }) {
                        Icon(Icons.Default.MyLocation, contentDescription = "Go to current chapter")
                    }
                    IconButton(onClick = { hideRead = !hideRead }) {
                        Icon(
                            imageVector = Icons.Default.FilterList,
                            contentDescription = if (hideRead) "Show read chapters" else "Hide read chapters",
                            tint = if (hideRead) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (searching) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = { Text("Search or type a chapter number") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        shape = RoundedCornerShape(10.dp)
                    )
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    items(items = displayed, key = { it.id }) { ch ->
                        val isCurrent = ch.id == currentChapterId
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    if (isCurrent) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
                                )
                                .clickable { onChapterClick(ch.id) }
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = ch.title.ifBlank { "Chapter ${ch.orderIndex + 1}" },
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                                    color = when {
                                        isCurrent -> MaterialTheme.colorScheme.onPrimaryContainer
                                        ch.read -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                        else -> MaterialTheme.colorScheme.onSurface
                                    }
                                ),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            if (isCurrent) {
                                Spacer(modifier = Modifier.width(8.dp))
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = "Current chapter",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            } else if (selectedTab == 1) {
                ReaderAnnotationsList(
                    annotations = annotations,
                    chapters = chapters,
                    onAnnotationClick = onAnnotationClick,
                    onEditAnnotation = onEditAnnotation,
                    onDeleteAnnotation = onDeleteAnnotation,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                )
            } else {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = onSearchQueryChange,
                    placeholder = { Text("Search entire novel…") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = onClearSearch) {
                                Icon(Icons.Default.Close, contentDescription = "Clear search")
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                        .testTag("reader_drawer_search_input")
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                NovelSearchResultsList(
                    query = searchQuery,
                    results = searchResults,
                    isSearching = isSearching,
                    showNovelTitle = false,
                    emptyPrompt = "Find any phrase, name, or scene across all chapters in this novel.",
                    onHitClick = onSearchHitClick,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun ReaderParagraphItem(
    paraIndex: Int,
    text: String,
    sentences: List<SentenceSpan>,
    paragraphAnnotations: List<NovelAnnotationEntity>,
    readerTheme: String,
    highlightStateFlow: StateFlow<TtsHighlightState>,
    highlightColor: Color,
    textStyle: TextStyle,
    maxWidth: Dp,
    onClick: () -> Unit,
    onAnnotationClick: (NovelAnnotationEntity) -> Unit,
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

    val bookmarkAnnotation = remember(paragraphAnnotations) {
        paragraphAnnotations.firstOrNull { it.type == NovelAnnotationEntity.TYPE_BOOKMARK }
    }
    val resolvedHighlights = remember(text, paragraphAnnotations, readerTheme) {
        paragraphAnnotations
            .filter { it.type == NovelAnnotationEntity.TYPE_HIGHLIGHT }
            .mapNotNull { ann ->
                val range = resolveHighlightRange(text, ann) ?: return@mapNotNull null
                Triple(ann, range, highlightWashColor(ann.colorKey, readerTheme))
            }
    }

    var textLayoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }

    // Keyed on the range ONLY: a background-color span never changes line geometry, so the
    // existing layout is valid. Keying on textLayoutResult made every sentence scroll twice.
    LaunchedEffect(highlightRange) {
        val range = highlightRange ?: return@LaunchedEffect
        val layout = textLayoutResult
            ?: androidx.compose.runtime.snapshotFlow { textLayoutResult }
                .filterNotNull().first()
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

    Column(
        modifier = modifier
            .fillMaxWidth()
            .widthIn(max = maxWidth)
            .padding(bottom = 12.dp)
    ) {
        if (bookmarkAnnotation != null) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.85f),
                modifier = Modifier
                    .padding(bottom = 4.dp)
                    .clickable { onAnnotationClick(bookmarkAnnotation) }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Bookmark,
                        contentDescription = "Bookmarked paragraph",
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(13.dp)
                    )
                    Text(
                        text = bookmarkAnnotation.note.ifBlank { "Bookmarked · Tap to add note" },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .pointerInput(text, sentences, resolvedHighlights) {
                    detectTapGestures(
                        onTap = { tapOffset ->
                            val layout = textLayoutResult
                            if (layout != null && resolvedHighlights.isNotEmpty()) {
                                val charOffset = layout.getOffsetForPosition(tapOffset)
                                val hit = resolvedHighlights.firstOrNull { (_, range, _) ->
                                    charOffset in range
                                }
                                if (hit != null) {
                                    onAnnotationClick(hit.first)
                                    return@detectTapGestures
                                }
                            }
                            onClick()
                        },
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
            // Highlights are DRAWN behind the text instead of being baked into an AnnotatedString.
            // Drawing only invalidates draw and avoids text re-measure/re-layout jank while scrolling.
            Text(
                text = text,
                style = textStyle,
                onTextLayout = { textLayoutResult = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .drawBehind {
                        val layout = textLayoutResult ?: return@drawBehind
                        for ((_, userRange, washColor) in resolvedHighlights) {
                            val start = userRange.first.coerceIn(0, text.length)
                            val end = (userRange.last + 1).coerceIn(start, text.length)
                            if (end > start) {
                                drawPath(layout.getPathForRange(start, end), washColor)
                            }
                        }
                        val ttsRange = highlightRange
                        if (ttsRange != null) {
                            val start = ttsRange.first.coerceIn(0, text.length)
                            val end = ttsRange.second.coerceIn(start, text.length)
                            if (end > start) {
                                drawPath(layout.getPathForRange(start, end), highlightColor)
                            }
                        }
                    }
            )
        }
    }
}
