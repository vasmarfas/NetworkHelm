package com.vasmarfas.networkhelm.openwrt

import com.vasmarfas.networkhelm.model.ValueType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DatatypesTest {

    private fun type(datatype: String) = uciArgument("option", datatype).type

    @Test
    fun anEnableOfZeroOrOneIsAFlag() {
        assertEquals(ValueType.Flag, uciSwitch("enable", listOf("1", "0", "0"))?.type)
        assertNull(uciSwitch("enable", listOf("1", "yes")))
        assertNull(uciSwitch("Interval", listOf("1")))
    }

    @Test
    fun datatypesOfTheRouterBecomeFields() {
        assertEquals(ValueType.Flag, type("bool"))
        assertEquals(ValueType.Integer(min = 0), type("uinteger"))
        assertEquals(ValueType.Integer(0, 65535), type("port"))
        assertEquals(ValueType.Integer(32, 65535), type("range(32, 65535)"))
        assertEquals(ValueType.Ip4Prefix, type("cidr4"))
        assertEquals(ValueType.Ip6Prefix, type("cidr6"))
        assertEquals(ValueType.Ip4, type("netmask4"))
        assertEquals(ValueType.Text(), type("hostname"))
        assertEquals(ValueType.Many(ValueType.Text(), LIST), type("list(file)"))
        assertEquals(ValueType.Many(ValueType.Integer(min = 0), LIST), type("list(uinteger)"))
        assertEquals(ValueType.Text(), type("or(range(0,65535),string)"))
        assertEquals(ValueType.Ip, type("ipaddr"))
        assertEquals(ValueType.Ip4Prefix, type("ipmask4"))
        assertEquals(ValueType.Hex, type("hexstring"))
        assertEquals(ValueType.TimeOfDay, type("timehhmmss"))
        assertEquals(ValueType.Unparsed("tuple(uinteger,string)"), type("tuple(uinteger,string)"))
    }

    @Test
    fun anAndOfNumbersKeepsTheBoundsOfAllItsParts() {
        assertEquals(ValueType.Integer(min = 0), type("and(uinteger,min(0))"))
        assertEquals(ValueType.Integer(1, 63), type("and(uinteger, min(1), max(63))"))
        assertEquals(ValueType.Text(32), type("and(hostname,maxlength(32))"))
        assertEquals(ValueType.Hex, type("and(hexstring,length(4))"))
    }

    @Test
    fun aNegationIsTheBangOfTheFieldOrOfEachEntry() {
        val port = uciArgument("dest_port", "neg(portrange)")
        assertEquals(ValueType.PortList, port.type)
        assertTrue(port.negatable)
        val masquerading = uciArgument("masq_src", "list(neg(or(uciname,hostname,ipmask4)))")
        assertEquals(ValueType.Many(ValueType.Text(), LIST, negatable = true), masquerading.type)
        assertFalse(masquerading.negatable)
    }

    @Test
    fun wordsBesideATypeAreChoicesOfIt() {
        val length = uciArgument("ip6assign", "or(range(0,64),128)")
        assertEquals(ValueType.Integer(0, 64), length.type)
        assertEquals(listOf("128"), length.choices)
    }

    @Test
    fun anOrOfPlainWordsIsAChoice() {
        val encaps = uciArgument("encaps", "or(llc, vc)")
        assertEquals(ValueType.Enumeration(), encaps.type)
        assertEquals(listOf("llc", "vc"), encaps.choices)
        assertEquals(listOf("try", "force", "none"), uciArgument("reqaddress", "or(\"try\",\"force\",\"none\")").choices)
        assertEquals(listOf("prohibit", "unreachable", "blackhole", "throw"), uciArgument("action", "or(prohibit, unreachable, blackhole, throw)").choices)
    }
}
