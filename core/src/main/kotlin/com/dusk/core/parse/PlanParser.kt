package com.dusk.core.parse

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.YearMonth
import java.time.temporal.TemporalAdjusters

/**
 * Offline rules that turn one spoken or typed sentence into draft cards.
 * No Android code and no network. Every guess is returned as a Flag so the card can show it in amber.
 */
object PlanParser {

    fun parse(text: String, now: LocalDateTime, cfg: ParserConfig = ParserConfig()): ParseResult {
        val toks = tokenize(text)
        if (toks.isEmpty()) return ParseResult(emptyList(), false)
        val parts = split(toks, now, cfg)
        val journal = parts.size == 1 && !parts[0].hadDate && !parts[0].hadTime &&
            parts[0].draft.repeat == null && toks.size > cfg.journalGuardWords
        return ParseResult(parts.map { it.draft }, journal)
    }

    // ---------------------------------------------------------------- tokens

    private class Tok(val raw: String, val low: String) {
        var used = false
        var sched = false // part of a date, time or repeat
    }

    private class Parsed(val draft: Draft, val hadDate: Boolean, val hadTime: Boolean, val titleEmpty: Boolean)

    private fun tokenize(text: String): List<Tok> {
        var s = text.trim()
        s = s.replace(Regex("(?i)\\b([ap])\\.\\s?m\\b\\.?"), "$1m")      // p.m. -> pm
        s = s.replace(Regex("(?i)(\\d)\\s*([ap]m)\\b"), "$1 $2")          // 2pm -> 2 pm
        s = s.replace('–', '-').replace('—', '-')
        val out = ArrayList<Tok>()
        for (piece in s.split(Regex("\\s+"))) {
            val raw = piece.trim(',', '.', ';', '!', '?', '"', '(', ')', ':', '\'', '“', '”')
            if (raw.isEmpty()) continue
            out += Tok(raw, raw.lowercase())
        }
        return out
    }

    private fun fresh(l: List<Tok>): List<Tok> = l.map { Tok(it.raw, it.low) }

    private val JOINERS = setOf("and", "aur", "then", "&")

    private fun split(toks: List<Tok>, now: LocalDateTime, cfg: ParserConfig): List<Parsed> {
        for (i in 1 until toks.size - 1) {
            if (toks[i].low !in JOINERS) continue
            val left = parseOne(fresh(toks.subList(0, i)), now, cfg)
            if (!(left.hadDate || left.hadTime) || left.titleEmpty) continue
            val rightToks = toks.subList(i + 1, toks.size)
            val right = parseOne(fresh(rightToks), now, cfg)
            if (!(right.hadDate || right.hadTime) || right.titleEmpty) continue
            return listOf(left) + split(fresh(rightToks), now, cfg)
        }
        return listOf(parseOne(fresh(toks), now, cfg))
    }

    // ---------------------------------------------------------------- vocabulary

    private val MONTHS = mapOf(
        "jan" to 1, "january" to 1, "feb" to 2, "february" to 2, "mar" to 3, "march" to 3,
        "apr" to 4, "april" to 4, "may" to 5, "jun" to 6, "june" to 6, "jul" to 7, "july" to 7,
        "aug" to 8, "august" to 8, "sep" to 9, "sept" to 9, "september" to 9, "oct" to 10,
        "october" to 10, "nov" to 11, "november" to 11, "dec" to 12, "december" to 12,
    )
    private val MONTH_NAMES = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
    private val DOW_NAMES = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

    private val WEEKDAYS = mapOf(
        "monday" to DayOfWeek.MONDAY, "mon" to DayOfWeek.MONDAY, "somvar" to DayOfWeek.MONDAY, "somwar" to DayOfWeek.MONDAY,
        "tuesday" to DayOfWeek.TUESDAY, "tue" to DayOfWeek.TUESDAY, "tues" to DayOfWeek.TUESDAY, "mangalvar" to DayOfWeek.TUESDAY, "mangalwar" to DayOfWeek.TUESDAY,
        "wednesday" to DayOfWeek.WEDNESDAY, "wed" to DayOfWeek.WEDNESDAY, "budhvar" to DayOfWeek.WEDNESDAY, "budhwar" to DayOfWeek.WEDNESDAY,
        "thursday" to DayOfWeek.THURSDAY, "thu" to DayOfWeek.THURSDAY, "thur" to DayOfWeek.THURSDAY, "thurs" to DayOfWeek.THURSDAY, "guruvar" to DayOfWeek.THURSDAY, "guruwar" to DayOfWeek.THURSDAY,
        "friday" to DayOfWeek.FRIDAY, "fri" to DayOfWeek.FRIDAY, "shukravar" to DayOfWeek.FRIDAY, "shukrawar" to DayOfWeek.FRIDAY,
        "saturday" to DayOfWeek.SATURDAY, "shanivar" to DayOfWeek.SATURDAY, "shaniwar" to DayOfWeek.SATURDAY,
        "sunday" to DayOfWeek.SUNDAY, "ravivar" to DayOfWeek.SUNDAY, "raviwar" to DayOfWeek.SUNDAY, "itwar" to DayOfWeek.SUNDAY,
    )

    private val ENG_NUM = mapOf(
        "one" to 1, "two" to 2, "three" to 3, "four" to 4, "five" to 5, "six" to 6,
        "seven" to 7, "eight" to 8, "nine" to 9, "ten" to 10, "eleven" to 11, "twelve" to 12,
    )
    private val HIN_NUM = mapOf(
        "ek" to 1, "do" to 2, "teen" to 3, "char" to 4, "chaar" to 4, "paanch" to 5, "panch" to 5,
        "chhe" to 6, "che" to 6, "cheh" to 6, "saat" to 7, "aath" to 8, "nau" to 9, "das" to 10,
        "gyarah" to 11, "barah" to 12,
    )

    private val EVERY = setOf("every", "har", "each")
    private val TARIKH = setOf("tarikh", "tareekh", "tarik", "taarikh")
    private val TO_WORDS = setOf("to", "till", "until", "through", "-", "se", "tak")
    private val RETURN_WORDS = setOf("return", "returning", "returns", "back", "wapas", "vapas")
    private val THIS_WORDS = setOf("this", "coming", "is", "iss", "upcoming")
    private val NEXT_WORDS = setOf("next", "agle", "agla", "agli")
    private val BAJE = setOf("baje", "bje", "o'clock", "oclock")
    private val MORNING = setOf("morning", "subah", "savere", "subha")
    private val AFTERNOON = setOf("afternoon", "dopahar", "dopaher")
    private val EVENING = setOf("evening", "shaam", "sham")
    private val NIGHT = setOf("night", "raat", "tonight")
    private val MIN_UNITS = mapOf(
        "min" to 1L, "mins" to 1L, "minute" to 1L, "minutes" to 1L,
        "hour" to 60L, "hours" to 60L, "hr" to 60L, "hrs" to 60L, "ghanta" to 60L, "ghante" to 60L,
        "day" to 1440L, "days" to 1440L, "din" to 1440L,
    )
    private val BEFORE_WORDS = setOf("before", "pehle", "prior", "early", "ahead")
    private val TENTATIVE_WORDS = setOf("tentative", "tentatively", "maybe", "shayad", "probably", "perhaps")
    private val TODO_VERBS = setOf(
        "call", "pay", "buy", "send", "email", "book", "renew", "submit", "order", "text",
        "message", "collect", "recharge", "transfer", "file", "apply", "cancel",
    )
    private val EVENT_WORDS = setOf(
        "meeting", "meet", "trip", "visit", "visiting", "dinner", "lunch", "breakfast", "movie",
        "flight", "train", "bus", "appointment", "party", "wedding", "shaadi", "interview", "class",
        "course", "gym", "match", "concert", "travel", "travelling", "traveling", "birthday",
        "function", "puja", "pooja", "webinar", "session", "workshop", "holiday", "vacation", "outing",
    )
    private val BILL_WORDS = setOf("bill", "rent", "emi", "sip", "premium", "subscription", "recharge", "fees", "fee")
    private val LEAD_FILLER = setOf(
        "add", "please", "pls", "schedule", "create", "put", "my", "a", "an", "the", "i", "have",
        "i've", "got", "need", "to", "mujhe", "there", "is", "there's", "also", "new", "ok", "okay",
        "hey", "on", "at", "for", "that", "about",
    )
    private val TAIL_FILLER = setOf(
        "on", "at", "in", "the", "ko", "hai", "h", "from", "by", "and", "this", "next", "coming",
        "pe", "par", "for", "to", "is", "aur", "hain",
    )

    private fun ordinal(s: String?): Int? {
        if (s == null) return null
        val m = Regex("^(\\d{1,2})(st|nd|rd|th)$").find(s) ?: return null
        return m.groupValues[1].toInt().takeIf { it in 1..31 }
    }

    private fun plainNum(s: String?): Int? = if (s != null && s.length <= 4 && s.all { it.isDigit() }) s.toIntOrNull() else null
    private fun dayNum(s: String?): Int? = (ordinal(s) ?: plainNum(s))?.takeIf { it in 1..31 }
    private fun yearNum(s: String?): Int? = plainNum(s)?.takeIf { it in 2000..2100 }

    // ---------------------------------------------------------------- date specs

    private sealed class DSpec {
        data class Abs(val day: Int, val month: Int?, val year: Int?) : DSpec()
        data class Rel(val days: Int, val word: String) : DSpec()
        data class Wd(val dow: DayOfWeek, val mode: Int) : DSpec() // 0 bare, 1 this, 2 next
        data class Weekend(val next: Boolean) : DSpec()
        object MonthEnd : DSpec()
    }

    private class DateHit(val from: Int, val to: Int, val a: DSpec, val b: DSpec?)

    private fun fmtDate(d: LocalDate, ref: LocalDate): String {
        val base = "${DOW_NAMES[d.dayOfWeek.value - 1]} ${d.dayOfMonth} ${MONTH_NAMES[d.monthValue - 1]}"
        return if (d.year != ref.year) "$base ${d.year}" else base
    }

    private fun fmtTime(t: LocalTime): String {
        val h12 = when {
            t.hour == 0 -> 12
            t.hour > 12 -> t.hour - 12
            else -> t.hour
        }
        val mm = t.minute.toString().padStart(2, '0')
        return "$h12:$mm ${if (t.hour < 12) "AM" else "PM"}"
    }

    private fun safeDate(year: Int, month: Int, day: Int): LocalDate? =
        if (day <= YearMonth.of(year, month).lengthOfMonth()) LocalDate.of(year, month, day) else null

    private fun lastWeekdayOfMonth(ym: YearMonth): LocalDate {
        var d = ym.atEndOfMonth()
        while (d.dayOfWeek == DayOfWeek.SATURDAY || d.dayOfWeek == DayOfWeek.SUNDAY) d = d.minusDays(1)
        return d
    }

    // ---------------------------------------------------------------- one sentence

    private fun parseOne(t: List<Tok>, now: LocalDateTime, cfg: ParserConfig): Parsed {
        val heard = t.joinToString(" ") { it.raw }
        val flags = ArrayList<Flag>()
        val suggestions = ArrayList<String>()
        val calToday = now.toLocalDate()
        val early = now.hour < cfg.dayRolloverHour
        val appToday = if (early) calToday.minusDays(1) else calToday

        fun low(i: Int): String? = if (i in t.indices && !t[i].used) t[i].low else null
        fun use(from: Int, to: Int, sched: Boolean = false) {
            for (k in from..to) if (k in t.indices) { t[k].used = true; if (sched) t[k].sched = true }
        }

        // ---- tentative
        var tentative = false
        for (i in t.indices) {
            val a = low(i) ?: continue
            if (a in TENTATIVE_WORDS) { tentative = true; use(i, i) }
            else if (a == "not" && (low(i + 1) == "confirmed" || low(i + 1) == "sure")) { tentative = true; use(i, i + 1) }
            else if (a == "pakka" && (low(i + 1) == "nahi" || low(i + 1) == "nahin")) { tentative = true; use(i, i + 1) }
        }

        // ---- reminder lead: "remind me 15 minutes before"
        var leadMinutes: Long? = null
        for (i in t.indices) {
            val a = low(i) ?: continue
            var endIdx = -1
            var mins = 0L
            val n = plainNum(a) ?: ENG_NUM[a] ?: (if (a == "ek") 1 else null)
            val unit = MIN_UNITS[low(i + 1)]
            if (n != null && unit != null && low(i + 2) in BEFORE_WORDS) {
                mins = n * unit; endIdx = i + 2
            } else if (a in setOf("half", "aadha", "aadhe")) {
                var j = i + 1
                if (low(j) == "an" || low(j) == "a") j++
                if (MIN_UNITS[low(j)] == 60L && low(j + 1) in BEFORE_WORDS) { mins = 30; endIdx = j + 1 }
            } else if ((a == "an" || a == "a") && MIN_UNITS[low(i + 1)] == 60L && low(i + 2) in BEFORE_WORDS) {
                mins = 60; endIdx = i + 2
            }
            if (endIdx < 0) continue
            leadMinutes = mins
            use(i, endIdx)
            if (low(i - 2) == "remind" && low(i - 1) == "me") use(i - 2, i - 1)
            else if (low(i - 1) == "reminder" || low(i - 1) == "alert") use(i - 1, i - 1)
            else if (low(endIdx + 1) == "remind" && low(endIdx + 2) == "me") use(endIdx + 1, endIdx + 2)
            else if (low(endIdx + 1) == "reminder") use(endIdx + 1, endIdx + 1)
            break
        }

        // ---- explicit kind
        var explicitKind: Kind? = null
        val todoTriggers = listOf(
            listOf("remind", "me", "to"), listOf("remind", "me", "about"), listOf("remind", "me"),
            listOf("set", "a", "reminder", "to"), listOf("set", "a", "reminder", "for"),
            listOf("set", "reminder", "to"), listOf("set", "reminder", "for"),
            listOf("reminder", "to"), listOf("reminder", "for"), listOf("reminder"),
            listOf("yaad", "dila", "dena"), listOf("yaad", "dilana"), listOf("yaad", "dilao"),
            listOf("don't", "forget", "to"), listOf("dont", "forget", "to"),
        )
        outer@ for (i in t.indices) {
            for (seq in todoTriggers) {
                if (seq.indices.all { low(i + it) == seq[it] }) {
                    explicitKind = Kind.TODO
                    use(i, i + seq.size - 1)
                    break@outer
                }
            }
        }
        if (explicitKind == null && low(0) == "note") {
            explicitKind = Kind.NOTE
            use(0, if (low(1) == "that" || low(1) == "down") 1 else 0)
        }

        // ---- repeat
        var repeat: Repeat? = null
        var pendingWeekly = false
        var pendingMonthly = false
        for (i in t.indices) {
            val a = low(i) ?: continue
            if (a == "last" && low(i + 1) == "working" && low(i + 2) == "day") {
                var e = i + 2
                if (low(e + 1) == "of") e++
                if (low(e + 1) in EVERY || low(e + 1) == "the") e++
                if (low(e + 1) == "month") e++
                use(i, e, true)
                repeat = Repeat.MonthlyLastWeekday
                flags += Flag(Field.REPEAT, Certainty.INFERRED, "\"working day\" is read as Monday to Friday; office holidays are not known yet")
                break
            }
            if (a == "last" && low(i + 1) == "day" && low(i + 2) == "of" && low(i + 3) in EVERY && low(i + 4) == "month") {
                use(i, i + 4, true); repeat = Repeat.MonthlyLastDay; break
            }
            val ord = ordinal(a)
            if (ord != null && low(i + 1) == "of" && low(i + 2) in EVERY && low(i + 3) == "month") {
                use(i, i + 3, true); repeat = Repeat.MonthlyOnDay(ord); break
            }
            if (a in EVERY) {
                val b = low(i + 1)
                val wd = WEEKDAYS[b]
                if (wd != null) { use(i, i + 1, true); repeat = Repeat.Weekly(wd); break }
                if (b == "day" || b == "din" || b == "roz") { use(i, i + 1, true); repeat = Repeat.Daily; break }
                if (b == "week" || b == "hafte" || b == "hafta") {
                    var e = i + 1
                    var d: DayOfWeek? = null
                    if (low(e + 1) == "on" && WEEKDAYS[low(e + 2)] != null) { d = WEEKDAYS[low(e + 2)]; e += 2 }
                    else if (WEEKDAYS[low(e + 1)] != null) { d = WEEKDAYS[low(e + 1)]; e += 1 }
                    use(i, e, true)
                    if (d != null) repeat = Repeat.Weekly(d) else pendingWeekly = true
                    break
                }
                if (b == "month" || b == "mahine" || b == "mahina") {
                    var e = i + 1
                    var j = e + 1
                    if (low(j) == "on") j++
                    if (low(j) == "the") j++
                    val dn = ordinal(low(j)) ?: plainNum(low(j))?.takeIf { it in 1..31 && low(j + 1) in TARIKH }
                    if (dn != null) {
                        e = j
                        if (low(e + 1) in TARIKH) e++
                        if (low(e + 1) == "ko") e++
                        repeat = Repeat.MonthlyOnDay(dn)
                    } else pendingMonthly = true
                    use(i, e, true)
                    break
                }
                if (b == "year" || b == "saal") { use(i, i + 1, true); repeat = Repeat.Yearly; break }
                if (ordinal(b) != null && WEEKDAYS[low(i + 2)] != null) {
                    repeat = Repeat.Weekly(WEEKDAYS[low(i + 2)]!!)
                    use(i, i + 2, true)
                    flags += Flag(Field.REPEAT, Certainty.AMBIGUOUS, "\"every 2nd Saturday\" style repeats are not supported yet; saved as every week")
                    break
                }
                continue
            }
        }
        // Single words only count when no "every ..." phrase was found: "weekly review every Friday".
        if (repeat == null && !pendingWeekly && !pendingMonthly) {
            for (i in t.indices) {
                when (low(i) ?: continue) {
                    "daily", "everyday", "rozana" -> { use(i, i, true); repeat = Repeat.Daily }
                    "weekly" -> { use(i, i, true); pendingWeekly = true }
                    "monthly" -> { use(i, i, true); pendingMonthly = true }
                    "yearly", "annually" -> { use(i, i, true); repeat = Repeat.Yearly }
                    else -> continue
                }
                break
            }
        }

        // ---- duration: "for 3 days"
        var durationDays: Int? = null
        for (i in t.indices) {
            if (low(i) != "for") continue
            val n = plainNum(low(i + 1)) ?: ENG_NUM[low(i + 1)] ?: continue
            val u = low(i + 2)
            if (u == "day" || u == "days" || u == "din") { durationDays = n; use(i, i + 2, true); break }
            if (u == "night" || u == "nights") { durationDays = n + 1; use(i, i + 2, true); break }
        }

        // ---- date
        var nightFromDate = false

        fun matchSingle(i: Int): Pair<Int, DSpec>? {
            val a = low(i) ?: return null
            if (a == "day" && low(i + 1) == "after" && (low(i + 2) == "tomorrow" || low(i + 2) == "kal")) return (i + 3) to DSpec.Rel(2, "day after tomorrow")
            if (a == "parso" || a == "parson" || a == "parsu") return (i + 1) to DSpec.Rel(2, a)
            if (a == "tomorrow" || a == "kal" || a == "tmrw" || a == "tomorow") return (i + 1) to DSpec.Rel(1, a)
            if (a == "today" || a == "aaj") return (i + 1) to DSpec.Rel(0, a)
            if (a == "tonight") { nightFromDate = true; return (i + 1) to DSpec.Rel(0, a) }
            if (a == "weekend") return (i + 1) to DSpec.Weekend(false)
            if (a in THIS_WORDS && low(i + 1) == "weekend") return (i + 2) to DSpec.Weekend(false)
            if (a in NEXT_WORDS && low(i + 1) == "weekend") return (i + 2) to DSpec.Weekend(true)
            if (a in NEXT_WORDS && low(i + 1) == "week") return (i + 2) to DSpec.Rel(7, "next week")
            WEEKDAYS[a]?.let { return (i + 1) to DSpec.Wd(it, 0) }
            if (a in THIS_WORDS) WEEKDAYS[low(i + 1)]?.let { return (i + 2) to DSpec.Wd(it, 1) }
            if (a in NEXT_WORDS) WEEKDAYS[low(i + 1)]?.let { return (i + 2) to DSpec.Wd(it, 2) }
            Regex("^(\\d{1,2})/(\\d{1,2})(?:/(\\d{2,4}))?$").find(a)?.let { m ->
                val d = m.groupValues[1].toInt(); val mo = m.groupValues[2].toInt()
                val y = m.groupValues[3].toIntOrNull()?.let { if (it < 100) 2000 + it else it }
                if (d in 1..31 && mo in 1..12) return (i + 1) to DSpec.Abs(d, mo, y)
            }
            Regex("^(\\d{1,2})-(\\d{1,2})-(\\d{4})$").find(a)?.let { m ->
                val d = m.groupValues[1].toInt(); val mo = m.groupValues[2].toInt()
                if (d in 1..31 && mo in 1..12) return (i + 1) to DSpec.Abs(d, mo, m.groupValues[3].toInt())
            }
            val dn = dayNum(a)
            if (dn != null) {
                var j = i + 1
                if (low(j) == "of") j++
                val mo = MONTHS[low(j)]
                if (mo != null) {
                    val y = yearNum(low(j + 1))
                    return (if (y != null) j + 2 else j + 1) to DSpec.Abs(dn, mo, y)
                }
                if (ordinal(a) != null) return (i + 1) to DSpec.Abs(dn, null, null)
                if (low(i + 1) in TARIKH) return (i + 2) to DSpec.Abs(dn, null, null)
            }
            val mo = MONTHS[a]
            if (mo != null) {
                val d2 = dayNum(low(i + 1))
                if (d2 != null) {
                    val y = yearNum(low(i + 2))
                    return (if (y != null) i + 3 else i + 2) to DSpec.Abs(d2, mo, y)
                }
            }
            if (a == "in" || a == "after") {
                val n = plainNum(low(i + 1)) ?: ENG_NUM[low(i + 1)]
                if (n != null && (low(i + 2) == "days" || low(i + 2) == "day" || low(i + 2) == "din")) return (i + 3) to DSpec.Rel(n, "in $n days")
            }
            val nb = plainNum(a)
            if (nb != null && low(i + 1) == "din" && low(i + 2) == "baad") return (i + 3) to DSpec.Rel(nb, "in $nb days")
            if (a == "month" && low(i + 1) == "end") return (i + 2) to DSpec.MonthEnd
            if (a == "end" && low(i + 1) == "of") {
                if (low(i + 2) == "month") return (i + 3) to DSpec.MonthEnd
                if (low(i + 2) == "the" && low(i + 3) == "month") return (i + 4) to DSpec.MonthEnd
            }
            return null
        }

        fun findDate(): DateHit? {
            for (i in t.indices) {
                if (low(i) == null) continue
                // "12 to 20 December"
                val d1 = dayNum(low(i))
                if (d1 != null && low(i + 1) in TO_WORDS) {
                    val d2 = dayNum(low(i + 2))
                    var j = i + 3
                    if (low(j) == "of") j++
                    val mo = MONTHS[low(j)]
                    if (d2 != null && mo != null) {
                        val y = yearNum(low(j + 1))
                        return DateHit(i, if (y != null) j + 2 else j + 1, DSpec.Abs(d1, mo, y), DSpec.Abs(d2, mo, y))
                    }
                }
                // "12-20 December"
                val rm = Regex("^(\\d{1,2})-(\\d{1,2})$").find(low(i)!!)
                if (rm != null && MONTHS[low(i + 1)] != null) {
                    val a = rm.groupValues[1].toInt(); val b = rm.groupValues[2].toInt()
                    if (a in 1..31 && b in 1..31) {
                        val mo = MONTHS[low(i + 1)]!!
                        val y = yearNum(low(i + 2))
                        return DateHit(i, if (y != null) i + 3 else i + 2, DSpec.Abs(a, mo, y), DSpec.Abs(b, mo, y))
                    }
                }
                val single = matchSingle(i) ?: continue
                val j = single.first
                if (low(j) in TO_WORDS) {
                    val second = matchSingle(j + 1)
                    if (second != null) return DateHit(i, second.first, single.second, second.second)
                }
                if (low(j) in RETURN_WORDS) {
                    var k = j + 1
                    if (low(k) == "on") k++
                    val second = matchSingle(k)
                    if (second != null) return DateHit(i, second.first, single.second, second.second)
                }
                return DateHit(i, j, single.second, null)
            }
            return null
        }

        val hit = findDate()
        if (hit != null) {
            use(hit.from, hit.to - 1, true)
            // prepositions around the date
            var p = hit.from - 1
            if (low(p) == "the") { use(p, p, true); p-- }
            if (low(p) == "on" || low(p) == "from" || low(p) == "by") use(p, p, true)
            if (low(hit.to) == "ko") use(hit.to, hit.to, true)
        }

        // ---- time
        var hour: Int? = null
        var minute = 0
        var ampm: String? = null
        fun takeAmPm(i: Int): Int {
            val s = low(i)
            if (s == "am" || s == "pm") { ampm = s; use(i, i, true); return i + 1 }
            return i
        }
        fun takePrefix(i: Int) { if (low(i - 1) == "at" || low(i - 1) == "@" || low(i - 1) == "around") use(i - 1, i - 1, true) }

        for (i in t.indices) {
            val a = low(i) ?: continue
            val m1 = Regex("^(\\d{1,2})[:.](\\d{2})$").find(a)
            if (m1 != null) {
                val h = m1.groupValues[1].toInt(); val mm = m1.groupValues[2].toInt()
                val colon = a.contains(':')
                val nextIsAmPm = low(i + 1) == "am" || low(i + 1) == "pm"
                if (h in 0..23 && mm in 0..59 && (colon || nextIsAmPm)) {
                    hour = h; minute = mm; takePrefix(i); use(i, i, true); takeAmPm(i + 1); break
                }
            }
            if (a == "noon") { hour = 12; ampm = "pm"; takePrefix(i); use(i, i, true); break }
            // Hindi fractions
            if (a == "dhai" || a == "dhaai") { hour = 2; minute = 30; use(i, i, true); if (low(i + 1) in BAJE) use(i + 1, i + 1, true); break }
            if (a == "dedh") { hour = 1; minute = 30; use(i, i, true); if (low(i + 1) in BAJE) use(i + 1, i + 1, true); break }
            if (a in setOf("saade", "sadhe", "sade", "sava", "sawa", "paune", "pone")) {
                val n = plainNum(low(i + 1)) ?: HIN_NUM[low(i + 1)] ?: ENG_NUM[low(i + 1)]
                if (n != null && n in 1..12) {
                    when (a) {
                        "saade", "sadhe", "sade" -> { hour = n; minute = 30 }
                        "sava", "sawa" -> { hour = n; minute = 15 }
                        else -> { hour = if (n == 1) 12 else n - 1; minute = 45 }
                    }
                    use(i, i + 1, true)
                    var j = i + 2
                    if (low(j) in BAJE) { use(j, j, true); j++ }
                    takeAmPm(j)
                    break
                }
            }
            val n = plainNum(a)
            // "9 30 pm"
            if (n != null && n in 1..12) {
                val nx = low(i + 1)
                val mm = if (nx != null && nx.length == 2) plainNum(nx) else null
                if (mm != null && mm in 0..59 && (low(i + 2) == "am" || low(i + 2) == "pm")) {
                    hour = n; minute = mm; takePrefix(i); use(i, i + 1, true); takeAmPm(i + 2); break
                }
                if (nx == "am" || nx == "pm") { hour = n; takePrefix(i); use(i, i, true); takeAmPm(i + 1); break }
            }
            // "7 baje", "saat baje"
            val nw = n ?: HIN_NUM[a] ?: ENG_NUM[a]
            if (nw != null && nw in 1..12 && low(i + 1) in BAJE) {
                hour = nw; takePrefix(i); use(i, i + 1, true); takeAmPm(i + 2); break
            }
            // "at 7", "at 9 30", "at seven"
            if (a == "at" || a == "@") {
                val h = plainNum(low(i + 1)) ?: ENG_NUM[low(i + 1)]
                if (h != null && h in 1..12 && MONTHS[low(i + 2)] == null) {
                    hour = h
                    var j = i + 2
                    val nx = low(j)
                    val mm = if (nx != null && nx.length == 2) plainNum(nx) else null
                    if (mm != null && mm in setOf(0, 15, 30, 45)) { minute = mm; j++ }
                    use(i, j - 1, true)
                    takeAmPm(j)
                    break
                }
            }
        }

        var daypart: String? = null
        var daypartWord: String? = null
        for (i in t.indices) {
            val a = low(i) ?: continue
            val dp = when (a) {
                in MORNING -> "morning"
                in AFTERNOON -> "afternoon"
                in EVENING -> "evening"
                in NIGHT -> "night"
                else -> null
            } ?: continue
            daypart = dp; daypartWord = t[i].raw
            use(i, i, true)
            if (low(i - 1) == "the" && low(i - 2) == "in") use(i - 2, i - 1, true)
            else if (low(i - 1) == "in" || low(i - 1) == "at" || low(i - 1) == "this") use(i - 1, i - 1, true)
            if (low(i + 1) == "ko" || low(i + 1) == "mein" || low(i + 1) == "me") use(i + 1, i + 1, true)
            break
        }
        if (daypart == null && nightFromDate) { daypart = "night"; daypartWord = "tonight" }
        val meal: String? = t.firstOrNull { it.low == "breakfast" || it.low == "lunch" || it.low == "dinner" }?.low

        // ---- resolve the time of day as far as it can be known before the date
        var time: LocalTime? = null
        var hourAmbiguous = false
        val hr = hour
        if (hr != null) {
            val h24: Int? = when {
                ampm == "am" -> if (hr == 12) 0 else hr
                ampm == "pm" -> if (hr == 12) 12 else if (hr < 12) hr + 12 else hr
                hr >= 13 || hr == 0 -> hr
                daypart == "morning" -> hr
                daypart == "afternoon" || daypart == "evening" -> if (hr == 12) 12 else hr + 12
                daypart == "night" -> if (hr == 12) 0 else if (hr in 1..4) hr else hr + 12
                meal == "breakfast" -> hr
                meal == "lunch" || meal == "dinner" -> if (hr == 12) 12 else hr + 12
                hr == 12 -> 12
                hr in 1..6 -> hr + 12
                else -> null
            }
            if (h24 != null) time = LocalTime.of(h24, minute) else hourAmbiguous = true
        } else if (daypart != null) {
            time = when (daypart) {
                "morning" -> LocalTime.of(9, 0)
                "afternoon" -> LocalTime.of(15, 0)
                "evening" -> LocalTime.of(18, 0)
                else -> LocalTime.of(21, 0)
            }
            flags += Flag(Field.TIME, Certainty.INFERRED, "time guessed from \"$daypartWord\"")
        } else if (meal != null) {
            time = when (meal) {
                "breakfast" -> LocalTime.of(9, 0)
                "lunch" -> LocalTime.of(13, 0)
                else -> LocalTime.of(20, 0)
            }
            flags += Flag(Field.TIME, Certainty.INFERRED, "time guessed from \"$meal\"", listOf("All day"))
        }
        val hadTime = hour != null || daypart != null

        // ---- resolve dates
        fun resolve(spec: DSpec, after: LocalDate?): LocalDate {
            val ref = after ?: appToday
            when (spec) {
                is DSpec.Abs -> {
                    if (spec.year != null && spec.month != null) {
                        return safeDate(spec.year, spec.month, spec.day) ?: YearMonth.of(spec.year, spec.month).atEndOfMonth()
                    }
                    if (spec.month != null) {
                        var y = ref.year
                        var d = safeDate(y, spec.month, spec.day) ?: YearMonth.of(y, spec.month).atEndOfMonth()
                        if (d.isBefore(ref)) {
                            y++
                            d = safeDate(y, spec.month, spec.day) ?: YearMonth.of(y, spec.month).atEndOfMonth()
                            if (after == null) flags += Flag(Field.DATE, Certainty.INFERRED, "that date has passed this year, so next year")
                        }
                        return d
                    }
                    var ym = YearMonth.from(ref)
                    var d = safeDate(ym.year, ym.monthValue, spec.day)
                    var moved = false
                    while (d == null || d.isBefore(ref)) {
                        ym = ym.plusMonths(1); moved = true
                        d = safeDate(ym.year, ym.monthValue, spec.day)
                    }
                    if (moved && after == null) {
                        flags += Flag(Field.DATE, Certainty.INFERRED, "${spec.day} ${MONTH_NAMES[ref.monthValue - 1]} has passed, so ${MONTH_NAMES[d.monthValue - 1]}")
                    }
                    return d
                }
                is DSpec.Rel -> {
                    if (spec.word == "next week") {
                        flags += Flag(Field.DATE, Certainty.INFERRED, "\"next week\" read as seven days from today")
                        return calToday.plusDays(7)
                    }
                    if (spec.word.startsWith("in ")) return calToday.plusDays(spec.days.toLong())
                    if (!early) return appToday.plusDays(spec.days.toLong())
                    // After midnight and before the day rollover
                    return if (spec.days == 0) {
                        calToday
                    } else {
                        val d = appToday.plusDays(spec.days.toLong())
                        if (after == null) flags += Flag(
                            Field.DATE, Certainty.AMBIGUOUS,
                            "it is after midnight, so \"${spec.word}\" is counted from the day you have not slept on yet",
                            listOf(fmtDate(calToday.plusDays(spec.days.toLong()), calToday)),
                        )
                        d
                    }
                }
                is DSpec.Wd -> {
                    if (after != null) return after.plusDays(1).with(TemporalAdjusters.nextOrSame(spec.dow))
                    if (spec.mode == 2) {
                        val c1 = calToday.with(TemporalAdjusters.next(spec.dow))
                        flags += Flag(Field.DATE, Certainty.AMBIGUOUS, "\"next ${DOW_NAMES[spec.dow.value - 1]}\" can mean either week",
                            listOf(fmtDate(c1.plusDays(7), calToday)))
                        return c1
                    }
                    val first = calToday.with(TemporalAdjusters.nextOrSame(spec.dow))
                    if (first == calToday) {
                        val tm = time
                        val passed = tm != null && !tm.isAfter(now.toLocalTime())
                        val pick = if (passed) first.plusDays(7) else first
                        val other = if (passed) first else first.plusDays(7)
                        flags += Flag(Field.DATE, Certainty.AMBIGUOUS, "today is ${DOW_NAMES[spec.dow.value - 1]}",
                            listOf(fmtDate(other, calToday)))
                        return pick
                    }
                    return first
                }
                is DSpec.MonthEnd -> return YearMonth.from(ref).atEndOfMonth()
                is DSpec.Weekend -> return ref // handled by the caller
            }
        }

        var start: LocalDate
        var end: LocalDate
        val hadDate = hit != null
        if (hit != null && hit.a is DSpec.Weekend) {
            val wk = hit.a
            val dow = calToday.dayOfWeek
            val comingSat = calToday.with(TemporalAdjusters.nextOrSame(DayOfWeek.SATURDAY))
            if (!wk.next) {
                when (dow) {
                    DayOfWeek.SATURDAY -> {
                        start = calToday; end = calToday.plusDays(1)
                        flags += Flag(Field.DATE, Certainty.AMBIGUOUS, "today is Saturday, so \"this weekend\" could also mean next weekend",
                            listOf("${fmtDate(calToday.plusDays(7), calToday)} to ${fmtDate(calToday.plusDays(8), calToday)}"))
                    }
                    DayOfWeek.SUNDAY -> {
                        start = calToday; end = calToday
                        flags += Flag(Field.DATE, Certainty.AMBIGUOUS, "today is Sunday, so \"this weekend\" could also mean next weekend",
                            listOf("${fmtDate(calToday.plusDays(6), calToday)} to ${fmtDate(calToday.plusDays(7), calToday)}"))
                    }
                    else -> { start = comingSat; end = comingSat.plusDays(1) }
                }
            } else {
                if (dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY) {
                    start = calToday.with(TemporalAdjusters.next(DayOfWeek.SATURDAY)); end = start.plusDays(1)
                } else {
                    start = comingSat.plusDays(7); end = start.plusDays(1)
                    flags += Flag(Field.DATE, Certainty.AMBIGUOUS, "\"next weekend\" can mean either weekend",
                        listOf("${fmtDate(comingSat, calToday)} to ${fmtDate(comingSat.plusDays(1), calToday)}"))
                }
            }
        } else if (hit != null) {
            start = resolve(hit.a, null)
            end = if (hit.b != null) resolve(hit.b, start) else start
            if (end.isBefore(start)) end = start
        } else {
            start = calToday
            end = calToday
        }
        if (durationDays != null && durationDays > 1 && (hit == null || hit.b == null)) end = start.plusDays(durationDays - 1L)

        // ---- repeats with no explicit date start at their next occurrence
        val rep0 = repeat
        if (!hadDate && rep0 != null) {
            val tm = time
            when (rep0) {
                is Repeat.Weekly -> {
                    var d = calToday.with(TemporalAdjusters.nextOrSame(rep0.day))
                    if (d == calToday && tm != null && !tm.isAfter(now.toLocalTime())) d = d.plusDays(7)
                    start = d; end = d
                }
                is Repeat.MonthlyOnDay -> {
                    var ym = YearMonth.from(calToday)
                    var d = safeDate(ym.year, ym.monthValue, rep0.day)
                    while (d == null || d.isBefore(calToday)) { ym = ym.plusMonths(1); d = safeDate(ym.year, ym.monthValue, rep0.day) }
                    start = d; end = d
                }
                Repeat.MonthlyLastWeekday -> {
                    var d = lastWeekdayOfMonth(YearMonth.from(calToday))
                    if (d.isBefore(calToday)) d = lastWeekdayOfMonth(YearMonth.from(calToday).plusMonths(1))
                    start = d; end = d
                }
                Repeat.MonthlyLastDay -> { start = YearMonth.from(calToday).atEndOfMonth(); end = start }
                Repeat.Daily -> if (tm != null && !tm.isAfter(now.toLocalTime())) { start = calToday.plusDays(1); end = start }
                else -> {}
            }
        }
        if (pendingWeekly) repeat = Repeat.Weekly(start.dayOfWeek)
        if (pendingMonthly) repeat = Repeat.MonthlyOnDay(start.dayOfMonth)

        // ---- a bare hour from 7 to 11
        if (hourAmbiguous && hr != null) {
            val am = LocalTime.of(hr, minute)
            val pm = LocalTime.of(hr + 12, minute)
            val pick: LocalTime
            if (!hadDate && repeat == null) {
                val nowT = now.toLocalTime()
                when {
                    am.isAfter(nowT) -> pick = am
                    pm.isAfter(nowT) -> pick = pm
                    else -> { pick = am; start = calToday.plusDays(1); end = start }
                }
            } else if (start == calToday) {
                pick = if (am.isAfter(now.toLocalTime())) am else pm
            } else {
                pick = am
            }
            time = pick
            flags += Flag(Field.TIME, Certainty.AMBIGUOUS, "no AM or PM heard", listOf(fmtTime(if (pick == am) pm else am)))
        } else if (!hadDate && repeat == null && hr != null) {
            val tm = time
            if (tm != null && !tm.isAfter(now.toLocalTime())) {
                start = calToday.plusDays(1); end = start
                flags += Flag(Field.DATE, Certainty.INFERRED, "that time has passed today, so tomorrow", listOf(fmtDate(calToday, calToday)))
            }
        }

        if (!hadDate && !hadTime && repeat == null) {
            flags += Flag(Field.DATE, Certainty.INFERRED, "no date heard, so today")
        }

        // ---- title and note
        var noteStart = -1
        for (k in t.indices) {
            if (t[k].used || t[k].low != "for") continue
            val schedBefore = (0 until k).any { t[it].sched }
            val titleBefore = (0 until k).any { !t[it].used }
            val restAfter = (k + 1 until t.size).any { !t[it].used }
            if (schedBefore && titleBefore && restAfter) { noteStart = k; break }
        }
        val titleToks = ArrayList<Tok>()
        val noteToks = ArrayList<Tok>()
        for (k in t.indices) {
            if (t[k].used) continue
            if (noteStart >= 0 && k == noteStart) continue
            if (noteStart >= 0 && k > noteStart) noteToks += t[k] else titleToks += t[k]
        }
        while (titleToks.isNotEmpty() && titleToks.first().low in LEAD_FILLER) titleToks.removeAt(0)
        while (titleToks.isNotEmpty() && titleToks.last().low in TAIL_FILLER) titleToks.removeAt(titleToks.size - 1)
        val titleEmpty = titleToks.isEmpty()
        var title = titleToks.joinToString(" ") { it.raw }
        title = if (titleEmpty) "Untitled" else title.replaceFirstChar { it.uppercase() }
        val note = noteToks.joinToString(" ") { it.raw }.ifEmpty { null }

        // ---- kind
        val titleLows = titleToks.map { it.low }
        val kind = explicitKind ?: when {
            titleLows.firstOrNull() in TODO_VERBS || titleLows.any { it in BILL_WORDS } -> Kind.TODO
            time != null || start != end || titleLows.any { it in EVENT_WORDS } -> Kind.EVENT
            else -> Kind.NOTE
        }
        if (kind == Kind.TODO && repeat == null && titleLows.any { it in BILL_WORDS }) suggestions += "Repeat monthly?"

        // ---- reminder
        var reminder: LocalDateTime? = null
        if (kind != Kind.NOTE) {
            val tm = time
            reminder = when {
                tm != null && leadMinutes != null -> start.atTime(tm).minusMinutes(leadMinutes)
                tm != null && kind == Kind.TODO -> start.atTime(tm)
                tm != null -> start.atTime(tm).minusMinutes(cfg.timedLeadMinutes)
                leadMinutes != null -> start.atTime(cfg.allDayReminderTime).minusMinutes(leadMinutes)
                else -> start.atTime(cfg.allDayReminderTime)
            }
            if (!reminder.isAfter(now)) {
                flags += Flag(Field.REMINDER, Certainty.INFERRED, "the reminder time has already passed, so no reminder")
                reminder = null
            }
        }

        val draft = Draft(
            heard = heard, kind = kind, title = title, start = start, end = end, time = time,
            repeat = repeat, tentative = tentative, note = note, reminder = reminder,
            flags = flags, suggestions = suggestions,
        )
        return Parsed(draft, hadDate, hadTime, titleEmpty)
    }
}
