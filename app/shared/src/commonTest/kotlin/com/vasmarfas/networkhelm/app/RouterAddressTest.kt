package com.vasmarfas.networkhelm.app

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RouterAddressTest {

    @Test
    fun namesAddressesAndPorts() {
        assertEquals(RouterAddress("router.lan", null), parseAddress(" router.lan "))
        assertEquals(RouterAddress("192.0.2.1", 8729), parseAddress("192.0.2.1:8729"))
        assertEquals(RouterAddress("fe80::1%ether1", null), parseAddress("fe80::1%ether1"))
        assertEquals(RouterAddress("2001:db8::1", 8728), parseAddress("[2001:db8::1]:8728"))
        assertEquals(RouterAddress("2001:db8::1", null), parseAddress("[2001:db8::1]"))
    }

    @Test
    fun anAddressWithAPortParsesBackToItself() {
        for (address in listOf(RouterAddress("router.lan", 2022), RouterAddress("192.0.2.1", 8729), RouterAddress("2001:db8::1", 22))) {
            assertEquals(address, parseAddress(formatAddress(address.host, address.port)))
        }
        assertEquals("router.lan", formatAddress("router.lan", null))
        assertEquals("[2001:db8::1]:22", formatAddress("2001:db8::1", 22))
    }

    @Test
    fun brokenAddressesAreRefused() {
        assertNull(parseAddress(""))
        assertNull(parseAddress("192.0.2.1:"))
        assertNull(parseAddress("192.0.2.1:70000"))
        assertNull(parseAddress(":8728"))
        assertNull(parseAddress("[2001:db8::1"))
        assertNull(parseAddress("[2001:db8::1]8728"))
        assertNull(parseAddress("two words"))
    }
}
