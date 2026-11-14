package com.dusk.app.data

import com.dusk.app.BuildConfig
import com.dusk.core.backup.BackupCodec
import com.dusk.core.backup.BackupContents
import com.dusk.core.journal.DayEntry
import com.dusk.core.journal.MergeResult
import com.dusk.core.journal.mergeEntries
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.OutputStream
import java.time.LocalDate

/** Making and restoring backup files. The file picker is handled by the screen; this works on streams. */
class BackupService(private val journal: JournalStore, private val prefs: Prefs) {

    suspend fun write(out: OutputStream, passphrase: CharArray): Int = withContext(Dispatchers.Default) {
        journal.flush()
        val entries = journal.entries.value.values.toList()
        val now = System.currentTimeMillis()
        val bytes = BackupCodec.encrypt(BackupContents(now, BuildConfig.VERSION_NAME, entries), passphrase)
        withContext(Dispatchers.IO) { out.use { it.write(bytes); it.flush() } }
        prefs.markBackedUp(now)
        entries.size
    }

    fun readBytes(input: InputStream): ByteArray = input.use { it.readBytes() }

    suspend fun open(bytes: ByteArray, passphrase: CharArray): BackupContents =
        withContext(Dispatchers.Default) { BackupCodec.decrypt(bytes, passphrase) }

    fun previewMerge(contents: BackupContents): MergeResult = mergeEntries(journal.entries.value, contents.entries)

    suspend fun apply(contents: BackupContents, replace: Boolean) {
        val all: Map<LocalDate, DayEntry> =
            if (replace) contents.entries.associateBy { it.date } else previewMerge(contents).entries
        journal.putAllAndWait(all, replace)
    }
}
