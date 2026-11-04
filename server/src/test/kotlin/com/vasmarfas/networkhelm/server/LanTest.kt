package com.vasmarfas.networkhelm.server

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LanTest {
    @Test
    fun localNetworksPassAndTheInternetDoesNot() {
        for (address in listOf("127.0.0.1", "::1", "10.1.2.3", "172.20.0.5", "192.168.1.100", "169.254.1.1", "fe80::1", "fd12:3456::1", "100.64.0.1", "100.127.255.254")) {
            assertTrue(isLocal(address), address)
        }
        for (address in listOf("8.8.8.8", "100.128.0.1", "172.32.0.1", "2001:db8::1", "193.0.2.1")) {
            assertFalse(isLocal(address), address)
        }
    }
}
