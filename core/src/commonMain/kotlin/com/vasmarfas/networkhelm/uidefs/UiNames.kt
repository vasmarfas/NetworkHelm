package com.vasmarfas.networkhelm.uidefs

internal fun squeezed(text: String): String = text.filter { it.isLetterOrDigit() }.lowercase()

private val skipped = setOf("the", "of", "for", "a", "an", "and", "to", "in", "on", "by", "with")

internal fun wordsOf(text: String): List<String> {
    val out = mutableListOf<String>()
    val word = StringBuilder()
    fun flush() {
        if (word.isNotEmpty()) out += word.toString().lowercase()
        word.setLength(0)
    }
    text.forEachIndexed { at, char ->
        when {
            !char.isLetterOrDigit() -> flush()
            char.isUpperCase() && at > 0 && text[at - 1].isLowerCase() -> {
                flush()
                word.append(char)
            }
            else -> word.append(char)
        }
    }
    flush()
    return out
}

private fun forms(word: String): Set<String> = buildSet {
    add(word)
    if (word.length > 3 && word.endsWith("ies")) add(word.dropLast(3) + "y")
    if (word.length > 3 && word.endsWith("es")) add(word.dropLast(2))
    if (word.length > 2 && word.endsWith("s") && !word.endsWith("ss")) add(word.dropLast(1))
    if (word.length > 4 && word.endsWith("ing")) {
        add(word.dropLast(3))
        add(word.dropLast(3) + "e")
    }
    if (word.length > 5 && word.endsWith("y")) add(word.dropLast(1))
    add(word + "s")
}

private fun same(a: String, b: String) = forms(a).any { it in forms(b) }

private fun abbreviates(a: String, b: String): Boolean {
    val (short, long) = if (a.length <= b.length) a to b else b to a
    return short.length >= 3 && long.length - short.length >= 3 && long.startsWith(short)
}

internal fun sharesWord(a: String, b: String): Boolean {
    val theirs = wordsOf(b).filter { it !in skipped }
    return wordsOf(a).filter { it !in skipped }.any { word -> theirs.any { same(word, it) } }
}

internal fun matchTitles(titles: List<String>, names: List<String>, pairLast: Boolean = true, loose: Boolean = false, abbreviated: Boolean = false): Map<Int, String> {
    val result = mutableMapOf<Int, String>()
    val taken = mutableSetOf<String>()
    val squeezedNames = names.associateWith(::squeezed)
    for ((at, title) in titles.withIndex()) {
        val wanted = squeezed(title)
        val exact = names.firstOrNull { it !in taken && squeezedNames.getValue(it) == wanted } ?: continue
        result[at] = exact
        taken += exact
    }
    val titleWords = titles.map { title -> wordsOf(title).filter { it !in skipped } }
    fun closest(same: (String, String) -> Boolean) {
        for ((at, title) in titles.withIndex()) {
            if (at in result) continue
            val mine = titleWords[at]
            if (mine.isEmpty()) continue
            var best = 0.0
            var winners = emptyList<String>()
            for (name in names) {
                if (name in taken) continue
                val theirs = wordsOf(name)
                if (theirs.isEmpty()) continue
                val inside = theirs.all { word -> mine.any { same(word, it) } } || (loose || mine.size >= 2) && mine.all { word -> theirs.any { same(word, it) } }
                if (!inside) continue
                val shared = mine.count { word -> theirs.any { same(word, it) } }.toDouble() / maxOf(mine.size, theirs.size)
                when {
                    shared > best + 1e-9 -> {
                        best = shared
                        winners = listOf(name)
                    }
                    shared > best - 1e-9 -> winners = winners + name
                }
            }
            if (winners.size == 1 && best >= 0.5) {
                result[at] = winners.single()
                taken += winners.single()
            }
        }
    }
    closest(::same)
    if (abbreviated) closest { a, b -> same(a, b) || abbreviates(a, b) }
    val titlesLeft = titles.indices.filter { it !in result && wordsOf(titles[it]).isNotEmpty() }
    val namesLeft = names.filter { it !in taken }
    if (pairLast && titlesLeft.size == 1 && namesLeft.size == 1) result[titlesLeft.single()] = namesLeft.single()
    return result
}

internal fun matchTitle(title: String, names: List<String>, loose: Boolean = false): String? = matchTitles(listOf(title), names, pairLast = false, loose = loose).values.firstOrNull()
