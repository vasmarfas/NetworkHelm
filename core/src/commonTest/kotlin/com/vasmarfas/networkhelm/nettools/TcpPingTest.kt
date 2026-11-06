package com.vasmarfas.networkhelm.nettools

import com.vasmarfas.networkhelm.model.DeviceException
import com.vasmarfas.networkhelm.model.ErrorCategory
import com.vasmarfas.networkhelm.transport.CannedStream
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TcpPingTest {

    @Test
    fun anAcceptedConnectionIsClosedAndTimed() = runTest {
        val stream = CannedStream("")
        val time = tcpConnectMillis("192.0.2.1", 443, 800) { host, port, timeout ->
            assertEquals(Triple("192.0.2.1", 443, 800), Triple(host, port, timeout))
            stream
        }
        assertTrue(assertNotNull(time) >= 0.0)
        assertTrue(stream.closed)
    }

    @Test
    fun aRefusedConnectionAndASilentHostHaveNoTime() = runTest {
        assertNull(tcpConnectMillis("192.0.2.1", 443, 800) { _, _, _ -> throw DeviceException(ErrorCategory.ConnectionLost, "refused") })
        assertNull(tcpConnectMillis("192.0.2.1", 443, 800) { _, _, _ -> throw Exception("did not answer in 800 ms") })
    }

    @Test
    fun aPlatformWithoutTcpFailsInsteadOfReportingClosedPorts() = runTest {
        val failure = assertFailsWith<DeviceException> {
            tcpConnectMillis("192.0.2.1", 443, 800) { _, _, _ -> throw DeviceException(ErrorCategory.Unsupported, "a browser cannot open TCP connections") }
        }
        assertEquals(ErrorCategory.Unsupported, failure.category)
    }
}
