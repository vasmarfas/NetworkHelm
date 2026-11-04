package com.vasmarfas.networkhelm.uidefs

// a field stands in the form where every rule holds: the field a rule asks about, and what the answer must be
class UiRule(val controller: UiNode, val pred: JgValue?)

class UiField(val label: String, val kind: String, val readonly: Boolean, val width: Int?, val node: UiNode, val needs: List<UiRule> = emptyList(), val autoset: Boolean = false)

// an inline tab is a heading of a section, and the sections of a window stand one under the other
class UiTab(val title: String, val items: List<UiField?>, val inline: Boolean = false)

class UiFlag(val name: String, val letter: String, val node: UiNode? = null)

// beside: the tuple whose toolbar holds the button; below: the field the toolbar follows; neither: a button of the window
// row: counts the toolbars and the separators of the window, so that the buttons of one toolbar share a row and a separator between two toolbars leaves a gap
class UiLink(val label: String, val entry: String, val group: String? = null, val beside: UiNode? = null, val below: UiNode? = null, val needs: List<UiRule> = emptyList(), val row: Int = 0)

// a button of a window: the number of the command of its handler, and where it stands
class UiDoit(val title: String, val number: Int, val confirm: Boolean, val needs: List<UiRule>, val tab: String, val beside: UiNode? = null, val below: UiNode? = null, val row: Int = 0)

class UiForm(
    val tabs: List<UiTab>,
    val columns: List<UiField>,
    val flags: List<UiFlag>,
    val commands: List<String>,
    val links: List<UiLink>,
    val extras: List<UiField> = emptyList(),
    val doits: List<UiDoit> = emptyList(),
    val buttons: List<UiLink> = emptyList(),
    val active: Boolean = false,
)

private val valueKinds = setOf(
    "string", "number", "enm", "bool", "ipaddr", "ip6addr", "macaddr", "interval", "bignumber", "multi", "multinumber", "multistring", "bytes", "bigbytes",
    "addr", "network", "network6", "secret", "union", "tuple", "set", "integer", "fixedpoint", "decimal", "bigdecimal", "dateandtime", "age", "raw", "numberrange",
    "multiipaddr", "multinumberrange", "bitrate", "bigbitrate", "unit", "bigunit", "multimacnetwork", "multinetwork", "multibignumber", "multituple", "multibits",
    "script", "netmask", "clocktime", "multiip6addr", "multilinestring", "tristate", "multitristate", "version", "timezone", "date", "file", "password", "opt",
    "not", "group", "numflag", "stringarray", "multiraw", "bigbitchangerate", "bigchangerate", "changerate",
)

// fields of these kinds are no part of a form: only the object model of the router reads them
private val nativeOnly = setOf("ipaddrandport", "hyperlink", "macnetwork", "numberrangelist", "clockdate", "packet", "numbar", "kbytes", "as")

private val holders = setOf("cond", "toolbar", "gridcell", "grid", "autoset")

// the conditions a part of a window names, and the fields they ask about: an object of a window has its own, and the window's stand behind them
private class Scope(val conds: Map<String, List<UiNode>>, val fields: Map<String, UiNode>, val parent: Scope?, val inherited: List<UiRule>) {
    fun cond(name: String): List<UiNode>? = conds[name] ?: parent?.cond(name)

    fun field(name: String): UiNode? = fields[name] ?: parent?.field(name)

    // a condition that names a field the window does not have asks nothing
    fun rules(name: String?): List<UiRule> {
        val entries = name?.let(::cond) ?: return emptyList()
        return entries.map { entry -> UiRule(entry.text("on")?.let(::field) ?: return emptyList(), entry.attrs["pred"]) }
    }

    fun under(on: String?, nodes: List<UiNode>) = Scope(nodes.condsByName(), nodes.fieldsByName(), this, inherited + rules(on))
}

private fun List<UiNode>.condsByName(): Map<String, List<UiNode>> = filter { it.kind == "cond" }.mapNotNull { cond -> cond.name?.let { it to cond.children } }.toMap()

private fun List<UiNode>.fieldsByName(): Map<String, UiNode> = buildMap {
    for (node in this@fieldsByName) {
        node.name?.takeIf { node.kind in valueKinds && it !in this }?.let { put(it, node) }
        if (node.kind == "group" || node.kind in holders) putAll(node.children.fieldsByName().filterKeys { it !in this })
    }
}

fun uiForm(window: UiNode): UiForm {
    val tabs = mutableListOf<UiTab>()
    var title = ""
    var items = mutableListOf<UiField?>()
    val columns = mutableListOf<UiField>()
    val extras = mutableListOf<UiField>()
    val flags = mutableListOf<UiFlag>()
    val commands = mutableListOf<String>()
    val links = mutableListOf<UiLink>()
    val doits = mutableListOf<UiDoit>()
    val buttons = mutableListOf<UiLink>()
    var around = emptyList<UiRule>()
    var inline = false
    var last: UiNode? = null
    var host: UiNode? = null
    var active = false
    var automatic = 0
    var row = 0

    fun close() {
        if (items.any { it != null }) tabs += UiTab(title, items, inline)
        items = mutableListOf()
    }

    // what a group holds is no part of a form, but the object model reads it as it reads the rest
    fun grouped(nodes: List<UiNode>) {
        for (node in nodes) {
            if ((node.kind in nativeOnly || node.kind in valueKinds) && node.name != null && !node.flag("nonpublic") && !node.flag("hide")) extras += field(node)
            if (node.kind == "group" || node.kind in holders) grouped(node.children)
        }
    }

    fun walk(nodes: List<UiNode>, scope: Scope) {
        for (node in nodes) {
            when (node.kind) {
                "tab" -> {
                    close()
                    title = node.name.orEmpty()
                    inline = node.flag("inline")
                    last = null
                    around = scope.rules(node.text("on"))
                }
                "separator" -> {
                    row++
                    if (items.isNotEmpty() && items.last() != null) items += null
                }
                "active" -> active = true
                "statusbar" -> node.children.filter { it.kind in valueKinds && it.name != null && it.number("width") != null }.forEach { columns += field(it) }
                "columnalias" -> node.name?.let { columns += field(node) }
                "doit" -> node.name?.takeIf { !node.flag("hide") && !node.flag("nonpublic") }?.let { name ->
                    commands += name
                    node.number("cmd")?.let { doits += UiDoit(name, it.toInt(), node.text("confirm") != null, around + scope.inherited + scope.rules(node.text("on")), title.ifEmpty { "General" }, host, last.takeIf { host == null }, row) }
                }
                "contextmenu" -> node.text("open")?.let { open -> if (node.flag("globalbutton")) links += UiLink(node.name.orEmpty(), open) else buttons += UiLink(node.name.orEmpty(), open, node.text("group"), host, last.takeIf { host == null }, around + scope.inherited + scope.rules(node.text("on")), row) }
                "enable" -> flags += UiFlag("disabled", "X")
                "dynamic" -> flags += UiFlag("dynamic", "D")
                "flag" -> if (node.name != null) node.text("hint")?.takeIf { it.isNotEmpty() }?.let { flags += UiFlag(node.name.orEmpty(), it, node) }
                "object" -> walk(node.children, scope.under(node.text("on"), node.children))
                "autoset" -> {
                    automatic++
                    walk(node.children, node.text("on")?.let { scope.under(it, node.children) } ?: scope)
                    automatic--
                }
                in holders -> {
                    if (node.kind == "toolbar") row++
                    walk(node.children, node.text("on")?.let { scope.under(it, node.children) } ?: scope)
                }
                in nativeOnly -> if (node.name != null && !node.flag("nonpublic") && !node.flag("hide")) extras += field(node)
                in valueKinds -> {
                    if (node.name != null && !node.flag("nonpublic") && !node.flag("hide")) {
                        val field = field(node, around + scope.inherited + scope.rules(node.text("on")), automatic > 0)
                        items += field
                        last = node
                        if (field.width != null && field.width > 0 || node.kind == "tuple" && node.children.any { (it.number("width") ?: 0) > 0 }) columns += field
                    }
                    if (node.kind == "group") grouped(node.children)
                    if (node.kind == "tuple") {
                        host = node
                        walk(node.children.filter { it.kind == "toolbar" }, scope.under(node.text("on"), emptyList()))
                        host = null
                    }
                }
            }
        }
    }
    walk(window.children, Scope(window.children.condsByName(), window.children.fieldsByName(), null, emptyList()))
    close()
    return UiForm(tabs.map { tab -> UiTab(tab.title.ifEmpty { "General" }, tab.items.dropLastWhile { it == null }, tab.inline) }, columns, flags, commands, links, extras, doits, buttons, active)
}

private fun field(node: UiNode, needs: List<UiRule> = emptyList(), autoset: Boolean = false) =
    UiField(node.label.orEmpty(), node.kind.orEmpty(), node.flag("ro"), node.number("width")?.toInt(), node, needs, autoset)
