package com.vasmarfas.networkhelm.openwrt

fun parseUci(text: String, file: String? = null): Map<String, List<UciSection>> {
    class Pending(val type: String, val name: String?) {
        val options = linkedMapOf<String, String>()
        val lists = linkedMapOf<String, MutableList<String>>()
    }
    val configs = linkedMapOf<String, MutableList<Pending>>()
    var config = file
    for (line in text.lines()) {
        val trimmed = line.trim()
        if (trimmed.startsWith("#")) continue
        val words = uciWords(trimmed)
        val sections = config?.let { configs.getOrPut(it) { mutableListOf() } }
        val option = words.getOrNull(1)
        when (words.firstOrNull()) {
            "package" -> config = option
            "config" -> sections?.add(Pending(option.orEmpty(), words.getOrNull(2)))
            "option" -> if (option != null) sections?.lastOrNull()?.options?.put(option, words.getOrElse(2) { "" })
            "list" -> if (option != null) sections?.lastOrNull()?.lists?.getOrPut(option) { mutableListOf() }?.add(words.getOrElse(2) { "" })
        }
    }
    return configs.filterValues { it.isNotEmpty() }.mapValues { (_, sections) ->
        val seen = mutableMapOf<String, Int>()
        sections.map { section ->
            val index = seen.getOrElse(section.type) { 0 }.also { seen[section.type] = it + 1 }
            UciSection(section.name ?: "@${section.type}[$index]", section.type, section.name == null, section.options, section.lists)
        }
    }
}

internal fun uciWords(line: String): List<String> {
    val words = mutableListOf<String>()
    val word = StringBuilder()
    var quote: Char? = null
    var started = false
    var at = 0
    while (at < line.length) {
        val char = line[at]
        when {
            quote == '\'' -> if (char == '\'') quote = null else word.append(char)
            quote == '"' && char == '"' -> quote = null
            char == '\\' && at + 1 < line.length -> word.append(line[++at]).also { started = true }
            quote == '"' -> word.append(char)
            char == '\'' || char == '"' -> quote = char.also { started = true }
            char.isWhitespace() -> if (started) {
                words += word.toString()
                word.clear()
                started = false
            }
            else -> word.append(char).also { started = true }
        }
        at++
    }
    if (started) words += word.toString()
    return words
}
