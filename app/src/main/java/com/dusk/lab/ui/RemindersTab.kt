@file:OptIn(ExperimentalLayoutApi::class)

package com.dusk.lab.ui

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dusk.lab.reminders.ReminderScheduler
import com.dusk.lab.reminders.ReminderStore
import com.dusk.lab.reminders.StoreData
import com.dusk.lab.reminders.TestAlarm
import kotlinx.coroutines.delay

private fun notificationsAllowed(ctx: Context) =
    ctx.getSystemService(NotificationManager::class.java).areNotificationsEnabled()

private fun methodName(a: TestAlarm) = if (a.api == "clock") "alarm-clock method" else "standard method"

private fun outcome(a: TestAlarm, now: Long): String = when {
    a.firedAt != null -> "fired ${(a.firedAt - a.scheduledAt) / 1000}s late"
    now > a.scheduledAt -> "NOT FIRED, ${(now - a.scheduledAt) / 1000}s overdue"
    else -> "waiting"
}

/** Plain-text reminder log for the Results tab. */
fun remindersText(ctx: Context): String {
    val d = ReminderStore.load(ctx)
    val now = System.currentTimeMillis()
    if (d.alarms.isEmpty() && d.events.isEmpty()) return "no reminders scheduled yet"
    return buildString {
        d.alarms.sortedBy { it.scheduledAt }.forEach { a ->
            append("#${a.id} ${a.label} | ${methodName(a)} | due ${ReminderScheduler.clock(a.scheduledAt)} | ${outcome(a, now)}")
            if (a.reschedules > 0) append(" | re-set after restart x${a.reschedules}")
            if (a.doneAt != null) append(" | Done tapped")
            if (a.note.isNotEmpty()) append(" | ${a.note}")
            appendLine()
        }
        if (d.events.isNotEmpty()) {
            appendLine("events:")
            d.events.forEach { appendLine("  $it") }
        }
    }.trimEnd()
}

@Composable
fun RemindersTab() {
    val ctx = LocalContext.current
    var data by remember { mutableStateOf<StoreData>(ReminderStore.load(ctx)) }
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    var notifAllowed by remember { mutableStateOf(notificationsAllowed(ctx)) }
    LaunchedEffect(Unit) {
        while (true) {
            data = ReminderStore.load(ctx)
            now = System.currentTimeMillis()
            notifAllowed = notificationsAllowed(ctx)
            delay(2000)
        }
    }
    val askNotifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        notifAllowed = notificationsAllowed(ctx)
    }
    val unrestricted = ctx.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(ctx.packageName)

    fun openAppSettings() {
        ctx.startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Reminder test", style = MaterialTheme.typography.titleLarge)
        Hint("Each button sets two reminders one minute apart, one per scheduling method. Lock the phone and leave it. The log shows how late each one fired.")

        Panel("Health check") {
            Text("Notifications allowed: ${if (notifAllowed) "yes" else "NO"}")
            Text("Exact alarms allowed: ${if (ReminderScheduler.canExact(ctx)) "yes" else "NO"}")
            Text("Battery unrestricted: ${if (unrestricted) "yes" else "no"}")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!notifAllowed) {
                    OutlinedButton(onClick = { askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS) }) { Text("Allow notifications") }
                }
                OutlinedButton(onClick = {
                    try {
                        // Samsung's own screen: Battery > Background usage limits > Never auto sleeping apps
                        ctx.startActivity(
                            Intent("com.samsung.android.sm.ACTION_OPEN_CHECKABLE_LISTACTIVITY")
                                .setPackage("com.samsung.android.lool")
                                .putExtra("activity_type", 2)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                    } catch (e: Exception) {
                        openAppSettings()
                    }
                }) { Text("Never-sleeping list") }
                OutlinedButton(onClick = { openAppSettings() }) { Text("App settings") }
            }
        }

        Panel("Schedule") {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("+1 min" to 1, "+10 min" to 10, "+2 hours" to 120, "+8 hours" to 480).forEach { (label, minutes) ->
                    OutlinedButton(
                        modifier = Modifier.testTag("sched-$minutes"),
                        onClick = {
                            ReminderScheduler.addPair(ctx, label, minutes)
                            data = ReminderStore.load(ctx)
                        },
                    ) { Text(label) }
                }
            }
        }

        Panel("Log") {
            if (data.alarms.isEmpty()) Hint("Nothing scheduled yet.")
            data.alarms.sortedByDescending { it.scheduledAt }.forEachIndexed { i, a ->
                if (i > 0) HorizontalDivider(Modifier.padding(vertical = 8.dp))
                val late = a.firedAt?.let { (it - a.scheduledAt) / 1000 }
                val bad = (late != null && late > 60) || (a.firedAt == null && now > a.scheduledAt + 60_000)
                Text("${a.label} · ${methodName(a)}", fontWeight = FontWeight.SemiBold)
                Text("due ${ReminderScheduler.clock(a.scheduledAt)}", style = MaterialTheme.typography.bodySmall)
                Text(
                    outcome(a, now) + if (a.reschedules > 0) " · re-set after restart" else "",
                    color = if (bad) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.testTag("outcome-${a.id}"),
                )
                if (a.note.isNotEmpty()) Hint(a.note)
            }
            if (data.events.isNotEmpty()) {
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                Text("Restarts seen", fontWeight = FontWeight.SemiBold)
                data.events.forEach { Hint(it) }
            }
            TextButton(onClick = { ReminderScheduler.clear(ctx); data = ReminderStore.load(ctx) }) { Text("Clear log") }
        }
    }
}
