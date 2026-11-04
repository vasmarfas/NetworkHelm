package com.vasmarfas.networkhelm.ui.table

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FiltersTest {

    @Test
    fun textOperatorsCompareTheWholeValueOrAPart() {
        assertTrue(Condition("chain", FilterOperator.Is, "input").matches("input"))
        assertFalse(Condition("chain", FilterOperator.Is, "input").matches("Input"))
        assertTrue(Condition("chain", FilterOperator.IsNot, "input").matches(null))
        assertTrue(Condition("comment", FilterOperator.Contains, "WEB").matches("web server"))
        assertFalse(Condition("comment", FilterOperator.ContainsNot, "web").matches("web server"))
        assertTrue(Condition("comment", FilterOperator.Contains, "").matches(null), "a line without a value lets rows through")
    }

    @Test
    fun inChecksThatAnAddressOrPrefixLiesInTheSubnet() {
        val lan = Condition("dst-address", FilterOperator.In, "192.168.88.0/24")
        assertTrue(lan.matches("192.168.88.1"))
        assertTrue(lan.matches("192.168.88.128/25"))
        assertFalse(lan.matches("192.168.0.0/16"), "a wider prefix is not inside")
        assertFalse(lan.matches("192.168.89.1"))
        assertFalse(lan.matches("ether1"))
        assertTrue(Condition("dst-address", FilterOperator.In, "0.0.0.0/0").matches("10.1.2.3/32"))
        assertTrue(Condition("dst-address", FilterOperator.In, "10.1.2.3").matches("10.1.2.3"))
        assertFalse(Condition("dst-address", FilterOperator.In, "10.1.2.300/8").matches("10.1.2.3"))
        assertTrue(lan.matches("192.168.88.10-192.168.88.20"), "a range of a pool")
    }

    @Test
    fun inTakesIpv6Too() {
        val site = Condition("address", FilterOperator.In, "2001:db8::/32")
        assertTrue(site.matches("2001:db8:1::1/64"))
        assertTrue(site.matches("2001:db8::1"))
        assertFalse(site.matches("2001:db9::1"))
        assertFalse(site.matches("192.0.2.1"))
        assertTrue(Condition("address", FilterOperator.In, "fe80::/10").matches("fe80::1%ether1"))
        assertTrue(FilterOperator.In in operatorsFor("2001:db8::/32"))
    }

    @Test
    fun onlyAddressColumnsOfferIn() {
        assertEquals(FilterOperator.entries, operatorsFor("10.0.0.0/8"))
        assertFalse(FilterOperator.In in operatorsFor("ether1"))
        assertFalse(FilterOperator.In in operatorsFor(null))
    }
}
