package com.vasmarfas.networkhelm.openwrt

import com.vasmarfas.networkhelm.model.Platform
import com.vasmarfas.networkhelm.packs.Menu
import com.vasmarfas.networkhelm.packs.MenuEntry
import com.vasmarfas.networkhelm.packs.NativeMenu
import com.vasmarfas.networkhelm.packs.Pack
import com.vasmarfas.networkhelm.packs.PackForm
import com.vasmarfas.networkhelm.packs.PackFormTab
import com.vasmarfas.networkhelm.packs.PackPath
import com.vasmarfas.networkhelm.packs.PackTab
import com.vasmarfas.networkhelm.packs.PackWindow
import com.vasmarfas.networkhelm.packs.packName
import com.vasmarfas.networkhelm.routeros.schema.label
import kotlinx.serialization.json.contentOrNull

internal fun luciApps(menu: List<LuciNode>, covered: Set<String>): List<LuciNode> =
    menu.filter { it.app.startsWith("luci-app-") && it.path.count { char -> char == '/' } == 2 && it.title != null && !(it.configs.isNotEmpty() && covered.containsAll(it.configs)) }

internal suspend fun luciPack(app: LuciNode, menu: List<LuciNode>, covered: Set<String>, maps: suspend (String) -> List<LuciMap>): Pack? {
    val title = app.title ?: return null
    val pages = listOf(app) + menu.filter { it.path.substringBeforeLast('/') == app.path }.sortedWith(luciOrder)
    val tabs = pages.flatMap { page ->
        val view = page.view ?: return@flatMap emptyList()
        // a type with a leading underscore only holds other sections in LuCI (__tagsparent__ of DHCP)
        maps(view).filter { it.config !in covered }.flatMap { map ->
            map.allSections.mapNotNull { section -> section.type?.takeUnless { it.startsWith('_') }?.let { SectionTab(map.config, it, section, page.title ?: title) } }
        }
    }.distinctBy { it.path }
    if (tabs.isEmpty()) return null
    return Pack(
        platform = Platform.OpenWrt.packName,
        menu = listOf(places[app.path.split('/')[1]] ?: label(app.path.split('/')[1]), title),
        window = PackWindow(title, tabs.map { PackTab(it.title, it.path) }),
        paths = tabs.associate { it.path to PackPath(ordered = it.section.sortable == true, columns = it.fields.take(6), form = it.path) },
        forms = tabs.associate { it.path to PackForm(it.title, it.formTabs()) },
    )
}

internal suspend fun luciMenu(menu: List<LuciNode>, maps: suspend (String) -> List<LuciMap>): NativeMenu {
    val sections = menu.filter { it.path.startsWith("admin/") && it.path.count { char -> char == '/' } == 1 && it.title != null && it.path != "admin/logout" }
        .sortedWith(luciOrder)
    val packs = mutableListOf<Pack>()
    val items = sections.mapNotNull { section ->
        val pages = menu.filter { it.path.substringBeforeLast('/') == section.path && it.title != null }.sortedWith(luciOrder)
        val entries = pages.mapNotNull { page ->
            val title = page.title ?: return@mapNotNull null
            luciPages[page.path]?.let { return@mapNotNull MenuEntry(title, it) }
            val pack = luciPack(page, menu, emptySet(), maps) ?: return@mapNotNull MenuEntry(title, page = page.path)
            packs += pack
            MenuEntry(title, pack.window.tabs.first().path)
        }
        section.title?.takeIf { entries.isNotEmpty() }?.let { MenuEntry(it, items = entries) }
    }
    val last = listOf(MenuEntry("Files", "status/files"), MenuEntry("Log", "status/log"), MenuEntry("New Terminal", window = "terminal"))
    return NativeMenu(Menu(platform = Platform.OpenWrt.packName, items = items + last), packs)
}

// LuCI 23.05 and later name some of these pages differently
private val luciPages = mapOf(
    "admin/status/overview" to "status/resource",
    "admin/status/routes" to "status/routes",
    "admin/status/routesj" to "status/routes",
    "admin/status/nftables" to "status/firewall",
    "admin/status/processes" to "status/processes",
    "admin/status/realtime" to "tool/traffic",
    "admin/status/syslog" to "status/log",
    "admin/status/logs" to "status/log",
    "admin/system/system" to "system/system",
    "admin/system/admin" to "tool/password",
    "admin/system/package-manager" to "status/packages",
    "admin/system/opkg" to "status/packages",
    "admin/system/startup" to "status/services",
    "admin/system/crontab" to "status/scheduler",
    "admin/system/leds" to "system/led",
    "admin/system/flash" to "status/files",
    "admin/system/reboot" to "tool/reboot",
    "admin/network/network" to "network/interface",
    "admin/network/wireless" to "wireless/wifi-iface",
    "admin/network/routes" to "network/route",
    "admin/network/dhcp" to "dhcp/dnsmasq",
    "admin/network/diagnostics" to "tool/ping",
    "admin/network/firewall" to "firewall/zone",
    "admin/network/sqm" to "sqm/queue",
    "admin/services/ddns" to "ddns/service",
    "admin/services/mixomo" to "mixomo/overview",
    "admin/services/pbr" to "pbr/policy",
    "admin/services/upnp" to "status/upnp",
    "admin/statistics/collectd" to "luci_statistics/statistics",
    "admin/status/wireguard" to "status/wireguard",
)

private class SectionTab(config: String, type: String, val section: LuciSection, page: String) {
    val path = "$config/$type"
    val title = section.title?.contentOrNull ?: page
    val fields = section.options.mapNotNull { it.argument()?.name }.distinct()

    fun formTabs(): List<PackFormTab> {
        val loose = section.options.filter { it.tab == null || section.tabs.none { tab -> tab.name == it.tab } }.mapNotNull { it.argument()?.name }.distinct()
        val tabbed = section.tabs.map { tab -> PackFormTab(tab.title?.contentOrNull ?: label(tab.name), section.options.filter { it.tab == tab.name }.mapNotNull { it.argument()?.name }.distinct()) }
        return (listOf(PackFormTab(title, loose)) + tabbed).filter { it.fields.isNotEmpty() }.ifEmpty { listOf(PackFormTab(title, fields)) }
    }
}

private val luciOrder = compareBy<LuciNode>({ it.order ?: 1000 }, { it.path.substringAfterLast('/') })

private val places = mapOf("services" to "Services", "system" to "System", "network" to "IP", "vpn" to "VPN", "status" to "Status")
