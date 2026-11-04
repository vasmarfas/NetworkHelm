package com.vasmarfas.networkhelm.app

// a semicolon separates the fields where spreadsheets use a decimal comma
fun parseCsv(text: String): List<List<String>> {
    val firstLine = text.substringBefore('\n')
    val separator = if (';' in firstLine && ',' !in firstLine) ';' else ','
    val rows = mutableListOf<List<String>>()
    var row = mutableListOf<String>()
    val field = StringBuilder()
    var quoted = false
    var i = 0
    fun endField() {
        row += field.toString()
        field.clear()
    }
    while (i < text.length) {
        val c = text[i]
        when {
            quoted && c == '"' && text.getOrNull(i + 1) == '"' -> {
                field.append('"')
                i++
            }
            c == '"' -> quoted = !quoted
            quoted -> field.append(c)
            c == separator -> endField()
            c == '\n' || c == '\r' -> {
                if (c == '\r' && text.getOrNull(i + 1) == '\n') i++
                endField()
                if (row.any { it.isNotEmpty() }) rows += row
                row = mutableListOf()
            }
            else -> field.append(c)
        }
        i++
    }
    endField()
    if (row.any { it.isNotEmpty() }) rows += row
    return rows
}
