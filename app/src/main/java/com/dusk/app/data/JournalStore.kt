package com.dusk.app.data

import android.content.Context
import android.util.Log
import com.dusk.core.journal.DayEntry
import com.dusk.core.json.Json
import com.dusk.core.json.JsonObj
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate

/**
 * The journal: one encrypted file per day under files/journal/.
 * Days that cannot be opened are moved aside, never deleted, and reported on screen.
 */
class JournalStore(context: Context) {
    private val dir = File(context.filesDir, "journal").apply { mkdirs() }
    private val box = SealedBox("journal", File(dir, "store.key"))

    @OptIn(ExperimentalCoroutinesApi::class)
    private val io = Dispatchers.IO.limitedParallelism(1)
    private val scope = CoroutineScope(SupervisorJob() + io)

    private val _entries = MutableStateFlow<Map<LocalDate, DayEntry>>(emptyMap())
    val entries: StateFlow<Map<LocalDate, DayEntry>> = _entries

    /** False until the first load finishes. */
    private val _loaded = MutableStateFlow(false)
    val loaded: StateFlow<Boolean> = _loaded

    /** Number of day files moved aside because this phone could not open them. */
    private val _unreadable = MutableStateFlow(0)
    val unreadable: StateFlow<Int> = _unreadable

    private fun fileFor(date: LocalDate) = File(dir, "$date.day")

    fun load() {
        scope.launch { loadNow() }
    }

    private fun loadNow() {
        val files = dir.listFiles { f -> f.name.endsWith(".day") }.orEmpty().sortedBy { it.name }
        val out = sortedMapOf<LocalDate, DayEntry>()
        if (files.isNotEmpty() && box.keyIsUnreadable()) {
            // The key itself cannot be opened here: every file is unreadable. Keep them all, start a fresh key.
            val aside = newQuarantineDir()
            files.forEach { it.renameTo(File(aside, it.name)) }
            File(dir, "store.key").renameTo(File(aside, "store.key"))
            box.reset()
        } else {
            var aside: File? = null
            for (f in files) {
                try {
                    val e = DayEntry.fromJson(JsonObj.parse(String(box.open(f.readBytes()), Charsets.UTF_8)))
                    out[e.date] = e
                } catch (e: Exception) {
                    Log.w("Dusk", "could not open ${f.name}: ${e.message}")
                    if (aside == null) aside = newQuarantineDir()
                    f.renameTo(File(aside, f.name))
                }
            }
        }
        _unreadable.value = countQuarantined()
        _entries.value = out
        _loaded.value = true
    }

    private val quarantineRoot = File(dir, "unreadable")

    private fun newQuarantineDir(): File = File(quarantineRoot, System.currentTimeMillis().toString()).apply { mkdirs() }

    private fun countQuarantined(): Int = quarantineRoot.walkTopDown().count { it.isFile && it.name.endsWith(".day") }

    private fun writeNow(e: DayEntry) {
        val f = fileFor(e.date)
        if (e.isEmpty) {
            f.delete()
        } else {
            SealedBox.writeAtomically(f, box.seal(Json.write(e.toMap()).toByteArray(Charsets.UTF_8)))
        }
    }

    /** Saves one day. The in-memory copy updates at once; the file is written in order on a background thread. */
    fun put(e: DayEntry) {
        _entries.value = if (e.isEmpty) _entries.value - e.date else (_entries.value + (e.date to e)).toSortedMap()
        scope.launch { writeNow(e) }
    }

    fun get(date: LocalDate): DayEntry? = _entries.value[date]

    suspend fun putAllAndWait(all: Map<LocalDate, DayEntry>, replace: Boolean) = withContext(io) {
        if (replace) {
            dir.listFiles { f -> f.name.endsWith(".day") }?.forEach { it.delete() }
        }
        all.values.forEach { writeNow(it) }
        _entries.value = if (replace) all.toSortedMap() else (_entries.value + all).toSortedMap()
    }

    /** Waits until every queued write has reached the disk. */
    suspend fun flush() = withContext(io) { }

    suspend fun deleteEverything() = withContext(io) {
        dir.listFiles { f -> f.name.endsWith(".day") }?.forEach { it.delete() }
        quarantineRoot.deleteRecursively()
        _entries.value = emptyMap()
        _unreadable.value = 0
    }
}
