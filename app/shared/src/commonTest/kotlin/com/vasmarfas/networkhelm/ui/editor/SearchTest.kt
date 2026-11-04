package com.vasmarfas.networkhelm.ui.editor

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SearchTest {

    private val text = "Name name NAMES\nrenamed name1"

    @Test
    fun aQueryFindsEveryMatchAndMatchCaseNarrowsThem() {
        assertEquals(listOf(0..3, 5..8, 10..13, 18..21, 24..27), findAll(text, FindOptions("name")).ranges)
        assertEquals(listOf(5..8, 18..21, 24..27), findAll(text, FindOptions("name", matchCase = true)).ranges)
    }

    @Test
    fun wholeWordLeavesOutWhatTheWordIsPartOf() {
        assertEquals(listOf(0..3, 5..8), findAll(text, FindOptions("name", wholeWord = true)).ranges)
    }

    @Test
    fun aRegularExpressionMatchesAndABadOneIsSaid() {
        assertEquals(2, findAll("a1 b22 c", FindOptions("\\d+", regex = true)).ranges.size)
        val bad = findAll("a", FindOptions("(", regex = true))
        assertTrue(bad.invalid)
        assertTrue(bad.ranges.isEmpty())
        assertFalse(findAll("a", FindOptions("")).invalid)
        assertTrue(findAll("aaa", FindOptions("x*", regex = true)).ranges.isEmpty())
    }

    @Test
    fun aPlainQueryTakesSpecialCharactersAsTheyAre() {
        assertEquals(1, findAll("a.b a-b", FindOptions("a.b")).ranges.size)
        assertEquals(2, findAll("a.b a-b", FindOptions("a.b", regex = true)).ranges.size)
    }

    @Test
    fun replacingGivesTheNewTextAndTheCount() {
        assertEquals("X X NAMES" to 2, replaceAll("a A NAMES", FindOptions("a", wholeWord = true), "X"))
        assertEquals("a+ c+" to 2, replaceAll("a-b c-b", FindOptions("(\\w)-b", regex = true), "\$1+"))
        assertEquals("a\$1b" to 1, replaceAll("a.b", FindOptions(".", matchCase = true), "\$1"))
        assertNull(replaceAll("a", FindOptions("(", regex = true), "x"))
        assertEquals("a" to 0, replaceAll("a", FindOptions("z"), "x"))
    }

    @Test
    fun linesAreFoundByTheirStarts() {
        val starts = lineStarts("one\ntwo\n\nfour")
        assertContentEquals(intArrayOf(0, 4, 8, 9), starts)
        assertEquals(0, lineOf(starts, 0))
        assertEquals(0, lineOf(starts, 3))
        assertEquals(1, lineOf(starts, 4))
        assertEquals(2, lineOf(starts, 8))
        assertEquals(3, lineOf(starts, 12))
        assertContentEquals(intArrayOf(0), lineStarts(""))
    }
}
