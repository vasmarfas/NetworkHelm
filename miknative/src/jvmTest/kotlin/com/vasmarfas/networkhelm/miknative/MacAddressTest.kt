package com.vasmarfas.networkhelm.miknative

import com.vasmarfas.networkhelm.model.DeviceException
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MacAddressTest {
    @Test
    fun formatsSixOctetsColonSeparated() {
        val mac = MacAddress(byteArrayOf(0x00, 0x0C, 0x42, 0x01, 0x02, 0x03))
        assertEquals("00:0c:42:01:02:03", mac.format())
        assertEquals("00:0c:42:01:02:03", mac.toString())
    }

    @Test
    fun parsesColonsDashesAndDots() {
        assertEquals("00:0c:42:01:02:03", MacAddress.parse("00:0c:42:01:02:03").format())
        assertEquals("00:0c:42:01:02:03", MacAddress.parse("00-0C-42-01-02-03").format())
        assertEquals("00:0c:42:01:02:03", MacAddress.parse("00.0c.42.01.02.03").format())
    }

    @Test
    fun rejectsMalformedInput() {
        assertFailsWith<DeviceException> { MacAddress.parse("00:0c:42") }
        assertFailsWith<DeviceException> { MacAddress.parse("00:0c:42:01:02:gg") }
        assertFailsWith<DeviceException> { MacAddress.parse("00:0c:42:01:02:300") }
    }

    @Test
    fun rejectsInvalidByteLength() {
        assertFailsWith<DeviceException> { MacAddress(ByteArray(5)) }
        assertFailsWith<DeviceException> { MacAddress(ByteArray(7)) }
    }

    @Test
    fun equalsAndHashCode() {
        val a = MacAddress.parse("00:0c:42:01:02:03")
        val b = MacAddress.parse("00:0C:42:01:02:03")
        val c = MacAddress.parse("00:0c:42:01:02:04")
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
        assertFalse(a == c)
    }
}
