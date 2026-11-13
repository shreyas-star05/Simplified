package com.dusk.lab.reminders

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** One test reminder and what actually happened to it. */
data class TestAlarm(
    val id: Int,
    val label: String,
    /** "idle" = setExactAndAllowWhileIdle, "clock" = setAlarmClock */
    val api: String,
    val scheduledAt: Long,
    val firedAt: Long? = null,
    val note: String = "",
    val reschedules: Int = 0,
    val doneAt: Long? = null,
)

class StoreData(val alarms: MutableList<TestAlarm>, val events: MutableList<String>)

/**
 * Kept in device-protected storage so the receivers can read it after a restart,
 * before the phone has been unlocked. It holds times and labels only.
 */
object ReminderStore {
    private val lock = Any()

    private fun file(ctx: Context): File =
        File(ctx.createDeviceProtectedStorageContext().filesDir, "alarms.json")

    fun load(ctx: Context): StoreData = synchronized(lock) { read(ctx) }

    fun update(ctx: Context, block: (StoreData) -> Unit): StoreData = synchronized(lock) {
        val d = read(ctx)
        block(d)
        write(ctx, d)
        d
    }

    private fun read(ctx: Context): StoreData {
        val f = file(ctx)
        if (!f.exists()) return StoreData(mutableListOf(), mutableListOf())
        return try {
            val o = JSONObject(f.readText())
            val arr = o.optJSONArray("alarms") ?: JSONArray()
            val alarms = MutableList(arr.length()) { i ->
                val a = arr.getJSONObject(i)
                TestAlarm(
                    id = a.getInt("id"),
                    label = a.getString("label"),
                    api = a.getString("api"),
                    scheduledAt = a.getLong("scheduledAt"),
                    firedAt = if (a.has("firedAt")) a.getLong("firedAt") else null,
                    note = a.optString("note", ""),
                    reschedules = a.optInt("reschedules", 0),
                    doneAt = if (a.has("doneAt")) a.getLong("doneAt") else null,
                )
            }
            val ev = o.optJSONArray("events") ?: JSONArray()
            StoreData(alarms, MutableList(ev.length()) { ev.getString(it) })
        } catch (e: Exception) {
            StoreData(mutableListOf(), mutableListOf("store unreadable: ${e.message}"))
        }
    }

    private fun write(ctx: Context, d: StoreData) {
        val arr = JSONArray()
        d.alarms.forEach { a ->
            val o = JSONObject()
                .put("id", a.id).put("label", a.label).put("api", a.api)
                .put("scheduledAt", a.scheduledAt).put("note", a.note).put("reschedules", a.reschedules)
            if (a.firedAt != null) o.put("firedAt", a.firedAt)
            if (a.doneAt != null) o.put("doneAt", a.doneAt)
            arr.put(o)
        }
        val ev = JSONArray()
        d.events.takeLast(40).forEach { ev.put(it) }
        val f = file(ctx)
        val tmp = File(f.parentFile, f.name + ".tmp")
        tmp.writeText(JSONObject().put("alarms", arr).put("events", ev).toString())
        if (!tmp.renameTo(f)) { f.writeText(tmp.readText()); tmp.delete() }
    }
}
