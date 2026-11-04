package com.vasmarfas.networkhelm.discovery

import com.vasmarfas.networkhelm.model.DeviceException
import com.vasmarfas.networkhelm.model.ErrorCategory
import com.vasmarfas.networkhelm.transport.ByteStream
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ScanTest {

    private object Closed : ByteStream {
        override suspend fun read(buffer: ByteArray, offset: Int, length: Int) = -1

        override suspend fun write(bytes: ByteArray) = Unit

        override fun close() = Unit
    }

    @Test
    fun aNetworkGivesItsHostsWithoutTheAddressesOfTheNetworkAndTheBroadcast() {
        assertEquals(listOf("192.168.88.1", "192.168.88.2"), hosts("192.168.88.0/30"))
        assertEquals(listOf("192.168.88.7"), hosts("192.168.88.7/32"))
        assertEquals(listOf("192.168.88.6", "192.168.88.7"), hosts("192.168.88.7/31"))
        assertEquals(254, hosts("10.0.5.77/24")?.size)
        assertEquals("10.0.5.1", hosts("10.0.5.77/24")?.first())
        assertEquals(1022, hosts("172.16.0.0/22")?.size)
        assertNull(hosts("172.16.0.0/21"), "wider than /22")
        assertNull(hosts("192.168.88.0"))
        assertNull(hosts("192.168.300.0/24"))
        assertNull(hosts("router/24"))
    }

    @Test
    fun theAddressesThatAnswerComeWithTheirPortsAndFewConnectionsAreOpenAtOnce() = runTest {
        var open = 0
        var most = 0
        val results = scan(assertNotNull(hosts("192.0.2.0/28")), parallel = 4) { address, port, _ ->
            open++
            most = maxOf(most, open)
            try {
                delay(10)
                if (address == "192.0.2.1" && port in setOf(22, 8729) || address == "192.0.2.9" && port == 80) Closed else throw DeviceException(ErrorCategory.ConnectionLost, "refused")
            } finally {
                open--
            }
        }.toList()
        assertEquals(mapOf("192.0.2.1" to listOf(22, 8729), "192.0.2.9" to listOf(80)), results.associate { it.address to it.ports })
        assertTrue(most <= 4, "at most 4 at once, $most")
    }

    @Test
    fun aPlatformWithoutTcpFailsTheScan() = runTest {
        val failure = assertFailsWith<DeviceException> {
            scan(listOf("192.0.2.1")) { _, _, _ -> throw DeviceException(ErrorCategory.Unsupported, "a browser cannot open TCP connections") }.toList()
        }
        assertEquals(ErrorCategory.Unsupported, failure.category)
    }
}
