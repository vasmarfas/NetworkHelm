package com.vasmarfas.networkhelm.routeros.api

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RoundsTest {
    private val asked = mutableListOf<List<String>>()

    private fun drive(vararg words: String, answer: (Int) -> List<Map<String, String>>): Flow<ApiReply>? =
        rounds(words.asList()) { sent ->
            asked += sent
            answer(asked.size - 1)
        }

    @Test
    fun aMonitorIsAskedForOneSnapshotARoundAndOnceGivenLeavesItAlone() = runTest {
        val replies = checkNotNull(drive("/interface/monitor-traffic", "=interface=ether1") { listOf(mapOf("name" to "ether1")) }).take(3).toList()
        assertEquals(List(3) { listOf("/interface/monitor-traffic", "=interface=ether1", "=once=") }, asked)
        assertEquals(listOf("0", "1", "2"), replies.map { it.attributes[".section"] })
        assertNull(drive("/interface/monitor-traffic", "=interface=ether1", "=once=") { emptyList() })
    }

    @Test
    fun aProfileIsAskedThreeSecondsAtATimeAndEveryRoundIsASection() = runTest {
        val table = listOf(mapOf("name" to "total", "usage" to "0"), mapOf("name" to "ipc", "usage" to "1"))
        val replies = checkNotNull(drive("/tool/profile") { table }).take(5).toList()
        assertEquals(List(3) { listOf("/tool/profile", "=duration=3") }, asked)
        assertEquals(listOf("0", "0", "1", "1", "2"), replies.map { it.attributes[".section"] })
        assertEquals(listOf("total", "ipc", "total", "ipc", "total"), replies.map { it.attributes["name"] })
    }

    @Test
    fun ofTheSecondsOfARunTheLastIsTheTableOfTheRound() = runTest {
        val run = listOf(
            mapOf(".section" to "0", "name" to "total", "usage" to "0"),
            mapOf(".section" to "1", "name" to "total", "usage" to "0"),
            mapOf(".section" to "2", "name" to "total", "usage" to "2"),
            mapOf(".section" to "2", "name" to "ipc", "usage" to "1"),
        )
        val replies = checkNotNull(drive("/tool/profile") { run }).take(4).toList()
        assertEquals(listOf("total", "ipc", "total", "ipc"), replies.map { it.attributes["name"] })
        assertEquals(listOf("0", "0", "1", "1"), replies.map { it.attributes[".section"] })
    }

    @Test
    fun aDurationGivenBoundsTheRoundsAndOneThatIsNoTimeIsLeftToTheRouter() = runTest {
        val replies = checkNotNull(drive("/tool/torch", "=interface=ether1", "=duration=3s") { listOf(mapOf("tx" to "1")) }).toList()
        assertEquals(2, replies.size)
        assertEquals(List(2) { listOf("/tool/torch", "=interface=ether1", "=duration=2") }, asked)
        assertNull(drive("/tool/torch", "=duration=soon") { emptyList() })
    }

    @Test
    fun aProfileThatTheLastWindowLeftRunningIsWaitedFor() = runTest {
        val replies = checkNotNull(
            drive("/tool/profile") { sent ->
                if (sent < 2) throw ApiTrapException(4, "failure: profile already running")
                listOf(mapOf("name" to "total"))
            },
        ).take(1).toList()
        assertEquals(1, replies.size)
        assertEquals(3, asked.size)
        assertFailsWith<ApiTrapException> { checkNotNull(drive("/tool/profile") { throw ApiTrapException(0, "no such command") }).take(1).toList() }
    }

    @Test
    fun aPingIsCountedHereRoundByRound() = runTest {
        val answers = listOf(
            mapOf(".id" to "*0", "host" to "10.0.0.1", "seq" to "0", "size" to "56", "ttl" to "64", "time" to "00:00:00.000061"),
            mapOf(".id" to "*0", "host" to "10.0.0.1", "seq" to "0", "status" to "timeout"),
            mapOf("host" to "10.0.0.1", "seq" to "0", "size" to "56", "ttl" to "64", "time" to "1ms234us", "sent" to "1"),
        )
        val replies = checkNotNull(drive("/tool/ping", "=address=10.0.0.1", "=count=3", "=interval=2s") { listOf(answers[it]) }).toList().map { it.attributes }
        assertEquals(List(3) { listOf("/tool/ping", "=address=10.0.0.1", "=count=1") }, asked)
        assertEquals(
            mapOf(
                "host" to "10.0.0.1", "seq" to "0", "size" to "56", "ttl" to "64", "time" to "61us", "sent" to "1", "received" to "1", "packet-loss" to "0",
                "min-rtt" to "61us", "avg-rtt" to "61us", "max-rtt" to "61us",
            ),
            replies[0],
        )
        assertEquals(
            mapOf(
                "host" to "10.0.0.1", "seq" to "1", "status" to "timeout", "sent" to "2", "received" to "1", "packet-loss" to "50",
                "min-rtt" to "61us", "avg-rtt" to "61us", "max-rtt" to "61us",
            ),
            replies[1],
        )
        assertEquals(
            mapOf(
                "host" to "10.0.0.1", "seq" to "2", "size" to "56", "ttl" to "64", "time" to "1ms234us", "sent" to "3", "received" to "2", "packet-loss" to "33",
                "min-rtt" to "61us", "avg-rtt" to "647us", "max-rtt" to "1ms234us",
            ),
            replies[2],
        )
        assertNull(drive("/tool/ping", "=address=10.0.0.1", "=count=many") { emptyList() })
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun aPingWithoutACountGoesOnAtItsInterval() = runTest {
        val replies = checkNotNull(drive("/tool/ping", "=address=10.0.0.1", "=interval=500ms") { listOf(mapOf("time" to "50us")) }).take(3).toList()
        assertEquals(listOf("0", "1", "2"), replies.map { it.attributes["seq"] })
        assertEquals(List(3) { listOf("/tool/ping", "=address=10.0.0.1", "=count=1") }, asked)
        assertTrue(currentTime in 900L..1_000L, "$currentTime")
    }

    @Test
    fun aTimeIsReadAsTheApiAndTheConsoleWriteIt() {
        assertEquals(61L, micros("61us"))
        assertEquals(1_234L, micros("1ms234us"))
        assertEquals(500_000L, micros("500ms"))
        assertEquals(2_000_000L, micros("2s"))
        assertEquals(61L, micros("00:00:00.000061"))
        assertEquals(1_500_000L, micros("00:00:01.5"))
        assertNull(micros("soon"))
        assertEquals("61us", routerTime(61))
        assertEquals("1ms234us", routerTime(1_234))
        assertEquals("1s500ms", routerTime(1_500_000))
        assertEquals("0us", routerTime(0))
    }
}
