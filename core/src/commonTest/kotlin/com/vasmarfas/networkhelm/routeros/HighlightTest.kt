package com.vasmarfas.networkhelm.routeros

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class HighlightTest {

    @Test
    fun theClassesOfTheBytesOfUtf8GoToTheirCharacters() {
        val classes = List(15) { "comment" } + listOf("dir", "cmd", "cmd", "cmd", "none", "none")
        assertEquals(List(9) { "comment" } + listOf("dir", "cmd", "cmd", "cmd", "none", "none"), perCharacter("# привет\n:put 1", classes))
        assertEquals(listOf("dir", "cmd", "cmd", "cmd"), perCharacter(":put", listOf("dir", "cmd", "cmd", "cmd")))
        assertEquals(listOf("none", "comment", "comment"), perCharacter("a😀", listOf("none", "comment", "comment", "comment", "comment")))
        assertNull(perCharacter(":put", listOf("dir", "cmd")))
    }
}
