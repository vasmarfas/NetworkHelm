package com.vasmarfas.networkhelm.packs

import com.vasmarfas.networkhelm.model.Argument
import com.vasmarfas.networkhelm.model.DeviceException
import com.vasmarfas.networkhelm.model.ErrorCategory
import com.vasmarfas.networkhelm.model.ListEvent
import com.vasmarfas.networkhelm.model.MenuPath
import com.vasmarfas.networkhelm.model.MenuSchema
import com.vasmarfas.networkhelm.model.NodeKind
import com.vasmarfas.networkhelm.model.ValueType
import com.vasmarfas.networkhelm.routeros.RouterOsVersion
import com.vasmarfas.networkhelm.routeros.absentNames
import com.vasmarfas.networkhelm.routeros.knownArguments
import com.vasmarfas.networkhelm.session.DeviceSession
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first

class PackFieldsSession(private val delegate: DeviceSession, packs: Collection<Pack>) : DeviceSession by delegate {
    private val fields = mutableMapOf<MenuPath, MutableSet<String>>()
    private val readonly = mutableMapOf<MenuPath, MutableSet<String>>()
    private val filled = mutableSetOf<MenuPath>()
    private val actions = mutableMapOf<MenuPath, MutableSet<String>>()
    private val editable = mutableMapOf<MenuPath, MutableSet<String>>()
    private val packed = mutableSetOf<MenuPath>()
    private val types = mutableMapOf<MenuPath, MutableMap<String, ValueType>>()
    private val running = RouterOsVersion.parse(delegate.device.version)

    init {
        for (pack in packs.filter { it.platform == delegate.device.platform.packName && running?.within(it.since, it.until) != false }) {
            for ((path, shown) in pack.paths) {
                packed += MenuPath.parse(path)
                readonly.getOrPut(MenuPath.parse(path)) { mutableSetOf() } += shown.readonly
                if (shown.form == null && shown.columns.isNotEmpty() && shown.columns.all { it in shown.readonly }) filled += MenuPath.parse(path)
                actions.getOrPut(MenuPath.parse(path)) { mutableSetOf() } += shown.actions.map { it.command }
                types.getOrPut(MenuPath.parse(path)) { mutableMapOf() } += shown.types
                val form = shown.form?.let(pack.forms::get) ?: continue
                for (tab in form.tabs) {
                    if (tab.readonly || tab.monitor != null || running?.within(tab.since, tab.until) == false) continue
                    fields.getOrPut(MenuPath.parse(tab.path ?: path)) { mutableSetOf() } += tab.properties
                    if (pack.until != null) editable.getOrPut(MenuPath.parse(tab.path ?: path)) { mutableSetOf() } += tab.properties
                }
            }
        }
    }

    override suspend fun describe(path: MenuPath): MenuSchema = shaped(delegate.describe(path), path, offer = true)

    override suspend fun outline(path: MenuPath): MenuSchema = shaped(delegate.outline(path), path, offer = false)

    private suspend fun shaped(schema: MenuSchema, path: MenuPath, offer: Boolean): MenuSchema {
        val fixed = readonly[path].orEmpty()
        val own = editable[path].orEmpty()
        val common = if (schema.kind == NodeKind.List && path in packed && path !in filled) listArguments else emptyMap()
        val given = types[path].orEmpty().mapValues { (name, type) -> Argument(name, type) }
        val typed = (common + knownArguments(path) + given).mapValues { (name, argument) -> if (argument.readOnly && name in own) argument.copy(readOnly = false) else argument }
        val lacking = running?.let { absentNames(path, it) }.orEmpty()
        val known = schema.arguments.filter { it.name !in fixed }.map { typed[it.name] ?: it }
        val more = (fields[path].orEmpty() + typed.keys).filter { name -> name !in fixed && name !in lacking && known.none { it.name == name } }
        val commands = (if (path in filled) schema.commands.filter { it == "print" || it == "remove" } else schema.commands).filter { it !in lacking } +
            actions[path].orEmpty().filter { it !in schema.commands && it !in lacking }
        val listed = mutableMapOf<String, List<String>>()
        val local = localReferences[path.toString()].orEmpty()
        val arguments = (known + more.map { typed[it] ?: Argument(it, ValueType.Text()) }).map { argument ->
            val source = (local[argument.name] ?: references[argument.name])?.takeIf { offer && argument.type == ValueType.Text() && !argument.readOnly } ?: return@map argument
            argument.copy(choices = listed.getOrPut(source) { names(MenuPath.parse(source)) })
        }
        return MenuSchema(schema.path, schema.kind, schema.children, commands, arguments, lacks = lacking)
    }

    private suspend fun names(path: MenuPath): List<String> = try {
        delegate.watch(path, properties = listOf("name")).filterIsInstance<ListEvent.Snapshot>().first().items.mapNotNull { it["name"] }
    } catch (e: DeviceException) {
        if (e.category == ErrorCategory.ConnectionLost) throw e
        emptyList()
    }

    private companion object {
        val listArguments = mapOf("comment" to Argument("comment", ValueType.Text()), "disabled" to Argument("disabled", ValueType.YesNo))

        val references = mapOf(
            "interface" to "interface",
            "in-interface" to "interface",
            "out-interface" to "interface",
            "master-interface" to "interface",
            "bridge" to "interface/bridge",
            "interface-list" to "interface/list",
            "in-interface-list" to "interface/list",
            "out-interface-list" to "interface/list",
            "allowed-interface-list" to "interface/list",
            "certificate" to "certificate",
            "default-profile" to "ppp/profile",
        )

        val localReferences = mapOf(
            "user" to mapOf("group" to "user/group"),
            "special-login" to mapOf("user" to "user", "port" to "port"),
            "system/ups" to mapOf("port" to "port"),
            "ppp/secret" to mapOf("profile" to "ppp/profile"),
            "interface/pppoe-client" to mapOf("profile" to "ppp/profile"),
            "interface/l2tp-client" to mapOf("profile" to "ppp/profile"),
            "interface/pptp-client" to mapOf("profile" to "ppp/profile"),
            "interface/sstp-client" to mapOf("profile" to "ppp/profile"),
            "interface/ovpn-client" to mapOf("profile" to "ppp/profile"),
            "ip/pool" to mapOf("next-pool" to "ip/pool"),
            "ip/dhcp-server" to mapOf("address-pool" to "ip/pool"),
            "ip/dhcp-server/lease" to mapOf("server" to "ip/dhcp-server"),
            "ip/hotspot" to mapOf("profile" to "ip/hotspot/profile", "address-pool" to "ip/pool"),
            "ip/hotspot/user" to mapOf("profile" to "ip/hotspot/user/profile", "server" to "ip/hotspot"),
            "ip/ipsec/peer" to mapOf("profile" to "ip/ipsec/profile"),
            "ip/ipsec/policy" to mapOf("peer" to "ip/ipsec/peer", "proposal" to "ip/ipsec/proposal", "group" to "ip/ipsec/policy/group"),
            "interface/wireless/scan" to mapOf("number" to "interface/wireless"),
            "interface/wireless/frequency-monitor" to mapOf("number" to "interface/wireless"),
            "interface/wireless/wps-client" to mapOf("number" to "interface/wireless"),
            "interface/wireless/setup-repeater" to mapOf("number" to "interface/wireless"),
        )
    }
}
