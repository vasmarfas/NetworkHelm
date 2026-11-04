package com.vasmarfas.networkhelm.journal

import com.vasmarfas.networkhelm.model.Argument
import com.vasmarfas.networkhelm.model.Item
import com.vasmarfas.networkhelm.model.MenuPath
import com.vasmarfas.networkhelm.model.MenuSchema
import com.vasmarfas.networkhelm.model.NodeKind
import com.vasmarfas.networkhelm.model.Platform
import com.vasmarfas.networkhelm.model.ValueType
import com.vasmarfas.networkhelm.offline.addsOf
import kotlin.test.Test
import kotlin.test.assertEquals

class RowCopyTest {

    private val filter = MenuPath.parse("ip/firewall/filter")

    private val schema = MenuSchema(
        filter, NodeKind.List, emptyList(), listOf("add", "print", "remove", "set"),
        listOf(
            Argument("chain", ValueType.Text()), Argument("action", ValueType.Enumeration()), Argument("protocol", ValueType.Text()),
            Argument("dst-port", ValueType.PortList), Argument("comment", ValueType.Text()), Argument("disabled", ValueType.YesNo),
            Argument("log-prefix", ValueType.Text()), Argument("bytes", ValueType.Integer(), readOnly = true),
        ),
    )

    private val accept = Item("*1", mapOf("chain" to "forward", "action" to "accept", "protocol" to "tcp", "dst-port" to "443", "comment" to "web"))

    @Test
    fun aRowBecomesTheAddThatMakesItInTheOrderOfThePrint() {
        assertEquals("/ip firewall filter add chain=forward action=accept protocol=tcp dst-port=443 comment=web", addCommands(Platform.RouterOs, filter, schema, listOf(accept)))
    }

    @Test
    fun whatTheRouterFillsInByItselfStaysOut() {
        val row = Item(
            "*2",
            mapOf(
                ".id" to "*2", "chain" to "input", "action" to "drop", "disabled" to "false", "log-prefix" to "", "bytes" to "1200", "packets" to "7",
                "dynamic" to "false", "invalid" to "false",
            ),
        )
        assertEquals("/ip firewall filter add chain=input action=drop", addCommands(Platform.RouterOs, filter, schema, listOf(row)))
        assertEquals("/ip firewall filter add chain=input action=drop disabled=true", addCommands(Platform.RouterOs, filter, schema, listOf(row.copy(values = row.values + ("disabled" to "true")))))
    }

    @Test
    fun aValueEqualToTheDefaultOfTheSchemaIsLeftOut() {
        val withDefault = MenuSchema(filter, NodeKind.List, emptyList(), listOf("add"), listOf(Argument("chain", ValueType.Text()), Argument("log", ValueType.YesNo, default = "no")))
        val rows = listOf(Item("*1", mapOf("chain" to "forward", "log" to "no")), Item("*2", mapOf("chain" to "input", "log" to "yes")))
        assertEquals("/ip firewall filter add chain=forward\n/ip firewall filter add chain=input log=yes", addCommands(Platform.RouterOs, filter, withDefault, rows))
    }

    @Test
    fun valuesWithSpacesAndSpecialCharactersAreQuoted() {
        val row = Item("*1", mapOf("chain" to "forward", "comment" to "core \"rack\" 2; \$x", "dst-port" to "80,443", "protocol" to "line one\nline two"))
        assertEquals(
            "/ip firewall filter add chain=forward comment=\"core \\\"rack\\\" 2; \\\$x\" dst-port=80,443 protocol=\"line one\\nline two\"",
            addCommands(Platform.RouterOs, filter, schema, listOf(row)),
        )
    }

    @Test
    fun aDynamicRowIsNotAddedAndAListWithoutAddHasNoCommands() {
        val dynamic = Item("*3", mapOf("chain" to "forward", "action" to "reject", "dynamic" to "true"))
        assertEquals("/ip firewall filter add chain=forward action=accept protocol=tcp dst-port=443 comment=web", addCommands(Platform.RouterOs, filter, schema, listOf(accept, dynamic)))
        assertEquals("", addCommands(Platform.RouterOs, filter, schema, listOf(dynamic)))
        val readOnly = MenuSchema(filter, NodeKind.List, emptyList(), listOf("print"), schema.arguments)
        assertEquals("", addCommands(Platform.RouterOs, filter, readOnly, listOf(accept)))
    }

    @Test
    fun aScriptHasTheMenuOnALineOfItsOwn() {
        val drop = Item("*2", mapOf("chain" to "input", "action" to "drop"))
        assertEquals(
            "/ip firewall filter\nadd chain=forward action=accept protocol=tcp dst-port=443 comment=web\nadd chain=input action=drop",
            addScript(Platform.RouterOs, filter, schema, listOf(accept, drop)),
        )
        assertEquals("", addScript(Platform.RouterOs, filter, schema, emptyList()))
    }

    @Test
    fun whatIsCopiedReadsBackAsTheSameAdds() {
        val tricky = Item("*2", mapOf("chain" to "input", "action" to "drop", "comment" to "say \"hi\"; \$x\nnext\tline \\ end"))
        val rows = listOf(accept, tricky)
        val expected = rows.map { filter to addValues(schema, it) }
        assertEquals(expected, addsOf(addCommands(Platform.RouterOs, filter, schema, rows)))
        assertEquals(expected, addsOf(addScript(Platform.RouterOs, filter, schema, rows)))
    }

    @Test
    fun aRowOfUciIsTheUciCommandsThatAddItAndThenCommitThem() {
        val rules = MenuPath.parse("firewall/rule")
        val uci = MenuSchema(
            rules, NodeKind.List, emptyList(), listOf("add", "print"),
            listOf(Argument("name", ValueType.Text()), Argument("target", ValueType.Text()), Argument("enabled", ValueType.Flag, default = "1"), Argument("dest_port", ValueType.PortList)),
        )
        val row = Item("cfg0a", mapOf(".name" to "cfg0a", ".type" to "rule", "name" to "Allow-SSH", "target" to "ACCEPT", "enabled" to "1", "dest_port" to "22"))
        assertEquals(
            "uci add firewall rule\nuci set firewall.@rule[-1].name='Allow-SSH'\nuci set firewall.@rule[-1].target='ACCEPT'\nuci set firewall.@rule[-1].dest_port='22'\nuci commit firewall",
            addCommands(Platform.OpenWrt, rules, uci, listOf(row)),
        )
        assertEquals(addCommands(Platform.OpenWrt, rules, uci, listOf(row)), addScript(Platform.OpenWrt, rules, uci, listOf(row)))
    }

    @Test
    fun aNamedSectionOfUciKeepsItsName() {
        val interfaces = MenuPath.parse("network/interface")
        val named = MenuSchema(
            interfaces, NodeKind.List, emptyList(), listOf("add", "print"),
            listOf(Argument(".name", ValueType.Text(), fixed = true), Argument("proto", ValueType.Text()), Argument("dns", ValueType.Many(ValueType.Ip4, "\n"))),
        )
        val row = Item("guest", mapOf(".name" to "guest", "proto" to "static", "dns" to "192.0.2.1\n192.0.2.2"))
        assertEquals(
            "uci set network.guest=interface\nuci set network.guest.proto='static'\nuci -q delete network.guest.dns\n" +
                "uci add_list network.guest.dns='192.0.2.1'\nuci add_list network.guest.dns='192.0.2.2'\nuci commit network",
            addCommands(Platform.OpenWrt, interfaces, named, listOf(row)),
        )
    }
}
