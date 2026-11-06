package com.vasmarfas.networkhelm.nettools

import com.vasmarfas.networkhelm.model.DeviceException
import com.vasmarfas.networkhelm.model.ErrorCategory
import com.vasmarfas.networkhelm.transport.CannedStream
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PortsTest {

    @Test
    fun aListOfPortsTakesSinglePortsAndRanges() {
        assertEquals(listOf(22, 80, 443, 8000, 8001, 8002), parsePorts("22, 80,443 8000-8002"))
        assertEquals(listOf(443, 22), parsePorts("443;22;443"))
        assertEquals(emptyList(), parsePorts("0, 70000, ssh"))
        assertEquals(65535, parsePorts("1-65535").size)
        assertEquals(65535, parsePorts("0-2000000000").size)
        assertEquals(listOf(65534, 65535), parsePorts("65534-70000"))
        assertEquals(emptyList(), parsePorts("90-80"))
    }

    @Test
    fun wellKnownPortsHaveTheirServiceNames() {
        assertEquals("ssh", WellKnownPorts.service(22))
        assertEquals("winbox", WellKnownPorts.service(8291))
        assertNull(WellKnownPorts.service(12345))
        assertTrue(WellKnownPorts.top.all { WellKnownPorts.service(it) != null }, "every top port has a name")
    }

    @Test
    fun theReferenceListsEachPortOnceAndTheScannerTakesItsNamesFromIt() {
        val ports = WellKnownPorts.all.map { it.port }
        assertEquals(ports.sorted(), ports)
        assertEquals(ports.size, ports.toSet().size)
        assertTrue(WellKnownPorts.all.all { it.protocol in listOf("TCP", "UDP", "TCP/UDP") })
        assertTrue(WellKnownPorts.all.all { it.service.isNotEmpty() && it.service == it.service.lowercase() })
        assertTrue(WellKnownPorts.top.all { it in ports }, "every port of the scanner is in the reference")
        for (entry in WellKnownPorts.all) assertEquals(entry.service, WellKnownPorts.service(entry.port))
        assertEquals("UDP", WellKnownPorts.all.first { it.port == 51820 }.protocol)
        assertEquals("TCP/UDP", WellKnownPorts.all.first { it.port == 53 }.protocol)
    }

    @Test
    fun aQueryMatchesTheNumberFromItsStartAndTheServiceTheProtocolAndTheDescription() {
        val winbox = WellKnownPorts.all.first { it.port == 8291 }
        assertTrue(winbox.matches("", "management"))
        assertTrue(winbox.matches("82", ""))
        assertFalse(winbox.matches("91", ""))
        assertTrue(winbox.matches("WINBOX", ""))
        assertTrue(winbox.matches("tcp", ""))
        assertFalse(winbox.matches("udp", ""))
        assertTrue(winbox.matches("manage", "MikroTik management"))
        assertFalse(winbox.matches("ssh", "MikroTik management"))
    }

    @Test
    fun everyPortIsReportedOpenOrClosedWithFewConnectionsAtOnce() = runTest {
        var open = 0
        var most = 0
        val results = scanPorts("192.0.2.1", parsePorts("20-29, 80"), parallel = 3) { host, port, timeout ->
            assertEquals("192.0.2.1", host)
            assertEquals(800, timeout)
            open++
            most = maxOf(most, open)
            try {
                delay(10)
                if (port == 22 || port == 80) CannedStream("") else throw DeviceException(ErrorCategory.ConnectionLost, "refused")
            } finally {
                open--
            }
        }.toList()
        assertEquals((20..29).toList() + 80, results.map { it.port }.sorted())
        assertEquals(listOf(22, 80), results.filter { it.open }.map { it.port }.sorted())
        assertTrue(most <= 3, "at most 3 at once, $most")
    }

    @Test
    fun aPlatformWithoutTcpFailsTheScan() = runTest {
        val failure = assertFailsWith<DeviceException> {
            scanPorts("192.0.2.1", listOf(22)) { _, _, _ -> throw DeviceException(ErrorCategory.Unsupported, "a browser cannot open TCP connections") }.toList()
        }
        assertEquals(ErrorCategory.Unsupported, failure.category)
    }
}
