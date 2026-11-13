package com.dusk.lab.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Speech runs and plan checks, kept as plain text blocks until they are copied out. */
object LabLog {
    private val lock = Any()
    private fun file(ctx: Context) = File(ctx.filesDir, "lab-log.json")

    private fun read(ctx: Context): JSONObject =
        try { JSONObject(file(ctx).readText()) } catch (e: Exception) { JSONObject() }

    private fun list(o: JSONObject, key: String): List<String> {
        val a = o.optJSONArray(key) ?: return emptyList()
        return List(a.length()) { a.getString(it) }
    }

    fun speech(ctx: Context): List<String> = synchronized(lock) { list(read(ctx), "speech") }
    fun plans(ctx: Context): List<String> = synchronized(lock) { list(read(ctx), "plans") }
    fun support(ctx: Context): String = synchronized(lock) { read(ctx).optString("support", "") }

    private fun append(ctx: Context, key: String, entry: String) = synchronized(lock) {
        val o = read(ctx)
        val a = o.optJSONArray(key) ?: JSONArray()
        a.put(entry)
        o.put(key, a)
        file(ctx).writeText(o.toString())
    }

    fun addSpeech(ctx: Context, entry: String) = append(ctx, "speech", entry)
    fun addPlan(ctx: Context, entry: String) = append(ctx, "plans", entry)

    fun setSupport(ctx: Context, text: String) = synchronized(lock) {
        file(ctx).writeText(read(ctx).put("support", text).toString())
    }

    fun clear(ctx: Context) = synchronized(lock) { file(ctx).delete() }
}
