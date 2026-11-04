package com.vasmarfas.networkhelm.uidefs

import com.vasmarfas.networkhelm.packs.Pack
import com.vasmarfas.networkhelm.packs.PackFill
import com.vasmarfas.networkhelm.packs.PackLink

// the conversions of the router that hand a value over as it stands; the others need a number or a list the client has no reading for
private val handedOver = setOf("u32_network", "any_network", "multi_network")

// the types of the Telnet window of WinBox: the router connects by an address or by a MAC address
private val clients = mapOf(1L to "telnet", 3L to "mac-telnet")

private val windowKinds = setOf("map", "item", "query", "action")

// an item of the context menu of a table: the window it opens by its group, name and tab (or the window of the client that stands for it), and the fields it fills from the row
internal class UiMenu(val label: String, val group: String?, val entry: String, val tab: String?, val fill: List<PackFill>, val autostart: Boolean, val global: Boolean, val client: String? = null)

// a condition on fields the table does not show, or one item for several rows, cannot be told from the rows the client reads
internal fun contextMenus(window: UiNode): List<UiMenu> = window.children.filter { it.kind == "contextmenu" }.mapNotNull { node ->
    val entry = node.text("open") ?: return@mapNotNull null
    val label = node.name?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
    if (node.flag("mergeobjs") || node.text("on") != null) return@mapNotNull null
    val client = client(node)
    val fill = fills(node, window.text("nameval")?.trim()?.takeIf { it.isNotEmpty() && it != "Name" }, client != null) ?: return@mapNotNull null
    UiMenu(label, node.text("group")?.takeIf { it.isNotEmpty() }, entry, node.text("tab")?.takeIf { it.isNotEmpty() }, fill, node.flag("autostart"), node.flag("globalbutton"), client)
}

private fun linkOf(item: JgValue) = (item as? JgObject)?.let { UiNode(null, it.fields, emptyList()) }

private fun client(node: UiNode): String? {
    if (node.text("open") != "Telnet") return null
    return (node.attrs["link"] as? JgList)?.items.orEmpty().firstNotNullOfOrNull { item ->
        linkOf(item)?.takeIf { it.text("conv") == "put" && it.text("dst")?.trim() == "Type" }?.number("val")?.let(clients::get)
    }
}

// a field without a source takes the row by its name, which the table names by a label of its own unless it is Name; a field that is put off
// (a filter the window starts with) stays as the window has it; null: a conversion this client has no reading for
private fun fills(node: UiNode, identity: String?, typed: Boolean): List<PackFill>? {
    val fill = mutableListOf<PackFill>()
    for (item in (node.attrs["link"] as? JgList)?.items.orEmpty()) {
        val link = linkOf(item) ?: return null
        val to = link.text("dst")?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        when (link.text("conv")) {
            null, in handedOver -> fill += PackFill(to, link.text("src")?.trim() ?: identity)
            "put" -> if (!(link.flag("bool") && link.number("val") == 0L) && !(typed && to == "Type")) return null
            else -> return null
        }
    }
    return fill
}

internal fun menuLinks(menus: List<UiMenu>, entries: List<UiNode>, packs: List<Pack>): List<PackLink> = menus.mapNotNull { menu ->
    val path = if (menu.client != null) "" else target(menu, packs) ?: return@mapNotNull null
    PackLink(menu.label, path, menu.client, fill = menu.fill, autostart = menu.autostart, global = menu.global, item = menu.client == null && menu.fill.isNotEmpty() && window(menu, entries)?.kind == "map")
}.distinctBy { it.title to it.path to it.window }

// the tab the item names, among the packs the router describes and those the client brings
private fun target(menu: UiMenu, packs: List<Pack>): String? {
    val place = listOfNotNull(menu.group, menu.entry).map(::squeezed)
    val wanted = squeezed(menu.tab ?: menu.entry)
    return packs.filter { pack -> pack.menu.map(::squeezed) == place }.firstNotNullOfOrNull { pack ->
        pack.window.tabs.firstOrNull { squeezed(it.title) == wanted }?.path ?: pack.window.tabs.singleOrNull()?.path?.takeIf { wanted == place.last() }
    }
}

private fun window(menu: UiMenu, entries: List<UiNode>): UiNode? {
    val group = squeezed(menu.group.orEmpty())
    val entry = entries.firstOrNull { node ->
        squeezed(node.name.orEmpty()) == squeezed(menu.entry) && squeezed((node.text("group") ?: node.text("section")).orEmpty()) == group
    } ?: return null
    val wanted = squeezed(menu.tab ?: menu.entry)
    return entry.children.firstOrNull { it.kind in windowKinds && squeezed(it.label.orEmpty()) == wanted }
}
