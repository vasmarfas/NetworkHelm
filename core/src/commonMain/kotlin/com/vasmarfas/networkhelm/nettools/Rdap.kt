package com.vasmarfas.networkhelm.nettools

import com.vasmarfas.networkhelm.transport.HttpResponse
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

private const val FALLBACK = "https://rdap.org"
private const val MAX_REDIRECTS = 3
private const val MAX_AS_NUMBER = 0xFFFFFFFFL
private const val MAX_DOMAIN_LENGTH = 253

private val accept = mapOf("Accept" to "application/rdap+json, application/json")
private val redirecting = setOf(301, 302, 303, 307, 308)
private val asNumber = Regex("""(?:as\s*)?(\d{1,10})""", RegexOption.IGNORE_CASE)
private val domainName = Regex("""[a-z0-9_-]+(\.[a-z0-9_-]+)*""")
private val knownRoles = setOf("registrar", "registrant", "abuse", "technical", "administrative", "billing")
private val eventKeys = mapOf(
    "registration" to "registered",
    "expiration" to "expires",
    "last changed" to "changed",
    "last update of rdap database" to "rdap-updated",
    "transfer" to "transferred",
)
private val pretty = Json { prettyPrint = true }

// a row of facts: the key names the label, the value is what the server said
class InfoRow(val key: String, val value: String)

enum class RdapKind(val path: String) { Domain("domain"), Ip("ip"), Autnum("autnum") }

class RdapTarget(val kind: RdapKind, val name: String)

sealed interface RdapResult {
    val server: String

    class Found(override val server: String, val json: JsonObject) : RdapResult
    class Missing(override val server: String) : RdapResult
    class Refused(override val server: String, val status: Int) : RdapResult
    class Unreadable(override val server: String) : RdapResult
    class Silent(override val server: String) : RdapResult
}

// the servers of the IANA bootstrap files (RFC 9224); a file is read once and kept until the program ends
class RdapBootstrap {
    private class Service(val keys: List<String>, val urls: List<String>)

    private val lock = Mutex()
    private val files = HashMap<String, List<Service>>()

    // null: the file did not load or names no HTTPS server for the target
    suspend fun serverFor(http: PublicHttp, target: RdapTarget): String? {
        val file = when (target.kind) {
            RdapKind.Domain -> "dns"
            RdapKind.Autnum -> "asn"
            RdapKind.Ip -> if (':' in target.name) "ipv6" else "ipv4"
        }
        return services(http, file)?.firstOrNull { it.serves(target) }?.urls?.firstOrNull { it.startsWith("https://") }
    }

    private suspend fun services(http: PublicHttp, file: String): List<Service>? = lock.withLock {
        files[file] ?: load(http, file)?.also { files[file] = it }
    }

    private suspend fun load(http: PublicHttp, file: String): List<Service>? {
        val reply = try {
            http.get("https://data.iana.org/rdap/$file.json")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return null
        }
        if (reply.status != 200) return null
        val entries = parseObject(reply.body.decodeToString())?.get("services") as? JsonArray ?: return null
        return entries.mapNotNull { entry ->
            val pair = entry as? JsonArray ?: return@mapNotNull null
            Service(pair.stringsAt(0), pair.stringsAt(1))
        }
    }

    private fun Service.serves(target: RdapTarget): Boolean = keys.any { key ->
        when (target.kind) {
            RdapKind.Domain -> key == target.name.substringAfterLast('.')
            RdapKind.Autnum -> inRange(key, target.name.toLong())
            RdapKind.Ip -> inBlock(key, target.name.substringBefore('/'))
        }
    }
}

internal val sessionBootstrap = RdapBootstrap()

// a domain, an IP address, a network in CIDR form or an AS number ("AS15169" or "15169"); null for anything else
fun rdapTarget(query: String): RdapTarget? {
    val typed = query.trim()
    val text = (if ("://" in typed) hostOf(typed) else typed).trimEnd('.')
    asNumber.matchEntire(text)?.groupValues?.get(1)?.toLong()?.takeIf { it <= MAX_AS_NUMBER }?.let { return RdapTarget(RdapKind.Autnum, it.toString()) }
    addressOrBlock(text)?.let { return RdapTarget(RdapKind.Ip, it) }
    return asciiDomain(text.lowercase()).takeIf { it.length <= MAX_DOMAIN_LENGTH && domainName.matches(it) && !it.substringAfterLast('.').all(Char::isDigit) }
        ?.let { RdapTarget(RdapKind.Domain, it) }
}

// the bootstrap names the server; rdap.org, which redirects to it, stands in when IANA is silent
suspend fun rdapLookup(http: PublicHttp, target: RdapTarget, bootstrap: RdapBootstrap = sessionBootstrap): RdapResult {
    var url = "${(bootstrap.serverFor(http, target) ?: FALLBACK).trimEnd('/')}/${target.kind.path}/${target.name}"
    var followed = 0
    while (true) {
        val reply = try {
            http.get(url, accept)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return RdapResult.Silent(hostOf(url))
        }
        val location = reply.headers["location"]
        if (reply.status !in redirecting || location == null) return answer(url, reply)
        val next = absolute(url, location)
        if (next == null || ++followed > MAX_REDIRECTS) return RdapResult.Refused(hostOf(url), reply.status)
        url = next
    }
}

fun rdapFacts(json: JsonObject): List<InfoRow> = buildList {
    val ldh = json.string("ldhName")
    ldh?.let { add(InfoRow("domain", it)) }
    json.string("unicodeName")?.takeIf { !it.equals(ldh, ignoreCase = true) }?.let { add(InfoRow("unicode", it)) }
    json.string("handle")?.let { add(InfoRow("handle", it)) }
    json.string("name")?.let { add(InfoRow("name", it)) }
    json.string("type")?.let { add(InfoRow("type", it)) }
    json.string("startAddress")?.let { add(InfoRow("range", "$it – ${json.string("endAddress").orEmpty()}")) }
    json.cidrs().takeIf { it.isNotEmpty() }?.let { add(InfoRow("cidr", it.joinToString("\n"))) }
    json.string("startAutnum")?.let { first -> json.string("endAutnum")?.takeIf { it != first }?.let { add(InfoRow("as-range", "$first – $it")) } }
    json.string("parentHandle")?.let { add(InfoRow("parent", it)) }
    json.string("country")?.let { add(InfoRow("country", it)) }
    json.strings("status").takeIf { it.isNotEmpty() }?.let { add(InfoRow("status", it.joinToString(", "))) }
    for (event in json.objects("events")) {
        val action = event.string("eventAction") ?: continue
        add(InfoRow(eventKeys[action.lowercase()] ?: action.replaceFirstChar { it.uppercase() }, event.string("eventDate").orEmpty()))
    }
    json.objects("nameservers").mapNotNull { it.string("ldhName") ?: it.string("unicodeName") }.takeIf { it.isNotEmpty() }
        ?.let { add(InfoRow("nameservers", it.joinToString("\n"))) }
    (json["secureDNS"] as? JsonObject)?.string("delegationSigned")?.let { add(InfoRow("dnssec", it)) }
    for (entity in json.entities()) {
        val roles = entity.strings("roles")
        val label = if (roles.size == 1 && roles[0] in knownRoles) roles[0] else roles.joinToString(", ").ifEmpty { "entity" }.replaceFirstChar { it.uppercase() }
        val who = listOfNotNull(entity.contact("fn") ?: entity.string("handle"), entity.contact("org"), entity.contact("email")).distinct().joinToString(" · ")
        if (who.isNotEmpty()) add(InfoRow(label, who))
        for (id in entity.objects("publicIds")) {
            val identifier = id.string("identifier") ?: continue
            add(InfoRow(id.string("type") ?: "id", identifier))
        }
    }
    json.string("port43")?.let { add(InfoRow("whois-server", it)) }
}

fun rdapRaw(json: JsonObject): String = pretty.encodeToString(JsonElement.serializer(), json)

internal fun parseObject(text: String): JsonObject? = try {
    Json.parseToJsonElement(text) as? JsonObject
} catch (e: SerializationException) {
    null
}

internal fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }

internal fun JsonObject.strings(key: String): List<String> = (this[key] as? JsonArray).orEmpty().mapNotNull { (it as? JsonPrimitive)?.contentOrNull }

internal fun JsonObject.objects(key: String): List<JsonObject> = (this[key] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }

// ARIN nests the abuse and technical contacts inside the organisation
internal fun JsonObject.entities(): List<JsonObject> = objects("entities").flatMap { listOf(it) + it.entities() }

internal fun JsonObject.cidrs(): List<String> = objects("cidr0_cidrs").mapNotNull { block ->
    (block.string("v4prefix") ?: block.string("v6prefix"))?.let { "$it/${block.string("length").orEmpty()}" }
}

// a field of the vCard of an entity; an address comes as the label the registry wrote for it
internal fun JsonObject.contact(field: String): String? {
    val items = ((this["vcardArray"] as? JsonArray)?.getOrNull(1) as? JsonArray).orEmpty()
    val entry = items.firstOrNull { ((it as? JsonArray)?.getOrNull(0) as? JsonPrimitive)?.contentOrNull == field } as? JsonArray ?: return null
    val label = ((entry.getOrNull(1) as? JsonObject)?.get("label") as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
    if (label != null) return label
    return when (val value = entry.getOrNull(3)) {
        is JsonArray -> value.mapNotNull { (it as? JsonPrimitive)?.contentOrNull?.takeIf { part -> part.isNotBlank() } }.joinToString(" ")
        is JsonPrimitive -> value.contentOrNull
        else -> null
    }?.takeIf { it.isNotBlank() }
}

// the A-label form of a name with letters outside ASCII (RFC 3492), a name in ASCII stays as typed
internal fun asciiDomain(name: String): String =
    if (name.all { it.code < 0x80 }) name else name.lowercase().split('.').joinToString(".") { label -> if (label.all { it.code < 0x80 }) label else "xn--" + punycode(label) }

private fun JsonArray.stringsAt(index: Int): List<String> = (getOrNull(index) as? JsonArray).orEmpty().mapNotNull { (it as? JsonPrimitive)?.contentOrNull }

private fun addressOrBlock(text: String): String? {
    val slash = text.indexOf('/')
    val address = if (slash < 0) text else text.substring(0, slash)
    val length = if (slash < 0) null else text.substring(slash + 1).toIntOrNull() ?: return null
    val v4 = Ipv4.parse(address)
    if (v4 != null) return if (length == null) Ipv4.format(v4) else "${Ipv4.format(v4)}/$length".takeIf { length in 0..32 }
    val v6 = Ipv6Address.parse(address) ?: return null
    return if (length == null) v6.compressed() else "${v6.compressed()}/$length".takeIf { length in 0..128 }
}

private fun inRange(range: String, number: Long): Boolean {
    val from = range.substringBefore('-').toLongOrNull() ?: return false
    val to = range.substringAfter('-', range).toLongOrNull() ?: return false
    return number in from..to
}

private fun inBlock(block: String, address: String): Boolean {
    val v4 = Ipv4.parse(address)
    if (v4 != null) return Ipv4.parseSubnet(block)?.let { (v4 and it.mask) == it.network } == true
    val v6 = Ipv6Address.parse(address) ?: return false
    val (network, prefix) = Ipv6Address.parseWithPrefix(block) ?: return false
    return v6.withPrefix(prefix).groups.contentEquals(network.withPrefix(prefix).groups)
}

private fun answer(url: String, reply: HttpResponse): RdapResult {
    val server = hostOf(url)
    return when {
        reply.status == 404 -> RdapResult.Missing(server)
        reply.status !in 200..299 -> RdapResult.Refused(server, reply.status)
        else -> parseObject(reply.body.decodeToString())?.let { RdapResult.Found(server, it) } ?: RdapResult.Unreadable(server)
    }
}

// a redirect leads on only over HTTPS
private fun absolute(from: String, location: String): String? = when {
    location.startsWith("https://") -> location
    location.startsWith("//") -> "https:$location"
    location.startsWith("/") -> "https://" + from.substringAfter("://").substringBefore('/') + location
    else -> null
}

private fun punycode(label: String): String {
    val points = ArrayList<Int>()
    var at = 0
    while (at < label.length) {
        val char = label[at]
        if (char.isHighSurrogate() && at + 1 < label.length && label[at + 1].isLowSurrogate()) {
            points += 0x10000 + ((char.code - 0xD800) shl 10) + (label[at + 1].code - 0xDC00)
            at += 2
        } else {
            points += char.code
            at++
        }
    }
    val out = StringBuilder()
    for (point in points) if (point < 0x80) out.append(point.toChar())
    val basic = out.length
    var handled = basic
    if (basic > 0) out.append('-')
    var n = 128
    var delta = 0L
    var bias = 72
    while (handled < points.size) {
        val next = points.filter { it >= n }.min()
        delta += (next - n).toLong() * (handled + 1)
        n = next
        for (point in points) {
            if (point < n) delta++
            if (point == n) {
                var q = delta
                var k = 36
                while (true) {
                    val t = if (k <= bias) 1 else if (k >= bias + 26) 26 else k - bias
                    if (q < t) break
                    out.append(digit((t + (q - t) % (36 - t)).toInt()))
                    q = (q - t) / (36 - t)
                    k += 36
                }
                out.append(digit(q.toInt()))
                bias = adapt(delta, handled + 1, handled == basic)
                delta = 0
                handled++
            }
        }
        delta++
        n++
    }
    return out.toString()
}

private fun adapt(delta: Long, count: Int, first: Boolean): Int {
    var scaled = if (first) delta / 700 else delta / 2
    scaled += scaled / count
    var k = 0
    while (scaled > 455) {
        scaled /= 35
        k += 36
    }
    return k + (36 * scaled / (scaled + 38)).toInt()
}

private fun digit(value: Int): Char = if (value < 26) 'a' + value else '0' + (value - 26)
