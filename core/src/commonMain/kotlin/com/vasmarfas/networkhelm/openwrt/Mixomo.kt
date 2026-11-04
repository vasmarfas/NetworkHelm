package com.vasmarfas.networkhelm.openwrt

import com.vasmarfas.networkhelm.model.Argument
import com.vasmarfas.networkhelm.model.DeviceException
import com.vasmarfas.networkhelm.model.ErrorCategory
import com.vasmarfas.networkhelm.model.Item
import com.vasmarfas.networkhelm.model.ValueType
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive

internal class Mixomo(private val ubus: Ubus, private val objects: Set<String>, private val local: suspend (Long) -> String) {

    val lists: Map<String, Live> = listOfNotNull(
        ("mixomo/overview" to Live(listOf("mixomo-version", "mihomo", "mihomo-version", "active-config", "magitrickle", "magitrickle-version", "schedules", "rule-lists", "routing-clients", "dns-servers", "automatic-backups"), { listOf(Item("overview", overview())) }, settings = true))
            .takeIf { ROUTING in objects },
        ("mixomo/magitrickle" to Live(listOf("status", "version", "variant", "template"), { listOf(Item("magitrickle", magitrickle())) }, settings = true)).takeIf { ROUTING in objects },
        ("mixomo/rules" to Live(listOf("name", "size", "url", "interval", "updated"), ::ruleLists)).takeIf { PROFILES in objects },
        ("mixomo/schedule" to Live(listOf("name", "type", "profile", "disabled"), ::schedules)).takeIf { SCHEDULE in objects },
        ("mixomo/dns" to Live(listOf("name", "servers", "active"), ::presets)).takeIf { DNS in objects },
        ("mixomo/clients" to Live(
            listOf("name", "source", "connection-type", "priority", "disabled"),
            { rules(RULES).map { rule -> Item(rule.text("id"), mapOf("name" to rule.text("label"), "source" to rule.text("source"), "connection-type" to rule.text("backend"), "priority" to rule.text("priority"), "disabled" to disabled(rule))) } },
            writable = listOf("name", "source", "connection-type"),
            arguments = listOf(Argument("connection-type", ValueType.Enumeration(), BACKENDS, optional = true), Argument("disabled", ValueType.YesNo)),
            add = { values ->
                added(RULES) { call(ROUTING, "add", "source" to (values["source"] ?: required("source")), "label" to values["name"].orEmpty(), "backend" to values["connection-type"].orEmpty()) }
            },
            set = { id, changes ->
                if (changes.keys.any { it != "disabled" }) {
                    val rule = rule(RULES, id)
                    call(
                        ROUTING, "update", "id" to id, "source" to (changes["source"] ?: rule.text("source")), "label" to changed(changes, "name", rule.text("label")),
                        "backend" to (changes["connection-type"] ?: rule.text("backend")),
                    )
                }
                if ("disabled" in changes) call(ROUTING, "set_enabled", "id" to id, "enabled" to (changes["disabled"] !in YES))
            },
            remove = { ids -> ids.forEach { call(ROUTING, "delete", "id" to it) } },
            move = { ids, before -> reorder("rule", RULES, ids, before) },
        )).takeIf { ROUTING in objects },
        ("mixomo/exclusions" to Live(
            listOf("name", "destination", "priority", "disabled"),
            { rules(EXCLUSIONS).map { rule -> Item(rule.text("id"), mapOf("name" to rule.text("label"), "destination" to rule.text("dest"), "priority" to rule.text("priority"), "disabled" to disabled(rule))) } },
            writable = listOf("name", "destination"),
            arguments = listOf(Argument("disabled", ValueType.YesNo)),
            add = { values -> added(EXCLUSIONS) { call(ROUTING, "exclude_add", "dest" to (values["destination"] ?: required("destination")), "label" to values["name"].orEmpty()) } },
            set = { id, changes ->
                if (changes.keys.any { it != "disabled" }) {
                    val rule = rule(EXCLUSIONS, id)
                    call(ROUTING, "exclude_update", "id" to id, "dest" to (changes["destination"] ?: rule.text("dest")), "label" to changed(changes, "name", rule.text("label")))
                }
                if ("disabled" in changes) call(ROUTING, "exclude_set_enabled", "id" to id, "enabled" to (changes["disabled"] !in YES))
            },
            remove = { ids -> ids.forEach { call(ROUTING, "exclude_delete", "id" to it) } },
            move = { ids, before -> reorder("excl", EXCLUSIONS, ids, before) },
        )).takeIf { ROUTING in objects },
        ("mixomo/routing" to Live(
            listOf("router-traffic", "block-quic", "connection-type", "table"),
            {
                val status = call(ROUTING, "status")
                listOf(
                    Item(
                        "routing",
                        mapOf("router-traffic" to switch(status, "router"), "block-quic" to switch(status, "udp443"), "connection-type" to status.text("variant"), "table" to status.text("table")),
                    ),
                )
            },
            settings = true,
            writable = listOf("router-traffic", "block-quic"),
            arguments = listOf(Argument("router-traffic", ValueType.YesNo), Argument("block-quic", ValueType.YesNo)),
            set = { _, changes ->
                if ("router-traffic" in changes) call(ROUTING, "set_router", "enabled" to (changes["router-traffic"] in YES))
                if ("block-quic" in changes) call(ROUTING, "set_udp443", "enabled" to (changes["block-quic"] in YES))
            },
        )).takeIf { ROUTING in objects },
        ("mixomo/profiles" to Live(
            listOf("name", "active", "url", "interval", "updated"),
            {
                call(PROFILES, "list").objects("profiles").map { profile ->
                    Item(
                        profile.text("name"),
                        buildMap {
                            put("name", profile.text("name"))
                            put("active", switch(profile, "active"))
                            for ((field, key) in listOf("url" to "url", "interval" to "interval")) profile.text(key).takeIf { it.isNotEmpty() }?.let { put(field, it) }
                            profile.text("updated").takeIf { it.isNotEmpty() }?.let { put("updated", it.toLongOrNull()?.let { seconds -> local(seconds) } ?: it) }
                        },
                    )
                }
            },
            // the link of a subscription carries its token
            arguments = listOf(Argument("url", ValueType.Secret, optional = true), Argument("interval", ValueType.Integer(0, 8760), optional = true)),
            writable = listOf("name", "url", "interval"),
            add = { values ->
                val name = values["name"] ?: required("name")
                call(PROFILES, "create", "name" to name, "copyActive" to false)
                if (values["url"] != null || values["interval"] != null) source(name, values["url"].orEmpty(), values["interval"].orEmpty())
                name
            },
            set = { name, changes ->
                val renamed = changes["name"]?.takeIf { it != name }
                if (renamed != null) call(PROFILES, "rename_profile", "old" to name, "new" to renamed)
                if ("url" in changes || "interval" in changes) {
                    val profile = call(PROFILES, "list").objects("profiles").firstOrNull { it.text("name") == (renamed ?: name) } ?: throw DeviceException(ErrorCategory.MissingItem, "no profile ${renamed ?: name}")
                    source(renamed ?: name, changed(changes, "url", profile.text("url")), changed(changes, "interval", profile.text("interval")))
                }
            },
            remove = { names -> names.forEach { call(PROFILES, "delete", "name" to it) } },
            commands = listOf("activate", "refresh"),
            run = { command, arguments ->
                for (name in numbers(arguments)) {
                    if (command == "activate") call(PROFILES, "apply", "name" to name, "panel" to "") else call(PROFILES, "refresh", "type" to "config", "name" to name)
                }
            },
        )).takeIf { PROFILES in objects },
        ("mixomo/backups" to Live(
            listOf("file", "component", "created"),
            {
                call(BACKUP, "status").objects("backups").map { backup ->
                    val created = backup.text("created")
                    Item(backup.text("file"), mapOf("file" to backup.text("file"), "component" to backup.text("component"), "created" to (created.toLongOrNull()?.let { local(it) } ?: created)))
                }
            },
            remove = { files -> files.forEach { call(BACKUP, "delete", "file" to it) } },
            commands = listOf("create", "restore"),
            run = { command, arguments ->
                if (command == "create") call(BACKUP, "create", "component" to "all") else numbers(arguments).forEach { call(BACKUP, "restore", "file" to it) }
            },
        )).takeIf { BACKUP in objects },
    ).toMap()

    private suspend fun overview(): Map<String, String> {
        val magitrickle = magitrickle()
        val entries = listOf("time", "trigger").flatMap { read(SCHEDULE, "list").objects(it) }
        val settings = read(BACKUP, "status")["settings"] as? JsonObject
        return mapOf(
            "mixomo-version" to version(stored("mixomo")),
            "mihomo" to if (running("mihomo")) "running" else "stopped",
            "mihomo-version" to version(stored("mihomo")),
            "active-config" to read(PROFILES, "list").text("active"),
            "magitrickle" to magitrickle.getValue("status"),
            "magitrickle-version" to magitrickle.getValue("version"),
            "schedules" to "${entries.filter { switch(it, "enabled") == "true" }.map { it.text("name") }.distinct().size}",
            "rule-lists" to "${ruleFiles().size}",
            "routing-clients" to "${read(ROUTING, "status").objects(RULES).count { switch(it, "enabled") == "true" }}",
            "dns-servers" to "${read(DNS, "status")["servers"]?.jsonArray?.size ?: 0}",
            "automatic-backups" to if (listOf("auto_mihomo", "auto_magitrickle").any { settings?.text(it) in YES }) "enabled" else "disabled",
        )
    }

    // the daemon tells its version only by starting, so it is read from its package
    private suspend fun magitrickle(): Map<String, String> {
        val template = optional("file", "read", "data", "path" to JsonPrimitive("/etc/mixomo/templates/magitrickle-template.active")).trim()
        val noted = stored("magitrickle")
        return mapOf(
            "status" to if (running("magitrickle")) "running" else "stopped",
            "version" to version(noted.substringAfter('\n', "").ifBlank { packageVersion("magitrickle") }),
            "variant" to if (noted.lineSequence().first().trim() == "mod") "mod" else "original",
            "template" to if (template == "from-mixomo") "default" else "personal",
        )
    }

    private suspend fun stored(part: String) = optional("file", "read", "data", "path" to JsonPrimitive("/etc/mixomo/versions/$part"))

    private suspend fun packageVersion(name: String): String {
        if ("rpc-sys" !in objects) return ""
        val packages = ubus.call("rpc-sys", "packagelist", JsonObject(emptyMap()))["packages"] as? JsonObject
        return packages?.get(name)?.jsonPrimitive?.contentOrNull.orEmpty()
    }

    private suspend fun running(service: String): Boolean {
        if ("service" !in objects) return false
        val instances = (ubus.call("service", "list", JsonObject(mapOf("name" to JsonPrimitive(service))))[service] as? JsonObject)?.get("instances") as? JsonObject
        return instances?.values?.any { (it as? JsonObject)?.text("running") == "true" } == true
    }

    private fun version(text: String) = Regex("v?[0-9]+\\.[0-9]+\\.[0-9]+[0-9A-Za-z.\\-]*").find(text)?.value?.replace(Regex("[-_]r[0-9]+$"), "")?.let { if (it.startsWith('v')) it else "v$it" }.orEmpty()

    private suspend fun optional(obj: String, method: String, key: String, vararg arguments: Pair<String, JsonElement>): String = try {
        ubus.call(obj, method, JsonObject(arguments.toMap())).text(key)
    } catch (e: UbusException) {
        ""
    }

    private suspend fun read(obj: String, method: String) = if (obj in objects) call(obj, method) else JsonObject(emptyMap())

    private suspend fun ruleLists(): List<Item> {
        val known = call(PROFILES, "list").objects("rules").associateBy { it.text("name") }
        return ruleFiles().map { file ->
            val name = file.text("name")
            val meta = known[name]
            Item(
                name,
                buildMap {
                    put("name", name)
                    put("size", file.text("size"))
                    for (field in listOf("url", "interval")) meta?.text(field)?.takeIf { it.isNotEmpty() }?.let { put(field, it) }
                    meta?.text("updated")?.takeIf { it.isNotEmpty() }?.let { put("updated", it.toLongOrNull()?.let { seconds -> local(seconds) } ?: it) }
                },
            )
        }
    }

    private suspend fun ruleFiles(): List<JsonObject> = try {
        ubus.call("file", "list", JsonObject(mapOf("path" to JsonPrimitive(RULE_DIR)))).objects("entries").filter { it.text("type") == "file" }
    } catch (e: UbusException) {
        if (e.status != UbusStatus.NOT_FOUND) throw e
        emptyList()
    }

    private suspend fun schedules(): List<Item> {
        val answer = call(SCHEDULE, "list")
        return listOf("time", "trigger").flatMap { type ->
            answer.objects(type).map { entry -> Item("$type:${entry.text("name")}", mapOf("name" to entry.text("name"), "type" to type, "profile" to entry.text("profile"), "disabled" to disabled(entry))) }
        }
    }

    private suspend fun presets(): List<Item> {
        val status = call(DNS, "status")
        val used = status["servers"]?.jsonArray.orEmpty().map { it.jsonPrimitive.content }
        val presets = status.objects("custom").associateBy { it.text("name") }
        val order = status["order"]?.jsonArray.orEmpty().map { it.jsonPrimitive.content }
        return (order + (presets.keys - order.toSet())).mapNotNull(presets::get).map { preset ->
            val addresses = preset.text("value").split(Regex("\\s+")).filter { it.isNotEmpty() }
            Item(preset.text("name"), mapOf("name" to preset.text("name"), "servers" to addresses.joinToString(", "), "active" to "${addresses.isNotEmpty() && addresses.all { it in used }}"))
        }
    }

    private suspend fun call(obj: String, method: String, vararg arguments: Pair<String, Any>): JsonObject {
        val answer = ubus.call(obj, method, JsonObject(arguments.associate { (name, value) -> name to if (value is Boolean) JsonPrimitive(value) else JsonPrimitive("$value") }))
        // the backup that could not be removed answers ok null, without a text
        if (answer["ok"]?.jsonPrimitive?.booleanOrNull != true) throw DeviceException(ErrorCategory.General, answer.text("error").ifEmpty { "$obj $method" })
        return answer
    }

    private suspend fun rules(key: String) = call(ROUTING, "status").objects(key).sortedBy { it.text("priority").toLongOrNull() }

    private suspend fun rule(key: String, id: String) = rules(key).firstOrNull { it.text("id") == id } ?: throw DeviceException(ErrorCategory.MissingItem, "no rule $id")

    private suspend fun added(key: String, add: suspend () -> JsonObject): String {
        val before = rules(key).map { it.text("id") }.toSet()
        return add().objects(key).map { it.text("id") }.firstOrNull { it !in before } ?: throw DeviceException(ErrorCategory.MissingItem, "mixomo did not list what it added")
    }

    private suspend fun reorder(type: String, key: String, ids: List<String>, before: String?) {
        val rest = rules(key).map { it.text("id") } - ids.toSet()
        val at = before?.let(rest::indexOf)?.takeIf { it >= 0 } ?: rest.size
        call(ROUTING, "reorder", "type" to type, "order" to (rest.take(at) + ids + rest.drop(at)).joinToString(","))
    }

    private suspend fun source(name: String, url: String, interval: String) = call(PROFILES, "set_url", "type" to "config", "name" to name, "url" to url, "interval" to interval)

    private fun numbers(arguments: Map<String, String>) = arguments["numbers"].orEmpty().split(',').filter { it.isNotEmpty() }

    private fun changed(changes: Map<String, String?>, field: String, current: String) = if (field in changes) changes[field].orEmpty() else current

    private fun switch(answer: JsonObject, key: String) = when (val value = answer.text(key)) {
        "1", "true" -> "true"
        "0", "false" -> "false"
        else -> value
    }

    private fun disabled(rule: JsonObject) = when (val enabled = switch(rule, "enabled")) {
        "true" -> "false"
        "false" -> "true"
        else -> enabled
    }

    private fun JsonObject.objects(key: String) = this[key]?.jsonArray.orEmpty().mapNotNull { it as? JsonObject }

    private fun JsonObject.text(key: String) = (this[key] as? JsonPrimitive)?.contentOrNull.orEmpty()

    private companion object {
        const val ROUTING = "mihomo-routing"
        const val PROFILES = "mihomo-profiles"
        const val BACKUP = "mixomo-backup"
        const val SCHEDULE = "mihomo-schedule"
        const val DNS = "mihomo-dns"
        const val RULE_DIR = "/etc/mihomo/rule-files"
        const val RULES = "rules"
        const val EXCLUSIONS = "exclusions"
        val BACKENDS = listOf("redir-tproxy", "tun-socks5")
        val YES = setOf("yes", "true")
    }
}
