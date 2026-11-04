package com.vasmarfas.networkhelm.ui.main

import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SkikoComposeUiTest
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import com.vasmarfas.networkhelm.app.MemoryStorage
import com.vasmarfas.networkhelm.model.DeviceException
import com.vasmarfas.networkhelm.model.DeviceInfo
import com.vasmarfas.networkhelm.model.ErrorCategory
import com.vasmarfas.networkhelm.model.Platform
import com.vasmarfas.networkhelm.opnsense.OpnsenseCall
import com.vasmarfas.networkhelm.opnsense.OpnsenseSession
import com.vasmarfas.networkhelm.packs.MenuEntry
import com.vasmarfas.networkhelm.packs.bundledPacks
import com.vasmarfas.networkhelm.ui.table.TableLayouts
import com.vasmarfas.networkhelm.ui.theme.NetworkHelmTheme
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import java.io.File
import java.util.Locale
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class OpnsenseWindowsTest {

    private fun options(chosen: String, vararg keys: String) = keys.joinToString(",", "{", "}") { """"$it":{"value":"$it","selected":${if (it == chosen) 1 else 0}}""" }

    private fun rule(sequence: String, enabled: String, action: String, protocol: String, port: String, description: String) =
        """{"enabled":"$enabled","sequence":"$sequence","action":${options(action, "pass", "block", "reject")},"interface":${options("lan", "lan", "wan")},""" +
            """"direction":${options("in", "in", "out")},"ipprotocol":${options("inet", "inet", "inet6")},"protocol":${options(protocol, "any", "TCP", "UDP")},""" +
            """"source_net":"any","source_port":"","destination_net":"192.0.2.1","destination_port":"$port","gateway":${options("", "", "Null4")},"description":"$description"}"""

    private val answers = mapOf(
        "GET firewall/filter/get_rule" to """{"rule":${rule("1", "1", "pass", "any", "", "")}}""",
        "GET firewall/filter/get" to """{"filter":{"rules":{"rule":{"r2":${rule("20", "0", "block", "UDP", "53", "rd-test dns")},"r1":${rule("10", "1", "pass", "TCP", "22", "rd-test ssh")}}},"snatrules":{"rule":[]}}}""",
        "GET firewall/source_nat/get_rule" to """{"rule":{"enabled":"1","sequence":"1","interface":${options("wan", "lan", "wan")},"target":"wanip","description":""}}""",
        "GET firewall/source_nat/get" to """{"filter":{"rules":{"rule":[]},"snatrules":{"rule":[]}}}""",
        "GET firewall/alias/get_item" to """{"alias":{"enabled":"1","name":"","type":${options("", "host", "network")},"content":{"":{"value":"","selected":1}},"description":""}}""",
        "POST firewall/alias/search_item" to """{"rows":[{"uuid":"a1","enabled":"1","name":"rd_test","type":"Host(s)","content":"192.0.2.1","description":"rd-test"}],"rowCount":1,"total":1,"current":1}""",
        "POST firewall/alias/set_item/a1" to """{"result":"saved"}""",
    )

    private fun SkikoComposeUiTest.picture(name: String) {
        val directory = File("build/screenshots").also { it.mkdirs() }
        ImageIO.write(captureToImage().toAwtImage(), "png", File(directory, "$name.png"))
    }

    @Test
    fun theFirewallShowsItsRulesWholeAndAnAliasIsDisabled() = runDesktopComposeUiTest(1280, 800) {
        Locale.setDefault(Locale.ENGLISH)
        val calls = mutableListOf<Pair<String, JsonObject?>>()
        val api = OpnsenseCall { method, path, body ->
            calls += "$method $path" to body
            Json.parseToJsonElement(answers["$method $path"] ?: throw DeviceException(ErrorCategory.MissingItem, "this OPNsense has no /api/$path"))
        }
        val session = OpnsenseSession(api, DeviceInfo(Platform.Opnsense, "OPNsense.localdomain", "24.7", "amd64", "", emptySet(), setOf("read", "write")))
        val storage = MemoryStorage()
        val tab = RouterTab(session, "192.0.2.1", bundledPacks().values, storage, TableLayouts(storage))
        setContent { NetworkHelmTheme(dark = true) { MainScreen(tab, onDisconnect = {}) } }
        tab.open(MenuEntry("Firewall", "firewall/filter"))
        waitUntil(timeoutMillis = 5_000) { onAllNodes(hasText("rd-test ssh")).fetchSemanticsNodes().isNotEmpty() }
        for (column in listOf("Sequence", "Action", "IP Version", "Source", "Destination", "Destination Port")) onAllNodes(hasText(column)).onFirst().assertExists()
        onAllNodes(hasText("block")).onFirst().assertExists()
        picture("opnsense-firewall")

        onNodeWithText("Aliases").performClick()
        waitUntil(timeoutMillis = 5_000) { onAllNodes(hasText("rd_test")).fetchSemanticsNodes().isNotEmpty() }
        onNodeWithText("rd_test").performClick()
        onAllNodes(hasText("Disable")).onFirst().performClick()
        waitUntil(timeoutMillis = 5_000) { calls.any { it.first == "POST firewall/alias/set_item/a1" } }
        assertEquals<JsonElement?>(Json.parseToJsonElement("""{"alias":{"enabled":"0"}}"""), calls.last { it.first == "POST firewall/alias/set_item/a1" }.second)
        picture("opnsense-aliases")
    }

    private val none = """{"rows":[],"rowCount":0,"total":0,"current":1}"""

    private val dnsmasq = mapOf(
        "GET dnsmasq/settings/get_range" to """{"range":{"interface":{"":{"value":"Any","selected":1},"lan":{"value":"LAN","selected":0}},"start_addr":"","end_addr":"","subnet_mask":"","prefix_len":"",""" +
            """"lease_time":"","domain_type":{"interface":{"value":"Interface","selected":0},"range":{"value":"Range","selected":1}},"domain":"","nosync":"0","description":""}}""",
        "POST dnsmasq/settings/search_range" to """{"rows":[{"uuid":"r1","interface":"lan","%interface":"LAN","start_addr":"192.0.2.100","end_addr":"192.0.2.199","subnet_mask":"","prefix_len":"",""" +
            """"lease_time":"3600","domain_type":"range","domain":"","nosync":"0","description":"rd-test range"}],"rowCount":1,"total":1,"current":1}""",
        "GET dnsmasq/settings/get_host" to """{"host":{"host":"","domain":"","local":"0","ip":{"":{"value":"","selected":1}},"hwaddr":{"":{"value":"","selected":1}},"client_id":"","lease_time":"",""" +
            """"ignore":"0","descr":""}}""",
        "GET dnsmasq/settings/get_host/h1" to """{"host":{"host":"nas","domain":"lan.example","local":"0","ip":{"192.0.2.10":{"value":"192.0.2.10","selected":1}},""" +
            """"hwaddr":{"02:00:00:aa:bb:cc":{"value":"02:00:00:aa:bb:cc","selected":1}},"client_id":"","lease_time":"","ignore":"0","descr":"rd-test host"}}""",
        "POST dnsmasq/settings/search_host" to """{"rows":[{"uuid":"h1","host":"nas","domain":"lan.example","local":"0","ip":"192.0.2.10","hwaddr":"02:00:00:aa:bb:cc","client_id":"",""" +
            """"lease_time":"","ignore":"0","descr":"rd-test host"}],"rowCount":1,"total":1,"current":1}""",
        "POST dnsmasq/settings/set_host/h1" to """{"result":"saved"}""",
        "GET dnsmasq/settings/get_option" to """{"option":{"type":${options("set", "set", "match")},"option":{"":{"value":"","selected":1},"6":{"value":"dns-server [6]","selected":0}},"interface":${options("", "", "lan")},""" +
            """"value":"","force":"0","description":""}}""",
        "POST dnsmasq/settings/search_option" to none,
    )

    private val kea = mapOf(
        "GET kea/dhcpv4/get_subnet" to """{"subnet4":{"subnet":"","valid_lifetime":"","next_server":"","option_data_autocollect":"1","option_data":{"routers":{"":{"value":"","selected":1}},""" +
            """"domain_name_servers":{"":{"value":"","selected":1}},"domain_name":""},"option":[],"match-client-id":"1","allocator":${options("", "", "iterative")},"pools":"","description":""}}""",
        "GET kea/dhcpv4/get_subnet/s1" to """{"subnet4":{"subnet":"10.0.0.0/24","valid_lifetime":"","next_server":"","option_data_autocollect":"1","option_data":{"routers":""" +
            """{"10.0.0.1":{"value":"10.0.0.1","selected":1}},"domain_name_servers":{"":{"value":"","selected":1}},"domain_name":""},"option":[],"match-client-id":"1","allocator":${options("", "", "iterative")},""" +
            """"pools":"10.0.0.100-10.0.0.150","description":"rd-test subnet"}}""",
        "POST kea/dhcpv4/search_subnet" to """{"rows":[{"uuid":"s1","subnet":"10.0.0.0/24","valid_lifetime":"","next_server":"","option_data_autocollect":"1","option_data.routers":"10.0.0.1",""" +
            """"option_data.domain_name_servers":"","option_data.domain_name":"","option":"","match-client-id":"1","allocator":"","pools":"10.0.0.100-10.0.0.150","description":"rd-test subnet"}],""" +
            """"rowCount":1,"total":1,"current":1}""",
        "POST kea/dhcpv4/set_subnet/s1" to """{"result":"saved"}""",
        "GET kea/dhcpv4/get_reservation" to """{"reservation":{"subnet":{"s1":{"value":"10.0.0.0/24","selected":0}},"ip_address":"","hw_address":"","hostname":"","description":""}}""",
        "POST kea/dhcpv4/search_reservation" to """{"rows":[{"uuid":"v1","subnet":"s1","%subnet":"10.0.0.0/24","ip_address":"10.0.0.101","hw_address":"02:00:00:aa:bb:cd","hostname":"printer",""" +
            """"description":"rd-test reservation"}],"rowCount":1,"total":1,"current":1}""",
    )

    private val system = mapOf(
        "GET diagnostics/system/systemResources" to """{"memory":{"total":"2102894592","total_frmt":"2005","used":508536950,"used_frmt":"484"}}""",
        "GET diagnostics/system/systemTime" to """{"uptime":"00:02:28","datetime":"Sat Oct 31 11:02:58 UTC 2026","boottime":"Sat Oct 31 11:00:30 UTC 2026","loadavg":"0.24, 0.09, 0.03"}""",
        "GET diagnostics/system/systemDisk" to """{"devices":[{"device":"/dev/ufs/OPNsense_Nano","type":"ufs","blocks":"2.8G","used":"2.1G","available":"503M","used_pct":81,"mountpoint":"/"}]}""",
        "GET diagnostics/cpu_usage/getCPUType" to """["Intel(R) Core(TM) i7 (2 cores, 2 threads)"]""",
        "GET auth/user/get" to """{"user":{"uid":"2000","name":"","disabled":"0","scope":"user","authorizedkeys":"","otp_seed":"","shell":${options("", "", "/bin/sh")},"password":"","email":"","descr":"",""" +
            """"priv":{"page-all":{"value":"All pages","selected":0},"page-diagnostics":{"value":"Diagnostics: All","selected":0}},"group_memberships":{"1999":{"value":"admins","selected":0}}}}""",
        "POST auth/user/search" to """{"rows":[{"uuid":"u1","uid":"0","name":"root","disabled":"0","scope":"system","password":"hash","group_memberships":"1999","%group_memberships":"admins",""" +
            """"descr":"System Administrator","email":"","shell":""}],"rowCount":1,"total":1,"current":1}""",
        "GET auth/group/get" to """{"group":{"gid":"2000","name":"","scope":"user","description":"","member":[{"value":"root","selected":0}]}}""",
        "POST auth/group/search" to """{"rows":[{"uuid":"g1","gid":"1999","name":"admins","scope":"system","description":"System Administrators","priv":"page-all","%priv":"All pages",""" +
            """"member":"0","%member":"root"}],"rowCount":1,"total":1,"current":1}""",
        "POST trust/cert/search" to """{"rows":[{"uuid":"c1","descr":"Web GUI TLS certificate","name":"/CN=OPNsense.internal","caref":"","crt":"LS0t","prv":"LS0t","valid_from":"1793444859",""" +
            """"valid_to":"1827153373","in_use":"1"}],"rowCount":1,"total":1,"current":1}""",
    )

    private fun session(answers: Map<String, String>, calls: MutableList<Pair<String, JsonObject?>>) = OpnsenseSession(
        { method, path, body ->
            calls += "$method $path" to body
            Json.parseToJsonElement(answers["$method $path"] ?: throw DeviceException(ErrorCategory.MissingItem, "this OPNsense has no /api/$path"))
        },
        DeviceInfo(Platform.Opnsense, "OPNsense.internal", "26.7", "amd64", "", emptySet(), setOf("read", "write")),
    )

    @Test
    fun theDnsmasqWindowListsRangesAndHostsAndAHostIsChangedInItsDialog() = runDesktopComposeUiTest(1280, 800) {
        Locale.setDefault(Locale.ENGLISH)
        val calls = mutableListOf<Pair<String, JsonObject?>>()
        val storage = MemoryStorage()
        val tab = RouterTab(session(dnsmasq, calls), "192.0.2.1", bundledPacks().values, storage, TableLayouts(storage))
        setContent { NetworkHelmTheme(dark = true) { MainScreen(tab, onDisconnect = {}) } }
        tab.open(MenuEntry("DHCP Server (Dnsmasq)", "dnsmasq/range"))
        waitUntil(timeoutMillis = 5_000) { onAllNodes(hasText("192.0.2.100")).fetchSemanticsNodes().isNotEmpty() }
        for (column in listOf("Interface", "Start Address", "End Address", "Subnet Mask", "Lease Time")) onAllNodes(hasText(column)).onFirst().assertExists()
        onAllNodes(hasText("LAN")).onFirst().assertExists()
        picture("opnsense-dnsmasq-ranges")

        onNodeWithText("Hosts").performClick()
        waitUntil(timeoutMillis = 5_000) { onAllNodes(hasText("nas")).fetchSemanticsNodes().isNotEmpty() }
        onAllNodes(hasText("nas")).onLast().performMouseInput { doubleClick() }
        waitUntil(timeoutMillis = 5_000) { onAllNodes(hasSetTextAction() and hasText("rd-test host")).fetchSemanticsNodes().isNotEmpty() }
        picture("opnsense-dnsmasq-host")
        onNode(hasSetTextAction() and hasAnyAncestor(hasTestTag("field-descr"))).performTextReplacement("rd-test changed")
        onNodeWithText("OK").performClick()
        waitUntil(timeoutMillis = 5_000) { calls.any { it.first == "POST dnsmasq/settings/set_host/h1" } }
        assertEquals<JsonElement?>(Json.parseToJsonElement("""{"host":{"descr":"rd-test changed"}}"""), calls.last { it.first == "POST dnsmasq/settings/set_host/h1" }.second)

        onNodeWithText("Options").performClick()
        waitUntil(timeoutMillis = 5_000) { calls.any { it.first == "POST dnsmasq/settings/search_option" } }
        picture("opnsense-dnsmasq-options")
    }

    @Test
    fun theKeaWindowKeepsTheOptionsOfASubnetOnATabOfItsDialog() = runDesktopComposeUiTest(1280, 800) {
        Locale.setDefault(Locale.ENGLISH)
        val calls = mutableListOf<Pair<String, JsonObject?>>()
        val storage = MemoryStorage()
        val tab = RouterTab(session(kea, calls), "192.0.2.1", bundledPacks().values, storage, TableLayouts(storage))
        setContent { NetworkHelmTheme(dark = true) { MainScreen(tab, onDisconnect = {}) } }
        tab.open(MenuEntry("DHCP Server (Kea)", "kea/subnet"))
        waitUntil(timeoutMillis = 5_000) { onAllNodes(hasText("10.0.0.0/24")).fetchSemanticsNodes().isNotEmpty() }
        for (column in listOf("Subnet", "Pools", "Routers", "DNS Servers", "Valid Lifetime")) onAllNodes(hasText(column)).onFirst().assertExists()
        onAllNodes(hasText("10.0.0.0/24")).onLast().performMouseInput { doubleClick() }
        waitUntil(timeoutMillis = 5_000) { onAllNodes(hasSetTextAction() and hasText("rd-test subnet")).fetchSemanticsNodes().isNotEmpty() }
        picture("opnsense-kea-subnet")
        onAllNodes(hasText("Options")).onLast().performClick()
        waitUntil(timeoutMillis = 5_000) { onAllNodes(hasTestTag("field-option_data.routers")).fetchSemanticsNodes().isNotEmpty() }
        picture("opnsense-kea-subnet-options")
        onNode(hasSetTextAction() and hasAnyAncestor(hasTestTag("field-option_data.routers"))).performTextReplacement("10.0.0.254")
        onNodeWithText("OK").performClick()
        waitUntil(timeoutMillis = 5_000) { calls.any { it.first == "POST kea/dhcpv4/set_subnet/s1" } }
        assertEquals<JsonElement?>(Json.parseToJsonElement("""{"subnet4":{"option_data":{"routers":"10.0.0.254"}}}"""), calls.last { it.first == "POST kea/dhcpv4/set_subnet/s1" }.second)

        tab.open(MenuEntry("DHCP Server (Kea)", "kea/reservation"))
        waitUntil(timeoutMillis = 5_000) { onAllNodes(hasText("10.0.0.101")).fetchSemanticsNodes().isNotEmpty() }
        for (column in listOf("IP Address", "MAC Address", "Hostname")) onAllNodes(hasText(column)).onFirst().assertExists()
        picture("opnsense-kea-reservations")
    }

    @Test
    fun theSystemWindowsShowTheirListsAndSettings() = runDesktopComposeUiTest(1280, 800) {
        Locale.setDefault(Locale.ENGLISH)
        val storage = MemoryStorage()
        val tab = RouterTab(session(system, mutableListOf()), "192.0.2.1", bundledPacks().values, storage, TableLayouts(storage))
        setContent { NetworkHelmTheme(dark = true) { MainScreen(tab, onDisconnect = {}) } }
        tab.open(MenuEntry("Users", "system/users"))
        waitUntil(timeoutMillis = 5_000) { onAllNodes(hasText("System Administrator")).fetchSemanticsNodes().isNotEmpty() }
        for (column in listOf("Name", "Full Name", "Member Of", "Email")) onAllNodes(hasText(column)).onFirst().assertExists()
        onAllNodes(hasText("admins")).onFirst().assertExists()
        picture("opnsense-users")
        onAllNodes(hasText("New")).onFirst().performClick()
        waitUntil(timeoutMillis = 5_000) { onAllNodes(hasTestTag("field-password")).fetchSemanticsNodes().isNotEmpty() }
        picture("opnsense-user-new")
        onAllNodes(hasText("Privileges")).onLast().performClick()
        waitUntil(timeoutMillis = 5_000) { onAllNodes(hasText("All pages")).fetchSemanticsNodes().isNotEmpty() }
        picture("opnsense-user-privileges")
        onAllNodes(hasText("Cancel")).onLast().performClick()
        onNodeWithText("Groups").performClick()
        waitUntil(timeoutMillis = 5_000) { onAllNodes(hasText("System Administrators")).fetchSemanticsNodes().isNotEmpty() }
        for (column in listOf("Members", "Privileges")) onAllNodes(hasText(column)).onFirst().assertExists()
        onAllNodes(hasText("All pages")).onFirst().assertExists()

        tab.open(MenuEntry("Resources", "system/resources"))
        waitUntil(timeoutMillis = 5_000) { onAllNodes(hasText("00:02:28")).fetchSemanticsNodes().isNotEmpty() }
        for (field in listOf("Hostname", "CPU", "Load Average", "Used Memory", "Total Memory", "Disks")) onAllNodes(hasText(field)).onFirst().assertExists()
        assertTrue(onAllNodes(hasText("508536950")).fetchSemanticsNodes().isEmpty())
        picture("opnsense-resources")

        tab.open(MenuEntry("Certificates", "system/certificates"))
        waitUntil(timeoutMillis = 5_000) { onAllNodes(hasText("2027-11-25 14:36:13")).fetchSemanticsNodes().isNotEmpty() }
        for (column in listOf("Description", "Subject", "Valid From", "Valid To", "In Use")) onAllNodes(hasText(column, substring = true)).onFirst().assertExists()
        picture("opnsense-certificates")
    }
}
