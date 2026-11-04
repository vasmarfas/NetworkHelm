package com.vasmarfas.networkhelm.uidefs

// router .jg files are JavaScript literals, not JSON: keys are unquoted and strings single-quoted
sealed interface JgValue

class JgObject(val fields: Map<String, JgValue>) : JgValue

class JgList(val items: List<JgValue>) : JgValue

class JgString(val text: String) : JgValue

class JgNumber(val value: Double) : JgValue

class JgFlag(val on: Boolean) : JgValue

data object JgNone : JgValue

fun parseJg(text: String): JgValue = JgReader(text).readAll()

private const val MAX_DEPTH = 64

private class JgReader(private val text: String) {
    private var at = 0
    private var depth = 0

    fun readAll(): JgValue {
        val value = value()
        space()
        require(at == text.length) { "unexpected text at $at" }
        return value
    }

    private fun space() {
        while (at < text.length) {
            val char = text[at]
            when {
                char == ' ' || char == '\n' || char == '\r' || char == '\t' -> at++
                char == '/' && text.startsWith("//", at) -> at = text.indexOf('\n', at).let { if (it < 0) text.length else it }
                else -> return
            }
        }
    }

    private fun value(): JgValue {
        space()
        require(at < text.length) { "the text ends early" }
        return when (val char = text[at]) {
            '[' -> nested(::list)
            '{' -> nested(::obj)
            '\'', '"' -> JgString(string())
            else -> if (char == '-' || char.isDigit()) number() else word()
        }
    }

    private fun nested(read: () -> JgValue): JgValue {
        require(++depth <= MAX_DEPTH) { "nested deeper than $MAX_DEPTH at $at" }
        return read().also { depth-- }
    }

    private fun list(): JgValue {
        at++
        val items = mutableListOf<JgValue>()
        while (true) {
            space()
            require(at < text.length) { "the list at $at is not closed" }
            if (text[at] == ']') {
                at++
                return JgList(items)
            }
            items += value()
            space()
            if (at < text.length && text[at] == ',') at++
        }
    }

    private fun obj(): JgValue {
        at++
        val fields = LinkedHashMap<String, JgValue>()
        while (true) {
            space()
            require(at < text.length) { "the object at $at is not closed" }
            if (text[at] == '}') {
                at++
                return JgObject(fields)
            }
            val key = key()
            space()
            require(at < text.length && text[at] == ':') { "no colon after $key at $at" }
            at++
            fields[key] = value()
            space()
            if (at < text.length && text[at] == ',') at++
        }
    }

    private fun key(): String {
        if (at < text.length && (text[at] == '\'' || text[at] == '"')) return string()
        val start = at
        while (at < text.length && (text[at].isLetterOrDigit() || text[at] == '_' || text[at] == '$')) at++
        require(at > start) { "no key at $at" }
        return text.substring(start, at)
    }

    private fun string(): String {
        val quote = text[at++]
        val out = StringBuilder()
        while (true) {
            require(at < text.length) { "the string is not closed" }
            val char = text[at++]
            when {
                char == quote -> return out.toString()
                char == '\\' -> {
                    require(at < text.length) { "the string is not closed" }
                    when (val escaped = text[at++]) {
                        'n' -> out.append('\n')
                        't' -> out.append('\t')
                        'r' -> out.append('\r')
                        'u' -> {
                            require(at + 4 <= text.length) { "a cut escape at $at" }
                            out.append(text.substring(at, at + 4).toInt(16).toChar())
                            at += 4
                        }
                        else -> out.append(escaped)
                    }
                }
                else -> out.append(char)
            }
        }
    }

    private fun number(): JgValue {
        val start = at
        if (text[at] == '-') at++
        if (text.startsWith("0x", at) || text.startsWith("0X", at)) {
            at += 2
            val digits = at
            while (at < text.length && text[at].isLetterOrDigit()) at++
            return JgNumber(text.substring(digits, at).toLong(16).toDouble())
        }
        while (at < text.length && (text[at].isDigit() || text[at] == '.' || text[at] == 'e' || text[at] == 'E' || ((text[at] == '+' || text[at] == '-') && text[at - 1] in "eE"))) at++
        return JgNumber(text.substring(start, at).toDoubleOrNull() ?: throw IllegalArgumentException("not a number at $start"))
    }

    private fun word(): JgValue {
        val start = at
        while (at < text.length && text[at].isLetter()) at++
        return when (text.substring(start, at)) {
            "true" -> JgFlag(true)
            "false" -> JgFlag(false)
            "null", "undefined" -> JgNone
            else -> throw IllegalArgumentException("unexpected text at $start")
        }
    }
}
