package com.vasmarfas.networkhelm.nettools

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PathMtuTest {

    // the path passes an echo whose payload is at most mtu - 28; a larger one is answered by a router or dropped without a word
    private class Path(private val mtu: Int, private val answers: Boolean) : HostOps {
        val sizes = mutableListOf<Int?>()

        override suspend fun ping(host: String, timeoutMillis: Int, ttl: Int?, size: Int?, dontFragment: Boolean): PingReply {
            assertTrue(dontFragment, "the search sends nothing that may be fragmented")
            sizes += size
            return when {
                size != null && size <= mtu - 28 -> PingReply(3.0, 64, host)
                answers -> PingReply(null, null, "10.0.0.1", fragmentationNeeded = true)
                else -> PingReply(null, null, null)
            }
        }

        override suspend fun wake(packet: ByteArray, broadcast: String, port: Int) = Unit

        override suspend fun resolve(host: String) = listOf(host)
    }

    @Test
    fun theSearchFindsTheMtuOfThePath() = runTest {
        assertEquals(1400, pathMtu(Path(1400, answers = true), "192.0.2.9"))
        assertEquals(1492, pathMtu(Path(1492, answers = true), "192.0.2.9"))
        assertEquals(576, pathMtu(Path(576, answers = true), "192.0.2.9"))
    }

    @Test
    fun aFullEthernetPathNeedsTwoEchoes() = runTest {
        val path = Path(1500, answers = true)
        assertEquals(1500, pathMtu(path, "192.0.2.9"))
        assertEquals<List<Int?>>(listOf(548, 1472), path.sizes)
    }

    @Test
    fun aPathThatDropsTheBigOnesWithoutAnswerIsAskedTwice() = runTest {
        val path = Path(1400, answers = false)
        assertEquals(1400, pathMtu(path, "192.0.2.9"))
        assertTrue(path.sizes.count { it == 1472 } == 2, "the first miss is not trusted: ${path.sizes}")
    }

    @Test
    fun aHostThatDoesNotAnswerHasNoMtu() = runTest {
        assertNull(pathMtu(Path(100, answers = false), "192.0.2.9"))
        assertNull(pathMtu(Path(100, answers = true), "192.0.2.9"))
    }

    @Test
    fun theMtuTellsWhatShrinksThePackets() {
        assertEquals(MtuKind.Ethernet, mtuKind(1500))
        assertEquals(MtuKind.Pppoe, mtuKind(1492))
        assertEquals(MtuKind.Tunnel, mtuKind(1420))
        assertEquals(MtuKind.Tunnel, mtuKind(1400))
        assertEquals(MtuKind.Low, mtuKind(1280))
    }
}
