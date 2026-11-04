package com.vasmarfas.networkhelm.ui.table

import kotlin.math.pow

val naturalOrder: Comparator<String> = Comparator { a, b ->
    when {
        a.isEmpty() && b.isEmpty() -> 0
        a.isEmpty() -> 1
        b.isEmpty() -> -1
        else -> compareRuns(a, b)
    }
}

// empty cells first, then values that read as a number, an interval or a date by magnitude, then text in natural order
internal class CellKey(private val rank: Int, private val magnitude: Double, private val text: String) : Comparable<CellKey> {
    override fun compareTo(other: CellKey): Int = when {
        rank != other.rank -> rank.compareTo(other.rank)
        rank == NUMBER -> magnitude.compareTo(other.magnitude)
        rank == TEXT -> naturalOrder.compare(text, other.text)
        else -> 0
    }
}

private const val EMPTY = 0
private const val NUMBER = 1
private const val TEXT = 2

private val quantity = Regex("""([+-]?\d+(?:\.\d+)?) ?([kKMGTP])?(i?B|bps|p/s)?""")
private val spelled = Regex("""(?:(\d+)w)?(?:(\d+)d)?(?:(\d+)h)?(?:(\d+)m)?(?:(\d+)s)?(?:(\d+)ms)?""")
private val clock = Regex("""(?:(\d+)d )?(\d+):(\d{2}):(\d{2})(?:\.(\d+))?""")
private val isoDate = Regex("""(\d{4})-(\d{2})-(\d{2})(?:[ T](\d{2}):(\d{2}):(\d{2}))?""")
private val consoleDate = Regex("""([a-z]{3})/(\d{2})/(\d{4})(?: (\d{2}):(\d{2}):(\d{2}))?""")
private val months = listOf("jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "oct", "nov", "dec")

internal fun cellKey(text: String): CellKey {
    if (text.isEmpty()) return CellKey(EMPTY, 0.0, text)
    val value = magnitude(text)
    return if (value == null) CellKey(TEXT, 0.0, text) else CellKey(NUMBER, value, text)
}

private fun magnitude(text: String): Double? {
    quantity.matchEntire(text)?.let { match ->
        val (number, prefix, unit) = match.destructured
        val step = if (unit.startsWith("i")) 1024.0 else 1000.0
        val exponent = "kMGTP".indexOf(if (prefix == "K") "k" else prefix) + 1
        return number.toDouble() * step.pow(if (prefix.isEmpty()) 0 else exponent)
    }
    spelled.matchEntire(text)?.let { match ->
        val (weeks, days, hours, minutes, seconds, millis) = match.destructured
        fun part(value: String, unit: Int) = (value.toIntOrNull() ?: 0).toDouble() * unit
        return part(weeks, 604800) + part(days, 86400) + part(hours, 3600) + part(minutes, 60) + part(seconds, 1) + part(millis, 1) / 1000
    }
    clock.matchEntire(text)?.let { match ->
        val (days, hours, minutes, seconds, fraction) = match.destructured
        return (days.toIntOrNull() ?: 0) * 86400.0 + hours.toInt() * 3600 + minutes.toInt() * 60 + seconds.toInt() + (fraction.takeIf { it.isNotEmpty() }?.let { "0.$it".toDouble() } ?: 0.0)
    }
    isoDate.matchEntire(text)?.let { match ->
        val (year, month, day, hour, minute, second) = match.destructured
        return stamp(year.toInt(), month.toInt(), day.toInt(), hour, minute, second)
    }
    consoleDate.matchEntire(text)?.let { match ->
        val (name, day, year, hour, minute, second) = match.destructured
        val month = months.indexOf(name) + 1
        return if (month == 0) null else stamp(year.toInt(), month, day.toInt(), hour, minute, second)
    }
    return null
}

private fun stamp(year: Int, month: Int, day: Int, hour: String, minute: String, second: String) =
    ((year * 12L + month) * 31 + day) * 86400.0 + (hour.toIntOrNull() ?: 0) * 3600 + (minute.toIntOrNull() ?: 0) * 60 + (second.toIntOrNull() ?: 0)

private fun compareRuns(a: String, b: String): Int {
    var i = 0
    var j = 0
    while (i < a.length && j < b.length) {
        if (a[i].isDigit() && b[j].isDigit()) {
            val endA = digitsEnd(a, i)
            val endB = digitsEnd(b, j)
            val result = compareNumbers(a.substring(i, endA), b.substring(j, endB))
            if (result != 0) return result
            i = endA
            j = endB
        } else {
            val result = a[i].lowercaseChar().compareTo(b[j].lowercaseChar())
            if (result != 0) return result
            i++
            j++
        }
    }
    return (a.length - i) - (b.length - j)
}

private fun digitsEnd(text: String, from: Int): Int {
    var at = from
    while (at < text.length && text[at].isDigit()) at++
    return at
}

private fun compareNumbers(a: String, b: String): Int {
    val x = a.trimStart('0')
    val y = b.trimStart('0')
    return if (x.length != y.length) x.length - y.length else x.compareTo(y)
}

fun <K> select(selected: Set<K>, anchor: K?, clicked: K, order: List<K>, ctrl: Boolean, shift: Boolean): Pair<Set<K>, K?> {
    if (shift && anchor != null) {
        val from = order.indexOf(anchor)
        val to = order.indexOf(clicked)
        if (from >= 0 && to >= 0) {
            val range = order.subList(minOf(from, to), maxOf(from, to) + 1).toSet()
            return (if (ctrl) selected + range else range) to anchor
        }
    }
    if (ctrl) return (if (clicked in selected) selected - clicked else selected + clicked) to clicked
    return setOf(clicked) to clicked
}

fun <K> moveTo(order: List<K>, selected: Set<K>, gap: Int): Pair<List<K>, K?>? {
    val moving = order.filter { it in selected }
    val before = order.drop(gap).firstOrNull { it !in selected }
    val rest = order.filterNot { it in selected }
    val at = before?.let(rest::indexOf) ?: rest.size
    if (moving.isEmpty() || rest.take(at) + moving + rest.drop(at) == order) return null
    return moving to before
}
