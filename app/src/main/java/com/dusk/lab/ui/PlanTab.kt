package com.dusk.lab.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.dusk.core.parse.Draft
import com.dusk.core.parse.Field
import com.dusk.core.parse.Kind
import com.dusk.core.parse.ParseResult
import com.dusk.core.parse.PlanParser
import com.dusk.core.parse.Repeat
import com.dusk.lab.data.LabLog
import com.dusk.lab.speech.Engine
import com.dusk.lab.speech.RecognizerRun
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private val DATE_FMT = DateTimeFormatter.ofPattern("EEE d MMM yyyy", Locale.ENGLISH)
private val TIME_FMT = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH)
private val STAMP_FMT = DateTimeFormatter.ofPattern("EEE d MMM, h:mm a", Locale.ENGLISH)

private fun repeatText(r: Repeat?): String = when (r) {
    null -> "None"
    Repeat.Daily -> "Every day"
    is Repeat.Weekly -> "Every week on " + r.day.name.lowercase().replaceFirstChar { it.uppercase() }
    is Repeat.MonthlyOnDay -> "Every month on day ${r.day}"
    Repeat.MonthlyLastWeekday -> "Every month, last weekday"
    Repeat.MonthlyLastDay -> "Every month, last day"
    Repeat.Yearly -> "Every year"
}

@Composable
private fun FieldRow(label: String, value: String, amber: Boolean) {
    val shape = RoundedCornerShape(8.dp)
    val base = Modifier
        .fillMaxWidth()
        .padding(vertical = 3.dp)
    val mod = if (amber) base
        .background(Amber.copy(alpha = 0.18f), shape)
        .border(1.5.dp, Amber, shape)
    else base.border(1.dp, MaterialTheme.colorScheme.outline, shape)
    Row(mod.padding(horizontal = 10.dp, vertical = 8.dp)) {
        Text(label, Modifier.width(72.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value)
    }
}

@Composable
private fun DraftCard(d: Draft) {
    Panel {
        val kind = when (d.kind) {
            Kind.EVENT -> "Event"
            Kind.TODO -> "To-do"
            Kind.NOTE -> "Note"
        }
        Text(kind + if (d.tentative) " · tentative" else "", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
        val flagged = d.flags.map { it.field }.toSet()
        FieldRow("Title", d.title, false)
        val dates = if (d.start == d.end) d.start.format(DATE_FMT) else d.start.format(DATE_FMT) + " to " + d.end.format(DATE_FMT)
        FieldRow("Date", dates, Field.DATE in flagged)
        FieldRow("Time", d.time?.format(TIME_FMT) ?: "All day", Field.TIME in flagged)
        FieldRow("Repeat", repeatText(d.repeat), Field.REPEAT in flagged)
        FieldRow("Remind", d.reminder?.format(STAMP_FMT) ?: "None", Field.REMINDER in flagged)
        if (d.note != null) FieldRow("Note", d.note!!, false)
        d.flags.forEach { f ->
            val alt = if (f.alternatives.isEmpty()) "" else " Other choice: " + f.alternatives.joinToString(" or ") + "."
            Text("${f.field.name.lowercase().replaceFirstChar { it.uppercase() }}: ${f.reason}.$alt", style = MaterialTheme.typography.bodySmall, color = Amber)
        }
        d.suggestions.forEach { Text("Suggestion: $it", style = MaterialTheme.typography.bodySmall) }
        Hint("Heard: ${d.heard}")
    }
}

@Composable
fun PlanTab() {
    val ctx = LocalContext.current
    var input by rememberSaveable { mutableStateOf("") }
    var fix by rememberSaveable { mutableStateOf("") }
    var parsedAt by remember { mutableStateOf<LocalDateTime?>(null) }
    var parsedText by remember { mutableStateOf("") }
    var result by remember { mutableStateOf<ParseResult?>(null) }
    var checked by remember { mutableIntStateOf(LabLog.plans(ctx).size) }
    var wrong by remember { mutableIntStateOf(LabLog.plans(ctx).count { it.startsWith("WRONG") }) }
    var mic by remember { mutableStateOf<RecognizerRun?>(null) }
    var micOn by remember { mutableStateOf(false) }
    var micMessage by remember { mutableStateOf("") }

    DisposableEffect(Unit) { onDispose { mic?.stop() } }

    fun make() {
        if (input.isBlank()) return
        val now = LocalDateTime.now()
        parsedAt = now
        parsedText = input.trim()
        result = PlanParser.parse(parsedText, now)
        fix = ""
    }

    fun verdict(ok: Boolean) {
        val r = result ?: return
        val entry = buildString {
            append(if (ok) "OK" else "WRONG")
            append(" | said ${parsedAt?.format(STAMP_FMT)} | $parsedText\n    -> ")
            append(r.drafts.joinToString(" || ") { it.canonical() })
            if (!ok && fix.isNotBlank()) append("\n    should be: ${fix.trim()}")
        }
        LabLog.addPlan(ctx, entry)
        checked++
        if (!ok) wrong++
        input = ""; fix = ""; result = null
    }

    fun listen() {
        micMessage = ""
        val r = RecognizerRun(ctx, Engine.DEFAULT, singleShot = true) { done ->
            micOn = false
            if (done.text.isNotBlank()) { input = done.text; make() }
            else micMessage = "Nothing heard" + if (done.errors.isEmpty()) "." else ": " + done.errors.last() + "."
        }
        mic = r
        micOn = true
        r.start()
    }

    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) listen() else micMessage = "Microphone permission was refused. Type the plan, or use the keyboard mic."
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Say a plan", style = MaterialTheme.typography.titleLarge)
        Hint("Say or type one plan the way you normally would. Check the card, then mark it Correct or Wrong. Aim for 30. Amber means the app guessed.")

        OutlinedTextField(
            value = input,
            onValueChange = { input = it },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("plan-input"),
            placeholder = { Text("dinner with friends on 20th Nov") },
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { make() }, modifier = Modifier.testTag("plan-make")) { Text("Make card") }
            OutlinedButton(onClick = {
                if (micOn) mic?.stop()
                else if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) listen()
                else micPermission.launch(Manifest.permission.RECORD_AUDIO)
            }) { Text(if (micOn) "Listening, tap to stop" else "Speak") }
        }
        if (micMessage.isNotEmpty()) Hint(micMessage)

        val r = result
        if (r != null) {
            if (r.looksLikeJournal) {
                Panel { Text("This is long and has no date or time. The real app would ask: save it to today's journal instead?") }
            }
            r.drafts.forEach { DraftCard(it) }
            OutlinedTextField(
                value = fix,
                onValueChange = { fix = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("If wrong, what should it be? (optional)") },
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { verdict(true) }, modifier = Modifier.testTag("plan-ok")) { Text("Correct") }
                OutlinedButton(onClick = { verdict(false) }, modifier = Modifier.testTag("plan-wrong")) { Text("Wrong") }
            }
        }
        Text("$checked of 30 checked, $wrong wrong", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag("plan-count"))
    }
}
