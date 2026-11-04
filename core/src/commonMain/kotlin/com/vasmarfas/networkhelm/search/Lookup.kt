package com.vasmarfas.networkhelm.search

sealed interface Lookup {
    fun matches(value: String): Boolean

    class Addresses(val span: Span) : Lookup {
        override fun matches(value: String) = words(value).any { word -> span(word)?.let { !it.everything && it.overlaps(span) } == true }
    }

    class Mac(val digits: String) : Lookup {
        override fun matches(value: String) = words(value).any { word -> macDigits(word)?.takeIf { it.length == 12 }?.startsWith(digits) == true }
    }

    class Text(val text: String) : Lookup {
        override fun matches(value: String) = value.contains(text, ignoreCase = true)
    }
}

fun lookup(query: String): Lookup? {
    val text = query.trim()
    span(text)?.let { return Lookup.Addresses(it) }
    macDigits(text)?.let { return Lookup.Mac(it) }
    return if (text.length >= 2) Lookup.Text(text) else null
}

class Span(val start: ByteArray, val end: ByteArray) {
    val everything: Boolean get() = start.all { it == 0.toByte() } && end.all { it == 0xFF.toByte() }

    fun overlaps(other: Span) = start.size == other.start.size && compare(start, other.end) <= 0 && compare(other.start, end) <= 0

    fun within(other: Span) = start.size == other.start.size && compare(other.start, start) <= 0 && compare(end, other.end) <= 0
}

fun span(text: String): Span? {
    val zone = text.indexOf('%')
    val word = if (zone < 0) text else text.substring(0, zone) + text.substring(zone).dropWhile { it != '/' }
    val dash = word.indexOf('-')
    if (dash > 0) {
        val start = address(word.substring(0, dash)) ?: return null
        val end = address(word.substring(dash + 1)) ?: return null
        return if (start.size == end.size && compare(start, end) <= 0) Span(start, end) else null
    }
    val bytes = address(word.substringBefore('/')) ?: return null
    val bits = bytes.size * 8
    val length = if ('/' in word) word.substringAfter('/').takeIf { it.isDecimal() }?.toIntOrNull()?.takeIf { it <= bits } ?: return null else bits
    val start = ByteArray(bytes.size)
    val end = ByteArray(bytes.size)
    for (i in bytes.indices) {
        val mask = (0xFF shl (8 - (length - i * 8).coerceIn(0, 8))) and 0xFF
        start[i] = (bytes[i].toInt() and mask).toByte()
        end[i] = (bytes[i].toInt() or (mask xor 0xFF)).toByte()
    }
    return Span(start, end)
}

private fun words(value: String): List<String> = value.split(separators).map { it.trimEnd('.') }.filter { it.isNotEmpty() }

private val separators = Regex("""[\s,;!()\[\]"'=]+""")

private fun address(text: String): ByteArray? = if (':' in text) ipv6(text) else ipv4(text)

private fun ipv4(text: String): ByteArray? {
    val parts = text.split('.')
    if (parts.size != 4) return null
    val bytes = ByteArray(4)
    for (i in 0..3) {
        val part = parts[i].takeIf { it.length in 1..3 && it.isDecimal() }?.toInt()?.takeIf { it <= 255 } ?: return null
        bytes[i] = part.toByte()
    }
    return bytes
}

private fun ipv6(text: String): ByteArray? {
    val halves = text.split("::")
    if (halves.size > 2) return null
    val head = groups(halves[0]) ?: return null
    val tail = if (halves.size == 2) groups(halves[1]) ?: return null else emptyList()
    val count = head.size + tail.size
    if (if (halves.size == 1) count != 8 else count > 7) return null
    val all = head + List(8 - count) { 0 } + tail
    return ByteArray(16) { i -> (all[i / 2] shr (if (i % 2 == 0) 8 else 0) and 0xFF).toByte() }
}

private fun groups(text: String): List<Int>? {
    if (text.isEmpty()) return emptyList()
    val parts = text.split(':')
    val groups = mutableListOf<Int>()
    for ((index, part) in parts.withIndex()) {
        if (index == parts.lastIndex && '.' in part) {
            val v4 = ipv4(part) ?: return null
            groups += (v4[0].toInt() and 0xFF) shl 8 or (v4[1].toInt() and 0xFF)
            groups += (v4[2].toInt() and 0xFF) shl 8 or (v4[3].toInt() and 0xFF)
        } else {
            if (part.length !in 1..4 || !part.isHex()) return null
            groups += part.toInt(16)
        }
    }
    return groups
}

private fun macDigits(text: String): String? {
    val lower = text.lowercase()
    for (separator in listOf(':', '-')) {
        val parts = lower.split(separator)
        if (parts.size in 3..6 && parts.all { it.length == 2 && it.isHex() }) return parts.joinToString("")
    }
    val dotted = lower.split('.')
    if (dotted.size == 3 && dotted.all { it.length == 4 && it.isHex() }) return dotted.joinToString("")
    return lower.takeIf { it.length == 12 && it.isHex() }
}

private fun String.isDecimal() = isNotEmpty() && all { it in '0'..'9' }

private fun String.isHex() = all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }

private fun compare(a: ByteArray, b: ByteArray): Int {
    for (i in a.indices) {
        val difference = (a[i].toInt() and 0xFF) - (b[i].toInt() and 0xFF)
        if (difference != 0) return difference
    }
    return 0
}
