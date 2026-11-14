package com.dusk.core

import com.dusk.core.backup.BackupCodec
import com.dusk.core.backup.BackupContents
import com.dusk.core.backup.BackupException
import com.dusk.core.journal.DayEntry
import com.dusk.core.journal.Prompts
import com.dusk.core.journal.appendSession
import com.dusk.core.journal.journalDay
import com.dusk.core.journal.mergeEntries
import com.dusk.core.journal.monthCells
import com.dusk.core.journal.wordCount
import com.dusk.core.json.Json
import com.dusk.core.json.JsonObj
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.YearMonth

/**
 * Release 1 core checks. Shared by the JUnit test and a plain runner,
 * so they can run on a build machine without Gradle too.
 */
object CoreChecks {
    private val failures = ArrayList<String>()

    private fun check(name: String, ok: Boolean, detail: () -> String = { "" }) {
        if (!ok) failures += "$name ${detail()}".trim()
    }

    private fun <T> eq(name: String, expected: T, actual: T) = check(name, expected == actual) { "expected <$expected> but was <$actual>" }

    private inline fun <reified E : Throwable> throws(name: String, block: () -> Unit): E? {
        return try {
            block(); failures += "$name: expected ${E::class.simpleName}"; null
        } catch (e: Throwable) {
            if (e is E) e else { failures += "$name: threw ${e::class.simpleName}: ${e.message}"; null }
        }
    }

    private fun entry(date: String, text: String = "", updated: Long = 1000, mood: Int? = null) =
        DayEntry(LocalDate.parse(date), text = text, mood = mood, createdAt = 500, updatedAt = updated)

    fun run(): List<String> {
        failures.clear()
        json()
        days()
        sessions()
        grid()
        merge()
        backup()
        prompts()
        return failures.toList()
    }

    private fun json() {
        val tricky = "Line one\n\"quoted\" \\ back\\slash\ttab ₹450 · Hinglish: kal subah 7 baje \u0001"
        val m = linkedMapOf<String, Any?>("s" to tricky, "n" to 42L, "neg" to -7L, "d" to 1.5, "b" to true, "z" to null, "l" to listOf(1L, "two", null))
        val back = Json.parse(Json.write(m))
        eq("json round trip", m, back)
        eq("json unicode escape", "é", Json.parse("\"\\u00e9\""))
        eq("json whitespace", mapOf("a" to listOf(1L, 2L)), Json.parse(" { \"a\" : [ 1 , 2 ] } "))
        throws<Json.ParseException>("json trailing garbage") { Json.parse("{} x") }
        throws<Json.ParseException>("json unterminated") { Json.parse("{\"a\": \"b") }
        throws<Json.ParseException>("json too deep") { Json.parse("[".repeat(100) + "]".repeat(100)) }
        eq("json large long", 1794635180021L, Json.parse("1794635180021"))
    }

    private fun days() {
        eq("4 AM rule, 00:40 is yesterday", LocalDate.of(2026, 11, 13), journalDay(LocalDateTime.of(2026, 11, 14, 0, 40)))
        eq("4 AM rule, 03:59 is yesterday", LocalDate.of(2026, 11, 13), journalDay(LocalDateTime.of(2026, 11, 14, 3, 59)))
        eq("4 AM rule, 04:00 is today", LocalDate.of(2026, 11, 14), journalDay(LocalDateTime.of(2026, 11, 14, 4, 0)))
        eq("4 AM rule, 23:15 is today", LocalDate.of(2026, 11, 14), journalDay(LocalDateTime.of(2026, 11, 14, 23, 15)))
        eq("4 AM rule across a month", LocalDate.of(2026, 11, 30), journalDay(LocalDateTime.of(2026, 12, 1, 1, 0)))
        eq("word count", 7, wordCount("  Aaj subah late utha,\n phir gym  gaya "))
        eq("word count empty", 0, wordCount("   \n "))
    }

    private fun sessions() {
        eq("first session has no stamp", "Hello there", appendSession("", "  Hello there ", LocalTime.of(23, 10)))
        eq("later session gets a stamp", "Morning bit\n\n[11:42 PM]\nNight bit", appendSession("Morning bit\n", "Night bit", LocalTime.of(23, 42)))
        eq("empty addition changes nothing", "Keep", appendSession("Keep", "   ", LocalTime.NOON))
        eq("after midnight stamp", "A\n\n[12:40 AM]\nB", appendSession("A", "B", LocalTime.of(0, 40)))
    }

    private fun grid() {
        val nov = monthCells(YearMonth.of(2026, 11))
        eq("grid has 42 cells", 42, nov.size)
        // 1 Nov 2026 is a Sunday, so with Monday first it sits in the 7th column
        eq("1 Nov 2026 position", 6, nov.indexOf(LocalDate.of(2026, 11, 1)))
        eq("30 Nov 2026 position", 35, nov.indexOf(LocalDate.of(2026, 11, 30)))
        eq("grid leading blanks", 6, nov.takeWhile { it == null }.size)
        val feb = monthCells(YearMonth.of(2027, 2))
        eq("Feb 2027 day count", 28, feb.count { it != null })
        eq("Feb 2027 starts Monday", 0, feb.indexOf(LocalDate.of(2027, 2, 1)))
        val sunFirst = monthCells(YearMonth.of(2026, 11), DayOfWeek.SUNDAY)
        eq("Sunday-first grid", 0, sunFirst.indexOf(LocalDate.of(2026, 11, 1)))
    }

    private fun merge() {
        val phone = mapOf(
            LocalDate.parse("2026-11-10") to entry("2026-11-10", "phone old", updated = 100),
            LocalDate.parse("2026-11-11") to entry("2026-11-11", "phone new", updated = 900),
            LocalDate.parse("2026-11-12") to entry("2026-11-12", "only on phone", updated = 100),
        )
        val backup = listOf(
            entry("2026-11-10", "backup newer", updated = 200),
            entry("2026-11-11", "backup older", updated = 800),
            entry("2026-11-13", "only in backup", updated = 50),
        )
        val r = mergeEntries(phone, backup)
        eq("merge: newer backup day replaces", "backup newer", r.entries[LocalDate.parse("2026-11-10")]?.text)
        eq("merge: newer phone day is kept", "phone new", r.entries[LocalDate.parse("2026-11-11")]?.text)
        eq("merge: phone-only day kept", "only on phone", r.entries[LocalDate.parse("2026-11-12")]?.text)
        eq("merge: backup-only day added", "only in backup", r.entries[LocalDate.parse("2026-11-13")]?.text)
        eq("merge counts", listOf(1, 1, 1), listOf(r.added, r.replaced, r.keptOnPhone))
        eq("merge sorted by date", r.entries.keys.sorted(), r.entries.keys.toList())
    }

    private fun backup() {
        val e1 = DayEntry(LocalDate.parse("2026-11-14"), text = "Aaj ka din \"theek\" tha.\nSpent ₹450.", draft = "half said",
            mood = 4, energy = 3, progress = 5, spend = 450, createdAt = 1794600000000, updatedAt = 1794603000000)
        val e2 = entry("2026-11-13", "Second day", updated = 1794500000000, mood = 2)
        val contents = BackupContents(createdAt = 1794700000000, appVersion = "1.0-r1", entries = listOf(e1, e2))
        val pass = "correct horse battery".toCharArray()
        val file = BackupCodec.encrypt(contents, pass.copyOf(), iterations = 20_000)
        val text = String(file, Charsets.UTF_8)
        check("backup hides the journal text", !text.contains("theek") && !text.contains("Second day")) { text.take(200) }
        eq("backup summary needs no passphrase", BackupCodec.Summary(1794700000000, 2), BackupCodec.summary(file))
        val back = BackupCodec.decrypt(file, pass.copyOf())
        eq("backup round trip keeps every field", contents.entries.sortedBy { it.date }, back.entries)
        eq("backup keeps createdAt", contents.createdAt, back.createdAt)

        val wrong = throws<BackupException>("backup wrong passphrase") { BackupCodec.decrypt(file, "wrong horse battery".toCharArray()) }
        eq("wrong passphrase reason", BackupException.Reason.WRONG_PASSPHRASE, wrong?.reason)

        // Flip one character inside the ciphertext: must be refused, never half-read.
        val o = JsonObj.parse(text).map.toMutableMap()
        val data = (o["data"] as String).toCharArray()
        data[10] = if (data[10] == 'A') 'B' else 'A'
        o["data"] = String(data)
        val tampered = throws<BackupException>("backup tampered") { BackupCodec.decrypt(Json.write(o).toByteArray(), pass.copyOf()) }
        check("tampered file refused", tampered?.reason == BackupException.Reason.WRONG_PASSPHRASE || tampered?.reason == BackupException.Reason.DAMAGED) { "${tampered?.reason}" }

        val notBackup = throws<BackupException>("random file") { BackupCodec.decrypt("hello".toByteArray(), pass.copyOf()) }
        eq("random file reason", BackupException.Reason.NOT_A_BACKUP, notBackup?.reason)
        val dusk = throws<BackupException>("old Dusk JSON is not this backup") { BackupCodec.summary("{\"entries\":{},\"reviews\":{}}".toByteArray()) }
        eq("old Dusk file reason", BackupException.Reason.NOT_A_BACKUP, dusk?.reason)

        val newer = Json.write(JsonObj.parse(text).map.toMutableMap().also { it["version"] = 9L }).toByteArray()
        eq("newer version refused", BackupException.Reason.NEWER_VERSION, throws<BackupException>("newer") { BackupCodec.decrypt(newer, pass.copyOf()) }?.reason)

        throws<IllegalArgumentException>("short passphrase refused") { BackupCodec.encrypt(contents, "short".toCharArray()) }
        val f2 = BackupCodec.encrypt(contents, pass.copyOf(), iterations = 20_000)
        check("two backups of the same data differ (fresh salt and IV)", !f2.contentEquals(file))

        val t0 = System.nanoTime()
        BackupCodec.encrypt(contents, pass.copyOf())
        val ms = (System.nanoTime() - t0) / 1_000_000
        println("backup with ${BackupCodec.DEFAULT_ITERATIONS} PBKDF2 rounds took ${ms} ms on this machine")
    }

    private fun prompts() {
        eq("ten lenses", 10, Prompts.LENSES.size)
        eq("lens ids unique", 10, Prompts.LENSES.map { it.id }.toSet().size)
        check("every weekday has a spotlight", DayOfWeek.entries.all { Prompts.spotlight(it).endsWith("?") })
    }
}
