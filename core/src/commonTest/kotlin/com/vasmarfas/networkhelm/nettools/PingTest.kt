package com.vasmarfas.networkhelm.nettools

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PingTest {

    // hops[n - 1] answers an echo with TTL n as an expired hop, null stays silent, and past the list the target answers
    private class Path(private val hops: List<String?>, private val target: String) : HostOps {
        val asked = mutableListOf<Pair<String, Int?>>()

        override suspend fun ping(host: String, timeoutMillis: Int, ttl: Int?, size: Int?, dontFragment: Boolean): PingReply {
            asked += host to ttl
            if (ttl == null || ttl > hops.size) return PingReply(12.5, 117, target)
            val hop = hops[ttl - 1] ?: return PingReply(null, null, null)
            return PingReply(1.0 * ttl, null, hop, expired = true)
        }

        override suspend fun wake(packet: ByteArray, broadcast: String, port: Int) = Unit

        override suspend fun resolve(host: String) = listOf(target)
    }

    @Test
    fun windowsRepliesInEnglishAndRussian() {
        val english = "\r\nPinging 8.8.8.8 with 32 bytes of data:\r\nReply from 8.8.8.8: bytes=32 time=12ms TTL=117\r\n\r\n" +
            "Ping statistics for 8.8.8.8:\r\n    Packets: Sent = 1, Received = 1, Lost = 0 (0% loss),\r\n"
        val reply = parsePing(english, null, ipv6 = false, elapsedMillis = 40.0)
        assertEquals(12.0, reply.timeMillis)
        assertEquals(117, reply.ttl)
        assertEquals("8.8.8.8", reply.from)
        val russian = parsePing("\r\nОбмен пакетами с 8.8.8.8 по с 32 байтами данных:\r\nОтвет от 8.8.8.8: число байт=32 время<1мс TTL=64\r\n", null, ipv6 = false, elapsedMillis = 40.0)
        assertEquals(1.0, russian.timeMillis)
        assertEquals(64, russian.ttl)
    }

    @Test
    fun aLocalizedTimeoutIsNotAReply() {
        val output = "\r\nОбмен пакетами с 192.168.1.77 по с 32 байтами данных:\r\nПревышен интервал ожидания для запроса.\r\n\r\nСтатистика Ping для 192.168.1.77:\r\n"
        val reply = parsePing(output, null, ipv6 = false, elapsedMillis = 1500.0)
        assertNull(reply.timeMillis)
        assertNull(reply.from)
        assertFalse(reply.fragmentationNeeded)
    }

    @Test
    fun anExpiredTtlNamesTheHop() {
        val windows = "\r\nОбмен пакетами с 8.8.8.8 по с 32 байтами данных:\r\nОтвет от 10.0.0.1: Превышен срок жизни (TTL) при передаче пакета.\r\n\r\nСтатистика Ping для 8.8.8.8:\r\n"
        assertEquals("10.0.0.1", parsePing(windows, 1, ipv6 = false, elapsedMillis = 30.0).from)
        val mac = "PING 8.8.8.8 (8.8.8.8): 56 data bytes\n36 bytes from 192.168.1.1: Time to live exceeded\n" +
            "Vr HL TOS  Len   ID Flg  off TTL Pro  cks      Src      Dst\n 4  5  00 5400 0f21   0 0000  01  01 7a0a 192.168.1.5  8.8.8.8\n\n" +
            "--- 8.8.8.8 ping statistics ---\n1 packets transmitted, 0 packets received, 100.0% packet loss\n"
        val hop = parsePing(mac, 1, ipv6 = false, elapsedMillis = 30.0)
        assertEquals("192.168.1.1", hop.from)
        assertTrue(hop.expired)
        assertFalse(hop.fragmentationNeeded)
    }

    @Test
    fun unixRepliesAndLoss() {
        val linux = "PING 8.8.8.8 (8.8.8.8) 56(84) bytes of data.\n64 bytes from 8.8.8.8: icmp_seq=1 ttl=117 time=12.3 ms\n\n" +
            "--- 8.8.8.8 ping statistics ---\n1 packets transmitted, 1 received, 0% packet loss, time 0ms\n"
        val reply = parsePing(linux, null, ipv6 = false, elapsedMillis = 40.0)
        assertEquals(12.3, reply.timeMillis)
        assertEquals(117, reply.ttl)
        assertFalse(reply.expired)
        val dead = "PING 10.0.0.99 (10.0.0.99): 56 data bytes\n\n--- 10.0.0.99 ping statistics ---\n1 packets transmitted, 0 packets received, 100.0% packet loss\n"
        assertNull(parsePing(dead, null, ipv6 = false, elapsedMillis = 1000.0).timeMillis)
    }

    @Test
    fun macAndAndroidRepliesCarryTheTimeTheTtlAndTheSender() {
        val mac = "PING 8.8.8.8 (8.8.8.8): 56 data bytes\n64 bytes from 8.8.8.8: icmp_seq=0 ttl=117 time=14.326 ms\n\n" +
            "--- 8.8.8.8 ping statistics ---\n1 packets transmitted, 1 packets received, 0.0% packet loss\nround-trip min/avg/max/stddev = 14.326/14.326/14.326/0.000 ms\n"
        val fromMac = parsePing(mac, null, ipv6 = false, elapsedMillis = 40.0)
        assertEquals(14.326, fromMac.timeMillis)
        assertEquals(117, fromMac.ttl)
        assertEquals("8.8.8.8", fromMac.from)
        val android = "PING 1.1.1.1 (1.1.1.1) 56(84) bytes of data.\n64 bytes from 1.1.1.1: icmp_seq=1 ttl=57 time=21.4 ms\n\n" +
            "--- 1.1.1.1 ping statistics ---\n1 packets transmitted, 1 received, 0% packet loss, time 0ms\nrtt min/avg/max/mdev = 21.419/21.419/21.419/0.000 ms\n"
        val fromAndroid = parsePing(android, null, ipv6 = false, elapsedMillis = 40.0)
        assertEquals(21.4, fromAndroid.timeMillis)
        assertEquals(57, fromAndroid.ttl)
    }

    @Test
    fun aWindowsIpv6ReplyHasNoTtl() {
        val output = "\r\nPinging 2001:4860:4860::8888 with 32 bytes of data:\r\nReply from 2001:4860:4860::8888: time=15ms\r\n\r\n"
        assertEquals(15.0, parsePing(output, null, ipv6 = true, elapsedMillis = 40.0).timeMillis)
    }

    @Test
    fun aUnixIpv6ReplyHasATtlAndNoIpv4Sender() {
        val output = "PING 2001:4860:4860::8888(2001:4860:4860::8888) 56 data bytes\n64 bytes from 2001:4860:4860::8888: icmp_seq=1 ttl=118 time=25.2 ms\n\n" +
            "--- 2001:4860:4860::8888 ping statistics ---\n1 packets transmitted, 1 received, 0% packet loss, time 0ms\n"
        val reply = parsePing(output, null, ipv6 = false, elapsedMillis = 40.0)
        assertEquals(25.2, reply.timeMillis)
        assertEquals(118, reply.ttl)
        assertNull(reply.from)
    }

    @Test
    fun aRouterThatCannotPassTheSizeIsNamedOnWindows() {
        val english = "\r\nPinging 10.9.0.1 with 1500 bytes of data:\r\nReply from 192.168.1.1: Packet needs to be fragmented but DF set.\r\n\r\n" +
            "Ping statistics for 10.9.0.1:\r\n    Packets: Sent = 1, Received = 1, Lost = 0 (0% loss),\r\n"
        val reply = parsePing(english, null, ipv6 = false, elapsedMillis = 40.0)
        assertNull(reply.timeMillis)
        assertTrue(reply.fragmentationNeeded)
        assertFalse(reply.expired)
        assertEquals("192.168.1.1", reply.from)
        val russian = "\r\nОбмен пакетами с 10.9.0.1 по с 1500 байтами данных:\r\nОтвет от 192.168.1.1: Требуется фрагментация пакета, но установлен запрещающий флаг.\r\n\r\n" +
            "Статистика Ping для 10.9.0.1:\r\n    Пакетов: отправлено = 1, получено = 1, потеряно = 0 (0% потерь),\r\n"
        val fromRussian = parsePing(russian, null, ipv6 = false, elapsedMillis = 40.0)
        assertTrue(fromRussian.fragmentationNeeded)
        assertEquals("192.168.1.1", fromRussian.from)
    }

    @Test
    fun aSizeThatTheOwnInterfaceCannotSendHasNoSender() {
        val windows = "\r\nPinging 8.8.8.8 with 1500 bytes of data:\r\nPacket needs to be fragmented but DF set.\r\n\r\n" +
            "Ping statistics for 8.8.8.8:\r\n    Packets: Sent = 1, Received = 0, Lost = 1 (100% loss),\r\n"
        val fromWindows = parsePing(windows, null, ipv6 = false, elapsedMillis = 40.0)
        assertTrue(fromWindows.fragmentationNeeded)
        assertNull(fromWindows.from)
        val linux = "PING 8.8.8.8 (8.8.8.8) 1500(1528) bytes of data.\nping: local error: message too long, mtu=1500\n\n" +
            "--- 8.8.8.8 ping statistics ---\n1 packets transmitted, 0 received, +1 errors, 100% packet loss, time 0ms\n"
        val fromLinux = parsePing(linux, null, ipv6 = false, elapsedMillis = 40.0)
        assertTrue(fromLinux.fragmentationNeeded)
        assertNull(fromLinux.from)
        val mac = "PING 8.8.8.8 (8.8.8.8): 1473 data bytes\nping: sendto: Message too long\n\n" +
            "--- 8.8.8.8 ping statistics ---\n1 packets transmitted, 0 packets received, 100.0% packet loss\n"
        assertTrue(parsePing(mac, null, ipv6 = false, elapsedMillis = 40.0).fragmentationNeeded)
    }

    @Test
    fun linuxAndroidAndMacNameTheRouterThatNeedsFragmentation() {
        val linux = "PING 10.9.0.1 (10.9.0.1) 1500(1528) bytes of data.\nFrom 192.168.1.1 icmp_seq=1 Frag needed and DF set (mtu = 1492)\n\n" +
            "--- 10.9.0.1 ping statistics ---\n1 packets transmitted, 0 received, +1 errors, 100% packet loss, time 0ms\n"
        val fromLinux = parsePing(linux, null, ipv6 = false, elapsedMillis = 40.0)
        assertNull(fromLinux.timeMillis)
        assertTrue(fromLinux.fragmentationNeeded)
        assertEquals("192.168.1.1", fromLinux.from)
        val android = "PING 10.20.30.40 (10.20.30.40) 1500(1528) bytes of data.\nFrom 10.20.30.1 icmp_seq=1 Frag needed and DF set (mtu = 1400)\n\n" +
            "--- 10.20.30.40 ping statistics ---\n1 packets transmitted, 0 received, +1 errors, 100% packet loss, time 0ms\n"
        assertEquals("10.20.30.1", parsePing(android, null, ipv6 = false, elapsedMillis = 40.0).from)
        val mac = "PING 10.9.0.1 (10.9.0.1): 1472 data bytes\n36 bytes from 192.168.1.1: frag needed and DF set (MTU 1492)\n" +
            "Vr HL TOS  Len   ID Flg  off TTL Pro  cks      Src      Dst\n 4  5  00 05dc 0f21   2 0000  40  01 7a0a 192.168.1.5  10.9.0.1\n\n" +
            "--- 10.9.0.1 ping statistics ---\n1 packets transmitted, 0 packets received, 100.0% packet loss\n"
        val fromMac = parsePing(mac, null, ipv6 = false, elapsedMillis = 40.0)
        assertTrue(fromMac.fragmentationNeeded)
        assertEquals("192.168.1.1", fromMac.from)
    }

    @Test
    fun anIpv6RouterSaysThePacketIsTooBig() {
        val output = "PING 2001:db8::1(2001:db8::1) 1452 data bytes\nFrom 2001:db8:0:1::1 icmp_seq=1 Packet too big: mtu=1280\n\n" +
            "--- 2001:db8::1 ping statistics ---\n1 packets transmitted, 0 received, +1 errors, 100% packet loss, time 0ms\n"
        val reply = parsePing(output, null, ipv6 = false, elapsedMillis = 40.0)
        assertNull(reply.timeMillis)
        assertTrue(reply.fragmentationNeeded)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun pingsComeOncePerIntervalAndAddUp() = runTest {
        val path = Path(emptyList(), "192.0.2.9")
        val replies = pings(4, 1_000) { path.ping("192.0.2.9", 3_000) }.toList()
        assertEquals(4, replies.size)
        assertTrue(currentTime in 2_900..3_000, "three gaps and no wait after the last echo, $currentTime ms")
        val summary = PingSummary(replies + PingReply(null, null, null))
        assertEquals(5, summary.sent)
        assertEquals(4, summary.received)
        assertEquals(1, summary.lost)
        assertEquals(20.0, summary.loss)
        assertEquals(12.5, summary.average)
        assertEquals(0.0, summary.jitter)
        assertNull(PingSummary(listOf(PingReply(null, null, null))).min)
        assertNull(PingSummary(emptyList()).average)
        assertEquals(0.0, PingSummary(emptyList()).loss)
        assertEquals("12.5", millis(12.5))
        assertEquals("0.3", millis(0.25))
        assertEquals("120.0", millis(119.99))
    }

    @Test
    fun theJitterIsTheDeviationOfTheTimesFromTheirAverage() {
        val summary = PingSummary(listOf(10.0, 20.0, 30.0).map { PingReply(it, 64, "192.0.2.9") })
        assertEquals(10.0, summary.min)
        assertEquals(20.0, summary.average)
        assertEquals(30.0, summary.max)
        assertEquals("8.2", millis(assertNotNull(summary.jitter)))
        assertEquals(0.0, PingSummary(listOf(PingReply(7.0, 64, null))).jitter)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun withoutACountThePingsGoOnUntilTheCollectorHasEnough() = runTest {
        var sent = 0
        val replies = pings(null, 500) { PingReply(1.0 * ++sent, 64, "192.0.2.9") }.take(5).toList()
        assertEquals(listOf(1.0, 2.0, 3.0, 4.0, 5.0), replies.map { it.timeMillis })
        assertEquals(5, sent)
        assertTrue(currentTime in 1_900..2_000, "four gaps of 500 ms, $currentTime ms")
    }

    @Test
    fun theTraceStopsAtTheTargetAndShowsSilentHopsAsUnknown() = runTest {
        val path = Path(listOf("10.0.0.1", "10.0.0.2", null, "10.0.0.4"), "192.0.2.9")
        val hops = traceroute(path, "192.0.2.9").toList()
        assertEquals(listOf(1, 2, 3, 4, 5), hops.map { it.number })
        assertEquals(listOf("10.0.0.1", "10.0.0.2", null, "10.0.0.4", "192.0.2.9"), hops.map { it.address })
        assertNull(hops[2].timeMillis)
        assertEquals(12.5, hops.last().timeMillis)
        assertEquals<List<Pair<String, Int?>>>((1..5).map { "192.0.2.9" to it }, path.asked)
    }

    @Test
    fun eightSilentHopsEndTheTrace() = runTest {
        val hops = traceroute(Path(listOf("10.0.0.1") + List(20) { null }, "192.0.2.9"), "192.0.2.9").toList()
        assertEquals(9, hops.size)
        assertTrue(hops.drop(1).all { it.address == null })
    }
}
