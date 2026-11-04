package com.vasmarfas.networkhelm

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CopyStringsTest {

    private val resources = File("src/commonMain/composeResources")

    private fun text(folder: String) = File(resources, "$folder/strings_copy.xml").readText()

    private fun keys(folder: String) = Regex("""<(?:string|plurals) name="([^"]+)"""").findAll(text(folder)).map { it.groupValues[1] }.toSet()

    @Test
    fun russianHasEveryEnglishStringAndNoMore() {
        assertTrue(keys("values").isNotEmpty())
        assertEquals(keys("values"), keys("values-ru"))
    }

    @Test
    fun russianPluralsHaveOneFewManyAndOther() {
        val plurals = Regex("""<plurals name="([^"]+)">(.*?)</plurals>""", RegexOption.DOT_MATCHES_ALL).findAll(text("values-ru")).toList()
        assertTrue(plurals.isNotEmpty())
        for (plural in plurals) {
            val quantities = Regex("""quantity="(\w+)"""").findAll(plural.groupValues[2]).map { it.groupValues[1] }.toSet()
            assertEquals(setOf("one", "few", "many", "other"), quantities, plural.groupValues[1])
        }
    }
}
