package com.vasmarfas.networkhelm.ui.main

import com.vasmarfas.networkhelm.model.Item
import kotlin.test.Test
import kotlin.test.assertEquals

class CommandTest {

    private fun hop(section: String, address: String) = Item("", mapOf(".section" to section, "address" to address))

    @Test
    fun aNewSectionReplacesTheTableAndPlainRowsAddUp() {
        var rows = emptyList<Item>()
        for (item in listOf(hop("0", "10.0.0.1"), hop("0", "10.0.0.2"), hop("1", "10.0.0.1"))) rows = collected(rows, item)
        assertEquals(listOf("10.0.0.1"), rows.map { it["address"] }, "the second round of traceroute starts over")

        rows = emptyList()
        repeat(1005) { rows = collected(rows, Item("", mapOf("seq" to "$it"))) }
        assertEquals(1000, rows.size)
        assertEquals("1004", rows.last()["seq"])
    }

    @Test
    fun aFlowOfTorchStaysThreeTablesInItsPlace() {
        val flows = listOf("src-address", "dst-address")
        fun flow(section: Int, source: String) = Item("", mapOf(".section" to "$section", "src-address" to source, "dst-address" to "192.0.2.1", "tx" to "300"))
        var rows = emptyList<Item>()
        for (item in listOf(flow(0, "10.0.0.1"), flow(0, "10.0.0.2"), flow(1, "10.0.0.2"))) rows = collected(rows, item, flows)
        assertEquals(listOf("10.0.0.1" to "0", "10.0.0.2" to "1"), rows.map { it["src-address"] to it[".section"] })
        for (section in 2..4) rows = collected(rows, flow(section, "10.0.0.2"), flows)
        assertEquals(listOf("10.0.0.2"), rows.map { it["src-address"] }, "three tables without it are the end of the first flow")
    }

    @Test
    fun aHopOfTracerouteKeepsItsReplies() {
        fun reply(section: Int, address: String, last: String) = Item("", mapOf(".section" to "$section", "address" to address, "last" to last))
        val kept = HashMap<Int, MutableList<String>>()
        var rows = emptyList<Item>()
        for (item in listOf(reply(0, "10.0.0.1", "1"), reply(0, "10.0.0.2", "4"), reply(1, "10.0.0.1", "2"), reply(1, "10.0.0.2", "timeout"))) {
            rows = collected(rows, withHistory(rows, item, "last", kept))
        }
        assertEquals(listOf("▅█", "█×"), rows.map { it["history"] })
        assertEquals("▁", bars(listOf("0")))
    }
}
