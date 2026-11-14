package com.dusk.app.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import com.dusk.app.DuskApp
import com.dusk.app.data.SpeechChoice
import com.dusk.app.security.SecureScreen
import com.dusk.app.speech.InAppMic
import com.dusk.core.journal.appendSession
import com.dusk.core.journal.wordCount
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.LocalTime

/**
 * Journal for one day: tap, speak, done. The raw words are the source of truth.
 * New words are autosaved as a draft while you speak, and added to the day with a time stamp on Done or Back.
 */
@Composable
fun JournalScreen(app: DuskApp, date: LocalDate, onClose: () -> Unit) {
    SecureScreen()
    val loaded by app.journal.loaded.collectAsState()
    if (!loaded) {
        Centered { CircularProgressIndicator() }
        return
    }
    val entries by app.journal.entries.collectAsState()
    val entry = entries[date]
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val today = rememberJournalToday()

    var draft by remember(date) {
        val d = app.journal.get(date)?.draft.orEmpty()
        mutableStateOf(TextFieldValue(d, TextRange(d.length)))
    }
    var committed by remember(date) { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }
    var showPrompts by remember { mutableStateOf(false) }
    var mic by remember { mutableStateOf<InAppMic?>(null) }
    var micProblem by remember { mutableStateOf<String?>(null) }

    fun saveDraft() {
        if (committed) return
        val stored = app.journal.get(date)?.draft.orEmpty()
        if (draft.text != stored) updateDay(app, date) { it.copy(draft = draft.text) }
    }

    fun commit() {
        if (committed) return
        mic?.stop()
        val words = draft.text
        committed = true
        val cur = app.journal.get(date)
        if (words.isNotBlank() || !cur?.draft.isNullOrEmpty()) {
            updateDay(app, date) { it.copy(text = appendSession(it.text, words, LocalTime.now()), draft = "") }
        }
    }

    fun close() {
        commit()
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        onClose()
    }

    // Autosave while speaking, so a call or a crash loses nothing.
    LaunchedEffect(draft.text) {
        delay(1000)
        saveDraft()
    }
    DisposableEffect(date) { onDispose { saveDraft() } }
    BackHandler { close() }

    fun startMic() {
        micProblem = null
        val m = InAppMic(
            context, app.prefs.speech,
            onPiece = { piece ->
                val t = draft.text
                val joined = if (t.isBlank()) piece else if (t.endsWith(" ") || t.endsWith("\n")) t + piece else "$t $piece"
                draft = TextFieldValue(joined, TextRange(joined.length))
            },
            onStopped = { problem -> mic = null; micProblem = problem },
        )
        mic = m
        m.start()
    }
    val askMic = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) startMic() else micProblem = "Microphone permission is off. The keyboard mic still works."
    }

    Column(
        Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .imePadding(),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = { close() }, modifier = Modifier.testTag("journal-back")) { Text("‹ Back") }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(date.format(DAY_SHORT), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                relativeDay(date, today)?.let { Hint(it) }
            }
            TextButton(onClick = { showPrompts = true }, modifier = Modifier.testTag("open-prompts")) { Text("Prompts") }
        }

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            if (entry?.hasText == true) {
                Panel(container = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.testTag("earlier-text")) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Saved for this day · ${wordCount(entry.text)} words", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                        TextButton(onClick = { editing = true }, modifier = Modifier.testTag("edit-earlier")) { Text("Edit") }
                    }
                    Text(entry.text, style = MaterialTheme.typography.bodyMedium)
                }
                Gap(12)
            }

            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 180.dp)
                    .testTag("journal-field"),
                placeholder = {
                    Text(
                        if (entry?.hasText == true) "Add more. It is saved under a time stamp."
                        else "Speak or type, in any order. Tap here, then the mic on your keyboard.",
                    )
                },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                shape = RoundedCornerShape(16.dp),
            )
            val m = mic
            if (m != null && m.partial.isNotEmpty()) {
                Gap(4)
                Hint("… ${m.partial}")
            }
            micProblem?.let { Gap(4); Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            Gap(6)
            val stored = entry?.draft.orEmpty()
            Hint(
                "${wordCount(draft.text)} words" +
                    when {
                        draft.text.isEmpty() -> ""
                        draft.text == stored -> " · saved as you speak"
                        else -> " · saving…"
                    },
                Modifier.testTag("draft-status"),
            )
            Gap(16)
            RatingsPanel(app, date)
            Gap(16)
        }

        Row(
            Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (app.prefs.speech != SpeechChoice.KEYBOARD) {
                MicButton(listening = mic != null) {
                    val cur = mic
                    when {
                        cur != null -> cur.stop()
                        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED -> startMic()
                        else -> askMic.launch(Manifest.permission.RECORD_AUDIO)
                    }
                }
            } else {
                Hint("Keyboard mic is on. Change it in Settings.", Modifier.weight(1f))
            }
            if (app.prefs.speech != SpeechChoice.KEYBOARD) Spacer(Modifier.weight(1f))
            Button(
                onClick = { close() },
                modifier = Modifier
                    .height(52.dp)
                    .testTag("journal-done"),
                shape = RoundedCornerShape(16.dp),
            ) { Text("Done", style = MaterialTheme.typography.titleMedium) }
        }
    }

    if (showPrompts) PromptsSheet(date) { showPrompts = false }
    if (editing && entry != null) {
        EditDayText(
            initial = entry.text,
            onSave = { newText -> updateDay(app, date) { it.copy(text = newText.trim()) }; editing = false },
            onCancel = { editing = false },
        )
    }
}

@Composable
private fun MicButton(listening: Boolean, onClick: () -> Unit) {
    val pulse = rememberInfiniteTransition(label = "mic")
    val s by pulse.animateFloat(1f, 1.08f, infiniteRepeatable(tween(600), RepeatMode.Reverse), label = "pulse")
    Button(
        onClick = onClick,
        modifier = Modifier
            .size(64.dp)
            .scale(if (listening) s else 1f)
            .testTag("journal-mic"),
        shape = CircleShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (listening) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primaryContainer,
            contentColor = if (listening) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.onPrimaryContainer,
        ),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
    ) { Text(if (listening) "Stop" else "Mic", fontWeight = FontWeight.SemiBold) }
}

/** Full-screen editor for a day's saved text. Runs in its own window, which inherits the screenshot block. */
@Composable
private fun EditDayText(initial: String, onSave: (String) -> Unit, onCancel: () -> Unit) {
    var text by remember { mutableStateOf(TextFieldValue(initial)) }
    var confirmClear by remember { mutableStateOf(false) }
    Dialog(onDismissRequest = onCancel, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        androidx.compose.material3.Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(
                Modifier
                    .fillMaxSize()
                    .safeDrawingPadding()
                    .imePadding()
                    .padding(16.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onCancel) { Text("Cancel") }
                    Text("Edit saved text", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    TextButton(onClick = { onSave(text.text) }, modifier = Modifier.testTag("edit-save")) { Text("Save") }
                }
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .testTag("edit-field"),
                    shape = RoundedCornerShape(16.dp),
                )
                Gap(8)
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
                    TextButton(onClick = { confirmClear = true }) { Text("Clear this day's text", color = MaterialTheme.colorScheme.error) }
                }
            }
        }
    }
    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Clear this day's text?") },
            text = { Text("The words saved for this day will be deleted. Ratings stay. This cannot be undone unless you have a backup.") },
            confirmButton = { TextButton(onClick = { confirmClear = false; onSave("") }) { Text("Clear", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Keep") } },
        )
    }
}
