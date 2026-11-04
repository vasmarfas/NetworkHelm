package com.vasmarfas.networkhelm.routeros.console

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ConsoleCursorTest {
    private fun answer(row: Int, column: Int) = "\u001b[$row;${column}R"

    @Test
    fun aTerminalOfTheSizeAnswersEveryQuestionOfTheConsoleWhereItsCursorIs() {
        val cursor = Cursor(24, 80)
        val answers = listOf(
            "\r\u001b[9999B\r\u001b[9999B\u001bZ  \u001b[6n",
            "\u001b[4l\u001b[20l\u001b[?7h\u001b[?5l\u001b[?25h\u001b[H\u001b[9999B\u001b[6n",
            "\u001b[H\u001b[9999B\u001bD\u001b[9999A\u001b[6n",
            "\u001b[H\u001b[9999C\u001b[6n",
            "\u001b[HěH\u001b[6n\r   ",
            "\u001b[H\u001b[9999C\u001b[6n \u001b[6n \u001b[6n",
            "\u000b\u001b[6n",
            "\u001b[?47l\u001b[1;2r\u001b[H\u001b[6n\n\n\u001b[6n\u001b[9999B\u001b[6n\u001b[r\u001b[1;9999r",
        ).flatMap(cursor::answers)
        assertEquals(
            listOf(answer(24, 3), answer(24, 1), answer(1, 1), answer(1, 80), answer(1, 3), answer(1, 80), answer(1, 80), answer(2, 2), answer(3, 2), answer(1, 1), answer(3, 1), answer(24, 1)),
            answers,
        )
    }

    @Test
    fun aSequenceThatTheTextEndsInTheMiddleOfIsReadWithTheNextPortion() {
        val cursor = Cursor(24, 80)
        assertEquals(emptyList(), cursor.answers("\u001b[99"))
        assertEquals(listOf(answer(24, 1)), cursor.answers("99B\u001b[6n"))
        assertEquals(emptyList(), cursor.answers("\u001b"))
        assertEquals(listOf(answer(1, 1)), cursor.answers("[H\u001b[6n"))
    }

    @Test
    fun textThatIsNoQuestionIsFollowedAndNeverAnswered() {
        val cursor = Cursor(24, 80)
        assertTrue(cursor.answers("[\u001b[36mtester\u001b[m@x] > \r\n\u001b(B\u001b=\u001b>\tend").isEmpty())
        assertEquals(listOf(answer(2, 12)), cursor.answers("\u001b[6n"))
    }
}
