package com.vasmarfas.networkhelm

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StringsTest {

    private val resources = File("src/commonMain/composeResources")

    private fun text(folder: String) = File(resources, folder).listFiles { file -> file.name.startsWith("strings") && file.extension == "xml" }
        .orEmpty().sortedBy { it.name }.joinToString("\n") { it.readText() }

    private fun keys(folder: String) = Regex("""<(?:string|plurals) name="([^"]+)"""").findAll(text(folder)).map { it.groupValues[1] }.toSet()

    @Test
    fun russianHasEveryEnglishStringAndNoMore() {
        assertEquals(keys("values"), keys("values-ru"))
    }

    @Test
    fun russianPluralsHaveOneFewManyAndOther() {
        val text = text("values-ru")
        val plurals = Regex("""<plurals name="([^"]+)">(.*?)</plurals>""", RegexOption.DOT_MATCHES_ALL).findAll(text).toList()
        assertTrue(plurals.isNotEmpty())
        for (plural in plurals) {
            val quantities = Regex("""quantity="(\w+)"""").findAll(plural.groupValues[2]).map { it.groupValues[1] }.toSet()
            assertEquals(setOf("one", "few", "many", "other"), quantities, plural.groupValues[1])
        }
    }
}
