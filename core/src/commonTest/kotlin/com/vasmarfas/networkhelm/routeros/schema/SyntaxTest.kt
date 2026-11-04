package com.vasmarfas.networkhelm.routeros.schema

import com.vasmarfas.networkhelm.model.ValueType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SyntaxTest {

    private fun syntax(vararg rows: String) = parseSyntax(
        rows.map { row ->
            val (nested, symbol, text) = row.split('|', limit = 3)
            SyntaxRow(nested.removeSuffix("e").toInt(), !nested.endsWith("e"), symbol, text)
        },
    )

    @Test
    fun addressesPortsAndMac() {
        val source = syntax("0|SrcAddress|[!]Range", "1|Range|A.B.C.D[-A.B.C.D |0..32 |/A.B.C.D ]    (IP address range)")
        assertEquals(ValueType.Ip4Range, source.type)
        assertTrue(source.negatable)
        val port = syntax("0|SrcPort|[!]Port", "1|Port|Range[,Port]    (max 15 times)", "2|Range|Start[-End]", "3|Start|0..65535", "3|End|0..65535")
        assertEquals(ValueType.PortList, port.type)
        assertTrue(port.negatable)
        assertEquals(ValueType.Ip4, syntax("0|Address|A.B.C.D    (IP address)").type)
        assertEquals(ValueType.Ip4Prefix, syntax("0e|DstAddress|Address[/Prefix]", "1|Address|IPv4", "2e|IPv4|IPv4 address", "1e|Prefix|integer value").type)
        assertEquals(ValueType.Ip, syntax("0e|Networks|Address[/Prefix]", "1|Address|IPv4 | IPv6", "2e|IPv4|IPv4 address", "2e|IPv6|IPv6 address").type)
        val mac = "AB[:|-|.]CD[:|-|.]EF[:|-|.]GH[:|-|.]IJ[:|-|.]KL    (MAC address)"
        assertEquals(ValueType.Mac, syntax("0|SrcMacAddress|[!]Address/Mask", "1|Address|$mac", "1|Mask|$mac").type)
        assertEquals(ValueType.Ip4Prefix, syntax("0|SrcAddress|[!]Address/Netmask", "1|Address|A.B.C.D    (IP address)", "1|Netmask|IpNetmask | Num", "2|IpNetmask|A.B.C.D", "2|Num|0..32    (integer number)").type)
        val target = syntax(
            "0|Target|TargetInterface | TargetAddress",
            "1|TargetInterface|",
            "2||lo | ether1 | bridge1 | ...",
            "1|TargetAddress|IpAddress | Ipv6Address",
            "2|IpAddress|A.B.C.D/M    (IP prefix)",
            "2|Ipv6Address|IPv6/0..128    (IPv6 prefix)",
        )
        assertEquals(ValueType.Ip, target.type)
        assertTrue(target.choices)
        assertEquals(ValueType.Ip, syntax("0|Address|Address | Address", "1|Address|A.B.C.D/M    (IP prefix)", "1|Address|IPv6/0..128    (IPv6 prefix)").type)
        assertEquals(ValueType.Mac, syntax("0|MacAddress|AB[:|-|.]CD[:|-|.]EF[:|-|.]GH[:|-|.]IJ[:|-|.]KL    (MAC address)").type)
        assertEquals(ValueType.Many(ValueType.Ip4), syntax("0|Server|Address[,Server]    (max 100 times)", "1|Address|A.B.C.D    (IP address)").type)
        assertEquals(ValueType.Ip6Prefix, syntax("0|Prefix|IPv6/0..128    (IPv6 prefix)").type)
        assertEquals(ValueType.Ip, syntax("0|Address|Ip | Ipv6", "1|Ip|A.B.C.D    (IP address)", "1|Ipv6|see documentation").type)
    }

    @Test
    fun numbersAndVariants() {
        val protocol = syntax("0|Protocol|[!]Protocol", "1|Protocol|IpProtocol", "2|IpProtocol|dccp | ddp | egp | ...", "2||0..4294967295    (integer number)")
        assertEquals(ValueType.Integer(0, 4294967295, scaled = true), protocol.type)
        assertTrue(protocol.choices)
        assertTrue(protocol.negatable)
        val priority = syntax("0|Priority|Priority | Priority", "1|Priority|", "2||0x0000 | 0x1000 | 0x2000 | ...", "1|Priority|Num", "2|Num|0..FFFF    (integer number)")
        assertEquals(ValueType.Integer(0, 65535, scaled = true), priority.type)
        assertEquals(ValueType.Integer(1, 4094, scaled = true), syntax("0|VlanId|Num", "1|Num|1..4094    (integer number)").type)
        val action = syntax("0||")
        assertEquals(ValueType.Enumeration(closed = true), action.type)
        assertTrue(action.choices)
        val inInterface = syntax("0|InInterface|[!]Interface", "1|Interface|FwInterface", "2||lo | ether1 | ...", "2|FwInterface|all-ethernet | all-ppp | all-vlan | all-wireless")
        assertEquals(ValueType.Enumeration(), inInterface.type)
        assertTrue(inInterface.negatable)
        assertEquals(ValueType.Enumeration(multiple = true), syntax("0|ConnectionState|[!]State", "1|State|invalid|established|related|new|untracked[,State*]").type)
        val include = syntax("0|IncludeList|", "1||all | none | dynamic | static", "2e|all|(words)", "2e|none|(words)", "2e|dynamic|(words)", "2e|static|(words)")
        assertEquals(ValueType.Enumeration(), include.type)
        assertEquals(ValueType.Enumeration(), syntax("0|AddressPool|", "1||pool1 | pool2", "1||static-only", "2e|static-only|(words)").type)
        assertEquals(ValueType.Enumeration(), syntax("0|ItemName|", "1|| | ...").type)
        assertEquals(ValueType.Enumeration(), syntax("0|Country|", "1||Oman | Togo | \"French Polynesia\" | Namibia | ...").type)
        assertEquals(ValueType.Enumeration(), syntax("0|TimeZoneName|", "1||Asia/Damascus | Kwajalein | Asia/Dhaka | ...").type)
        val flags = syntax("0|TcpFlags|[!,]Flags", "1|Flags|Flag[,Flags]", "2|Flag|[!]Flag", "3|Flag|", "4||ack | cwr | ece | fin | psh | ...")
        assertEquals(ValueType.Enumeration(multiple = true), flags.type)
    }

    @Test
    fun textTimeAndWhatStaysUnparsed() {
        val comment = syntax("0e|Comment|string value")
        assertEquals(ValueType.Text(), comment.type)
        assertFalse(comment.choices)
        val addressList = syntax("0|SrcAddressList|[!]AddressList", "1|AddressList|", "2||", "2e||string value")
        assertEquals(ValueType.Text(), addressList.type)
        assertTrue(addressList.choices)
        val arpTimeout = syntax("0|ArpTimeout|ArpTimeout | ArpTimeout", "1|ArpTimeout|", "2||auto", "1e|ArpTimeout|time interval")
        assertEquals(ValueType.Time, arpTimeout.type)
        assertTrue(arpTimeout.choices)
        assertEquals(ValueType.Time, syntax("0e|LeaseTime|1s..7101w3d6h28m15s    (time interval)").type)
        assertEquals(ValueType.Text(20), syntax("0e|OnuModel|string value, min length 1, max length 20").type)
        assertEquals(ValueType.Integer(64, 4294967295, "KiB", scaled = true), syntax("0|CacheSize|Num[KiB]", "1|Num|64..4294967295    (integer number)").type)
        assertEquals(ValueType.Integer(0, null, "bps", scaled = true), syntax("0|Bandwidth|Num[bps]", "1|Num|0..18446744073709551615    (integer number)").type)
        assertEquals(ValueType.Integer(0, 100, "%", scaled = true), syntax("0|ThrLossPercent|Num[%]", "1|Num|0..100    (integer number)").type)
        assertEquals(ValueType.Integer(-64, 256, scaled = true), syntax("0|CakeOverhead|[-]Num", "1|Num|-64..256    (integer number)").type)
        val label = syntax("0|Label|[+][-]Label", "1|Label|", "2||all", "2e||string value")
        assertEquals(ValueType.Text(), label.type)
        assertTrue(label.choices)
        val server = syntax("0|Interface|", "1||ether1 | ether2 | bridge1 | ...", "1||<l2tp>")
        assertEquals(ValueType.Enumeration(), server.type)
        assertTrue(server.choices)
        assertEquals(ValueType.Date, syntax("0|Date|Year-Month-Day", "1|Year|1970..2106", "1|Month|1..12", "1|Day|1..31").type)
        val start = syntax("0|StartTime|Startup | Time", "1|Startup|", "2||startup", "1|Time|Hour:Min:Sec", "2|Hour|00..24", "2|Min|00..59", "2|Sec|00..59")
        assertEquals(ValueType.TimeOfDay, start.type)
        assertTrue(start.choices)
        val summer = syntax("0|DstStart|Year-Month-Day Hour:Min:Sec", "1|Year|1970..2106", "1|Month|1..12", "1|Day|1..31", "1|Hour|00..24", "1|Min|00..59", "1|Sec|00..59")
        assertEquals(ValueType.DateTime, summer.type)
        assertEquals(ValueType.Host(ValueType.Ip, names = false, "Address[@VRF]"), syntax("0e|Servers|Address[@VRF]", "1|Address|IPv4 | IPv6", "2e|IPv4|IPv4 address", "2e|IPv6|IPv6 address", "1|VRF|main").type)
        val tunnel = syntax("0e|RemoteAddress|Address[@VRF]", "1|Address|IPv4 | Hostname", "2e|IPv4|IPv4 address", "2e|Hostname|string value", "1|VRF|main")
        assertEquals(ValueType.Host(ValueType.Ip4, names = true, "Address[@VRF]"), tunnel.type)
        assertFalse(tunnel.choices)
        assertEquals(ValueType.Host(ValueType.Ip, names = true, "IPv4 | IPv6 | Hostname"), syntax("0|Servers|IPv4 | IPv6 | Hostname", "1e|IPv4|IPv4 address", "1e|IPv6|IPv6 address", "1e|Hostname|string value").type)
        val gateway = syntax(
            "0e|Gateway|Address[%Interface][@VRF]", "1|Address|IPv4 | IPv6 | Interface", "2e|IPv4|IPv4 address", "2e|IPv6|IPv6 address",
            "2|Interface|lo | ether1 | ...", "1|Interface|lo | ether1 | ...", "1|VRF|main",
        )
        assertEquals(ValueType.Host(ValueType.Ip, names = true, "Address[%Interface][@VRF]"), gateway.type)
        assertTrue(gateway.choices, "the names of the interfaces")
        assertEquals(ValueType.Unparsed("see documentation"), syntax("0|Address|see documentation").type)
    }

    @Test
    fun theCompositesOfAFirewallRuleAreTold() {
        val time = syntax("0|Time|[!]Start-End,Day", "1e|Start|0s..1d    (time interval)", "1e|End|0s..1d    (time interval)", "1|Day|sun|mon|tue|wed|thu|fri|sat[,Day*]")
        assertEquals(ValueType.TimeWindow(listOf("sun", "mon", "tue", "wed", "thu", "fri", "sat")), time.type)
        assertTrue(time.negatable)
        val limit = syntax(
            "0|Limit|[!]Count[/Time],Burst[:Mode]",
            "1|Count|Num",
            "2|Num|1..32000000000    (integer number)",
            "1e|Time|1s..7101w3d6h28m15s    (time interval)",
            "1|Burst|Num",
            "2|Num|0..2000000000    (integer number)",
            "1|Mode|",
            "2||bit | packet",
        )
        assertEquals(ValueType.Rate(listOf("bit", "packet")), limit.type)
        assertFalse(limit.choices)
        assertEquals(ValueType.Unparsed("Count[/Time],Burst[:Mode]"), syntax("0|Limit|[!]Count[/Time],Burst[:Mode]", "1|Count|Num", "2|Num|1..32000000000    (integer number)").type)
        val netmask = syntax("0|ConnectionLimit|[!]Limit,Netmask", "1|Limit|Num", "2|Num|0..4294967295    (integer number)", "1|Netmask|IpNetmask | Num", "2|IpNetmask|A.B.C.D", "2|Num|0..32    (integer number)")
        assertEquals(ValueType.ConnectionLimit(32), netmask.type)
        val classifier = syntax(
            "0|PerConnectionClassifier|[!]ValuesToHash:Denominator/Remainder",
            "1|ValuesToHash|",
            "2||both-addresses | both-addresses-and-ports | both-ports | dst-address | dst-address-and-port | ...",
            "1|Denominator|Num",
            "2|Num|1..4294967295    (integer number)",
            "1|Remainder|Num",
            "2|Num|0..4294967295    (integer number)",
        )
        assertEquals(ValueType.Classifier, classifier.type)
        assertTrue(classifier.choices, "the hashes come from completion")
        assertEquals(ValueType.ConnectionLimit(128), syntax("0|ConnectionLimit|[!]Limit,PrefixLen", "1|Limit|Num", "2|Num|0..4294967295    (integer number)", "1|PrefixLen|Num", "2|Num|0..128    (integer number)").type)
        val icmp = syntax("0|IcmpOptions|[!]Type[:Code]", "1|Type|Num", "2|Num|0..255    (integer number)", "1|Code|Start[-End]", "2|Start|0..255", "2|End|0..255")
        assertEquals(ValueType.Icmp(), icmp.type)
        assertTrue(icmp.negatable)
        val headers = syntax("0|Headers|[!]Type[:Mode]", "1|Type|hop|dst|route|frag|ah|esp|none|proto[,Type*]", "1|Mode|", "2||contains | exact")
        assertEquals(ValueType.Headers(listOf("hop", "dst", "route", "frag", "ah", "esp", "none", "proto"), listOf("contains", "exact")), headers.type)
        assertTrue(headers.negatable)
    }

    @Test
    fun spansOfNumbers() {
        val short = ValueType.Integer(0, 65535, scaled = true)
        val size = syntax("0|PacketSize|[!]Min[-Max]", "1|Min|Num", "2|Num|0..65535    (integer number)", "1|Max|Num", "2|Num|0..65535    (integer number)")
        assertEquals(ValueType.Span(short, listOf("min", "max"), open = true), size.type)
        assertTrue(size.negatable)
        val mss = syntax("0|TcpMss|[!]Min-Max", "1|Min|Num", "2|Num|0..65535    (integer number)", "1|Max|Num", "2|Num|0..65535    (integer number)")
        assertEquals(ValueType.Span(short, listOf("min", "max"), open = false), mss.type)
        val rate = syntax("0|ConnectionRate|[!]From-To", "1|From|Num", "2|Num|0..4294967295    (integer number)", "1|To|Num", "2|Num|0..4294967295    (integer number)")
        assertEquals(ValueType.Span(ValueType.Integer(0, 4294967295, scaled = true), listOf("from", "to"), open = false), rate.type)
        val labels = syntax("0|DynamicLabelRange|Start[-End]", "1|Start|16..1048575", "1|End|16..1048575")
        assertEquals(ValueType.Span(ValueType.Integer(16, 1048575, scaled = true), listOf("start", "end"), open = true), labels.type)
        val tos = syntax("0|Tos|[!]Tos[/Mask]", "1|Tos|Num", "2|Num|0..FF    (integer number)", "1|Mask|Num", "2|Num|0..FF    (integer number)")
        assertEquals(ValueType.Span(ValueType.Integer(0, 255, scaled = true), listOf("tos", "mask"), open = true, separator = "/"), tos.type)
        assertTrue(tos.negatable)
    }

    @Test
    fun pairsOfAddressFamiliesAndHex() {
        assertEquals(ValueType.Ip, syntax("0|SrcAddress|Ip | Ip6", "1|Ip|A.B.C.D    (IP address)", "1|Ip6|see documentation").type)
        assertEquals(ValueType.Ip, syntax("0|Group|Address | Ipv6Address", "1|Address|A.B.C.D    (IP address)", "1|Ipv6Address|see documentation").type)
        assertEquals(ValueType.Hex, syntax("0e|HostUniq|hexadecimal string value").type)
        assertEquals(ValueType.Hex, syntax("0e|Cak|hexadecimal string value, max length 16").type)
        val key = syntax("0|PresharedKey|PresharedKey | PresharedKey", "1|PresharedKey|auto | Num", "2|Num|0..4294967295    (integer number)", "1e|PresharedKey|string value")
        assertEquals(ValueType.Text(), key.type)
        val id = syntax(
            "0|MyId|String | Address | Other",
            "1|String|Type:Data",
            "2|Type|",
            "3||fqdn | key-id | user-fqdn",
            "2e|Data|string value",
            "1|Address|Type:Data",
            "2|Type|",
            "3||address",
            "2|Data|Ipv6 | Ip",
            "3|Ipv6|see documentation",
            "3|Ip|A.B.C.D    (IP address)",
            "1|Other|",
            "2||auto | dn",
            "2||0..4294967295    (integer number)",
        )
        assertEquals(ValueType.Text(), id.type)
        assertTrue(id.choices)
        assertEquals(ValueType.Decimal(0.0, 100.0, "%"), syntax("0|ThrLossPercent|Num[%]", "1|Num|0..100    (decimal number)").type)
        assertEquals(ValueType.RouteDistinguisher, syntax("0e|Rd|Address[&RD]", "1|Address|", "1e|RD|route distinguisher").type)
    }

    @Test
    fun aNumberOfRouterOsMayHaveAMultiplierOrBeHex() {
        val type = ValueType.Integer(0, 4294967295, scaled = true)
        assertEquals(listOf(4000L, 4000L, 1_000_000L, 1_000_000_000L, 4096L, -5L), listOf("4k", "4K", "1M", "1G", "0x1000", "-5").map(type::number))
        assertEquals(listOf(null, null, null, null), listOf("2m", "2ki", "k", "9223372036854775807k").map(type::number))
        assertEquals(null, ValueType.Integer(0, 65535).number("4k"))
    }
}
