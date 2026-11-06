package com.vasmarfas.networkhelm.nettools

import kotlin.test.Test
import kotlin.test.assertEquals

class ArpTest {

    @Test
    fun windowsPrintsDashesAndAHeaderPerInterface() {
        val output = "\r\nInterface: 192.168.1.10 --- 0x6\r\n  Internet Address      Physical Address      Type\r\n" +
            "  192.168.1.1           a0-0b-0c-0d-0e-0f     dynamic   \r\n  192.168.1.77          3c-52-82-aa-bb-cc     dynamic   \r\n" +
            "  192.168.1.255         ff-ff-ff-ff-ff-ff     static    \r\n  224.0.0.22            01-00-5e-00-00-16     static    \r\n"
        assertEquals(
            mapOf(
                "192.168.1.1" to "A0:0B:0C:0D:0E:0F",
                "192.168.1.77" to "3C:52:82:AA:BB:CC",
                "224.0.0.22" to "01:00:5E:00:00:16",
            ),
            parseArp(output),
        )
    }

    @Test
    fun macOsDropsTheLeadingZerosAndMarksTheUnanswered() {
        val output = "? (192.168.1.1) at a0:b:c:d:e:f on en0 ifscope [ethernet]\n" +
            "? (192.168.1.50) at (incomplete) on en0 ifscope [ethernet]\n" +
            "? (192.168.1.77) at 3c:52:82:aa:bb:cc on en0 ifscope [ethernet]\n" +
            "? (192.168.1.255) at ff:ff:ff:ff:ff:ff on en0 ifscope [ethernet]\n"
        assertEquals(mapOf("192.168.1.1" to "A0:0B:0C:0D:0E:0F", "192.168.1.77" to "3C:52:82:AA:BB:CC"), parseArp(output))
    }

    @Test
    fun linuxAndAndroidListTheTableOfTheKernel() {
        val output = "IP address       HW type     Flags       HW address            Mask     Device\n" +
            "192.168.1.1      0x1         0x2         a0:0b:0c:0d:0e:0f     *        wlan0\n" +
            "192.168.1.50     0x1         0x0         00:00:00:00:00:00     *        wlan0\n" +
            "192.168.1.77     0x1         0x2         3c:52:82:aa:bb:cc     *        wlan0\n"
        assertEquals(mapOf("192.168.1.1" to "A0:0B:0C:0D:0E:0F", "192.168.1.77" to "3C:52:82:AA:BB:CC"), parseArp(output))
    }

    @Test
    fun theNeighbourCommandOfLinuxReadsTheSameWay() {
        val output = "192.168.1.1 dev wlan0 lladdr a0:0b:0c:0d:0e:0f REACHABLE\n" +
            "192.168.1.50 dev wlan0  FAILED\n" +
            "fe80::a20b:cff:fe0d:e0f dev wlan0 lladdr a0:0b:0c:0d:0e:0f router STALE\n"
        assertEquals(mapOf("192.168.1.1" to "A0:0B:0C:0D:0E:0F"), parseArp(output))
    }

    @Test
    fun anEmptyOrUnreadableTableHasNoEntries() {
        assertEquals(emptyMap(), parseArp(""))
        assertEquals(emptyMap(), parseArp("IP address       HW type     Flags       HW address            Mask     Device\n"))
    }
}
