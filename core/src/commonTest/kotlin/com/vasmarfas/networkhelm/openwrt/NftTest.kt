package com.vasmarfas.networkhelm.openwrt

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlin.test.Test
import kotlin.test.assertEquals

class NftTest {

    private fun rule(json: String) = nftRule(Json.parseToJsonElement(json).jsonArray)

    @Test
    fun rulesOfFirewall4ReadAsNftPrintsThem() {
        assertEquals("iif \"lo\" accept", rule("""[{"match":{"op":"==","left":{"meta":{"key":"iif"}},"right":"lo"}},{"accept":null}]"""))
        assertEquals(
            "ct state vmap { established : accept, related : accept }",
            rule("""[{"vmap":{"key":{"ct":{"key":"state"}},"data":{"set":[["established",{"accept":null}],["related",{"accept":null}]]}}}]"""),
        )
        assertEquals(
            "tcp flags & (fin | syn | rst | ack) == syn jump syn_flood",
            rule("""[{"match":{"op":"==","left":{"&":[{"payload":{"protocol":"tcp","field":"flags"}},{"|":["fin","syn","rst","ack"]}]},"right":"syn"}},{"jump":{"target":"syn_flood"}}]"""),
        )
        assertEquals("limit rate 25/second burst 50 packets return", rule("""[{"limit":{"rate":25,"burst":50,"per":"second"}},{"return":null}]"""))
        assertEquals("meta l4proto tcp reject with tcp reset", rule("""[{"match":{"op":"==","left":{"meta":{"key":"l4proto"}},"right":"tcp"}},{"reject":{"type":"tcp reset"}}]"""))
        assertEquals("tcp option maxseg size set rt mtu", rule("""[{"mangle":{"key":{"tcp option":{"name":"maxseg","field":"size"}},"value":{"rt":{"key":"mtu"}}}}]"""))
    }

    @Test
    fun countersAreLeftToTheirColumnsAndSetsAndPrefixesAreWritten() {
        assertEquals(
            "tcp dport { 22, 80, 443 } accept",
            rule("""[{"match":{"op":"==","left":{"payload":{"protocol":"tcp","field":"dport"}},"right":{"set":[22,80,443]}}},{"counter":{"packets":3,"bytes":180}},{"accept":null}]"""),
        )
        assertEquals(
            "ip saddr != 192.0.2.0/24 drop",
            rule("""[{"match":{"op":"!=","left":{"payload":{"protocol":"ip","field":"saddr"}},"right":{"prefix":{"addr":"192.0.2.0","len":24}}}},{"drop":null}]"""),
        )
        assertEquals("oifname \"eth1\" masquerade", rule("""[{"match":{"op":"==","left":{"meta":{"key":"oifname"}},"right":"eth1"}},{"masquerade":null}]"""))
    }
}
