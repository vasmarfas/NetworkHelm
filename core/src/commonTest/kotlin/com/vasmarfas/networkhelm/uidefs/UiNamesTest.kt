package com.vasmarfas.networkhelm.uidefs

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class UiNamesTest {

    private val names = listOf("name", "interface", "use-peer-dns", "allow-reconfigure", "default-route-tables", "address", "last-counter", "dscp")

    @Test
    fun aTitleIsTheNameWithSpacesAndCapitals() {
        assertEquals("use-peer-dns", matchTitle("Use Peer DNS", names))
        assertEquals("interface", matchTitle("Interface", names))
        assertEquals("dscp", matchTitle("DSCP", names))
    }

    @Test
    fun aTitleThatSaysMoreOrLessThanTheNameFitsItAlone() {
        assertEquals("allow-reconfigure", matchTitle("Allow Reconfigure Messages", names))
        assertEquals("address", matchTitle("IP Address", names))
        assertEquals("default-route-tables", matchTitle("Default Route Tables", names))
    }

    @Test
    fun aPluralFitsItsSingular() {
        assertEquals("address", matchTitle("Addresses", names))
        assertEquals("default-route-tables", matchTitle("Default Route Table", names))
    }

    @Test
    fun aTitleOfOneWordDoesNotTakeALongerNameAndOneOfTwoWordsDoes() {
        assertNull(matchTitle("Rate", listOf("connection-rate", "name")))
        assertEquals("cisco-static-id", matchTitle("Cisco ID", listOf("cisco-static-id", "name")))
    }

    @Test
    fun aNameCutShortFitsItsTitleAndAWordThatOnlyContinuesTheOtherDoesNot() {
        fun fit(title: String, vararg names: String) = matchTitles(listOf(title), names.toList(), pairLast = false, abbreviated = true).values.firstOrNull()
        assertEquals("lldp-max-frame-size", fit("LLDP Maximum Frame Size", "lldp-max-frame-size", "name"))
        assertEquals("lldp-vlan-info", fit("LLDP VLAN Information", "lldp-vlan-info", "lldp-mac-phy-config"))
        assertEquals("auto-media-sharing", fit("Auto Media Share", "auto-media-sharing", "name"))
        assertEquals("discover-interface-list", fit("Discovery Interface List", "discover-interface-list", "name"))
        assertNull(fit("Pause Duration", "paused", "name"))
    }

    @Test
    fun theMenusAndCommandsOfARouterAreNotMatchedByAbbreviationAndSettingsIsNotSet() {
        assertNull(matchTitle("Settings", listOf("device", "alert", "print", "set", "get")))
    }

    @Test
    fun aTitleTwoNamesFitEquallyStaysWithoutAndAWordNoNameHasStaysToo() {
        assertNull(matchTitle("Port", listOf("src-port", "dst-port")))
        assertNull(matchTitle("Frequency", names))
    }

    @Test
    fun theLastTitleAndTheLastNameBelongTogetherWhenNoWordsSayIt() {
        val matched = matchTitles(listOf("Name", "Routing Tables", "Interface"), listOf("name", "interface", "default-route-tables"))
        assertEquals(mapOf(0 to "name", 1 to "default-route-tables", 2 to "interface"), matched)
        assertEquals(mapOf(0 to "name"), matchTitles(listOf("Name", "Alpha", "Beta"), listOf("name", "one", "two")))
    }

    @Test
    fun aFieldWithNoTitleIsNotPairedWithTheNameLeftOver() {
        assertEquals(mapOf(0 to "name"), matchTitles(listOf("Name", ""), listOf("name", "status")))
    }

    @Test
    fun aNameIsCutIntoWordsByHyphensCapitalsAndDigits() {
        assertEquals(listOf("use", "peer", "dns"), wordsOf("use-peer-dns"))
        assertEquals(listOf("wi", "fi"), wordsOf("WiFi"))
        assertEquals(listOf("dhcp", "client", "option"), wordsOf("DHCP Client Option"))
        assertEquals("srcaddress", squeezed("Src. Address"))
    }
}
