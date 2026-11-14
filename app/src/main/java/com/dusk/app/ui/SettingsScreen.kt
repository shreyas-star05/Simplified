@file:OptIn(ExperimentalLayoutApi::class)

package com.dusk.app.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import com.dusk.app.BuildConfig
import com.dusk.app.DuskApp
import com.dusk.app.data.SpeechChoice
import com.dusk.app.data.ThemeMode
import com.dusk.app.security.AppLock
import com.dusk.core.backup.BackupCodec
import com.dusk.core.backup.BackupContents
import com.dusk.core.backup.BackupException
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

fun hasInternetPermission(context: Context): Boolean {
    val info = context.packageManager.getPackageInfo(
        context.packageName,
        PackageManager.PackageInfoFlags.of(PackageManager.GET_PERMISSIONS.toLong()),
    )
    return info.requestedPermissions?.contains(Manifest.permission.INTERNET) == true
}

private sealed interface Flow {
    data object None : Flow
    data object AskBackupPassphrase : Flow
    data class Busy(val what: String) : Flow
    data class AskRestorePassphrase(val bytes: ByteArray, val summary: BackupCodec.Summary, val error: String? = null) : Flow
    data class ConfirmRestore(val contents: BackupContents) : Flow
    data object ConfirmDelete : Flow
}

@Composable
fun SettingsScreen(app: DuskApp, onClose: () -> Unit) {
    BackHandler(onBack = onClose)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = app.prefs
    val entries by app.journal.entries.collectAsState()
    var flow by remember { mutableStateOf<Flow>(Flow.None) }
    var message by remember { mutableStateOf<String?>(null) }
    var pendingPassphrase by remember { mutableStateOf<CharArray?>(null) }

    val createFile = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        val pass = pendingPassphrase
        pendingPassphrase = null
        if (uri == null || pass == null) { flow = Flow.None; pass?.fill(' '); return@rememberLauncherForActivityResult }
        flow = Flow.Busy("Encrypting and saving the backup…")
        scope.launch {
            message = try {
                val n = context.contentResolver.openOutputStream(uri, "wt")!!.let { app.backup.write(it, pass) }
                "Backup saved: $n ${if (n == 1) "day" else "days"} of journal."
            } catch (e: Exception) {
                "The backup could not be saved: ${e.message}"
            } finally {
                pass.fill(' ')
            }
            flow = Flow.None
        }
    }

    val openFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        try {
            val bytes = context.contentResolver.openInputStream(uri)!!.let { app.backup.readBytes(it) }
            flow = Flow.AskRestorePassphrase(bytes, BackupCodec.summary(bytes))
        } catch (e: BackupException) {
            message = e.message
        } catch (e: Exception) {
            message = "That file could not be read."
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .safeDrawingPadding(),
    ) {
        Row(Modifier.padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onClose, modifier = Modifier.testTag("settings-back")) { Text("‹ Back") }
            Text("Settings", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        }
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            message?.let {
                Panel(container = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.testTag("settings-message")) {
                    Text(it, color = MaterialTheme.colorScheme.onPrimaryContainer)
                }
            }

            Panel(title = "Theme") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ThemeMode.entries.forEach { t ->
                        FilterChip(
                            selected = prefs.theme == t,
                            onClick = { prefs.chooseTheme(t) },
                            label = { Text(t.label) },
                            modifier = Modifier.testTag("theme-${t.name}"),
                        )
                    }
                }
            }

            Panel(title = "App lock") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Ask for fingerprint or PIN", style = MaterialTheme.typography.bodyMedium)
                        Hint("When Dusk opens, and after a minute away.")
                    }
                    Switch(checked = prefs.lockEnabled, onCheckedChange = { prefs.setLock(it) }, modifier = Modifier.testTag("lock-switch"))
                }
                val activity = context as? FragmentActivity
                if (prefs.lockEnabled && activity != null && !AppLock.phoneHasLock(activity)) {
                    Gap(8)
                    Text(
                        "Your phone has no screen lock, so Dusk cannot ask for one. Set a fingerprint or PIN in the phone's Settings to protect your journal.",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }

            Panel(title = "Speech") {
                Hint("Your release 0 test decides which is best. The keyboard mic always works by tapping the text box.")
                Gap(8)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SpeechChoice.entries.forEach { s ->
                        FilterChip(selected = prefs.speech == s, onClick = { prefs.chooseSpeech(s) }, label = { Text(s.label) }, modifier = Modifier.testTag("speech-${s.name}"))
                    }
                }
                Gap(4)
                Hint(prefs.speech.hint)
            }

            Panel(title = "Backup") {
                val last = prefs.lastBackupAt
                Text(
                    if (last == null) "Never backed up."
                    else {
                        val d = Instant.ofEpochMilli(last).atZone(ZoneId.systemDefault()).toLocalDate()
                        val ago = ChronoUnit.DAYS.between(d, LocalDate.now())
                        "Last backup ${d.format(DAY_SHORT)}" + when (ago) { 0L -> ", today."; 1L -> ", yesterday."; else -> ", $ago days ago." }
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.testTag("backup-status"),
                )
                Hint("A backup file holds your whole journal, locked with a passphrase you choose. It is the only copy outside this phone. Keep it somewhere safe, and never give it to an AI.")
                Gap(10)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { flow = Flow.AskBackupPassphrase }, enabled = entries.isNotEmpty(), modifier = Modifier.testTag("backup-now")) { Text("Back up now") }
                    OutlinedButton(onClick = { openFile.launch(arrayOf("*/*")) }, modifier = Modifier.testTag("restore")) { Text("Restore") }
                }
            }

            Panel(title = "Privacy") {
                val internet = remember { hasInternetPermission(context) }
                Text(
                    if (internet) "Internet access: this build CAN use the internet. Please report this." else "Internet access: none. Checked on this phone just now.",
                    color = if (internet) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.testTag("internet-status"),
                )
                Gap(4)
                Hint("Your journal is stored only on this phone, encrypted with a key kept in the phone's secure hardware. Journal screens block screenshots.")
                Gap(4)
                Hint("${entries.size} ${if (entries.size == 1) "day" else "days"} in the journal.")
            }

            Panel(title = "Delete everything") {
                Hint("Deletes every journal entry on this phone. Settings stay.")
                Gap(8)
                OutlinedButton(onClick = { flow = Flow.ConfirmDelete }, modifier = Modifier.testTag("delete-all")) {
                    Text("Delete everything", color = MaterialTheme.colorScheme.error)
                }
            }

            Hint(
                "Dusk ${BuildConfig.VERSION_NAME}" + when {
                    BuildConfig.DEBUG -> " · test build"
                    BuildConfig.PREVIEW -> " · preview build"
                    else -> ""
                },
                Modifier.padding(bottom = 24.dp),
            )
        }
    }

    when (val f = flow) {
        Flow.None -> {}
        is Flow.Busy -> AlertDialog(
            onDismissRequest = {},
            confirmButton = {},
            title = { Text("One moment") },
            text = { Row(verticalAlignment = Alignment.CenterVertically) { CircularProgressIndicator(); Text("   ${f.what}") } },
        )
        Flow.AskBackupPassphrase -> BackupPassphraseDialog(
            onCancel = { flow = Flow.None },
            onOk = { pass ->
                pendingPassphrase = pass
                flow = Flow.None
                createFile.launch("dusk-backup_${LocalDate.now()}.duskbackup")
            },
        )
        is Flow.AskRestorePassphrase -> RestorePassphraseDialog(
            summary = f.summary,
            error = f.error,
            onCancel = { flow = Flow.None },
            onOk = { pass ->
                flow = Flow.Busy("Opening the backup…")
                scope.launch {
                    flow = try {
                        Flow.ConfirmRestore(app.backup.open(f.bytes, pass))
                    } catch (e: BackupException) {
                        f.copy(error = e.message)
                    } finally {
                        pass.fill(' ')
                    }
                }
            },
        )
        is Flow.ConfirmRestore -> {
            val preview = remember(f) { app.backup.previewMerge(f.contents) }
            AlertDialog(
                onDismissRequest = { flow = Flow.None },
                title = { Text("Restore ${f.contents.entries.size} ${if (f.contents.entries.size == 1) "day" else "days"}?") },
                text = {
                    Column {
                        Text("This phone has ${entries.size} ${if (entries.size == 1) "day" else "days"}.")
                        Gap(8)
                        Text("Merge: adds ${preview.added}, replaces ${preview.replaced} with a newer version from the backup, keeps ${preview.keptOnPhone} that are newer on this phone.")
                        Gap(8)
                        Text("Replace everything: the phone ends up with exactly the ${f.contents.entries.size} days in the backup.")
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        flow = Flow.Busy("Restoring…")
                        scope.launch { app.backup.apply(f.contents, replace = false); message = "Restored. ${preview.added} added, ${preview.replaced} replaced."; flow = Flow.None }
                    }, modifier = Modifier.testTag("restore-merge")) { Text("Merge") }
                },
                dismissButton = {
                    Row {
                        TextButton(onClick = { flow = Flow.None }) { Text("Cancel") }
                        TextButton(onClick = {
                            flow = Flow.Busy("Restoring…")
                            scope.launch { app.backup.apply(f.contents, replace = true); message = "Restored. The journal now matches the backup."; flow = Flow.None }
                        }) { Text("Replace everything", color = MaterialTheme.colorScheme.error) }
                    }
                },
            )
        }
        Flow.ConfirmDelete -> DeleteDialog(
            onCancel = { flow = Flow.None },
            onDelete = {
                flow = Flow.Busy("Deleting…")
                scope.launch { app.journal.deleteEverything(); message = "Everything was deleted."; flow = Flow.None }
            },
        )
    }
}

@Composable
private fun PassField(value: String, label: String, show: Boolean, tag: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        visualTransformation = if (show) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
        modifier = Modifier
            .fillMaxWidth()
            .testTag(tag),
    )
}

@Composable
private fun BackupPassphraseDialog(onCancel: () -> Unit, onOk: (CharArray) -> Unit) {
    var a by remember { mutableStateOf("") }
    var b by remember { mutableStateOf("") }
    var show by remember { mutableStateOf(false) }
    val tooShort = a.length < BackupCodec.MIN_PASSPHRASE
    val mismatch = b.isNotEmpty() && a != b
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("Choose a passphrase") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("The backup opens only with this passphrase. If you forget it, nobody can open the backup, not even you.")
                PassField(a, "Passphrase, at least ${BackupCodec.MIN_PASSPHRASE} characters", show, "pass-1") { a = it }
                PassField(b, "Type it again", show, "pass-2") { b = it }
                if (mismatch) Text("The two don't match.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = { show = !show }) { Text(if (show) "Hide" else "Show") }
            }
        },
        confirmButton = {
            TextButton(onClick = { onOk(a.toCharArray()) }, enabled = !tooShort && a == b, modifier = Modifier.testTag("pass-ok")) { Text("Choose where to save") }
        },
        dismissButton = { TextButton(onClick = onCancel) { Text("Cancel") } },
    )
}

@Composable
private fun RestorePassphraseDialog(summary: BackupCodec.Summary, error: String?, onCancel: () -> Unit, onOk: (CharArray) -> Unit) {
    var a by remember { mutableStateOf("") }
    var show by remember { mutableStateOf(false) }
    val made = Instant.ofEpochMilli(summary.createdAt).atZone(ZoneId.systemDefault()).toLocalDate()
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("Open this backup") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Made ${made.format(DAY_LONG)}, ${summary.entries} ${if (summary.entries == 1) "day" else "days"} of journal.")
                PassField(a, "Passphrase", show, "restore-pass") { a = it }
                if (error != null) Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = { show = !show }) { Text(if (show) "Hide" else "Show") }
            }
        },
        confirmButton = { TextButton(onClick = { onOk(a.toCharArray()) }, enabled = a.isNotEmpty()) { Text("Open") } },
        dismissButton = { TextButton(onClick = onCancel) { Text("Cancel") } },
    )
}

@Composable
private fun DeleteDialog(onCancel: () -> Unit, onDelete: () -> Unit) {
    var typed by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("Delete everything?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Every journal entry on this phone will be deleted. Only a backup file can bring them back.")
                OutlinedTextField(value = typed, onValueChange = { typed = it }, label = { Text("Type DELETE") }, singleLine = true, modifier = Modifier.testTag("delete-confirm"))
            }
        },
        confirmButton = { TextButton(onClick = onDelete, enabled = typed.trim() == "DELETE") { Text("Delete", color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = onCancel) { Text("Cancel") } },
    )
}
