package com.dusk.core.json

/**
 * Small JSON reader and writer with no dependencies, so the core module stays plain Kotlin.
 * Objects become Map<String, Any?> (insertion order kept), arrays List<Any?>,
 * integers Long, other numbers Double.
 */
object Json {

    class ParseException(message: String) : Exception(message)

    fun write(value: Any?): String = StringBuilder().also { write(value, it) }.toString()

    private fun write(v: Any?, sb: StringBuilder) {
        when (v) {
            null -> sb.append("null")
            is String -> str(v, sb)
            is Boolean -> sb.append(v)
            is Int, is Long, is Short, is Byte -> sb.append(v.toString())
            is Double -> { require(v.isFinite()) { "JSON cannot hold $v" }; sb.append(v.toString()) }
            is Float -> write(v.toDouble(), sb)
            is Map<*, *> -> {
                sb.append('{')
                var first = true
                for ((k, value) in v) {
                    if (!first) sb.append(',')
                    first = false
                    str(k as String, sb)
                    sb.append(':')
                    write(value, sb)
                }
                sb.append('}')
            }
            is Iterable<*> -> {
                sb.append('[')
                var first = true
                for (item in v) {
                    if (!first) sb.append(',')
                    first = false
                    write(item, sb)
                }
                sb.append(']')
            }
            else -> throw IllegalArgumentException("cannot write ${v::class.simpleName} as JSON")
        }
    }

    private fun str(s: String, sb: StringBuilder) {
        sb.append('"')
        for (c in s) {
            when (c) {
                '"' -> sb.append("\\\"")
                '\\' -> sb.append("\\\\")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                '\b' -> sb.append("\\b")
                '\u000C' -> sb.append("\\f")
                else -> if (c < ' ') sb.append("\\u%04x".format(c.code)) else sb.append(c)
            }
        }
        sb.append('"')
    }

    fun parse(text: String): Any? {
        val p = Parser(text)
        p.ws()
        val v = p.value()
        p.ws()
        if (p.i != text.length) throw ParseException("unexpected text at ${p.i}")
        return v
    }

    private class Parser(val s: String) {
        var i = 0
        var depth = 0

        fun ws() { while (i < s.length && s[i] in " \t\r\n") i++ }

        fun fail(msg: String): Nothing = throw ParseException("$msg at $i")

        fun value(): Any? {
            if (i >= s.length) fail("unexpected end")
            return when (val c = s[i]) {
                '{' -> obj()
                '[' -> arr()
                '"' -> string()
                't' -> lit("true", true)
                'f' -> lit("false", false)
                'n' -> lit("null", null)
                else -> if (c == '-' || c.isDigit()) number() else fail("unexpected '$c'")
            }
        }

        fun lit(word: String, v: Any?): Any? {
            if (!s.startsWith(word, i)) fail("expected $word")
            i += word.length
            return v
        }

        fun obj(): Map<String, Any?> {
            if (++depth > 64) fail("nested too deep")
            i++
            val m = LinkedHashMap<String, Any?>()
            ws()
            if (i < s.length && s[i] == '}') { i++; depth--; return m }
            while (true) {
                ws()
                if (i >= s.length || s[i] != '"') fail("expected a key")
                val k = string()
                ws()
                if (i >= s.length || s[i] != ':') fail("expected ':'")
                i++
                ws()
                m[k] = value()
                ws()
                if (i >= s.length) fail("unexpected end")
                when (s[i]) {
                    ',' -> i++
                    '}' -> { i++; depth--; return m }
                    else -> fail("expected ',' or '}'")
                }
            }
        }

        fun arr(): List<Any?> {
            if (++depth > 64) fail("nested too deep")
            i++
            val l = ArrayList<Any?>()
            ws()
            if (i < s.length && s[i] == ']') { i++; depth--; return l }
            while (true) {
                ws()
                l += value()
                ws()
                if (i >= s.length) fail("unexpected end")
                when (s[i]) {
                    ',' -> i++
                    ']' -> { i++; depth--; return l }
                    else -> fail("expected ',' or ']'")
                }
            }
        }

        fun string(): String {
            i++
            val sb = StringBuilder()
            while (true) {
                if (i >= s.length) fail("unterminated string")
                val c = s[i++]
                when (c) {
                    '"' -> return sb.toString()
                    '\\' -> {
                        if (i >= s.length) fail("bad escape")
                        when (val e = s[i++]) {
                            '"' -> sb.append('"'); '\\' -> sb.append('\\'); '/' -> sb.append('/')
                            'b' -> sb.append('\b'); 'f' -> sb.append('\u000C'); 'n' -> sb.append('\n')
                            'r' -> sb.append('\r'); 't' -> sb.append('\t')
                            'u' -> {
                                if (i + 4 > s.length) fail("bad unicode escape")
                                sb.append(s.substring(i, i + 4).toInt(16).toChar())
                                i += 4
                            }
                            else -> fail("bad escape '\\$e'")
                        }
                    }
                    else -> sb.append(c)
                }
            }
        }

        fun number(): Any {
            val start = i
            if (s[i] == '-') i++
            while (i < s.length && s[i].isDigit()) i++
            var frac = false
            if (i < s.length && s[i] == '.') { frac = true; i++; while (i < s.length && s[i].isDigit()) i++ }
            if (i < s.length && (s[i] == 'e' || s[i] == 'E')) {
                frac = true; i++
                if (i < s.length && (s[i] == '+' || s[i] == '-')) i++
                while (i < s.length && s[i].isDigit()) i++
            }
            val t = s.substring(start, i)
            if (t == "-" || t.isEmpty()) fail("bad number")
            return if (frac) t.toDouble() else t.toLongOrNull() ?: t.toDouble()
        }
    }
}

/** Typed reads from a parsed object, with clear errors when a backup file is damaged. */
@Suppress("UNCHECKED_CAST")
class JsonObj(val map: Map<String, Any?>) {
    fun str(k: String): String = map[k] as? String ?: throw Json.ParseException("missing text field '$k'")
    fun strOr(k: String, d: String): String = map[k] as? String ?: d
    fun long(k: String): Long = (map[k] as? Number)?.toLong() ?: throw Json.ParseException("missing number field '$k'")
    fun longOrNull(k: String): Long? = (map[k] as? Number)?.toLong()
    fun intOrNull(k: String): Int? = (map[k] as? Number)?.toInt()
    fun obj(k: String): JsonObj = JsonObj(map[k] as? Map<String, Any?> ?: throw Json.ParseException("missing object '$k'"))
    fun list(k: String): List<Any?> = map[k] as? List<Any?> ?: emptyList()
    fun objs(k: String): List<JsonObj> = list(k).map { JsonObj(it as? Map<String, Any?> ?: throw Json.ParseException("'$k' holds something that is not an object")) }

    companion object {
        fun parse(text: String): JsonObj = JsonObj(Json.parse(text) as? Map<String, Any?> ?: throw Json.ParseException("not a JSON object"))
    }
}
