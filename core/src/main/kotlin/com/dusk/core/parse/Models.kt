package com.dusk.core.parse

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

enum class Kind { EVENT, TODO, NOTE }

/** The card field a flag belongs to. Flagged fields are shown in amber. */
enum class Field { DATE, TIME, REPEAT, REMINDER }

enum class Certainty { INFERRED, AMBIGUOUS }

data class Flag(
    val field: Field,
    val certainty: Certainty,
    val reason: String,
    val alternatives: List<String> = emptyList(),
)

sealed class Repeat {
    object Daily : Repeat() { override fun toString() = "daily" }
    data class Weekly(val day: DayOfWeek) : Repeat() {
        override fun toString() = "weekly:" + day.name.take(3)
    }
    data class MonthlyOnDay(val day: Int) : Repeat() {
        override fun toString() = "monthly:$day"
    }
    object MonthlyLastWeekday : Repeat() { override fun toString() = "monthly:last-weekday" }
    object MonthlyLastDay : Repeat() { override fun toString() = "monthly:last-day" }
    object Yearly : Repeat() { override fun toString() = "yearly" }
}

data class Draft(
    val heard: String,
    val kind: Kind,
    val title: String,
    val start: LocalDate,
    val end: LocalDate,
    val time: LocalTime?,
    val repeat: Repeat?,
    val tentative: Boolean,
    val note: String?,
    val reminder: LocalDateTime?,
    val flags: List<Flag>,
    val suggestions: List<String>,
) {
    val allDay: Boolean get() = time == null

    /** One-line form used by the test corpus. Any change here changes every expected line. */
    fun canonical(): String {
        val dates = if (start == end) start.toString() else "$start..$end"
        val t = time?.toString() ?: "allday"
        val rem = reminder?.toString() ?: "-"
        val fl = flags.map { it.field.name }.distinct().sorted().joinToString(",").ifEmpty { "-" }
        val extras = buildList {
            if (tentative) add("tentative")
            if (note != null) add("note=$note")
            suggestions.forEach { add("suggest=$it") }
        }.joinToString(",").ifEmpty { "-" }
        return "$kind; $title; $dates; $t; rep=${repeat ?: "-"}; rem=$rem; flags=$fl; $extras"
    }
}

data class ParseResult(
    val drafts: List<Draft>,
    /** True when the text is long and has no date or time: the app offers to save it as journal. */
    val looksLikeJournal: Boolean,
)

data class ParserConfig(
    /** The app's day ends at this hour, not at midnight. */
    val dayRolloverHour: Int = 4,
    val timedLeadMinutes: Long = 15,
    val allDayReminderTime: LocalTime = LocalTime.of(10, 0),
    val journalGuardWords: Int = 25,
)
