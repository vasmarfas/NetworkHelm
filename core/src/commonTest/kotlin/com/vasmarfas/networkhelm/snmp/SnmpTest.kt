package com.vasmarfas.networkhelm.snmp

import com.vasmarfas.networkhelm.model.DeviceException
import com.vasmarfas.networkhelm.model.DeviceInfo
import com.vasmarfas.networkhelm.model.Item
import com.vasmarfas.networkhelm.model.ListEvent
import com.vasmarfas.networkhelm.model.MenuPath
import com.vasmarfas.networkhelm.model.NodeKind
import com.vasmarfas.networkhelm.model.Platform
import com.vasmarfas.networkhelm.transport.UdpLink
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class SnmpTest {

    private class Agent(private val community: String = "rd-test", private val answers: Int = 64) : UdpLink {
        val values = mutableMapOf<Oid, ByteArray>()
        var requests = 0
        private val replies = ArrayDeque<ByteArray>()

        operator fun set(name: String, value: ByteArray) {
            values[oid(name)] = value
        }

        override suspend fun send(datagram: ByteArray) {
            requests++
            val message = children(datagram, 0, datagram.size).single()
            val (_, communityTlv, pdu) = children(datagram, message.start, message.end)
            if (datagram.copyOfRange(communityTlv.start, communityTlv.end).decodeToString() != community) return
            val (id, _, b, list) = children(datagram, pdu.start, pdu.end)
            val names = children(datagram, list.start, list.end).map { binding -> Oid(decode(datagram, children(datagram, binding.start, binding.end).first())) }
            val bindings = when (pdu.tag) {
                0xA0 -> names.map { it to (values[it] ?: byteArrayOf(0x81.toByte(), 0)) }
                else -> (0 until number(datagram, b).toInt()).flatMap { r ->
                    names.map { name ->
                        val next = values.keys.sorted().filter { it > name }.getOrNull(r)
                        if (next == null) name to byteArrayOf(0x82.toByte(), 0) else next to values.getValue(next)
                    }
                }.take(answers)
            }
            val body = tlv(0x02, datagram.copyOfRange(id.start, id.end)) + tlv(0x02, byteArrayOf(0)) + tlv(0x02, byteArrayOf(0)) +
                tlv(0x30, bindings.fold(ByteArray(0)) { all, (name, value) -> all + tlv(0x30, tlv(0x06, encode(name)) + value) })
            replies += tlv(0x30, tlv(0x02, byteArrayOf(1)) + tlv(0x04, community.encodeToByteArray()) + tlv(0xA2, body))
        }

        override suspend fun receive(timeoutMillis: Int): ByteArray? = replies.removeFirstOrNull()

        override fun close() {}

        private class Part(val tag: Int, val start: Int, val end: Int)

        private fun children(bytes: ByteArray, from: Int, until: Int): List<Part> = buildList {
            var at = from
            while (at < until) {
                var length = bytes[at + 1].toInt() and 0xFF
                var start = at + 2
                if (length and 0x80 != 0) {
                    val count = length and 0x7F
                    length = (0 until count).fold(0) { sum, i -> (sum shl 8) or (bytes[start + i].toInt() and 0xFF) }
                    start += count
                }
                add(Part(bytes[at].toInt() and 0xFF, start, start + length))
                at = start + length
            }
        }

        private fun number(bytes: ByteArray, part: Part) = (part.start until part.end).fold(0L) { sum, i -> (sum shl 8) or (bytes[i].toLong() and 0xFF) }

        private fun decode(bytes: ByteArray, part: Part): List<Long> {
            val arcs = mutableListOf((bytes[part.start].toLong() and 0xFF) / 40, (bytes[part.start].toLong() and 0xFF) % 40)
            var value = 0L
            for (i in part.start + 1 until part.end) {
                value = (value shl 7) or (bytes[i].toLong() and 0x7F)
                if (bytes[i].toInt() and 0x80 == 0) {
                    arcs += value
                    value = 0
                }
            }
            return arcs
        }

        private fun encode(oid: Oid) = (listOf(oid.arcs[0] * 40 + oid.arcs[1]) + oid.arcs.drop(2)).fold(ByteArray(0)) { all, arc ->
            val chunk = mutableListOf((arc and 0x7F).toByte())
            var rest = arc shr 7
            while (rest > 0) {
                chunk.add(0, ((rest and 0x7F) or 0x80).toByte())
                rest = rest shr 7
            }
            all + chunk.toByteArray()
        }
    }

    private val device = DeviceInfo(Platform.Snmp, "rd-test-wifi", "", "", "", emptySet(), setOf("read"))

    @Test
    fun aGetGoesAsTheBytesOfSnmpV2c() {
        val bytes = request("public", pdu(0xA0, 4242, 0, 0, listOf(oid("1.3.6.1.2.1.1.5.0"), oid("1.3.6.1.2.1.1.1.0"), oid("1.3.6.1.2.1.1.3.0"))))
        val expected = "304302010104067075626c6963a03602021092020100020100302a300c06082b060102010105000500300c06082b060102010101000500" +
            "300c06082b060102010103000500"
        assertEquals(expected, bytes.joinToString("") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') })
        val lldp = request("public", pdu(0xA5, -2, 0, 10, listOf(oid("1.0.8802.1.1.2.1.4.1.1.9"))))
        assertContentEquals(byteArrayOf(0x06, 0x0B, 0x28, 0xC4.toByte(), 0x62, 0x01, 0x01, 0x02, 0x01, 0x04, 0x01, 0x01, 0x09), lldp.copyOfRange(lldp.size - 15, lldp.size - 2))
    }

    @Test
    fun columnsAreReadToTheirEndAcrossShortenedAnswers() = runTest {
        val agent = Agent(answers = 3)
        for (i in 1..5) {
            agent["1.3.6.1.2.1.31.1.1.1.1.$i"] = text("eth$i")
            agent["1.3.6.1.2.1.2.2.1.3.$i"] = integer(6)
        }
        agent["1.3.6.1.2.1.31.1.1.1.2.1"] = integer(0)
        val client = SnmpClient(agent, "rd-test")
        val columns = client.columns(listOf(oid("1.3.6.1.2.1.31.1.1.1.1"), oid("1.3.6.1.2.1.2.2.1.3")))
        assertEquals((1..5).map { listOf(it.toLong()) }, columns.getValue(oid("1.3.6.1.2.1.31.1.1.1.1")).keys.toList())
        assertEquals(listOf("eth1", "eth5"), listOf(1L, 5L).map { text(columns.getValue(oid("1.3.6.1.2.1.31.1.1.1.1"))[listOf(it)]) })
        assertEquals(5, columns.getValue(oid("1.3.6.1.2.1.2.2.1.3")).size)
    }

    @Test
    fun aWrongCommunityGetsNoAnswer() = runTest {
        val agent = Agent()
        agent["1.3.6.1.2.1.1.5.0"] = text("rd-test-wifi")
        val failure = assertFailsWith<DeviceException> { SnmpClient(agent, "public", tries = 2).get(listOf(oid("1.3.6.1.2.1.1.5.0"))) }
        assertTrue("community" in failure.message.orEmpty(), failure.message)
        assertEquals(2, agent.requests)
    }

    private fun router() = Agent().apply {
        for ((index, name, type, mac) in listOf(
            listOf("1", "lo", 24, ""),
            listOf("2", "eth0", 6, "00:00:5e:00:53:19"),
            listOf("4", "br-lan", 6, "00:00:5e:00:53:18"),
        )) {
            this["1.3.6.1.2.1.2.2.1.2.$index"] = text(name as String)
            this["1.3.6.1.2.1.2.2.1.3.$index"] = integer((type as Int).toLong())
            this["1.3.6.1.2.1.2.2.1.6.$index"] = tlv(0x04, (mac as String).split(':').filter { it.isNotEmpty() }.map { it.toInt(16).toByte() }.toByteArray())
            this["1.3.6.1.2.1.2.2.1.7.$index"] = integer(1)
            this["1.3.6.1.2.1.2.2.1.8.$index"] = integer(if (name == "br-lan") 2 else 1)
            this["1.3.6.1.2.1.31.1.1.1.1.$index"] = text(name)
            this["1.3.6.1.2.1.31.1.1.1.6.$index"] = number(0x46, 1_000)
            this["1.3.6.1.2.1.31.1.1.1.10.$index"] = number(0x46, 2_000)
            this["1.3.6.1.2.1.31.1.1.1.15.$index"] = number(0x42, if (name == "eth0") 1_000 else 0)
        }
        this["1.3.6.1.2.1.4.34.1.3.1.4.198.51.100.82"] = integer(2)
        this["1.3.6.1.2.1.4.34.1.3.1.4.198.51.100.255"] = integer(2)
        this["1.3.6.1.2.1.4.34.1.3.2.16.254.128.0.0.0.0.0.0.130.175.202.255.254.204.188.24"] = integer(4)
        this["1.3.6.1.2.1.4.34.1.4.1.4.198.51.100.82"] = integer(1)
        this["1.3.6.1.2.1.4.34.1.4.1.4.198.51.100.255"] = integer(3)
        this["1.3.6.1.2.1.4.34.1.4.2.16.254.128.0.0.0.0.0.0.130.175.202.255.254.204.188.24"] = integer(1)
        this["1.3.6.1.2.1.4.34.1.5.1.4.198.51.100.82"] = id("1.3.6.1.2.1.4.32.1.5.2.1.4.198.51.100.0.24")
        this["1.3.6.1.2.1.4.34.1.5.2.16.254.128.0.0.0.0.0.0.130.175.202.255.254.204.188.24"] = id("1.3.6.1.2.1.4.32.1.5.4.2.16.254.128.0.0.0.0.0.0.0.0.0.0.0.0.0.0.64")
        this["1.3.6.1.2.1.4.34.1.6.1.4.198.51.100.82"] = integer(4)
        this["1.3.6.1.2.1.4.24.7.1.7.1.4.0.0.0.0.0.2.0.0.1.4.198.51.100.1"] = integer(2)
        this["1.3.6.1.2.1.4.24.7.1.7.1.4.198.51.100.0.24.3.0.0.2.1.4.0.0.0.0"] = integer(2)
        this["1.3.6.1.2.1.4.24.7.1.9.1.4.0.0.0.0.0.2.0.0.1.4.198.51.100.1"] = integer(3)
        this["1.3.6.1.2.1.4.24.7.1.9.1.4.198.51.100.0.24.3.0.0.2.1.4.0.0.0.0"] = integer(2)
        this["1.3.6.1.2.1.4.35.1.4.2.1.4.198.51.100.10"] = tlv(0x04, "6Bad!4".encodeToByteArray())
        this["1.3.6.1.2.1.4.35.1.6.2.1.4.198.51.100.10"] = integer(3)
        this["1.3.6.1.2.1.4.35.1.7.2.1.4.198.51.100.10"] = integer(1)
    }

    @Test
    fun interfacesAddressesRoutesAndNeighboursComeInTheNamesOfRouterOs() = runTest {
        val session = SnmpSession(SnmpClient(router(), "rd-test"), device)
        val interfaces = (session.watch(MenuPath.parse("interface")).first() as ListEvent.Snapshot).items
        assertEquals(listOf("lo", "eth0", "br-lan"), interfaces.map { it["name"] })
        val eth0 = interfaces[1]
        assertEquals(listOf("2", "ether", "00:00:5E:00:53:19", "1Gbps", "true", "1000"), listOf(eth0.id, eth0["type"], eth0["mac-address"], eth0["speed"], eth0["running"], eth0["rx-byte"]))
        assertEquals(listOf("loopback", "", "down", "false"), listOf(interfaces[0]["type"], interfaces[0]["mac-address"], interfaces[2]["status"], interfaces[2]["running"]))

        val addresses = (session.watch(MenuPath.parse("ip/address")).first() as ListEvent.Snapshot).items
        assertEquals(listOf("198.51.100.82/24"), addresses.map { it["address"] })
        assertEquals(listOf("198.51.100.0", "eth0", "dhcp", "true"), listOf("network", "interface", "origin", "dynamic").map { addresses.single()[it] })
        assertEquals("fe80::82af:caff:fecc:bc18/64", session.get(MenuPath.parse("ipv6/address"), null)?.get("address"))

        val routes = (session.watch(MenuPath.parse("ip/route")).first() as ListEvent.Snapshot).items.associateBy { it["dst-address"] }
        assertEquals(listOf("198.51.100.1", "eth0", "static", "true"), listOf("gateway", "interface", "protocol", "static").map { routes.getValue("0.0.0.0/0")[it] })
        assertEquals(listOf("", "local", "true"), listOf("gateway", "protocol", "connect").map { routes.getValue("198.51.100.0/24")[it] })

        val arp = session.get(MenuPath.parse("ip/arp"), null)
        assertEquals(listOf("198.51.100.10", "36:42:61:64:21:34", "eth0", "reachable", "true"), listOf("address", "mac-address", "interface", "status", "dynamic").map { arp?.get(it) })
        assertEquals(0, session.count(MenuPath.parse("ipv6/neighbor")))
    }

    @Test
    fun menusTheAgentLacksAreMissing() = runTest {
        val session = SnmpSession(SnmpClient(router(), "rd-test"), device)
        assertEquals(listOf("interface", "ip", "ipv6", "system"), session.describe(MenuPath.Root).children)
        assertEquals(NodeKind.Settings, session.describe(MenuPath.parse("system/resource")).kind)
        assertEquals(listOf("print", "monitor-traffic"), session.describe(MenuPath.parse("interface")).commands)
        assertFailsWith<DeviceException> { session.describe(MenuPath.parse("ip/neighbor")) }
        assertFailsWith<DeviceException> { session.describe(MenuPath.parse("system/storage")) }
        assertFailsWith<DeviceException> { session.set(MenuPath.parse("interface"), "2", mapOf("comment" to "rd-test")) }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun aRateGoesFromTheLastMoveOfTheCounters() = runTest {
        val agent = router()
        agent["1.3.6.1.2.1.31.1.1.1.6.2"] = number(0x46, -1_000)
        val session = SnmpSession(SnmpClient(agent, "rd-test"), device, time = testScheduler.timeSource)
        val rounds = mutableListOf<Item>()
        backgroundScope.launch { session.run(MenuPath.parse("interface"), "monitor-traffic", mapOf("interface" to "eth0")).collect { rounds += it } }
        advanceTimeBy(500)
        assertTrue(rounds.isEmpty())
        agent["1.3.6.1.2.1.31.1.1.1.6.2"] = number(0x46, 124_000)
        advanceTimeBy(4_000)
        assertEquals(List(4) { "1000000" }, rounds.map { it["rx-bits-per-second"] })
        assertEquals(listOf("0", "1", "2", "3"), rounds.map { it[".section"] })
        assertTrue(rounds.all { it["name"] == "eth0" && it["tx-bits-per-second"] == "0" })
        advanceTimeBy(8_000)
        assertEquals("0", rounds.last()["rx-bits-per-second"])
    }

    private companion object {
        fun tlv(tag: Int, content: ByteArray): ByteArray = byteArrayOf(tag.toByte()) + when {
            content.size < 0x80 -> byteArrayOf(content.size.toByte())
            content.size < 0x100 -> byteArrayOf(0x81.toByte(), content.size.toByte())
            else -> byteArrayOf(0x82.toByte(), (content.size shr 8).toByte(), content.size.toByte())
        } + content

        fun text(value: String) = tlv(0x04, value.encodeToByteArray())

        fun integer(value: Long) = number(0x02, value)

        fun number(tag: Int, value: Long): ByteArray {
            val bytes = ByteArray(8) { (value shr (56 - it * 8)).toByte() }.dropWhile { it == 0.toByte() }.toByteArray()
            return tlv(tag, if (bytes.isEmpty() || bytes[0] < 0) byteArrayOf(0) + bytes else bytes)
        }

        fun id(value: String): ByteArray {
            val arcs = value.split('.').map(String::toLong)
            return tlv(0x06, (listOf(arcs[0] * 40 + arcs[1]) + arcs.drop(2)).fold(ByteArray(0)) { all, arc ->
                val chunk = mutableListOf((arc and 0x7F).toByte())
                var rest = arc shr 7
                while (rest > 0) {
                    chunk.add(0, ((rest and 0x7F) or 0x80).toByte())
                    rest = rest shr 7
                }
                all + chunk.toByteArray()
            })
        }
    }
}
