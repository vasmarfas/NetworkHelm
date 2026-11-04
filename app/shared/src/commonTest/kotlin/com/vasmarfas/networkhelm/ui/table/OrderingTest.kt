package com.vasmarfas.networkhelm.ui.table

import kotlin.test.Test
import kotlin.test.assertEquals

class OrderingTest {

    @Test
    fun numbersInsideTextCompareAsNumbers() {
        val values = listOf("ether10", "", "ether2", "Ether1", "192.168.1.10", "192.168.1.9", "bridge", "10.0.0.1", "ether02")
        assertEquals(
            listOf("10.0.0.1", "192.168.1.9", "192.168.1.10", "bridge", "Ether1", "ether2", "ether02", "ether10", ""),
            values.sortedWith(naturalOrder),
        )
    }

    @Test
    fun limitsRatesDurationsAndDatesCompareByTheirValue() {
        fun ordered(values: List<String>) = values.sortedBy { cellKey(it) }
        assertEquals(listOf("", "1500", "100k", "9M", "10M"), ordered(listOf("10M", "100k", "9M", "1500", "")))
        assertEquals(listOf("0 bps", "9.5 kbps", "71.0 kbps", "1.2 Mbps"), ordered(listOf("71.0 kbps", "1.2 Mbps", "0 bps", "9.5 kbps")))
        assertEquals(listOf("106.8 MiB", "5.1 GiB"), ordered(listOf("5.1 GiB", "106.8 MiB")))
        assertEquals(listOf("00:00:05", "30s", "1m", "1h", "1d", "1w"), ordered(listOf("1w", "1d", "1h", "1m", "30s", "00:00:05")))
        assertEquals(
            listOf("", "nov/03/2026 23:59:59", "2026-11-04 11:48:30", "2026-11-04 11:48:31"),
            ordered(listOf("2026-11-04 11:48:31", "2026-11-04 11:48:30", "nov/03/2026 23:59:59", "")),
        )
        assertEquals(listOf("", "4", "9", "10", "bridge", "ether2", "ether10"), ordered(listOf("ether10", "10", "bridge", "", "ether2", "9", "4")))
    }

    @Test
    fun addressesKeepTheirOctetOrder() {
        val values = listOf("10.99.1.1", "10.99.0.100", "10.99.0.9", "192.168.35.1/24", "10.99.0.10")
        assertEquals(listOf("10.99.0.9", "10.99.0.10", "10.99.0.100", "10.99.1.1", "192.168.35.1/24"), values.sortedBy { cellKey(it) })
    }

    @Test
    fun descendingIsTheExactReverseOfAscendingWithEqualRowsReversed() {
        val rows = listOf("a" to "1", "b" to "", "c" to "1", "d" to "2", "e" to "")
        val columns = listOf(TableColumn<Pair<String, String>>("n", "N", null) { it.second })
        val up = sorted(rows, columns, listOf(SortKey("n", true)))
        val down = sorted(rows, columns, listOf(SortKey("n", false)))
        assertEquals(listOf("b", "e", "a", "c", "d"), up.map { it.first })
        assertEquals(up.reversed(), down)
    }

    @Test
    fun aHeaderTurnsAscendingDescendingAscendingAndNeverUnsorted() {
        val state = TableState<String>()
        fun order() = state.sort.map { it.column to it.ascending }
        state.toggle("a", false)
        assertEquals(listOf("a" to true), order())
        state.toggle("a", false)
        assertEquals(listOf("a" to false), order())
        state.toggle("a", false)
        assertEquals(listOf("a" to true), order())
        state.toggle("b", true)
        state.toggle("b", true)
        assertEquals(listOf("a" to true, "b" to false), order())
        state.toggle("a", false)
        assertEquals(listOf("a" to false), order())
    }

    @Test
    fun clicksWithCtrlAndShiftSelectLikeADesktopTable() {
        val order = listOf("a", "b", "c", "d", "e")
        var state: Pair<Set<String>, String?> = emptySet<String>() to null
        state = select(state.first, state.second, "b", order, ctrl = false, shift = false)
        assertEquals(setOf("b"), state.first)
        state = select(state.first, state.second, "d", order, ctrl = false, shift = true)
        assertEquals(setOf("b", "c", "d"), state.first)
        state = select(state.first, state.second, "c", order, ctrl = true, shift = false)
        assertEquals(setOf("b", "d"), state.first)
        state = select(state.first, state.second, "e", order, ctrl = true, shift = true)
        assertEquals(setOf("b", "c", "d", "e"), state.first)
        state = select(state.first, state.second, "a", order, ctrl = false, shift = false)
        assertEquals(setOf("a") to "a", state)
    }

    @Test
    fun droppedRowsGoBeforeTheFirstRowLeftAfterTheGap() {
        val order = listOf("a", "b", "c", "d")
        assertEquals(listOf("c") to "a", moveTo(order, setOf("c"), 0))
        assertEquals(listOf("a", "c") to null, moveTo(order, setOf("a", "c"), 4))
        assertEquals(listOf("b") to "d", moveTo(order, setOf("b"), 3))
        assertEquals(listOf("a") to "d", moveTo(order, setOf("a"), 3))
        assertEquals(null, moveTo(order, setOf("b", "c"), 2), "a gap inside the moved rows")
        assertEquals(null, moveTo(order, setOf("b"), 1), "dropped where it was")
        assertEquals(null, moveTo(order, setOf("b"), 2), "dropped right after itself")
        assertEquals(null, moveTo(order, emptySet(), 2))
    }
}
