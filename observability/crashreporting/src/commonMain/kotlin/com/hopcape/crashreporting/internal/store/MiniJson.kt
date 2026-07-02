package com.hopcape.crashreporting.internal.store

// ─────────────────────────────────────────────────────────────
// MiniJson — a tiny, dependency-free JSON codec. The project ships
// no kotlinx-serialization (the sibling observability modules
// hand-roll JSON too, e.g. logging's FileSink), and the crash store
// is the one place that must *round-trip* — write a report to disk
// as the process dies, then read it back on the next launch. A
// hand-rolled writer alone (like FileSink) isn't enough; we need a
// reader as well, so this provides both over the small value shape
// we emit: null / String / Boolean / Long / Double / List / Map.
//
// Kept deliberately minimal (no streaming, no schema): correct
// escaping and correct parsing of exactly what encode() produces.
// ─────────────────────────────────────────────────────────────
internal object MiniJson {

    // ── Encoding ────────────────────────────────────────────

    fun encode(value: Any?): String = StringBuilder().also { append(it, value) }.toString()

    private fun append(sb: StringBuilder, value: Any?) {
        when (value) {
            null -> sb.append("null")
            is String -> appendString(sb, value)
            is Boolean -> sb.append(value.toString())
            is Int, is Long -> sb.append(value.toString())
            is Double, is Float -> sb.append(value.toString())
            is Map<*, *> -> appendObject(sb, value)
            is List<*> -> appendArray(sb, value)
            else -> appendString(sb, value.toString()) // fallback: stringify unknown types
        }
    }

    private fun appendObject(sb: StringBuilder, map: Map<*, *>) {
        sb.append('{')
        var first = true
        for ((k, v) in map) {
            if (!first) sb.append(',')
            first = false
            appendString(sb, k.toString())
            sb.append(':')
            append(sb, v)
        }
        sb.append('}')
    }

    private fun appendArray(sb: StringBuilder, list: List<*>) {
        sb.append('[')
        list.forEachIndexed { i, v ->
            if (i > 0) sb.append(',')
            append(sb, v)
        }
        sb.append(']')
    }

    private fun appendString(sb: StringBuilder, s: String) {
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
                else -> if (c < ' ') sb.append("\\u").append(c.code.toString(16).padStart(4, '0')) else sb.append(c)
            }
        }
        sb.append('"')
    }

    // ── Decoding ────────────────────────────────────────────

    /** Parses [text] produced by [encode]. Throws [IllegalArgumentException] on malformed input. */
    fun decode(text: String): Any? = Parser(text).run {
        val v = parseValue()
        skipWhitespace()
        require(atEnd()) { "trailing content at $pos" }
        v
    }

    private class Parser(private val s: String) {
        var pos = 0

        fun atEnd(): Boolean = pos >= s.length

        fun skipWhitespace() {
            while (pos < s.length && s[pos].let { it == ' ' || it == '\n' || it == '\r' || it == '\t' }) pos++
        }

        fun parseValue(): Any? {
            skipWhitespace()
            require(pos < s.length) { "unexpected end of input" }
            return when (s[pos]) {
                '{' -> parseObject()
                '[' -> parseArray()
                '"' -> parseString()
                't', 'f' -> parseBoolean()
                'n' -> parseNull()
                else -> parseNumber()
            }
        }

        private fun parseObject(): Map<String, Any?> {
            expect('{')
            val map = LinkedHashMap<String, Any?>()
            skipWhitespace()
            if (peek() == '}') { pos++; return map }
            while (true) {
                skipWhitespace()
                val key = parseString()
                skipWhitespace()
                expect(':')
                map[key] = parseValue()
                skipWhitespace()
                when (val c = next()) {
                    ',' -> continue
                    '}' -> return map
                    else -> throw IllegalArgumentException("expected ',' or '}' but got '$c' at ${pos - 1}")
                }
            }
        }

        private fun parseArray(): List<Any?> {
            expect('[')
            val list = ArrayList<Any?>()
            skipWhitespace()
            if (peek() == ']') { pos++; return list }
            while (true) {
                list.add(parseValue())
                skipWhitespace()
                when (val c = next()) {
                    ',' -> continue
                    ']' -> return list
                    else -> throw IllegalArgumentException("expected ',' or ']' but got '$c' at ${pos - 1}")
                }
            }
        }

        private fun parseString(): String {
            expect('"')
            val sb = StringBuilder()
            while (true) {
                require(pos < s.length) { "unterminated string" }
                when (val c = s[pos++]) {
                    '"' -> return sb.toString()
                    '\\' -> {
                        require(pos < s.length) { "unterminated escape" }
                        when (val e = s[pos++]) {
                            '"' -> sb.append('"')
                            '\\' -> sb.append('\\')
                            '/' -> sb.append('/')
                            'n' -> sb.append('\n')
                            'r' -> sb.append('\r')
                            't' -> sb.append('\t')
                            'b' -> sb.append('\b')
                            'f' -> sb.append('\u000C')
                            'u' -> {
                                val hex = s.substring(pos, pos + 4)
                                pos += 4
                                sb.append(hex.toInt(16).toChar())
                            }
                            else -> throw IllegalArgumentException("bad escape '\\$e' at ${pos - 1}")
                        }
                    }
                    else -> sb.append(c)
                }
            }
        }

        private fun parseBoolean(): Boolean = when {
            s.startsWith("true", pos) -> { pos += 4; true }
            s.startsWith("false", pos) -> { pos += 5; false }
            else -> throw IllegalArgumentException("invalid literal at $pos")
        }

        private fun parseNull(): Any? {
            require(s.startsWith("null", pos)) { "invalid literal at $pos" }
            pos += 4
            return null
        }

        private fun parseNumber(): Any {
            val start = pos
            while (pos < s.length && s[pos].let { it == '-' || it == '+' || it == '.' || it == 'e' || it == 'E' || it in '0'..'9' }) pos++
            val token = s.substring(start, pos)
            require(token.isNotEmpty()) { "invalid number at $start" }
            return if (token.any { it == '.' || it == 'e' || it == 'E' }) token.toDouble() else token.toLong()
        }

        private fun peek(): Char = s[pos]
        private fun next(): Char = s[pos++]
        private fun expect(c: Char) {
            require(pos < s.length && s[pos] == c) { "expected '$c' at $pos" }
            pos++
        }
    }
}
