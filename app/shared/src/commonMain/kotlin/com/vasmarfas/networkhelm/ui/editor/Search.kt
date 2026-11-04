package com.vasmarfas.networkhelm.ui.editor

class FindOptions(val query: String, val matchCase: Boolean = false, val wholeWord: Boolean = false, val regex: Boolean = false)

class Matches(val ranges: List<IntRange>, val invalid: Boolean = false)

private const val MATCH_LIMIT = 20_000

private fun pattern(options: FindOptions): Regex? {
    if (options.query.isEmpty()) return null
    val body = if (options.regex) options.query else Regex.escape(options.query)
    return try {
        Regex(if (options.wholeWord) "\\b(?:$body)\\b" else body, if (options.matchCase) emptySet() else setOf(RegexOption.IGNORE_CASE))
    } catch (e: IllegalArgumentException) {
        null
    }
}

fun findAll(text: CharSequence, options: FindOptions): Matches {
    val regex = pattern(options) ?: return Matches(emptyList(), invalid = options.query.isNotEmpty())
    val ranges = regex.findAll(text).filter { it.value.isNotEmpty() }.take(MATCH_LIMIT).map { it.range }.toList()
    return Matches(ranges)
}

fun replaceAll(text: String, options: FindOptions, replacement: String): Pair<String, Int>? {
    val regex = pattern(options) ?: return null
    var count = 0
    val result = regex.replace(text) { match ->
        if (match.value.isEmpty()) return@replace match.value
        count++
        if (options.regex) match.groupValues.indices.reversed().fold(replacement) { acc, index -> acc.replace("$$index", match.groupValues[index]) } else replacement
    }
    return result to count
}

fun lineStarts(text: CharSequence): IntArray {
    var count = 1
    for (i in text.indices) if (text[i] == '\n') count++
    val starts = IntArray(count)
    var line = 1
    for (i in text.indices) if (text[i] == '\n') starts[line++] = i + 1
    return starts
}

fun lineOf(starts: IntArray, offset: Int): Int {
    var low = 0
    var high = starts.lastIndex
    while (low < high) {
        val middle = (low + high + 1) ushr 1
        if (starts[middle] <= offset) low = middle else high = middle - 1
    }
    return low
}
