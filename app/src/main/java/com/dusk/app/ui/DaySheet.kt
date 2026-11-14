@file:OptIn(ExperimentalMaterial3Api::class)

package com.dusk.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalBottomSheetProperties
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dusk.app.DuskApp
import com.dusk.app.security.SecureScreens
import com.dusk.core.journal.wordCount
import java.time.LocalDate

/** The sheet of options for one day. Plans and reminders join it in release 3. */
@Composable
fun DaySheet(app: DuskApp, date: LocalDate, today: LocalDate, onJournal: (LocalDate) -> Unit, onDismiss: () -> Unit) {
    val entries by app.journal.entries.collectAsState()
    val entry = entries[date]
    val future = date.isAfter(today)
    var ratingsOpen by remember { mutableStateOf(false) }
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = state,
        containerColor = MaterialTheme.colorScheme.surface,
        // The sheet is its own window. When it shows journal text, it blocks screenshots itself.
        properties = ModalBottomSheetProperties(securePolicy = SecureScreens.policy(entry?.hasText == true)),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 16.dp)
                .navigationBarsPadding()
                .testTag("day-sheet"),
        ) {
            Text(date.format(DAY_LONG), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            relativeDay(date, today)?.let { Hint(it) }
            Gap(16)

            if (future) {
                Panel(container = MaterialTheme.colorScheme.surfaceVariant) {
                    Text("This day is still ahead. You can journal about it once it comes.", style = MaterialTheme.typography.bodyMedium)
                }
                return@Column
            }

            Panel(container = MaterialTheme.colorScheme.surfaceVariant) {
                if (entry?.hasText == true) {
                    Text(
                        entry.text,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.testTag("sheet-preview"),
                    )
                    Gap(6)
                    Hint("${wordCount(entry.text)} words" + ratingSummary(entry).let { if (it.isEmpty()) "" else " · $it" })
                } else {
                    Text("No journal entry for this day.", style = MaterialTheme.typography.bodyMedium)
                    entry?.let { e -> ratingSummary(e).takeIf { it.isNotEmpty() }?.let { Gap(4); Hint(it) } }
                }
            }
            Gap(16)
            Button(
                onClick = { onJournal(date) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .testTag("sheet-journal"),
                shape = RoundedCornerShape(16.dp),
            ) {
                Text(if (entry?.hasText == true) "Open journal" else "Journal (speak)", style = MaterialTheme.typography.titleMedium)
            }
            Gap(10)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SecondaryAction(if (ratingsOpen) "Hide ratings" else "Ratings", Modifier.weight(1f).testTag("sheet-ratings")) { ratingsOpen = !ratingsOpen }
            }
            if (ratingsOpen) {
                Gap(12)
                RatingsPanel(app, date)
            }
        }
    }
}
