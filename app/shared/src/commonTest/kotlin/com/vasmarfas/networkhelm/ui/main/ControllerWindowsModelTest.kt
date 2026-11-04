package com.vasmarfas.networkhelm.ui.main

import com.vasmarfas.networkhelm.model.Item
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ControllerWindowsModelTest {

    private fun item(id: String, vararg values: Pair<String, String>) = Item(id, mapOf(*values))

    @Test
    fun onlyAPairedDeviceThatAnswersCountsAsOnline() {
        val devices = listOf(
            item("*1", "pairing-status" to "paired", "connected" to "true"),
            item("*2", "pairing-status" to "paired", "connected" to "false"),
            item("*3", "pairing-status" to "pending", "connected" to "true"),
            item("*4", "pairing-status" to "paired", "connected" to "false", "controller" to "true"),
        )
        val counts = deviceCounts(devices)
        assertEquals(4, counts.total)
        assertEquals(3, counts.paired)
        assertEquals(2, counts.online)
    }

    @Test
    fun aRouterThatNamesNoStatusMarksTheWaitingOnesByFlags() {
        val counts = deviceCounts(listOf(item("*1", "connected" to "true"), item("*2", "pending" to "true"), item("*3", "remote-pending" to "true")))
        assertEquals(1, counts.paired)
        assertEquals(1, counts.online)
    }

    @Test
    fun anAlertIsActiveWhereItFiresOnADevice() {
        val rules = listOf(
            item("*1", "name" to "memory", "devices-on" to "0"),
            item("*2", "name" to "disk", "devices-on" to "2"),
            item("*3", "name" to "cpu", "devices-on" to "1"),
            item("*4", "name" to "unread", "devices-on" to "many"),
            item("*5", "name" to "none"),
        )
        assertEquals(listOf("cpu", "disk"), activeAlerts(rules).map { it["name"] })
    }

    @Test
    fun theUpdatesAreTheDevicesThatHaveOne() {
        val devices = listOf(
            item("*1", "identity" to "b", "upgrade-available" to "true"),
            item("*2", "identity" to "c", "upgrade-available" to "false"),
            item("*3", "identity" to "a", "upgrade-available" to "true"),
        )
        assertEquals(listOf("a", "b"), availableUpdates(devices).map { it["identity"] })
    }

    @Test
    fun theThroughputAddsWhatTheInterfacesReceiveAndSend() {
        val rows = listOf(
            item("#0", "bandwidth-rx" to "1000", "bandwidth-tx" to "300"),
            item("#1", "bandwidth-rx" to "2500", "bandwidth-tx" to "200"),
            item("#2", "bandwidth-rx" to "idle", "bandwidth-tx" to "5"),
            item("#3"),
        )
        val rates = throughput(rows)
        assertEquals(3500.0, rates.download)
        assertEquals(505.0, rates.upload)
    }

    @Test
    fun aShareReadsFromAPercentageAndNothingElse() {
        assertEquals(0.41f, share("41%"))
        assertEquals(0.5f, share("50%"))
        assertEquals(1f, share("140%"))
        assertEquals(0f, share("0"))
        assertNull(share("n/a"))
        assertNull(share(null))
    }

    @Test
    fun theBusiestApplicationsComeFirstAndAreEight() {
        val rows = (1..12).map { item("*$it", "name" to "app$it", "last-hour-in" to "${it * 100}", "last-hour-out" to "${it}") } + item("*13", "last-hour-in" to "99999")
        val shown = busiest(rows)
        assertEquals(8, shown.size)
        assertEquals("app12", shown.first()["name"])
        assertEquals("app5", shown.last()["name"])
        assertEquals(107799.0, summed(rows, "last-hour-in"))
        assertEquals(78.0, summed(rows, "last-hour-out"))
    }

    @Test
    fun valuesAreCountedByTheirTextMostFrequentFirst() {
        val rows = listOf(
            item("*1", "channel" to "b"),
            item("*2", "channel" to "a"),
            item("*3", "channel" to "b"),
            item("*4", "channel" to ""),
            item("*5"),
            item("*6", "channel" to "c"),
        )
        assertEquals(listOf("b" to 2, "a" to 1, "c" to 1), counted(rows, "channel"))
    }

    @Test
    fun aCoordinateOfTheRouterIsASignedNumberKeptUnsigned() {
        assertEquals(-24, coordinate("4294967272"))
        assertEquals(-35, coordinate("4294967261"))
        assertEquals(300, coordinate("300"))
        assertEquals(-113, coordinate("-113"))
        assertNull(coordinate("north"))
        assertNull(coordinate(null))
    }

    @Test
    fun aTopologyKeepsTheNodesOfItsLayoutAndTheLinksBetweenThem() {
        val nodes = listOf(
            item("*1", "name" to "core", "layout" to "main", "device" to "core@192.0.2.1", "x" to "100", "y" to "4294967261"),
            item("*2", "name" to "ap", "layout" to "main", "device" to "ap@192.0.2.2", "x" to "4294967272", "y" to "20"),
            item("*3", "name" to "upstairs", "layout" to "main", "target-layout" to "floor", "x" to "0", "y" to "0"),
            item("*4", "name" to "lost", "layout" to "main", "x" to "left", "y" to "0"),
            item("*5", "name" to "switch", "layout" to "floor", "device" to "sw", "x" to "1", "y" to "1"),
        )
        val links = listOf(
            item("*1", "layout" to "main", "node1" to "core", "node2" to "ap"),
            item("*2", "layout" to "main", "node1" to "core", "node2" to "upstairs"),
            item("*3", "layout" to "main", "node1" to "core", "node2" to "lost"),
            item("*4", "layout" to "floor", "node1" to "switch", "node2" to "core"),
        )
        val topology = topology("main", nodes, links)
        assertEquals(listOf("core", "ap", "upstairs"), topology.nodes.map { it.name })
        assertEquals(listOf(-35, 20, 0), topology.nodes.map { it.y })
        assertEquals(-24, topology.nodes[1].x)
        assertEquals("floor", topology.nodes[2].target)
        assertNull(topology.nodes[2].device)
        assertEquals(listOf("core" to "ap", "core" to "upstairs"), topology.links.map { (from, to) -> from.name to to.name })
    }
}
