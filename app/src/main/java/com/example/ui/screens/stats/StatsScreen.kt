package com.example.ui.screens.stats

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.audio.AudioFormat
import com.example.audio.AudioStatsSummary
import com.example.ui.components.MtlTopBar

@Composable
fun StatsScreen(
    viewModel: StatsViewModel
) {
    val totalNovels by viewModel.totalNovels.collectAsStateWithLifecycle()
    val totalChapters by viewModel.totalChapters.collectAsStateWithLifecycle()
    val totalRead by viewModel.totalReadChapters.collectAsStateWithLifecycle()
    val totalWords by viewModel.totalWords.collectAsStateWithLifecycle()
    val novels by viewModel.novels.collectAsStateWithLifecycle()
    val audio by viewModel.audio.collectAsStateWithLifecycle()

    val approxMinutes = (totalWords / 180).toInt()
    val approxHours = String.format("%.1f", approxMinutes / 60.0)

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            MtlTopBar(
                title = "Reading Digest",
                subtitle = "READING & LISTENING"
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 840.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Reading Time Editorial Hero Card
                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        shadowElevation = 1.dp
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    Brush.horizontalGradient(
                                        listOf(
                                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.42f),
                                            MaterialTheme.colorScheme.surface
                                        )
                                    )
                                )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(18.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primaryContainer),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Schedule,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(14.dp))
                                Column {
                                    Text(
                                        text = "ESTIMATED READING VOLUME",
                                        style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.sp),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "~$approxHours hours",
                                        style = MaterialTheme.typography.displaySmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = "${String.format("%,d", approxMinutes)} min across ${String.format("%,d", totalWords)} words",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }

                // Reading Vault KPI Section
                item {
                    SectionOverline("NOVEL VAULT")
                }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        MetricCard(
                            value = totalNovels.toString(),
                            label = "Volumes",
                            modifier = Modifier.weight(1f)
                        )
                        MetricCard(
                            value = totalChapters.toString(),
                            label = "Total Chapters",
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        MetricCard(
                            value = totalRead.toString(),
                            label = "Chapters Read",
                            accentSecondary = true,
                            modifier = Modifier.weight(1f)
                        )
                        MetricCard(
                            value = String.format("%,d", totalWords),
                            label = "Total Words",
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                // Per-Novel Progress
                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AutoStories,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = "Per-Novel Progress",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            Spacer(modifier = Modifier.height(14.dp))

                            if (novels.isEmpty()) {
                                Text(
                                    text = "No novels in your vault yet.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            } else {
                                novels.forEachIndexed { i, n ->
                                    if (i > 0) {
                                        Spacer(modifier = Modifier.height(12.dp))
                                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                                        Spacer(modifier = Modifier.height(12.dp))
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = n.title,
                                            style = MaterialTheme.typography.titleSmall,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "${n.readChapters}/${n.totalChapters} (${n.progressPercent}%)",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(6.dp))

                                    LinearProgressIndicator(
                                        progress = { if (n.totalChapters > 0) n.progressPercent / 100f else 0f },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(6.dp)
                                            .clip(RoundedCornerShape(3.dp)),
                                        color = MaterialTheme.colorScheme.primary,
                                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                                        strokeCap = StrokeCap.Round
                                    )
                                }
                            }
                        }
                    }
                }

                // Audiobook Listening Section
                audioStatsItems(audio)

                item {
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }
    }
}

@Composable
private fun SectionOverline(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.2.sp),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 6.dp, start = 2.dp)
    )
}

@Composable
private fun MetricCard(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    accentSecondary: Boolean = false
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.headlineLarge.copy(fontSize = 24.sp),
                color = if (accentSecondary) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private val WEEKDAY_LETTERS = listOf("M", "T", "W", "T", "F", "S", "S")

private fun LazyListScope.audioStatsItems(audio: AudioStatsSummary) {
    item {
        SectionOverline("AUDIOBOOK LISTENING")
    }

    if (!audio.hasBooks && !audio.hasListening) {
        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.secondaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Headphones,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Add an audiobook in the Audiobooks tab to track listening hours, streaks, and weekly momentum.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        return
    }

    item {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MetricCard(AudioFormat.duration(audio.todayMs), "Listened Today", Modifier.weight(1f), accentSecondary = true)
            MetricCard(AudioFormat.duration(audio.weekMs), "Last 7 Days", Modifier.weight(1f))
        }
    }
    item {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MetricCard(AudioFormat.duration(audio.totalMs), "Total Listened", Modifier.weight(1f))
            MetricCard(
                value = if (audio.streakDays == 1) "1 day" else "${audio.streakDays} days",
                label = "Listening Streak",
                accentSecondary = audio.streakDays > 0,
                modifier = Modifier.weight(1f)
            )
        }
    }
    item {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MetricCard(audio.booksFinished.toString(), "Books Finished", Modifier.weight(1f))
            MetricCard(audio.booksInProgress.toString(), "In Progress", Modifier.weight(1f))
        }
    }
    item {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MetricCard(AudioFormat.speed(audio.averageSpeed), "Average Speed", Modifier.weight(1f))
            MetricCard(audio.booksTotal.toString(), "Audiobooks", Modifier.weight(1f))
        }
    }

    // Seven-day bar chart
    item {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Last 7 Days",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(14.dp))

                val biggest = audio.last7Days.maxOfOrNull { it.listenedMs } ?: 0L
                val chartHeight = 112.dp
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(chartHeight + 22.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.Bottom
                ) {
                    audio.last7Days.forEachIndexed { index, day ->
                        val isToday = index == audio.last7Days.lastIndex
                        val fraction = if (biggest > 0L) day.listenedMs.toFloat() / biggest else 0f
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Bottom
                        ) {
                            if (day.listenedMs > 0L) {
                                Text(
                                    text = AudioFormat.duration(day.listenedMs),
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1
                                )
                            }
                            Spacer(modifier = Modifier.height(3.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height((chartHeight - 18.dp) * fraction + 4.dp)
                                    .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                                    .background(
                                        when {
                                            day.listenedMs <= 0L -> MaterialTheme.colorScheme.surfaceVariant
                                            isToday -> MaterialTheme.colorScheme.secondary
                                            else -> MaterialTheme.colorScheme.primary.copy(alpha = 0.72f)
                                        }
                                    )
                            )
                            Spacer(modifier = Modifier.height(5.dp))
                            Text(
                                text = WEEKDAY_LETTERS[day.dayOfWeek],
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isToday) MaterialTheme.colorScheme.secondary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }
            }
        }
    }

    // Time per book
    item {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Time per Audiobook",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(14.dp))

                if (audio.perBook.isEmpty()) {
                    Text(
                        text = "Nothing listened to yet.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    val longest = audio.perBook.first().listenedMs.coerceAtLeast(1L)
                    audio.perBook.forEachIndexed { i, book ->
                        if (i > 0) {
                            Spacer(modifier = Modifier.height(12.dp))
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                            Spacer(modifier = Modifier.height(12.dp))
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = book.title,
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = AudioFormat.duration(book.listenedMs),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        LinearProgressIndicator(
                            progress = { book.listenedMs.toFloat() / longest },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = MaterialTheme.colorScheme.secondary,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant,
                            strokeCap = StrokeCap.Round
                        )
                    }
                }
            }
        }
    }
}
