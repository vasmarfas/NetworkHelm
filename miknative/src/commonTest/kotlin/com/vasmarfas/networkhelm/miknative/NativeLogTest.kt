package com.vasmarfas.networkhelm.miknative

import com.vasmarfas.networkhelm.model.DeviceException
import com.vasmarfas.networkhelm.model.Item
import com.vasmarfas.networkhelm.packs.PackNative
import com.vasmarfas.networkhelm.packs.PackNativeField
import com.vasmarfas.networkhelm.packs.PackNativeRef
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private const val GET_ALL = 0xFE0004L
private const val NO_MORE = 0xFE0004L
private const val CURSOR = 0xFE0003
private const val ID = 0xFE0001
private const val RECORDS = 0xFE0002
private const val NAME = 0xFE0010
private const val START_OF_2026 = 1_767_225_600L

// a router that keeps the lines it holds by their ids, serves them in pages of a few and names the line each next page starts with
private class Journal(var ids: List<Int>, private val page: Int = 4, var empty: Boolean = false, var refusal: Long? = null) {
    val asked = ArrayList<M2Message>()

    private fun record(id: Int) = M2Message.of(
        ID to M2Value.U32(id.toLong()),
        1 to M2Value.U32(START_OF_2026 + id),
        2 to M2Value.Text("memory"),
        3 to M2Value.Text("line $id"),
        4 to M2Value.U32s(longArrayOf(2, 1)),
        5 to M2Value.Text("extra of $id"),
    )

    suspend fun FakeRouter.handle(message: M2Message) {
        if (message.number(M2Key.Command) != GET_ALL) return
        if (message.numbers(M2Key.To)?.toList()?.takeLast(2) == listOf(9L, 3L)) {
            val names = listOf(1 to "info", 2 to "warning").map { (id, name) -> M2Message.of(ID to M2Value.U8(id), NAME to M2Value.Text(name)) }
            return reply(message, RECORDS to M2Value.Messages(names))
        }
        asked += message
        refusal?.let { return reply(message, M2Key.ErrorCode to M2Value.U32(it)) }
        val from = message.number(CURSOR)
        val start = if (from == null) 0 else ids.indexOf(from.toInt())
        if (start < 0 || empty && from == null) return reply(message, M2Key.ErrorCode to M2Value.U32(NO_MORE))
        val next = ids.getOrNull(start + page)
        reply(message, RECORDS to M2Value.Messages(ids.drop(start).take(page).map(::record)), *listOfNotNull(next?.let { CURSOR to M2Value.U32(it.toLong()) }).toTypedArray())
    }
}

class NativeLogTest {

    private val spec = PackNative(
        listOf(9, 4),
        listOf(
            PackNativeField("time", 1, "dateandtime", letter = "u"),
            PackNativeField("buffer", 2, "string", letter = "s"),
            PackNativeField("topics", 4, "multinumber", letter = "U", refers = PackNativeRef(listOf(9, 3), listOf(NAME))),
            PackNativeField("message", 3, "string", letter = "s"),
        ),
        log = true,
    )

    private fun line(id: Int) = Item("*${id.toString(16).uppercase()}", mapOf("time" to "2026-01-01 00:${(id / 60).toString().padStart(2, '0')}:${(id % 60).toString().padStart(2, '0')}", "buffer" to "memory", "topics" to "warning,info", "message" to "line $id"))

    private fun session(journal: Journal, block: suspend CoroutineScope.(NativeObjectModel, Journal) -> Unit) = runTest {
        val (client, server) = pipe()
        val fake = FakeRouter(this, server, "admin", "secret") { with(journal) { handle(it) } }
        val connection = NativeConnection.open(client, "admin", "secret", this)
        try {
            withTimeout(30_000) { block(NativeObjectModel(connection), journal) }
        } finally {
            connection.close()
            fake.close()
        }
    }

    @Test
    fun theFirstReadGivesTheWholeBufferInOrderAcrossThePagesWithTheNamesTheFieldsHave() = session(Journal((1..10).toList())) { model, journal ->
        val first = model.lines(spec).read()
        assertTrue(first.snapshot)
        assertFalse(first.finished)
        assertEquals((1..10).map(::line), first.rows)
        assertEquals(listOf(null, 5L, 9L), journal.asked.map { it.number(CURSOR) })
    }

    @Test
    fun aLaterReadStartsFromTheLastLineAndGivesOnlyTheOnesAdded() = session(Journal((1..10).toList())) { model, journal ->
        val run = model.lines(spec)
        run.read()
        journal.ids = (1..12).toList()
        val second = run.read()
        assertFalse(second.snapshot)
        assertEquals(listOf(line(11), line(12)), second.rows)
        assertEquals(10L, journal.asked.last().number(CURSOR))
        val third = run.read()
        assertFalse(third.snapshot)
        assertEquals(emptyList(), third.rows)
        assertEquals(12L, journal.asked.last().number(CURSOR))
    }

    @Test
    fun aLineThatLeftTheBufferStartsTheReadAgainFromItsFirstLineAsASnapshot() = session(Journal((1..10).toList())) { model, journal ->
        val run = model.lines(spec)
        run.read()
        journal.ids = (20..25).toList()
        val again = run.read()
        assertTrue(again.snapshot)
        assertEquals((20..25).map(::line), again.rows)
        journal.ids = (20..26).toList()
        val next = run.read()
        assertFalse(next.snapshot)
        assertEquals(listOf(line(26)), next.rows)
        assertEquals(25L, journal.asked.last().number(CURSOR))
    }

    @Test
    fun anEmptyLogGivesNoLinesWhetherTheRouterRefusesTheStartOrAnswersWithNoRows() = session(Journal(emptyList(), empty = true)) { model, journal ->
        val run = model.lines(spec)
        assertEquals(emptyList(), run.read().rows)
        journal.empty = false
        assertEquals(emptyList(), run.read().rows)
        journal.ids = listOf(1, 2)
        val read = run.read()
        assertTrue(read.snapshot)
        assertEquals(listOf(line(1), line(2)), read.rows)
    }

    @Test
    fun aRefusalOfTheRouterThatIsNotTheEndOfTheLinesIsAnError() = session(Journal((1..3).toList(), refusal = 0xFE0009)) { model, _ ->
        assertFailsWith<DeviceException> { model.lines(spec).read() }
    }
}
