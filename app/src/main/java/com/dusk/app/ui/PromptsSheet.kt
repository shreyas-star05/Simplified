@file:OptIn(ExperimentalMaterial3Api::class)

package com.dusk.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dusk.core.journal.Prompts
import java.time.LocalDate

private val LENS_COLORS = mapOf(
    "feel" to Color(0xFF7C4DDB), "replay" to Color(0xFF1A7FBD), "body" to Color(0xFFCC5E14),
    "progress" to Color(0xFF1C8F53), "good" to Color(0xFFA87300), "money" to Color(0xFF0D877C),
    "people" to Color(0xFFCF2F66), "direction" to Color(0xFF4556D8), "tomorrow" to Color(0xFFC22F3A),
    "free" to Color(0xFF5B607F),
)

/** Dusk's prompt card: a reference while speaking, not a form. */
@Composable
fun PromptsSheet(date: LocalDate, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surface) {
        LazyColumn(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .testTag("prompts"),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp),
        ) {
            item {
                Text("Ten ways into your day", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Gap(6)
                Hint(Prompts.INTRO)
                Gap(16)
                Panel(container = MaterialTheme.colorScheme.primaryContainer) {
                    Text("Tonight's question", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    Gap(4)
                    Text(Prompts.spotlight(date.dayOfWeek), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
                }
                Gap(16)
            }
            items(Prompts.LENSES, key = { it.id }) { lens ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(bottom = 18.dp)
                        .height(IntrinsicSize.Min),
                ) {
                    Box(
                        Modifier
                            .width(4.dp)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(2.dp))
                            .background(LENS_COLORS[lens.id] ?: MaterialTheme.colorScheme.primary),
                    )
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(lens.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(4.dp))
                        lens.questions.forEach { q -> Text("•  $q", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 2.dp)) }
                        Spacer(Modifier.height(4.dp))
                        Hint(lens.why)
                    }
                }
            }
        }
    }
}
