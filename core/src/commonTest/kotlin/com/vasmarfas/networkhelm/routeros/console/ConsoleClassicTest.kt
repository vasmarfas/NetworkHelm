package com.vasmarfas.networkhelm.routeros.console

import com.vasmarfas.networkhelm.routeros.api.ApiTrapException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ConsoleClassicTest {
    private fun call(vararg words: String, yesNo: Set<String> = setOf("disabled"), refused: Map<String, Set<String>> = emptyMap()) = classicCall(words.toList(), yesNo, refused)

    @Test
    fun elementsAreFoundAndEachIsGotWhole() {
        val call = call("/ip/address/print", "=.proplist=.id,address", "?disabled=false", "?interface=ether1")
        assertEquals(":foreach i in=[/ip address find where disabled=no && interface=\"ether1\"] do={ \$rdrow [/ip address get \$i] }", call.line)
        assertEquals(Reply.Records, call.reply)
        assertEquals(listOf("address"), call.proplist)
        assertEquals("/ip address", call.menu)
        assertEquals(setOf("find"), call.offered)
    }

    @Test
    fun aMenuOfSettingsIsGotAtOnce() {
        val call = call("/ip/dns/print", refused = mapOf("/ip dns" to setOf("find")))
        assertEquals("\$rdrow [/ip dns get]", call.line)
        assertEquals(emptySet(), call.offered)
    }

    @Test
    fun aCountIsAProbeOfWhatTheMenuIs() {
        val call = call("/ip/address/print", "=count-only=", "?.id=*1A", "?>mtu=1000", "?comment=x y")
        assertEquals("/ip address print count-only where .id=*1A && mtu>1000 && comment=\"x y\"", call.line)
        assertEquals(Reply.Count, call.reply)
        assertTrue(call.probe)
    }

    @Test
    fun theChangesAreMadeInsideAPutSoThatTheConsoleDoesNotAskForWhatIsMissing() {
        assertEquals(":put [/ip firewall address-list add list=rd address=203.0.113.9 comment=\"a b\"]", call("/ip/firewall/address-list/add", "=list=rd", "=address=203.0.113.9", "=comment=a b").line)
        assertEquals(":put [/ip address set numbers=*1 disabled=yes comment=x]", call("/ip/address/set", "=.id=*1", "=disabled=true", "=comment=x").line)
        assertEquals(":put [/ip address remove numbers=*1,*2]", call("/ip/address/remove", "=.id=*1,*2").line)
        assertEquals(":put [/ip address set numbers=*1 comment=\"\"]", call("/ip/address/unset", "=.id=*1", "=value-name=comment").line)
        assertTrue(call("/ip/address/disable", "=.id=*1").changes)
    }

    @Test
    fun aPasswordIsChangedWithItsOwnCommand() {
        val call = call("/password", "=old-password=old", "=new-password=Nw 1", "=confirm-new-password=Nw 1")
        assertEquals("/password old-password=old new-password=\"Nw 1\" confirm-new-password=\"Nw 1\"", call.line)
        assertEquals(Reply.Nothing, call.reply)
    }

    @Test
    fun theRatesOfSeveralInterfacesAreAskedForOneByOne() {
        val call = call("/interface/monitor-traffic", "=interface=ether1,lo", "=once=")
        assertEquals(":foreach n in={\"ether1\";\"lo\"} do={ \$rdrow [/interface monitor-traffic \$n once as-value] }", call.line)
        assertFailsWith<ApiTrapException> { call("/interface/monitor-traffic", "=once=") }
    }

    @Test
    fun aCommandThatOnlyPrintsTextIsNotThere() {
        assertEquals(0, assertFailsWith<ApiTrapException> { call("/tool/ping", "=address=127.0.0.1") }.category)
    }

    @Test
    fun aScriptRunsAsItIs() {
        val script = call("/execute", "=script=/ip firewall export", "=as-string=")
        assertEquals("/ip firewall export", script.line)
        assertEquals(Reply.Text, script.reply)
    }

    @Test
    fun recordsGiveTheTypesTheApiHas() {
        val text = listOf(
            "\u0001.id\u0002id\u0002*1",
            "\u0001uptime\u0002time\u00021w2d03:04:05",
            "\u0001policy\u0002array\u0002local;ssh;!dude",
            "\u0001disabled\u0002bool\u0002false",
            "\u0001comment\u0002str\u0002line one",
            "line two; a=b",
            "\u0001gone\u0002nil\u0002",
            "\u0001local\u0002ip6\u0002::ffff:192.0.2.66",
            "\u0001servers\u0002array\u0002::ffff:1.1.1.1;2001:db8::1",
            "\u0003",
            "\u0001.id\u0002id\u0002*2",
            "\u0001ttl\u0002time\u000200:00:02",
            "\u0003",
        ).joinToString("\n")
        val records = records(text)
        assertEquals(
            listOf(
                mapOf(
                    ".id" to "*1", "uptime" to "1w2d3h4m5s", "policy" to "local,ssh,!dude", "disabled" to "false", "comment" to "line one\nline two; a=b",
                    "local" to "192.0.2.66", "servers" to "1.1.1.1,2001:db8::1",
                ),
                mapOf(".id" to "*2", "ttl" to "2s"),
            ),
            records.rows,
        )
        assertEquals(setOf("disabled"), records.flags)
    }

    @Test
    fun whatARouterOs6ConsoleSaysOfACountIsWhatTheApiSays() {
        assertEquals("unknown parameter", probed("expected end of command (line 1 column 27)"))
        assertEquals("unknown parameter", probed("bad parameter count-only (line 1 column 47)"))
        assertEquals("no such command", probed("bad parameter print (line 1 column 38)"))
        assertEquals("no such command or directory (print)", probed("bad command name print (line 1 column 17)"))
        assertEquals("no such command or directory (nope)", probed("bad command name nope (line 1 column 14)"))
        assertEquals("no such item (4)", probed("no such item (4)"))
    }
}
