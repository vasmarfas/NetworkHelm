package com.vasmarfas.networkhelm.search

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LookupTest {

    @Test
    fun anAddressFindsItselfAndTheNetworksAndRangesThatHoldIt() {
        val lookup = assertIs<Lookup.Addresses>(lookup("192.168.88.10"))
        assertTrue(lookup.matches("192.168.88.10"))
        assertTrue(lookup.matches("192.168.88.1/24"))
        assertTrue(lookup.matches("!192.168.88.0/24"))
        assertTrue(lookup.matches("192.168.88.2-192.168.88.254"))
        assertTrue(lookup.matches("10.0.0.1,192.168.88.10"))
        assertTrue(lookup.matches("printer at 192.168.88.10."))
        assertFalse(lookup.matches("192.168.88.100"))
        assertFalse(lookup.matches("192.168.89.0/24"))
        assertFalse(lookup.matches("0.0.0.0/0"))
        assertFalse(lookup.matches("2001:db8::10"))
    }

    @Test
    fun aNetworkOrARangeFindsWhatOverlapsIt() {
        val network = assertIs<Lookup.Addresses>(lookup("10.0.0.0/8"))
        assertTrue(network.matches("10.1.2.3"))
        assertTrue(network.matches("10.20.0.0/16"))
        assertTrue(network.matches("9.255.255.250-10.0.0.5"))
        assertFalse(network.matches("11.0.0.1"))
        val range = assertIs<Lookup.Addresses>(lookup("192.0.2.10-192.0.2.20"))
        assertTrue(range.matches("192.0.2.20"))
        assertFalse(range.matches("192.0.2.21"))
        assertIs<Lookup.Text>(lookup("10.0.0.0/33"))
        assertIs<Lookup.Text>(lookup("192.0.2.20-192.0.2.10"))
    }

    @Test
    fun anAddressOfIpv6IsReadInEveryShortenedForm() {
        val lookup = assertIs<Lookup.Addresses>(lookup("2001:db8::10"))
        assertTrue(lookup.matches("2001:DB8:0:0:0:0:0:10"))
        assertTrue(lookup.matches("2001:db8::/64"))
        assertFalse(lookup.matches("2001:db8::11"))
        assertFalse(lookup.matches("::/0"))
        assertFalse(lookup.matches("192.168.88.10"))
        assertTrue(assertIs<Lookup.Addresses>(lookup("::ffff:192.0.2.1")).matches("::ffff:c000:201"))
        assertTrue(assertIs<Lookup.Addresses>(lookup("fe80::1%bridge-lan")).matches("fe80::1"))
        assertIs<Lookup.Text>(lookup("2001:db8::1::2"))
        assertIs<Lookup.Text>(lookup("1:2:3:4:5:6:7:8:9"))
    }

    @Test
    fun aMacAddressMatchesHoweverItIsWritten() {
        val lookup = assertIs<Lookup.Mac>(lookup("b8:69:f4:12:34:56"))
        assertTrue(lookup.matches("B8:69:F4:12:34:56"))
        assertTrue(lookup.matches("b8-69-f4-12-34-56"))
        assertTrue(lookup.matches("b869.f412.3456"))
        assertTrue(lookup.matches("aa:bb:cc:dd:ee:ff\nb8:69:f4:12:34:56"))
        assertFalse(lookup.matches("B8:69:F4:12:34:57"))
        val maker = assertIs<Lookup.Mac>(lookup("B8-69-F4"))
        assertTrue(maker.matches("B8:69:F4:AA:BB:CC"))
        assertFalse(maker.matches("1:b8:69:f4:aa:bb:cc"))
    }

    @Test
    fun otherTextIsLookedForInAnyCase() {
        val lookup = assertIs<Lookup.Text>(lookup(" Printer "))
        assertTrue(lookup.matches("office printer"))
        assertFalse(lookup.matches("scanner"))
        assertIs<Lookup.Text>(lookup("192.168.88."))
        assertIs<Lookup.Text>(lookup("aa:bb"))
        assertNull(lookup("a"))
        assertNull(lookup("  "))
    }
}
