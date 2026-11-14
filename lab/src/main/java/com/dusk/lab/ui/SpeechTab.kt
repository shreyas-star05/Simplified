@file:OptIn(ExperimentalLayoutApi::class)

package com.dusk.lab.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.dusk.core.speech.SpeechScorer
import com.dusk.core.speech.TestScript
import com.dusk.lab.data.DeviceInfo
import com.dusk.lab.data.LabLog
import com.dusk.lab.reminders.ReminderScheduler
import com.dusk.lab.speech.Engine
import com.dusk.lab.speech.RecognizerRun
import kotlinx.coroutines.delay

@Composable
fun SpeechTab() {
    val ctx = LocalContext.current
    var engine by rememberSaveable { mutableStateOf(Engine.KEYBOARD) }
    var scriptMode by rememberSaveable { mutableStateOf(true) }
    var typed by rememberSaveable { mutableStateOf("") }
    var running by remember { mutableStateOf(false) }
    var startedAt by remember { mutableLongStateOf(0L) }
    var elapsed by remember { mutableLongStateOf(0L) }
    var run by remember { mutableStateOf<RecognizerRun?>(null) }
    var result by rememberSaveable { mutableStateOf("") }

    val view = LocalView.current
    DisposableEffect(running) {
        view.keepScreenOn = running
        onDispose { view.keepScreenOn = false }
    }
    LaunchedEffect(running) {
        while (running) {
            elapsed = System.currentTimeMillis() - startedAt
            delay(500)
        }
    }
    DisposableEffect(Unit) { onDispose { run?.stop() } }

    fun record(e: Engine, script: Boolean, text: String, started: Long, ended: Long, restarts: Int, errors: List<String>, stoppedByItself: Boolean) {
        val secs = ((ended - started) / 1000).coerceAtLeast(1)
        val words = text.trim().split(Regex("\\s+")).count { it.isNotEmpty() }
        val sb = StringBuilder()
        sb.appendLine("${e.short} | ${if (script) "script" else "free talk"} | ${ReminderScheduler.clock(started)} | airplane mode ${DeviceInfo.airplaneMode(ctx)}")
        sb.appendLine("  duration ${secs}s, $words words, ${words * 60 / secs} words/min")
        if (e != Engine.KEYBOARD) {
            val errs = if (errors.isEmpty()) "" else " (" + errors.groupingBy { it }.eachCount().entries.joinToString { "${it.key} x${it.value}" } + ")"
            sb.appendLine("  restarts $restarts, errors ${errors.size}$errs${if (stoppedByItself) ", STOPPED BY ITSELF" else ""}")
        }
        if (script) {
            val s = SpeechScorer.score(TestScript.TEXT, text)
            sb.appendLine("  " + s.summary())
            if (s.droppedExamples.isNotEmpty()) sb.appendLine("  dropped, for example: " + s.droppedExamples.joinToString(" / ") { it.take(70) })
            sb.append("  heard: " + text.trim())
        } else {
            sb.append("  free talk text is not kept; judge it by eye on the screen")
        }
        val entry = sb.toString()
        result = entry
        LabLog.addSpeech(ctx, entry)
    }

    fun startRecognizer() {
        result = ""
        val e = engine
        val script = scriptMode
        val r = RecognizerRun(ctx, e) { done ->
            running = false
            record(e, script, done.text, done.startedAt, done.endedAt, done.restarts, done.errors, done.stoppedByItself)
        }
        run = r
        startedAt = System.currentTimeMillis()
        elapsed = 0
        running = true
        r.start()
    }

    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startRecognizer() else result = "Microphone permission was refused, so engines B, C and D cannot run."
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Speech test", style = MaterialTheme.typography.titleLarge)
        Hint("Read the script once with each engine. Then switch on airplane mode, switch Wi-Fi off, and do it again. Every run is saved on the Results tab.")

        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Engine.entries.forEach { e ->
                FilterChip(
                    selected = engine == e,
                    onClick = { if (!running) engine = e },
                    label = { Text(e.label) },
                    modifier = Modifier.testTag("engine-${e.ordinal}"),
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = scriptMode, onClick = { if (!running) scriptMode = true }, label = { Text("Read the script") })
            FilterChip(selected = !scriptMode, onClick = { if (!running) scriptMode = false }, label = { Text("Free talk") })
        }

        if (scriptMode) {
            Panel("Script, about 3 minutes") {
                Box(
                    Modifier
                        .heightIn(max = 230.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    Text(TestScript.TEXT, style = MaterialTheme.typography.bodyMedium)
                }
            }
        } else {
            Hint("Talk about your day for about 3 minutes, the way you would in the journal. Nothing you say here is saved.")
        }

        Panel("What was heard") {
            if (engine == Engine.KEYBOARD) {
                OutlinedTextField(
                    value = typed,
                    onValueChange = { typed = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 140.dp)
                        .testTag("keyboard-field"),
                    placeholder = { Text("Tap Start, tap here, then tap the mic on your keyboard and speak") },
                )
            } else {
                val r = run
                val shown = if (r == null) "" else (r.text + " " + r.partial).trim()
                Text(
                    shown.ifEmpty { "Tap Start and speak. The words appear here." },
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (shown.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                )
                if (r != null) Hint("${r.status} · restarts ${r.restarts}")
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                modifier = Modifier.testTag("speech-start"),
                onClick = {
                    if (engine == Engine.KEYBOARD) {
                        if (!running) {
                            typed = ""; result = ""
                            startedAt = System.currentTimeMillis(); elapsed = 0; running = true
                        } else {
                            running = false
                            record(Engine.KEYBOARD, scriptMode, typed, startedAt, System.currentTimeMillis(), 0, emptyList(), false)
                        }
                    } else if (!running) {
                        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) startRecognizer()
                        else micPermission.launch(Manifest.permission.RECORD_AUDIO)
                    } else {
                        run?.stop()
                    }
                },
            ) { Text(if (!running) "Start" else if (engine == Engine.KEYBOARD) "Done" else "Stop") }
            val s = elapsed / 1000
            Text(if (running) "%d:%02d".format(s / 60, s % 60) else "", style = MaterialTheme.typography.titleMedium)
        }

        if (result.isNotEmpty()) {
            Panel("Result") { Text(result, style = MaterialTheme.typography.bodySmall) }
        }
    }
}
