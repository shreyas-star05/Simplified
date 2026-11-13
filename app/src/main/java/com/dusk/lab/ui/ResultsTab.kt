@file:OptIn(ExperimentalLayoutApi::class)

package com.dusk.lab.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dusk.lab.data.DeviceInfo
import com.dusk.lab.data.LabLog
import com.dusk.lab.reminders.ReminderScheduler
import com.dusk.lab.speech.RecognizerRun

fun buildResults(ctx: Context): String = buildString {
    appendLine("DUSK LAB RESULTS, ${ReminderScheduler.clock(System.currentTimeMillis())}")
    appendLine()
    appendLine("== Phone ==")
    appendLine(DeviceInfo.text(ctx))
    val support = LabLog.support(ctx)
    appendLine(support.ifEmpty { "Speech support: not checked yet (tap Check speech support)" })
    appendLine()
    appendLine("== Speech runs ==")
    val speech = LabLog.speech(ctx)
    if (speech.isEmpty()) appendLine("none yet") else speech.forEach { appendLine(it); appendLine() }
    appendLine("== Reminders ==")
    appendLine(remindersText(ctx))
    appendLine()
    appendLine("== Plans ==")
    val plans = LabLog.plans(ctx)
    if (plans.isEmpty()) appendLine("none yet")
    else {
        appendLine("${plans.size} checked, ${plans.count { it.startsWith("WRONG") }} wrong")
        plans.forEach { appendLine(it) }
    }
}.trimEnd()

@Composable
fun ResultsTab() {
    val ctx = LocalContext.current
    var version by remember { mutableIntStateOf(0) }
    var message by remember { mutableStateOf("") }
    val text = remember(version) { buildResults(ctx) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Results", style = MaterialTheme.typography.titleLarge)
        Hint("When you have finished the tests, tap Copy all and paste the text back to Claude. Read it first: it includes the plans you spoke.")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(modifier = Modifier.testTag("results-copy"), onClick = {
                ctx.getSystemService(ClipboardManager::class.java)
                    .setPrimaryClip(ClipData.newPlainText("Dusk Lab results", text))
                message = "Copied."
            }) { Text("Copy all") }
            OutlinedButton(onClick = {
                ctx.startActivity(
                    Intent.createChooser(
                        Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text),
                        "Share results",
                    ),
                )
            }) { Text("Share") }
            OutlinedButton(modifier = Modifier.testTag("results-support"), onClick = {
                message = "Checking..."
                RecognizerRun.checkSupport(ctx, false) { a ->
                    RecognizerRun.checkSupport(ctx, true) { b ->
                        LabLog.setSupport(ctx, a + "\n" + b)
                        message = "Speech support checked."
                        version++
                    }
                }
            }) { Text("Check speech support") }
            OutlinedButton(onClick = { message = RecognizerRun.requestDownload(ctx) }) { Text("Get offline pack") }
            OutlinedButton(onClick = { version++; message = "Refreshed." }) { Text("Refresh") }
            TextButton(onClick = { LabLog.clear(ctx); version++; message = "Speech and plan results cleared." }) { Text("Clear") }
        }
        if (message.isNotEmpty()) Hint(message)
        Panel {
            SelectionContainer {
                Text(text, fontFamily = FontFamily.Monospace, fontSize = 12.sp, lineHeight = 17.sp)
            }
        }
    }
}
