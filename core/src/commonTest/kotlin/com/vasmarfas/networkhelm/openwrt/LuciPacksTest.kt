package com.vasmarfas.networkhelm.openwrt

import com.vasmarfas.networkhelm.model.ValueType
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class LuciPacksTest {

    private val menu = """
        {
            "admin/services/rdtest": { "title": "RD Test", "order": 50, "action": { "type": "firstchild" }, "depends": { "uci": { "rdtest": true } } },
            "admin/services/rdtest/settings": { "title": "Settings", "order": 2, "action": { "type": "view", "path": "rdtest/settings" } },
            "admin/services/rdtest/status": { "title": "Status", "order": 1, "action": { "type": "view", "path": "rdtest/status" } },
            "admin/services/rdtest/settings/deep": { "title": "Deep", "action": { "type": "view", "path": "rdtest/deep" } }
        }
    """

    @Test
    fun anApplicationGetsAWindowWithATabForEachSectionOfItsViews() = runTest {
        val nodes = menuNodes("luci-app-rdtest", menu) + menuNodes("luci-mod-network", """{ "admin/network/dhcp": { "title": "DHCP", "action": { "type": "view", "path": "network/dhcp" } } }""")
        assertEquals(listOf("rdtest"), nodes.first().configs)
        val global = LuciSection("NamedSection", "global", options = listOf(LuciOption("Value", "password", password = true)))
        val instance = LuciSection(
            "TypedSection",
            "instance",
            JsonPrimitive("Instances"),
            addremove = true,
            sortable = true,
            tabs = listOf(LuciTab("general", JsonPrimitive("General Settings")), LuciTab("advanced", JsonPrimitive("Advanced"))),
            options = listOf(
                LuciOption("Flag", "enabled", JsonPrimitive("Enable"), tab = "general"),
                LuciOption("Value", "port", tab = "advanced"),
                LuciOption("Value", "port", tab = "advanced"),
                LuciOption("DummyValue", "state"),
                LuciOption("Value", "name"),
            ),
        )
        val maps = mapOf(
            "rdtest/settings" to listOf(LuciMap("rdtest", listOf(global, instance)), LuciMap("dhcp", listOf(LuciSection("TypedSection", "host")))),
            "rdtest/deep" to listOf(LuciMap("rdtest", listOf(LuciSection("TypedSection", "deep")))),
        )
        val apps = luciApps(nodes, setOf("dhcp"))
        assertEquals(listOf("admin/services/rdtest"), apps.map { it.path })
        assertEquals(emptyList(), luciApps(nodes, setOf("rdtest")), "an application whose configs bundled packs show gets no window")
        val pack = assertNotNull(luciPack(apps.single(), nodes, setOf("dhcp")) { maps[it].orEmpty() })
        assertEquals(listOf("Services", "RD Test"), pack.menu)
        assertEquals(listOf("Settings" to "rdtest/global", "Instances" to "rdtest/instance"), pack.window.tabs.map { it.title to it.path })
        assertEquals(true, pack.paths["rdtest/instance"]?.ordered)
        assertEquals(listOf("enabled", "port", "name"), pack.paths["rdtest/instance"]?.columns)
        assertEquals(
            listOf("Instances" to listOf("name"), "General Settings" to listOf("enabled"), "Advanced" to listOf("port")),
            pack.forms["rdtest/instance"]?.tabs?.map { it.title to it.fields },
        )
        assertEquals(ValueType.Secret, global.options.single().argument()?.type)
    }

    @Test
    fun theMenuOfLuciKeepsItsOrderAndEveryPage() = runTest {
        val nodes = menuNodes("luci-base", """{ "admin/services": { "title": "Services", "order": 40 } }""") +
            menuNodes("luci-app-adblock", """{ "admin/services/adblock": { "title": "Adblock", "order": "60", "action": { "type": "alias", "path": "admin/services/adblock/overview" } } }""") +
            menuNodes("luci-app-mixomo", """{ "admin/services/mixomo": { "title": "Mixomo", "order": 60, "action": { "type": "view", "path": "mihomo/config" } } }""") +
            menuNodes("luci-app-ddns", """{ "admin/services/ddns": { "title": "Dynamic DNS", "order": 59, "action": { "type": "view", "path": "ddns/overview" } } }""") +
            menuNodes("luci-app-upnp", """{ "admin/services/upnp": { "title": "UPnP IGD & PCP", "action": { "type": "view", "path": "upnp/upnp" } } }""")
        val services = luciMenu(nodes) { emptyList() }.menu.items.single { it.title == "Services" }.items
        assertEquals(listOf("Dynamic DNS", "Mixomo", "Adblock", "UPnP IGD & PCP"), services.map { it.title })
        assertEquals(listOf("ddns/service", "mixomo/overview", null, "status/upnp"), services.map { it.path })
        assertEquals("admin/services/adblock", services[2].page)
    }
}
