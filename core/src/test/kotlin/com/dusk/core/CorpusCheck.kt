package com.dusk.core

import com.dusk.core.parse.PlanParser
import java.time.LocalDateTime

/** Shared by the JUnit test and by a plain runner, so both check the same corpus the same way. */
object CorpusCheck {
    data class Case(val line: Int, val now: LocalDateTime, val sentence: String, val expected: String)

    fun load(text: String): List<Case> =
        text.lines().mapIndexedNotNull { idx, raw ->
            if (raw.isBlank() || raw.startsWith("#")) return@mapIndexedNotNull null
            val p = raw.split("\t")
            require(p.size == 3) { "corpus line ${idx + 1} needs 3 tab-separated fields, has ${p.size}" }
            Case(idx + 1, LocalDateTime.parse(p[0]), p[1], p[2])
        }

    fun actual(c: Case): String =
        PlanParser.parse(c.sentence, c.now).drafts.joinToString(" || ") { it.canonical() }

    fun failures(cases: List<Case>): List<String> =
        cases.mapNotNull { c ->
            val got = actual(c)
            if (got == c.expected) null
            else "line ${c.line}: \"${c.sentence}\" at ${c.now}\n  expected: ${c.expected}\n  actual:   $got"
        }
}
