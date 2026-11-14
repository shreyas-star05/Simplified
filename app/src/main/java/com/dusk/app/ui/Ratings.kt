package com.dusk.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dusk.app.DuskApp
import com.dusk.core.journal.DayEntry
import kotlinx.coroutines.delay
import java.time.LocalDate

private val MOOD_FACES = listOf("😞", "🙁", "😐", "🙂", "😄")

fun updateDay(app: DuskApp, date: LocalDate, change: (DayEntry) -> DayEntry) {
    val now = System.currentTimeMillis()
    val cur = app.journal.get(date) ?: DayEntry(date, createdAt = now, updatedAt = now)
    app.journal.put(change(cur).copy(updatedAt = now))
}

/** The quick tap ratings from Dusk: mood, energy and progress from 1 to 5, and rough spend. Saved on every tap. */
@Composable
fun RatingsPanel(app: DuskApp, date: LocalDate) {
    val entries by app.journal.entries.collectAsState()
    val e = entries[date]
    Panel(Modifier.testTag("ratings")) {
        Scale("Mood", "mood", e?.mood, labels = MOOD_FACES, ends = null) { v -> updateDay(app, date) { it.copy(mood = v) } }
        Gap(14)
        Scale("Energy", "energy", e?.energy, labels = (1..5).map { "$it" }, ends = "Drained" to "Buzzing") { v -> updateDay(app, date) { it.copy(energy = v) } }
        Gap(14)
        Scale("Progress on what matters", "progress", e?.progress, labels = (1..5).map { "$it" }, ends = "Nothing moved" to "Big step") { v -> updateDay(app, date) { it.copy(progress = v) } }
        Gap(14)
        SpendField(app, date, e?.spend)
    }
}

@Composable
private fun Scale(name: String, tag: String, value: Int?, labels: List<String>, ends: Pair<String, String>?, onSet: (Int?) -> Unit) {
    val haptic = LocalHapticFeedback.current
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Hint(value?.let { "$it of 5" } ?: "not set")
        }
        Gap(8)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            labels.forEachIndexed { i, label ->
                val v = i + 1
                val on = value == v
                val shape = RoundedCornerShape(12.dp)
                Box(
                    Modifier
                        .weight(1f)
                        .height(44.dp)
                        .clip(shape)
                        .background(if (on) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant)
                        .border(if (on) 2.dp else 0.dp, if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant, shape)
                        .clickable {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onSet(if (on) null else v)
                        }
                        .testTag("$tag-$v"),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(label, fontSize = if (label.length > 1 || label.first().isDigit()) 16.sp else 22.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.Normal)
                }
            }
        }
        if (ends != null) {
            Row(Modifier.fillMaxWidth()) {
                Hint(ends.first, Modifier.weight(1f))
                Hint(ends.second)
            }
        }
    }
}

@Composable
private fun SpendField(app: DuskApp, date: LocalDate, saved: Long?) {
    var text by remember(date) { mutableStateOf(saved?.toString().orEmpty()) }
    LaunchedEffect(text) {
        delay(600)
        val v = text.filter { it.isDigit() }.take(9).toLongOrNull()
        if (v != saved) updateDay(app, date) { it.copy(spend = v) }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("Spent today, roughly", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Hint("optional")
        }
        OutlinedTextField(
            value = text,
            onValueChange = { new -> text = new.filter { it.isDigit() }.take(9) },
            prefix = { Text("₹ ") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier
                .width(150.dp)
                .testTag("spend"),
        )
    }
}
