package com.vasmarfas.networkhelm.session

import com.vasmarfas.networkhelm.discovery.formatIpv6
import com.vasmarfas.networkhelm.model.DeviceException
import com.vasmarfas.networkhelm.model.ErrorCategory
import com.vasmarfas.networkhelm.model.Item
import com.vasmarfas.networkhelm.model.MenuPath
import com.vasmarfas.networkhelm.model.duration
import com.vasmarfas.networkhelm.ssh.SshSession
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlin.time.Instant

internal class LinuxHost(private val ssh: SshSession) {
    private var cpu: List<Long>? = null

    suspend fun read(path: MenuPath): List<Item> = when (path.toString()) {
        "interface" -> interfaces()
        "ip/address" -> addresses(v6 = false)
        "ipv6/address" -> addresses(v6 = true)
        "ip/route" -> routes(v6 = false)
        "ipv6/route" -> routes(v6 = true)
        "ip/arp" -> neighbours(v6 = false)
        "ipv6/neighbor" -> neighbours(v6 = true)
        "system/resource" -> listOf(Item("", resource()))
        "system/clock" -> listOf(Item("", clock()))
        "system/storage" -> storage()
        "system/process" -> processes()
        "system/service" -> services()
        "log" -> log()
        else -> throw DeviceException(ErrorCategory.MissingItem, "a Linux host has no $path here")
    }

    suspend fun has(path: MenuPath): Boolean = path.toString() != "system/service" || exists("systemctl")

    suspend fun traffic(): Map<String, List<Long>> = output("cat /proc/net/dev").lines().drop(2).mapNotNull { line ->
        val name = line.substringBefore(':', "").trim().ifEmpty { return@mapNotNull null }
        val fields = line.substringAfter(':').trim().split(blanks).mapNotNull(String::toLongOrNull)
        if (fields.size < 10) null else name to listOf(fields[0], fields[8], fields[1], fields[9])
    }.toMap()

    private suspend fun exists(tool: String) = ssh.exec("command -v $tool").output.isNotBlank()

    private suspend fun output(command: String): String {
        val result = ssh.exec(command)
        if (result.exitStatus != 0 && result.output.isBlank()) throw DeviceException(ErrorCategory.Api, "$command: ${result.error.trim().ifEmpty { "exit ${result.exitStatus}" }}")
        return result.output
    }

    private suspend fun json(command: String): JsonArray = try {
        Json.parseToJsonElement(output(command).ifBlank { "[]" }).jsonArray
    } catch (e: SerializationException) {
        throw DeviceException(ErrorCategory.Api, "$command gave no JSON", e)
    } catch (e: IllegalArgumentException) {
        throw DeviceException(ErrorCategory.Api, "$command gave no list", e)
    }

    private var iproute: Boolean? = null

    private suspend fun iproute(): Boolean = iproute ?: ssh.exec("ip -j link show lo").output.trimStart().startsWith("[").also { iproute = it }

    private suspend fun interfaces(): List<Item> = if (iproute()) json("ip -j -d -s link show").mapNotNull { it as? JsonObject }.map { link ->
        val flags = link.strings("flags")
        val kind = (link["linkinfo"] as? JsonObject)?.text("info_kind")
        val stats = link["stats64"] as? JsonObject
        fun counter(direction: String, name: String) = ((stats?.get(direction) as? JsonObject)?.get(name) as? JsonPrimitive)?.longOrNull?.toString().orEmpty()
        val linkType = link.text("link_type")
        link(
            link.text("ifindex"), link.text("ifname"), types[kind] ?: kind ?: types[linkType] ?: linkType, link.text("mtu"),
            link.text("address").takeIf { linkType == "ether" }.orEmpty(), "LOWER_UP" in flags || link.text("operstate") == "UP", "UP" !in flags,
            link.text("ifalias"), link.text("master"), link.text("link"),
            listOf("rx" to "bytes", "tx" to "bytes", "rx" to "packets", "tx" to "packets", "rx" to "dropped", "tx" to "dropped", "rx" to "errors", "tx" to "errors").map { (d, n) -> counter(d, n) },
        )
    } else {
        output(SYSFS).lines().map { it.split('|') }.filter { it.size >= 18 }.map { field ->
            val type = field[16].ifEmpty { arphrd[field[2]] ?: field[2] }
            val up = (field[3].removePrefix("0x").toIntOrNull(16) ?: 0) and 1 == 1
            link(field[1], field[0], types[type] ?: type, field[4], field[6].takeIf { field[2] == "1" }.orEmpty(), field[5] == "up" || field[5] == "unknown" && up, !up, field[17], field[15], "", field.subList(7, 15))
        }
    }

    private fun link(index: String, name: String, type: String, mtu: String, mac: String, running: Boolean, disabled: Boolean, alias: String, master: String, parent: String, counters: List<String>) =
        Item(index, buildMap {
            put("name", name)
            put("type", type)
            put("mtu", mtu)
            put("mac-address", mac.uppercase())
            put("running", "$running")
            put("disabled", "$disabled")
            put("comment", alias)
            if (master.isNotEmpty()) put("master", master)
            if (parent.isNotEmpty()) put("parent", parent)
            listOf("rx-byte", "tx-byte", "rx-packet", "tx-packet", "rx-drop", "tx-drop", "rx-error", "tx-error").zip(counters).forEach { (key, value) -> put(key, value) }
        })

    private suspend fun addressRows(v6: Boolean): List<Map<String, String>> = if (iproute()) {
        json(if (v6) "ip -j -6 addr show" else "ip -j -4 addr show").mapNotNull { it as? JsonObject }.flatMap { link ->
            link["addr_info"]?.jsonArray.orEmpty().mapNotNull { it as? JsonObject }.map { address -> address.mapValues { plain(it.value) } + ("ifname" to link.text("ifname")) }
        }
    } else {
        output(if (v6) "ip -o -6 addr show" else "ip -o -4 addr show").lines().map { it.substringBefore('\\').trim().split(blanks) }.filter { it.size >= 4 }.map { words ->
            val (local, prefix) = words[3].split('/').let { it[0] to it.getOrElse(1) { "" } }
            val scope = words.indexOf("scope").takeIf { it > 0 }?.let { words.getOrNull(it + 1) }.orEmpty()
            val label = words.last().takeIf { !v6 && words.size > words.indexOf("scope") + 2 && words.indexOf("scope") > 0 }.orEmpty()
            mapOf("ifname" to words[1].removeSuffix(":"), "local" to local, "prefixlen" to prefix, "scope" to scope, "label" to label, "dynamic" to "${"dynamic" in words}")
        }
    }

    private suspend fun addresses(v6: Boolean): List<Item> = addressRows(v6).map { address ->
        val local = address["local"].orEmpty()
        val prefix = address["prefixlen"].orEmpty()
        val name = address["ifname"].orEmpty()
        Item("$name/$local/$prefix", buildMap {
            put("address", "$local/$prefix")
            if (!v6) prefix.toIntOrNull()?.let { put("network", network(local, it)) }
            put("interface", address["label"].orEmpty().ifEmpty { name })
            put("scope", address["scope"].orEmpty())
            put("dynamic", "${address["dynamic"] == "true"}")
        })
    }

    private suspend fun routeRows(v6: Boolean): List<Map<String, String>> = if (iproute()) {
        json(if (v6) "ip -j -6 route show table all" else "ip -j route show table all").mapNotNull { it as? JsonObject }.map { route -> route.mapValues { plain(it.value) } }
    } else {
        output(if (v6) "ip -6 route show table all" else "ip route show table all").lines().map { it.trim().split(blanks) }.filter { it.isNotEmpty() && it[0].isNotEmpty() }.map { words ->
            val typed = words[0] in routeTypes
            val rest = words.drop(if (typed) 2 else 1)
            val named = rest.zipWithNext().filter { it.first in routeKeys }.associate { (key, value) -> routeKeys.getValue(key) to value }
            named + mapOf("type" to if (typed) words[0] else "", "dst" to words.getOrElse(if (typed) 1 else 0) { "" })
        }
    }

    private suspend fun routes(v6: Boolean): List<Item> = routeRows(v6)
        .filter { it["table"] != "local" && it["type"] !in setOf("local", "broadcast", "anycast", "multicast") }
        .map { route ->
            val destination = route["dst"].orEmpty().let { if (it == "default") (if (v6) "::/0" else "0.0.0.0/0") else if ('/' in it) it else "$it/${if (v6) 128 else 32}" }
            val protocol = route["protocol"].orEmpty()
            val gateway = route["gateway"].orEmpty()
            val static = protocol == "static" || protocol == "boot" || protocol.isEmpty() && gateway.isNotEmpty()
            val table = route["table"].orEmpty().ifEmpty { "main" }
            Item(listOf(destination, gateway, route["dev"].orEmpty(), table, route["metric"].orEmpty()).joinToString("/"), buildMap {
                put("dst-address", destination)
                put("gateway", gateway)
                put("interface", route["dev"].orEmpty())
                put("protocol", protocol)
                put("metric", route["metric"].orEmpty())
                put("routing-table", table)
                put("pref-src", route["prefsrc"].orEmpty())
                put("type", route["type"].orEmpty().ifEmpty { "unicast" })
                put("active", "true")
                put("connect", "${(protocol == "kernel" || protocol.isEmpty()) && route["scope"] == "link" && gateway.isEmpty()}")
                put("static", "$static")
                put("dynamic", "${!static}")
            })
        }

    private suspend fun neighbourRows(v6: Boolean): List<Map<String, String>> = if (iproute()) {
        json(if (v6) "ip -j -6 neigh show" else "ip -j -4 neigh show").mapNotNull { it as? JsonObject }.map { neighbour ->
            neighbour.mapValues { plain(it.value) } + ("state" to neighbour.strings("state").joinToString(","))
        }
    } else {
        output(if (v6) "ip -6 neigh show" else "ip -4 neigh show").lines().map { it.trim().split(blanks) }.filter { it.size >= 3 }.map { words ->
            val named = words.zipWithNext().filter { it.first == "dev" || it.first == "lladdr" }.associate { it }
            buildMap {
                put("dst", words[0])
                put("dev", named["dev"].orEmpty())
                put("lladdr", named["lladdr"].orEmpty())
                put("state", words.last().takeIf { word -> word.all { it.isUpperCase() } }.orEmpty())
                if ("router" in words) put("router", "true")
            }
        }
    }

    private suspend fun neighbours(v6: Boolean): List<Item> = neighbourRows(v6).map { neighbour ->
        val states = neighbour["state"].orEmpty().split(',').filter { it.isNotEmpty() }
        val mac = neighbour["lladdr"].orEmpty().uppercase()
        Item("${neighbour["dev"]}/${neighbour["dst"]}", buildMap {
            put("address", neighbour["dst"].orEmpty())
            put("mac-address", mac)
            put("interface", neighbour["dev"].orEmpty())
            put("status", states.joinToString(",") { it.lowercase() })
            put("dynamic", "${"PERMANENT" !in states}")
            put("complete", "${mac.isNotEmpty()}")
            if ("router" in neighbour) put("router", "true")
        })
    }

    private suspend fun sections(vararg commands: String): List<String> =
        output(commands.joinToString("; echo $SECTION; ") { "$it 2>/dev/null" }).split("$SECTION\n")

    private suspend fun resource(): Map<String, String> {
        val parts = sections(
            "cat /proc/uptime", "cat /proc/loadavg", "cat /proc/meminfo", "head -1 /proc/stat", "cat /etc/os-release", "uname -m", "grep -c ^processor /proc/cpuinfo",
            "cat /proc/device-tree/model || cat /sys/class/dmi/id/product_name",
        )
        val (uptime, load, memory, stat) = parts
        val (release, machine, count, model) = parts.drop(4) + List(4) { "" }
        val meminfo = memory.lines().associate { it.substringBefore(':') to (it.substringAfter(':').trim().substringBefore(' ').toLongOrNull() ?: 0) }
        val times = stat.trim().split(blanks).drop(1).mapNotNull(String::toLongOrNull)
        val previous = cpu
        cpu = times
        return buildMap {
            uptime.trim().substringBefore(' ').substringBefore('.').toLongOrNull()?.let { put("uptime", duration(it)) }
            put("load", load.trim().split(blanks).take(3).joinToString(" "))
            if (previous != null && times.size >= 8 && previous.size >= 8) {
                val busy = listOf(0, 1, 2, 5, 6, 7).sumOf { times[it] - previous[it] }
                val all = busy + times[3] - previous[3] + times[4] - previous[4]
                if (all > 0) put("cpu-load", "${busy * 100 / all}")
            }
            put("cpu-count", count.trim())
            meminfo["MemTotal"]?.let { put("total-memory", "${it * 1024}") }
            (meminfo["MemAvailable"] ?: meminfo["MemFree"])?.let { put("free-memory", "${it * 1024}") }
            put("version", release.lines().firstOrNull { it.startsWith("PRETTY_NAME=") }?.substringAfter('=')?.trim('"').orEmpty())
            put("architecture-name", machine.trim())
            put("board-name", model.trim().trimEnd('\u0000'))
        }
    }

    private suspend fun clock(): Map<String, String> {
        val now = output("date '+%Y-%m-%d %H:%M:%S %z'").trim().split(' ')
        if (now.size < 3) return emptyMap()
        return mapOf("date" to now[0], "time" to now[1], "gmt-offset" to now[2])
    }

    private suspend fun storage(): List<Item> = output("df -P -k").lines().drop(1).map { it.trim().split(blanks) }.filter { it.size >= 6 }.map { row ->
        val mount = row.drop(5).joinToString(" ")
        Item(mount, mapOf(
            "description" to mount,
            "file-system" to row[0],
            "size" to (row[1].toLongOrNull()?.times(1024)?.toString().orEmpty()),
            "used" to (row[2].toLongOrNull()?.times(1024)?.toString().orEmpty()),
            "free" to (row[3].toLongOrNull()?.times(1024)?.toString().orEmpty()),
        ))
    }

    private suspend fun processes(): List<Item> {
        val full = ssh.exec("ps -eo pid=,user=,pcpu=,rss=,stat=,args=")
        if (full.exitStatus == 0 && full.output.isNotBlank()) {
            return full.output.lines().map { it.trim().split(blanks, limit = 6) }.filter { it.size >= 6 }.map { row ->
                Item(row[0], mapOf("pid" to row[0], "user" to row[1], "cpu" to row[2], "memory" to (row[3].toLongOrNull()?.times(1024)?.toString().orEmpty()), "stat" to row[4], "command" to row[5]))
            }
        }
        return output("ps w").lines().drop(1).map { it.trim().split(blanks, limit = 5) }.filter { it.size >= 5 }.map { row ->
            Item(row[0], mapOf("pid" to row[0], "user" to row[1], "vsz" to (row[2].toLongOrNull()?.times(1024)?.toString() ?: row[2]), "stat" to row[3], "command" to row[4]))
        }
    }

    private suspend fun services(): List<Item> = output("systemctl list-units --type=service --all --no-legend --plain --no-pager").lines()
        .map { it.trim().split(blanks, limit = 5) }.filter { it.size >= 4 && it[0].endsWith(".service") }.map { row ->
            Item(row[0], mapOf(
                "name" to row[0].removeSuffix(".service"),
                "load" to row[1],
                "status" to "${row[2]} (${row[3]})",
                "running" to "${row[3] == "running"}",
                "disabled" to "${row[1] == "masked"}",
                "description" to row.getOrElse(4) { "" },
            ))
        }

    private suspend fun log(): List<Item> {
        if (exists("journalctl")) {
            val (offset, lines) = sections("date +%z", "journalctl -o json -n 500 --no-pager").let { it[0].trim() to it.getOrElse(1) { "" } }
            return lines.lines().filter { it.isNotBlank() }.mapNotNull { line -> journal(line, offset) }
        }
        val seen = mutableMapOf<String, Int>()
        return output("logread -l 500").lines().filter { it.length > 25 }.map { line ->
            val time = line.take(24)
            val rest = line.drop(25)
            val topics = rest.substringBefore(' ')
            val message = rest.substringAfter(' ')
            val count = (seen[line] ?: 0) + 1
            seen[line] = count
            Item("$line#$count", mapOf("time" to time, "topics" to topics, "message" to message))
        }
    }

    private fun journal(line: String, offset: String): Item? {
        val entry = try {
            Json.parseToJsonElement(line).jsonObject
        } catch (e: SerializationException) {
            return null
        }
        val micros = entry.text("__REALTIME_TIMESTAMP").toLongOrNull() ?: return null
        val priority = entry.text("PRIORITY").toIntOrNull()
        val facility = entry.text("SYSLOG_FACILITY").toIntOrNull()
        val message = when (val value = entry["MESSAGE"]) {
            is JsonArray -> value.mapNotNull { it.jsonPrimitive.longOrNull?.toByte() }.toByteArray().decodeToString()
            else -> value?.let(::plain).orEmpty()
        }
        val identifier = entry.text("SYSLOG_IDENTIFIER")
        return Item(entry.text("__CURSOR").ifEmpty { "$micros" }, mapOf(
            "time" to local(micros / 1_000_000, offset),
            "topics" to listOfNotNull(facility?.let { facilities.getOrNull(it) }, priority?.let { levels.getOrNull(it) }).joinToString("."),
            "message" to if (identifier.isEmpty()) message else "$identifier: $message",
        ))
    }

}

private const val SECTION = "--rd-section--"

private val blanks = Regex("\\s+")

private val types = mapOf("ether" to "ether", "loopback" to "loopback", "wireguard" to "wg", "bridge" to "bridge", "vlan" to "vlan", "bond" to "bond", "none" to "tunnel", "wlan" to "wlan")

private val arphrd = mapOf("1" to "ether", "772" to "loopback", "65534" to "none")

private const val SYSFS = $$"""for d in /sys/class/net/*; do n=${d##*/}; s=$d/statistics; m=$(readlink $d/master 2>/dev/null); """ +
    $$"""t=$(sed -n 's/^DEVTYPE=//p' $d/uevent 2>/dev/null); echo "$n|$(cat $d/ifindex)|$(cat $d/type)|$(cat $d/flags)|$(cat $d/mtu)|$(cat $d/operstate)|""" +
    $$"""$(cat $d/address)|$(cat $s/rx_bytes)|$(cat $s/tx_bytes)|$(cat $s/rx_packets)|$(cat $s/tx_packets)|$(cat $s/rx_dropped)|$(cat $s/tx_dropped)|""" +
    $$"""$(cat $s/rx_errors)|$(cat $s/tx_errors)|${m##*/}|$t|$(cat $d/ifalias 2>/dev/null)"; done"""

private val routeTypes = setOf("unicast", "unreachable", "blackhole", "prohibit", "local", "broadcast", "anycast", "multicast", "throw", "nat")
private val routeKeys = mapOf("via" to "gateway", "dev" to "dev", "proto" to "protocol", "scope" to "scope", "src" to "prefsrc", "metric" to "metric", "table" to "table")

private val facilities = listOf("kern", "user", "mail", "daemon", "auth", "syslog", "lpr", "news", "uucp", "cron", "authpriv", "ftp", "ntp", "security", "console", "solaris-cron", "local0", "local1", "local2", "local3", "local4", "local5", "local6", "local7")
private val levels = listOf("emerg", "alert", "crit", "err", "warning", "notice", "info", "debug")

private fun JsonObject.text(key: String) = this[key]?.let(::plain).orEmpty()

private fun JsonObject.strings(key: String) = (this[key] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }.orEmpty()

private fun plain(value: JsonElement) = (value as? JsonPrimitive)?.contentOrNull ?: value.toString()

private fun network(address: String, prefix: Int): String {
    val bytes = address.split('.').mapNotNull(String::toIntOrNull)
    if (bytes.size != 4) return ""
    return bytes.mapIndexed { i, byte -> byte and (0xFF shl (8 - (prefix - i * 8).coerceIn(0, 8))) and 0xFF }.joinToString(".")
}

internal fun local(seconds: Long, offset: String): String {
    val sign = if (offset.startsWith("-")) -1 else 1
    val digits = offset.trimStart('+', '-')
    val shift = sign * ((digits.take(2).toLongOrNull() ?: 0) * 3600 + (digits.drop(2).take(2).toLongOrNull() ?: 0) * 60)
    return Instant.fromEpochSeconds(seconds + shift).toString().removeSuffix("Z").replace('T', ' ')
}
