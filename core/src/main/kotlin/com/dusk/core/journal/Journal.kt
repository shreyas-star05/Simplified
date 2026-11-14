package com.dusk.core.journal

import com.dusk.core.json.JsonObj
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** The app's day ends at 4 AM, not midnight: an entry spoken at 12:40 AM belongs to the day before. */
const val DAY_ROLLOVER_HOUR = 4

fun journalDay(now: LocalDateTime, rolloverHour: Int = DAY_ROLLOVER_HOUR): LocalDate =
    if (now.hour < rolloverHour) now.toLocalDate().minusDays(1) else now.toLocalDate()

fun wordCount(text: String): Int = text.split(Regex("\\s+")).count { it.isNotBlank() }

private val STAMP = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH)

/**
 * A later session on the same day is added after a time stamp line, so the raw transcript
 * keeps its order and the weekly report can see when each part was said.
 */
fun appendSession(existing: String, addition: String, at: LocalTime): String {
    val add = addition.trim()
    if (add.isEmpty()) return existing
    val old = existing.trimEnd()
    if (old.isEmpty()) return add
    return "$old\n\n[${at.format(STAMP)}]\n$add"
}

/** One journal day: the raw transcript, an unsaved draft, and the tap ratings. */
data class DayEntry(
    val date: LocalDate,
    val text: String = "",
    /** What is being spoken right now and not yet committed with Done. Autosaved so nothing is lost. */
    val draft: String = "",
    val mood: Int? = null,
    val energy: Int? = null,
    val progress: Int? = null,
    /** Rough spend in whole rupees. */
    val spend: Long? = null,
    val createdAt: Long,
    val updatedAt: Long,
    /** "app" or "dusk" for entries imported from the old web app. */
    val source: String = "app",
) {
    val hasText: Boolean get() = text.isNotBlank()
    val hasRatings: Boolean get() = mood != null || energy != null || progress != null || spend != null
    val isEmpty: Boolean get() = text.isBlank() && draft.isBlank() && !hasRatings

    fun toMap(): Map<String, Any?> = linkedMapOf(
        "date" to date.toString(),
        "text" to text,
        "draft" to draft,
        "mood" to mood,
        "energy" to energy,
        "progress" to progress,
        "spend" to spend,
        "createdAt" to createdAt,
        "updatedAt" to updatedAt,
        "source" to source,
    )

    companion object {
        fun fromJson(o: JsonObj): DayEntry {
            fun rating(k: String): Int? = o.intOrNull(k)?.takeIf { it in 1..5 }
            return DayEntry(
                date = LocalDate.parse(o.str("date")),
                text = o.strOr("text", ""),
                draft = o.strOr("draft", ""),
                mood = rating("mood"),
                energy = rating("energy"),
                progress = rating("progress"),
                spend = o.longOrNull("spend")?.takeIf { it >= 0 },
                createdAt = o.long("createdAt"),
                updatedAt = o.long("updatedAt"),
                source = o.strOr("source", "app"),
            )
        }
    }
}

data class MergeResult(
    val entries: Map<LocalDate, DayEntry>,
    val added: Int,
    val replaced: Int,
    val keptOnPhone: Int,
)

/**
 * Restore rule from Dusk: for a day that exists in both places, the version edited last wins.
 * Days only on the phone are kept; days only in the backup are added.
 */
fun mergeEntries(onPhone: Map<LocalDate, DayEntry>, fromBackup: Collection<DayEntry>): MergeResult {
    val out = LinkedHashMap(onPhone)
    var added = 0; var replaced = 0; var kept = 0
    for (b in fromBackup) {
        val cur = out[b.date]
        when {
            cur == null -> { out[b.date] = b; added++ }
            b.updatedAt > cur.updatedAt -> { out[b.date] = b; replaced++ }
            else -> kept++
        }
    }
    return MergeResult(out.toSortedMap(), added, replaced, kept)
}
