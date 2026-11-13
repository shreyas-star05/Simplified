package com.dusk.lab.reminders

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** A scheduled test reminder is due. */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getIntExtra("id", -1)
        if (id >= 0) ReminderScheduler.fire(context, id)
    }
}

/** Done and Snooze buttons on the notification. */
class ActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getIntExtra("id", -1)
        if (id < 0) return
        context.getSystemService(NotificationManager::class.java).cancel(id)
        val now = System.currentTimeMillis()
        when (intent.action) {
            ReminderScheduler.ACTION_DONE -> ReminderStore.update(context) { d ->
                val i = d.alarms.indexOfFirst { it.id == id }
                if (i >= 0) d.alarms[i] = d.alarms[i].copy(doneAt = now)
            }
            ReminderScheduler.ACTION_SNOOZE -> {
                val label = ReminderStore.load(context).alarms.firstOrNull { it.id == id }?.label ?: "reminder"
                ReminderScheduler.add(context, "snooze of $label", "idle", now + 10 * 60_000L)
            }
        }
    }
}

/** Phone restarted (before or after unlock) or the app was updated: put the alarms back. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val reason = when (intent.action) {
            Intent.ACTION_LOCKED_BOOT_COMPLETED -> "restart, before unlock"
            Intent.ACTION_BOOT_COMPLETED -> "restart, after unlock"
            Intent.ACTION_MY_PACKAGE_REPLACED -> "app updated"
            else -> return
        }
        ReminderScheduler.rescheduleAll(context, reason)
    }
}
