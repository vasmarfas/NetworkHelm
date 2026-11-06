package com.vasmarfas.networkhelm.nettools

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonObject

private const val A = 1
private const val PTR = 12
private const val TXT = 16
private const val AAAA = 28
private const val CYMRU = "asn.cymru.com"
private const val OTHER_FAMILY_MILLIS = 5_000L

private val locationHosts = listOf("ipwho.is", "ipapi.co")
private val registries = listOf("arin" to "ARIN", "ripe" to "RIPE NCC", "apnic" to "APNIC", "lacnic" to "LACNIC", "afrinic" to "AFRINIC")

enum class IpPart { Address, Reverse, Routing, Network, Location, Connection }

// hosts: where the rows come from, none stands for the resolver of this device;
// failed: no source answered, as against an answer with nothing in it (no rows)
class IpSection(val part: IpPart, val hosts: List<String>, val rows: List<InfoRow>, val failed: Boolean = false)

private class Located(val ip: String, val source: String, val place: List<InfoRow>, val connection: List<InfoRow>)

// an empty query asks about the public address of this device; the parts come as they arrive and fail on their own
fun ipInfo(
    http: PublicHttp,
    ops: HostOps?,
    query: String,
    resolver: String = PublicResolvers.first().second,
    bootstrap: RdapBootstrap = sessionBootstrap,
    dns: suspend (String, String, Int) -> DnsResponse? = { server, name, type -> dnsQuery(server, name, type) },
): Flow<IpSection> = channelFlow {
    val lookup = IpLookup(http, resolver, bootstrap, dns)
    val typed = query.trim()
    val literal = canonicalAddress(typed)
    val here = if (typed.isEmpty()) lookup.locate("") else null
    val ip: String
    if (typed.isEmpty()) {
        if (here == null) {
            send(IpSection(IpPart.Address, locationHosts, emptyList(), failed = true))
            return@channelFlow
        }
        ip = here.ip
        launch { send(lookup.yours(here)) }
    } else if (literal != null) {
        ip = literal
    } else {
        val name = hostOf(typed)
        val found = lookup.resolve(ops, name)
        val rows = found.orEmpty().map { InfoRow(familyKey(it), it) }
        send(IpSection(IpPart.Address, if (ops == null) listOf(resolver) else emptyList(), if (rows.isEmpty()) rows else listOf(InfoRow("host", name)) + rows, failed = found == null))
        ip = found.orEmpty().let(::preferred) ?: return@channelFlow
    }
    launch { send(lookup.reverse(ip)) }
    launch { send(lookup.routing(ip)) }
    launch { send(lookup.network(ip)) }
    if (here != null) {
        here.sections().forEach { send(it) }
    } else {
        launch {
            val located = lookup.locate(ip)
            if (located == null) send(IpSection(IpPart.Location, locationHosts, emptyList(), failed = true)) else located.sections().forEach { send(it) }
        }
    }
}

// the registrant, the abuse and admin contacts and the dates out of the RDAP record of an address block
internal fun networkOwner(json: JsonObject): List<InfoRow> {
    val entities = json.entities()
    val registrant = entities.firstOrNull { it.holds("registrant") && it.contact("kind") == "org" } ?: entities.firstOrNull { it.holds("registrant") }
    val abuse = entities.firstOrNull { it.holds("abuse") && it.contact("email") != null }
    val admin = entities.firstOrNull { (it.holds("technical") || it.holds("administrative")) && it.contact("email") != null }
    val abuseEmail = abuse?.contact("email")
    val description = json.objects("remarks").firstOrNull { remark -> remark.string("title").let { it == null || it == "description" } }
        ?.strings("description")?.joinToString("\n")
    fun date(action: String) = json.objects("events").firstOrNull { it.string("eventAction") == action }?.string("eventDate")?.substringBefore('T')
    val registry = json.string("port43")?.let { host -> registries.firstOrNull { it.first in host }?.second }
    val cidrs = json.cidrs()
    return listOfNotNull(
        json.string("name")?.let { InfoRow("network-name", it) },
        description?.takeIf { it.isNotBlank() }?.let { InfoRow("description", it) },
        registrant?.contact("fn")?.let { InfoRow("organisation", it) },
        registrant?.contact("adr")?.let { InfoRow("postal", it.replace("\n", ", ")) },
        abuseEmail?.let { InfoRow("abuse-email", it) },
        abuse?.contact("tel")?.let { InfoRow("abuse-phone", it.removePrefix("tel:")) },
        admin?.contact("email")?.takeIf { it != abuseEmail }?.let { InfoRow("admin-email", it) },
        json.string("startAddress")?.let { InfoRow("range", "$it – ${json.string("endAddress").orEmpty()}") },
        cidrs.takeIf { it.isNotEmpty() }?.let { InfoRow("cidr", it.joinToString("\n")) },
        json.string("country")?.let { InfoRow("country", it) },
        registry?.let { InfoRow("registry", it) },
        date("registration")?.let { InfoRow("registered", it) },
        date("last changed")?.let { InfoRow("changed", it) },
    )
}

// "15169 | 8.8.8.0/24 | US | arin | 1992-12-01" and "15169 | US | arin | 2000-03-30 | GOOGLE, US"
internal fun cymruFields(text: String): List<String> = text.split('|').map { it.trim() }

private class IpLookup(
    private val http: PublicHttp,
    private val resolver: String,
    private val bootstrap: RdapBootstrap,
    private val dns: suspend (String, String, Int) -> DnsResponse?,
) {
    // null: nobody answered
    suspend fun resolve(ops: HostOps?, name: String): List<String>? {
        if (ops != null) {
            return try {
                ops.resolve(name).map { it.substringBefore('%') }.distinct()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                emptyList()
            }
        }
        val (v4, v6) = coroutineScope { listOf(async { records(name, A) }, async { records(name, AAAA) }).awaitAll() }
        return if (v4 == null && v6 == null) null else v4.orEmpty() + v6.orEmpty()
    }

    // the lookup service sees one family of the caller, the other one answers only on hosts that have no records of the first
    suspend fun yours(here: Located): IpSection {
        val other = otherFamily(here.ip)
        return IpSection(
            IpPart.Address,
            listOfNotNull(here.source, other?.first),
            listOfNotNull(InfoRow(familyKey(here.ip), here.ip), other?.let { InfoRow(familyKey(it.second), it.second) }),
        )
    }

    suspend fun reverse(ip: String): IpSection {
        val hosts = listOf(resolver)
        val name = reverseName(ip) ?: return IpSection(IpPart.Reverse, hosts, emptyList())
        val names = records(name, PTR) ?: return IpSection(IpPart.Reverse, hosts, emptyList(), failed = true)
        return IpSection(IpPart.Reverse, hosts, names.map { InfoRow("name", it.trimEnd('.')) })
    }

    suspend fun routing(ip: String): IpSection {
        val hosts = listOf(CYMRU)
        val pointer = reverseName(ip) ?: return IpSection(IpPart.Routing, hosts, emptyList())
        val origin = records(cymruName(pointer), TXT) ?: return IpSection(IpPart.Routing, hosts, emptyList(), failed = true)
        val fields = origin.firstOrNull()?.let(::cymruFields) ?: return IpSection(IpPart.Routing, hosts, emptyList())
        val asns = fields[0].split(' ').filter { it.isNotEmpty() }
        if (asns.isEmpty()) return IpSection(IpPart.Routing, hosts, emptyList())
        val name = records("AS${asns.first()}.$CYMRU", TXT)?.firstOrNull()?.let(::cymruFields)?.cell(4)
        return IpSection(
            IpPart.Routing,
            hosts,
            listOfNotNull(
                InfoRow("asn", asns.joinToString(", ") { "AS$it" }),
                fields.cell(1)?.let { InfoRow("prefix", it) },
                fields.cell(2)?.let { InfoRow("country", it) },
                fields.cell(3)?.let { InfoRow("registry", it.uppercase()) },
                fields.cell(4)?.let { InfoRow("allocated", it) },
                name?.let { InfoRow("as-name", it) },
            ),
        )
    }

    suspend fun network(ip: String): IpSection {
        val result = rdapLookup(http, RdapTarget(RdapKind.Ip, ip), bootstrap)
        val hosts = listOf(result.server)
        return when (result) {
            is RdapResult.Found -> IpSection(IpPart.Network, hosts, networkOwner(result.json))
            is RdapResult.Missing -> IpSection(IpPart.Network, hosts, emptyList())
            is RdapResult.Refused, is RdapResult.Unreadable, is RdapResult.Silent -> IpSection(IpPart.Network, hosts, emptyList(), failed = true)
        }
    }

    suspend fun locate(ip: String): Located? =
        json("https://ipwho.is/$ip")?.let(::ipwho)
            ?: json(if (ip.isEmpty()) "https://ipapi.co/json/" else "https://ipapi.co/$ip/json/")?.let(::ipapi)

    private suspend fun otherFamily(ip: String): Pair<String, String>? {
        val host = if (':' in ip) "api.ipify.org" else "api6.ipify.org"
        val found = withTimeoutOrNull(OTHER_FAMILY_MILLIS) {
            try {
                http.get("https://$host").takeIf { it.status == 200 }?.body?.decodeToString()?.trim()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            }
        }
        return found?.let(::canonicalAddress)?.takeIf { it != ip }?.let { host to it }
    }

    private suspend fun records(name: String, type: Int): List<String>? {
        val response = try {
            dns(resolver, name, type)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        } ?: return null
        return response.answers.filter { it.type == type }.map { it.data }
    }

    private suspend fun json(url: String): JsonObject? {
        val reply = try {
            http.get(url)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return null
        }
        return if (reply.status == 200) parseObject(reply.body.decodeToString()) else null
    }
}

private fun Located.sections() = listOf(
    IpSection(IpPart.Location, listOf(source), place),
    IpSection(IpPart.Connection, listOf(source), connection),
)

private fun ipwho(json: JsonObject): Located? {
    if (json.string("success") == "false") return null
    val connection = json["connection"] as? JsonObject
    val zone = json["timezone"] as? JsonObject
    return Located(
        ip = json.string("ip") ?: return null,
        source = "ipwho.is",
        place = listOfNotNull(
            json.string("country")?.let { InfoRow("country", countryName(it, json.string("country_code"))) },
            json.string("region")?.let { InfoRow("region", it) },
            json.string("city")?.let { InfoRow("city", it) },
            json.string("postal")?.let { InfoRow("postal-code", it) },
            json.string("latitude")?.let { InfoRow("coordinates", "$it, ${json.string("longitude").orEmpty()}") },
            zone?.string("id")?.let { InfoRow("timezone", "$it (${zone.string("utc").orEmpty()})") },
            zone?.string("current_time")?.let { InfoRow("local-time", it) },
            json.string("calling_code")?.let { InfoRow("calling-code", "+$it") },
            json.string("is_eu")?.let { InfoRow("eu", it) },
        ),
        connection = listOfNotNull(
            connection?.string("isp")?.let { InfoRow("isp", it) },
            connection?.string("org")?.let { InfoRow("organisation", it) },
            connection?.string("asn")?.let { InfoRow("asn", "AS$it") },
            connection?.string("domain")?.let { InfoRow("domain", it) },
        ),
    )
}

private fun ipapi(json: JsonObject): Located? {
    if (json.string("error") == "true") return null
    return Located(
        ip = json.string("ip") ?: return null,
        source = "ipapi.co",
        place = listOfNotNull(
            json.string("country_name")?.let { InfoRow("country", countryName(it, json.string("country_code"))) },
            json.string("region")?.let { InfoRow("region", it) },
            json.string("city")?.let { InfoRow("city", it) },
            json.string("postal")?.let { InfoRow("postal-code", it) },
            json.string("latitude")?.let { InfoRow("coordinates", "$it, ${json.string("longitude").orEmpty()}") },
            json.string("timezone")?.let { InfoRow("timezone", "$it (${json.string("utc_offset").orEmpty()})") },
        ),
        connection = listOfNotNull(
            json.string("org")?.let { InfoRow("organisation", it) },
            json.string("asn")?.let { InfoRow("asn", it) },
        ),
    )
}

private fun JsonObject.holds(role: String) = role in strings("roles")

private fun List<String>.cell(index: Int): String? = getOrNull(index)?.takeIf { it.isNotEmpty() }

private fun countryName(name: String, code: String?) = if (code == null) name else "$name ($code)"

private fun familyKey(address: String) = if (':' in address) "ipv6" else "ipv4"

private fun preferred(addresses: List<String>): String? = addresses.firstOrNull { Ipv4.parse(it) != null } ?: addresses.firstOrNull()

private fun canonicalAddress(text: String): String? = Ipv4.parse(text)?.let(Ipv4::format) ?: Ipv6Address.parse(text)?.compressed()

private fun cymruName(pointer: String): String =
    if (pointer.endsWith(".in-addr.arpa")) pointer.removeSuffix(".in-addr.arpa") + ".origin.$CYMRU" else pointer.removeSuffix(".ip6.arpa") + ".origin6.$CYMRU"
