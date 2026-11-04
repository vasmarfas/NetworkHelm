package com.vasmarfas.networkhelm.routeros.console

private const val ESCAPE = '\u001b'

// the console of MAC-Telnet asks where the cursor is (ESC [ 6 n) and draws nothing until it is answered
internal class Cursor(private val rows: Int, private val columns: Int) {
    private var row = 1
    private var column = 1
    private var wrap = false

    private var cut = ""

    fun answers(text: String): List<String> {
        val answers = ArrayList<String>()
        val input = cut + text
        cut = ""
        var at = 0
        while (at < input.length) {
            val c = input[at]
            if (c == ESCAPE) {
                val kind = input.getOrNull(at + 1)
                val end = when (kind) {
                    null -> -1
                    '[' -> (at + 2 until input.length).firstOrNull { input[it] in '@'..'~' } ?: -1
                    '(', ')', '*', '+' -> if (at + 2 < input.length) at + 2 else -1
                    else -> at + 1
                }
                if (end < 0) {
                    cut = input.substring(at)
                    break
                }
                if (kind == '[') sequence(input.substring(at + 2, end), input[end], answers) else if (kind != null) escape(kind)
                at = end + 1
                continue
            }
            when {
                c == '\r' -> move(column = 1)
                c == '\n' || c == '\u000B' || c == '\u000C' -> move(row = minOf(rows, row + 1))
                c == '\b' -> move(column = maxOf(1, column - 1))
                c == '\t' -> move(column = minOf(columns, (column - 1) / 8 * 8 + 9))
                c >= ' ' && c != '\u007F' && !c.isLowSurrogate() -> print()
            }
            at++
        }
        return answers
    }

    private fun move(row: Int = this.row, column: Int = this.column) {
        this.row = row
        this.column = column
        wrap = false
    }

    private fun print() {
        if (wrap) move(row = minOf(rows, row + 1), column = 1)
        if (column == columns) wrap = true else column++
    }

    private fun escape(kind: Char) {
        when (kind) {
            'D' -> move(row = minOf(rows, row + 1))
            'E' -> move(row = minOf(rows, row + 1), column = 1)
            'M' -> move(row = maxOf(1, row - 1))
        }
    }

    private fun sequence(parameters: String, final: Char, answers: MutableList<String>) {
        val numbers = parameters.split(';').map { it.toIntOrNull() }
        val first = numbers.firstOrNull()
        val count = maxOf(1, first ?: 1)
        when (final) {
            'A' -> move(row = maxOf(1, row - count))
            'B' -> move(row = minOf(rows, row + count))
            'C' -> move(column = minOf(columns, column + count))
            'D' -> move(column = maxOf(1, column - count))
            'H', 'f' -> move((first ?: 1).coerceIn(1, rows), (numbers.getOrNull(1) ?: 1).coerceIn(1, columns))
            'n' -> if (parameters == "6") answers += "$ESCAPE[$row;${column}R"
        }
    }
}
