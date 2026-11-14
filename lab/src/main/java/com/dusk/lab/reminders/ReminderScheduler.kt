package com.dusk.lab.reminders

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.UserManager
import com.dusk.lab.MainActivity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ReminderScheduler {
    const val CHANNEL = "test-reminders"
    const val ACTION_FIRE = "com.dusk.lab.FIRE"
    const val ACTION_DONE = "com.dusk.lab.DONE"
    const val ACTION_SNOOZE = "com.dusk.lab.SNOOZE"

    fun clock(ms: Long): String = SimpleDateFormat("EEE d MMM HH:mm:ss", Locale.ENGLISH).format(Date(ms))

    fun canExact(ctx: Context): Boolean = ctx.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()

    fun ensureChannel(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, "Test reminders", NotificationManager.IMPORTANCE_HIGH),
            )
        }
    }

    private fun firePending(ctx: Context, id: Int): PendingIntent =
        PendingIntent.getBroadcast(
            ctx, id,
            Intent(ctx, AlarmReceiver::class.java).setAction(ACTION_FIRE).putExtra("id", id),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun setAlarm(ctx: Context, a: TestAlarm) {
        val am = ctx.getSystemService(AlarmManager::class.java)
        val pi = firePending(ctx, a.id)
        if (a.api == "clock") {
            val show = PendingIntent.getActivity(
                ctx, 100_000 + a.id, Intent(ctx, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            am.setAlarmClock(AlarmManager.AlarmClockInfo(a.scheduledAt, show), pi)
        } else {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, a.scheduledAt, pi)
        }
    }

    /** Schedules two reminders one minute apart, one per scheduling method, so they can be compared. */
    fun addPair(ctx: Context, label: String, minutes: Int) {
        val base = System.currentTimeMillis() + minutes * 60_000L
        add(ctx, label, "idle", base)
        add(ctx, label, "clock", base + 60_000L)
    }

    fun add(ctx: Context, label: String, api: String, at: Long) {
        var created: TestAlarm? = null
        ReminderStore.update(ctx) { d ->
            val a = TestAlarm(id = (d.alarms.maxOfOrNull { it.id } ?: 0) + 1, label = label, api = api, scheduledAt = at)
            d.alarms += a
            created = a
        }
        created?.let { setAlarm(ctx, it) }
    }

    /** Called after a restart or an app update: Android drops every alarm at shutdown. */
    fun rescheduleAll(ctx: Context, reason: String) {
        val now = System.currentTimeMillis()
        val overdue = ArrayList<Int>()
        val d = ReminderStore.update(ctx) { d ->
            d.events += "${clock(now)}  $reason"
            for (i in d.alarms.indices) {
                val a = d.alarms[i]
                if (a.firedAt != null) continue
                if (a.scheduledAt <= now) overdue += a.id
                else d.alarms[i] = a.copy(reschedules = a.reschedules + 1)
            }
        }
        d.alarms.filter { it.firedAt == null && it.scheduledAt > now }.forEach { setAlarm(ctx, it) }
        overdue.forEach { fire(ctx, it, "missed while the phone was off; shown at start-up") }
    }

    fun fire(ctx: Context, id: Int, extraNote: String? = null) {
        val now = System.currentTimeMillis()
        val unlocked = ctx.getSystemService(UserManager::class.java).isUserUnlocked
        var fired: TestAlarm? = null
        ReminderStore.update(ctx) { d ->
            val i = d.alarms.indexOfFirst { it.id == id }
            if (i < 0 || d.alarms[i].firedAt != null) return@update
            val notes = listOfNotNull(
                d.alarms[i].note.ifEmpty { null },
                extraNote,
                if (!unlocked) "fired before the phone was unlocked" else null,
            ).joinToString("; ")
            d.alarms[i] = d.alarms[i].copy(firedAt = now, note = notes)
            fired = d.alarms[i]
        }
        val a = fired ?: return
        ensureChannel(ctx)
        val delaySec = (now - a.scheduledAt) / 1000
        val method = if (a.api == "clock") "alarm-clock method" else "standard method"
        val done = PendingIntent.getBroadcast(
            ctx, 200_000 + id, Intent(ctx, ActionReceiver::class.java).setAction(ACTION_DONE).putExtra("id", id),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val snooze = PendingIntent.getBroadcast(
            ctx, 300_000 + id, Intent(ctx, ActionReceiver::class.java).setAction(ACTION_SNOOZE).putExtra("id", id),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val open = PendingIntent.getActivity(
            ctx, 400_000 + id, Intent(ctx, MainActivity::class.java).putExtra("tab", 1),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val n = Notification.Builder(ctx, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle(if (unlocked) "Test reminder: ${a.label}" else "Reminder. Unlock to see it.")
            .setContentText("Due ${clock(a.scheduledAt)}, ${delaySec}s late, $method")
            .setCategory(Notification.CATEGORY_REMINDER)
            .setContentIntent(open)
            .setAutoCancel(true)
            .addAction(Notification.Action.Builder(null as Icon?, "Done", done).build())
            .addAction(Notification.Action.Builder(null as Icon?, "Snooze 10 min", snooze).build())
            .build()
        ctx.getSystemService(NotificationManager::class.java).notify(id, n)
    }

    fun clear(ctx: Context) {
        val am = ctx.getSystemService(AlarmManager::class.java)
        val nm = ctx.getSystemService(NotificationManager::class.java)
        ReminderStore.update(ctx) { d ->
            d.alarms.forEach { am.cancel(firePending(ctx, it.id)); nm.cancel(it.id) }
            d.alarms.clear()
            d.events.clear()
        }
    }
}
