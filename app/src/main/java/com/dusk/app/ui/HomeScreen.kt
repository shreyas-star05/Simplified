@file:OptIn(ExperimentalMaterial3Api::class)

package com.dusk.app.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.dusk.app.BuildConfig
import com.dusk.app.DuskApp
import com.dusk.core.journal.DayEntry
import com.dusk.core.journal.journalDay
import com.dusk.core.journal.monthCells
import com.dusk.core.journal.weekdayInitials
import com.dusk.core.journal.wordCount
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.temporal.ChronoUnit

private const val PAGES = 2400
private const val MIDDLE = 1200

/** Today in the app's sense (the day ends at 4 AM), refreshed every 30 seconds. */
@Composable
fun rememberJournalToday(): LocalDate {
    var today by remember { mutableStateOf(journalDay(LocalDateTime.now())) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            today = journalDay(LocalDateTime.now())
        }
    }
    return today
}

fun ratingSummary(e: DayEntry): String = listOfNotNull(
    e.mood?.let { "mood $it/5" },
    e.energy?.let { "energy $it/5" },
    e.progress?.let { "progress $it/5" },
    e.spend?.let { "₹$it" },
).joinToString(" · ")

@Composable
fun HomeScreen(app: DuskApp, onJournal: (LocalDate) -> Unit, onSettings: () -> Unit) {
    val entries by app.journal.entries.collectAsState()
    val unreadable by app.journal.unreadable.collectAsState()
    val today = rememberJournalToday()
    val baseMonth = remember { YearMonth.from(journalDay(LocalDateTime.now())) }
    val pager = rememberPagerState(initialPage = MIDDLE) { PAGES }
    val scope = rememberCoroutineScope()
    val shownMonth = baseMonth.plusMonths((pager.currentPage - MIDDLE).toLong())
    var sheetDate by rememberSaveable { mutableStateOf<String?>(null) }

    Column(
        Modifier
            .fillMaxSize()
            .safeDrawingPadding(),
    ) {
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    shownMonth.atDay(1).format(MONTH_TITLE),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("month-title"),
                )
                if (shownMonth != YearMonth.from(today)) {
                    TextButton(onClick = {
                        scope.launch { pager.animateScrollToPage(MIDDLE + ChronoUnit.MONTHS.between(baseMonth, YearMonth.from(today)).toInt()) }
                    }) { Text("Today") }
                }
                TextButton(onClick = { scope.launch { pager.animateScrollToPage(pager.currentPage - 1) } }, modifier = Modifier.testTag("prev-month")) { Text("‹", style = MaterialTheme.typography.titleLarge) }
                TextButton(onClick = { scope.launch { pager.animateScrollToPage(pager.currentPage + 1) } }, modifier = Modifier.testTag("next-month")) { Text("›", style = MaterialTheme.typography.titleLarge) }
                TextButton(onClick = onSettings, modifier = Modifier.testTag("open-settings")) { Text("Settings") }
            }

            Row(Modifier.fillMaxWidth()) {
                weekdayInitials().forEach {
                    Text(
                        it,
                        Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            HorizontalPager(state = pager, modifier = Modifier.fillMaxWidth(), beyondViewportPageCount = 1) { page ->
                val month = baseMonth.plusMonths((page - MIDDLE).toLong())
                MonthGrid(month, today, entries) { sheetDate = it.toString() }
            }

            Spacer(Modifier.height(16.dp))
            TodayPanel(today, entries[today]) { onJournal(today) }

            if (unreadable > 0) {
                Spacer(Modifier.height(12.dp))
                Panel(Modifier.testTag("unreadable-banner"), container = MaterialTheme.colorScheme.errorContainer) {
                    Text(
                        "$unreadable ${if (unreadable == 1) "day" else "days"} could not be opened on this phone. They are kept aside, not deleted. Restore a backup file in Settings to get them back.",
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            if (BuildConfig.PREVIEW) {
                Spacer(Modifier.height(12.dp))
                Panel(container = MaterialTheme.colorScheme.secondaryContainer) {
                    Text(
                        "Preview build. Before you install the final Dusk, make a backup here and restore it there.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
            }
            BackupAge(app, entries.isNotEmpty(), onSettings)
            Spacer(Modifier.height(16.dp))
        }

        Button(
            onClick = { onJournal(today) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .height(56.dp)
                .testTag("journal-button"),
            shape = RoundedCornerShape(16.dp),
        ) {
            val has = entries[today]?.hasText == true
            Text(if (has) "Add to tonight's journal" else "Journal for tonight", style = MaterialTheme.typography.titleMedium)
        }
    }

    sheetDate?.let { d ->
        DaySheet(
            app = app,
            date = LocalDate.parse(d),
            today = today,
            onJournal = { sheetDate = null; onJournal(it) },
            onDismiss = { sheetDate = null },
        )
    }
}

@Composable
private fun BackupAge(app: DuskApp, hasEntries: Boolean, onSettings: () -> Unit) {
    if (!hasEntries) return
    val last = app.prefs.lastBackupAt
    val days = last?.let { ChronoUnit.DAYS.between(java.time.Instant.ofEpochMilli(it), java.time.Instant.now()) }
    // Only a quiet line, and only once it has been a while: backups are manual by choice.
    if (days != null && days < 14) return
    Spacer(Modifier.height(8.dp))
    Text(
        if (days == null) "Never backed up" else "Last backup $days days ago",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .clickable(onClick = onSettings)
            .padding(vertical = 4.dp)
            .testTag("backup-age"),
    )
}

@Composable
private fun MonthGrid(month: YearMonth, today: LocalDate, entries: Map<LocalDate, DayEntry>, onDay: (LocalDate) -> Unit) {
    val cells = remember(month) { monthCells(month) }
    Column {
        for (row in 0 until 6) {
            Row(Modifier.fillMaxWidth()) {
                for (col in 0 until 7) {
                    val date = cells[row * 7 + col]
                    Box(
                        Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .padding(2.dp),
                    ) {
                        if (date != null) DayCell(date, today, entries[date], onDay)
                    }
                }
            }
        }
    }
}

@Composable
private fun DayCell(date: LocalDate, today: LocalDate, entry: DayEntry?, onDay: (LocalDate) -> Unit) {
    val isToday = date == today
    val future = date.isAfter(today)
    val written = entry != null && (entry.hasText || entry.draft.isNotBlank())
    val barWidth by animateFloatAsState(if (written) 1f else 0f, tween(280), label = "bar")
    val scheme = MaterialTheme.colorScheme
    Box(
        Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(12.dp))
            .then(if (isToday) Modifier.background(scheme.primaryContainer) else Modifier)
            .then(if (isToday) Modifier.border(1.5.dp, scheme.primary, RoundedCornerShape(12.dp)) else Modifier)
            .clickable { onDay(date) }
            .testTag("day-$date"),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            date.dayOfMonth.toString(),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
            color = if (isToday) scheme.onPrimaryContainer else scheme.onBackground,
            modifier = Modifier.alpha(if (future) 0.55f else 1f),
        )
        if (barWidth > 0f) {
            val color = entry?.mood?.let { MoodColors[it - 1] } ?: scheme.primary
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 6.dp)
                    .width((16 * barWidth).dp)
                    .height(3.dp)
                    .clip(CircleShape)
                    .background(color),
            )
        }
    }
}

@Composable
private fun TodayPanel(today: LocalDate, entry: DayEntry?, onOpen: () -> Unit) {
    Panel(
        Modifier
            .clickable(onClick = onOpen)
            .testTag("today-panel"),
    ) {
        Text("Today, ${today.format(DAY_SHORT)}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        val words = entry?.let { wordCount(it.text) } ?: 0
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(if (words > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                when {
                    words > 0 -> "Journal: $words words"
                    entry?.draft?.isNotBlank() == true -> "Journal: unsaved words are waiting"
                    else -> "Journal: not written yet"
                },
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        val summary = entry?.let { ratingSummary(it) }.orEmpty()
        if (summary.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            Hint(summary)
        }
    }
}

@Composable
fun SecondaryAction(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = modifier.height(48.dp),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant, contentColor = MaterialTheme.colorScheme.onSurface),
    ) { Text(text) }
}

@Composable
fun Centered(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { content() }
}

@Composable
fun Gap(h: Int = 12) = Spacer(Modifier.height(h.dp))
