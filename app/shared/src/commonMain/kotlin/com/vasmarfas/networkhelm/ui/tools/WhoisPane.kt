package com.vasmarfas.networkhelm.ui.tools

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.composeunstyled.Text
import com.vasmarfas.networkhelm.nettools.InfoRow
import com.vasmarfas.networkhelm.nettools.PublicHttp
import com.vasmarfas.networkhelm.nettools.RdapResult
import com.vasmarfas.networkhelm.nettools.WhoisEnd
import com.vasmarfas.networkhelm.nettools.WhoisReply
import com.vasmarfas.networkhelm.nettools.rdapFacts
import com.vasmarfas.networkhelm.nettools.rdapLookup
import com.vasmarfas.networkhelm.nettools.rdapRaw
import com.vasmarfas.networkhelm.nettools.rdapTarget
import com.vasmarfas.networkhelm.nettools.whois
import com.vasmarfas.networkhelm.nettools.whoisText
import com.vasmarfas.networkhelm.transport.tcpListening
import com.vasmarfas.networkhelm.ui.kit.Labeled
import com.vasmarfas.networkhelm.ui.kit.Segments
import com.vasmarfas.networkhelm.ui.kit.TextField
import com.vasmarfas.networkhelm.ui.kit.Toggle
import com.vasmarfas.networkhelm.ui.theme.Theme
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import networkhelm.app.shared.generated.resources.Res
import networkhelm.app.shared.generated.resources.tools_country
import networkhelm.app.shared.generated.resources.tools_host
import networkhelm.app.shared.generated.resources.tools_n2_abuse
import networkhelm.app.shared.generated.resources.tools_n2_abuse_email
import networkhelm.app.shared.generated.resources.tools_n2_abuse_phone
import networkhelm.app.shared.generated.resources.tools_n2_admin_email
import networkhelm.app.shared.generated.resources.tools_n2_administrative
import networkhelm.app.shared.generated.resources.tools_n2_allocated
import networkhelm.app.shared.generated.resources.tools_n2_as_name
import networkhelm.app.shared.generated.resources.tools_n2_as_number
import networkhelm.app.shared.generated.resources.tools_n2_as_range
import networkhelm.app.shared.generated.resources.tools_n2_billing
import networkhelm.app.shared.generated.resources.tools_n2_calling_code
import networkhelm.app.shared.generated.resources.tools_n2_changed
import networkhelm.app.shared.generated.resources.tools_n2_cidr
import networkhelm.app.shared.generated.resources.tools_n2_city
import networkhelm.app.shared.generated.resources.tools_n2_coordinates
import networkhelm.app.shared.generated.resources.tools_n2_description
import networkhelm.app.shared.generated.resources.tools_n2_dnssec
import networkhelm.app.shared.generated.resources.tools_n2_domain
import networkhelm.app.shared.generated.resources.tools_n2_eu
import networkhelm.app.shared.generated.resources.tools_n2_expires
import networkhelm.app.shared.generated.resources.tools_n2_handle
import networkhelm.app.shared.generated.resources.tools_n2_http_status
import networkhelm.app.shared.generated.resources.tools_n2_ipv4
import networkhelm.app.shared.generated.resources.tools_n2_ipv6
import networkhelm.app.shared.generated.resources.tools_n2_isp
import networkhelm.app.shared.generated.resources.tools_n2_local_time
import networkhelm.app.shared.generated.resources.tools_n2_name_servers
import networkhelm.app.shared.generated.resources.tools_n2_network_name
import networkhelm.app.shared.generated.resources.tools_n2_no
import networkhelm.app.shared.generated.resources.tools_n2_organisation
import networkhelm.app.shared.generated.resources.tools_n2_parent
import networkhelm.app.shared.generated.resources.tools_n2_postal
import networkhelm.app.shared.generated.resources.tools_n2_postal_code
import networkhelm.app.shared.generated.resources.tools_n2_prefix
import networkhelm.app.shared.generated.resources.tools_n2_range
import networkhelm.app.shared.generated.resources.tools_n2_raw
import networkhelm.app.shared.generated.resources.tools_n2_rdap
import networkhelm.app.shared.generated.resources.tools_n2_rdap_from
import networkhelm.app.shared.generated.resources.tools_n2_rdap_hosts
import networkhelm.app.shared.generated.resources.tools_n2_rdap_missing
import networkhelm.app.shared.generated.resources.tools_n2_rdap_unreadable
import networkhelm.app.shared.generated.resources.tools_n2_rdap_updated
import networkhelm.app.shared.generated.resources.tools_n2_region
import networkhelm.app.shared.generated.resources.tools_n2_registered
import networkhelm.app.shared.generated.resources.tools_n2_registrant
import networkhelm.app.shared.generated.resources.tools_n2_registrar
import networkhelm.app.shared.generated.resources.tools_n2_registry
import networkhelm.app.shared.generated.resources.tools_n2_status
import networkhelm.app.shared.generated.resources.tools_n2_technical
import networkhelm.app.shared.generated.resources.tools_n2_timezone
import networkhelm.app.shared.generated.resources.tools_n2_transferred
import networkhelm.app.shared.generated.resources.tools_n2_unicode_name
import networkhelm.app.shared.generated.resources.tools_n2_whois_43
import networkhelm.app.shared.generated.resources.tools_n2_whois_43_hosts
import networkhelm.app.shared.generated.resources.tools_n2_whois_cut
import networkhelm.app.shared.generated.resources.tools_n2_whois_query
import networkhelm.app.shared.generated.resources.tools_n2_whois_server
import networkhelm.app.shared.generated.resources.tools_n2_whois_silent
import networkhelm.app.shared.generated.resources.tools_n2_whois_unreachable
import networkhelm.app.shared.generated.resources.tools_n2_yes
import networkhelm.app.shared.generated.resources.tools_name
import networkhelm.app.shared.generated.resources.tools_no_answer
import networkhelm.app.shared.generated.resources.tools_type

private enum class WhoisMode(val title: StringResource) {
    Rdap(Res.string.tools_n2_rdap),
    Port43(Res.string.tools_n2_whois_43),
}

private val infoLabels: Map<String, StringResource> = mapOf(
    "domain" to Res.string.tools_n2_domain,
    "unicode" to Res.string.tools_n2_unicode_name,
    "handle" to Res.string.tools_n2_handle,
    "name" to Res.string.tools_name,
    "type" to Res.string.tools_type,
    "range" to Res.string.tools_n2_range,
    "as-range" to Res.string.tools_n2_as_range,
    "cidr" to Res.string.tools_n2_cidr,
    "parent" to Res.string.tools_n2_parent,
    "country" to Res.string.tools_country,
    "status" to Res.string.tools_n2_status,
    "registered" to Res.string.tools_n2_registered,
    "expires" to Res.string.tools_n2_expires,
    "changed" to Res.string.tools_n2_changed,
    "rdap-updated" to Res.string.tools_n2_rdap_updated,
    "transferred" to Res.string.tools_n2_transferred,
    "nameservers" to Res.string.tools_n2_name_servers,
    "dnssec" to Res.string.tools_n2_dnssec,
    "whois-server" to Res.string.tools_n2_whois_server,
    "registrar" to Res.string.tools_n2_registrar,
    "registrant" to Res.string.tools_n2_registrant,
    "abuse" to Res.string.tools_n2_abuse,
    "technical" to Res.string.tools_n2_technical,
    "administrative" to Res.string.tools_n2_administrative,
    "billing" to Res.string.tools_n2_billing,
    "host" to Res.string.tools_host,
    "ipv4" to Res.string.tools_n2_ipv4,
    "ipv6" to Res.string.tools_n2_ipv6,
    "asn" to Res.string.tools_n2_as_number,
    "prefix" to Res.string.tools_n2_prefix,
    "registry" to Res.string.tools_n2_registry,
    "allocated" to Res.string.tools_n2_allocated,
    "as-name" to Res.string.tools_n2_as_name,
    "network-name" to Res.string.tools_n2_network_name,
    "description" to Res.string.tools_n2_description,
    "organisation" to Res.string.tools_n2_organisation,
    "postal" to Res.string.tools_n2_postal,
    "abuse-email" to Res.string.tools_n2_abuse_email,
    "abuse-phone" to Res.string.tools_n2_abuse_phone,
    "admin-email" to Res.string.tools_n2_admin_email,
    "region" to Res.string.tools_n2_region,
    "city" to Res.string.tools_n2_city,
    "postal-code" to Res.string.tools_n2_postal_code,
    "coordinates" to Res.string.tools_n2_coordinates,
    "timezone" to Res.string.tools_n2_timezone,
    "local-time" to Res.string.tools_n2_local_time,
    "calling-code" to Res.string.tools_n2_calling_code,
    "eu" to Res.string.tools_n2_eu,
    "isp" to Res.string.tools_n2_isp,
)

@Composable
internal fun WhoisPane(http: PublicHttp?) {
    val modes = remember(http) { listOfNotNull(WhoisMode.Rdap.takeIf { http != null }, WhoisMode.Port43.takeIf { tcpListening }) }
    var mode by remember { mutableStateOf(modes.first()) }
    val query = remember { TextFieldState() }
    if (modes.size > 1) Segments(mode, modes, { stringResource(it.title) }, { mode = it }, Modifier.testTag("whois-mode"))
    Labeled(stringResource(Res.string.tools_n2_whois_query)) { TextField(query, Modifier.width(FieldWidth).testTag("whois-query"), placeholder = "example.com", mono = true) }
    when (mode) {
        WhoisMode.Rdap -> http?.let { RdapTab(it, query) }
        WhoisMode.Port43 -> Port43Tab(query)
    }
}

@Composable
private fun RdapTab(http: PublicHttp, query: TextFieldState) {
    val scope = rememberCoroutineScope()
    var job by remember { mutableStateOf<Job?>(null) }
    var found by remember { mutableStateOf<RdapResult.Found?>(null) }
    var problem by remember { mutableStateOf<String?>(null) }
    var raw by remember { mutableStateOf(false) }
    val asked = query.text.toString().trim()
    val target = rdapTarget(asked)
    fun start() {
        val goal = target ?: return
        found = null
        problem = null
        job = scope.launch {
            try {
                when (val result = rdapLookup(http, goal)) {
                    is RdapResult.Found -> found = result
                    is RdapResult.Missing -> problem = getString(Res.string.tools_n2_rdap_missing, result.server, asked)
                    is RdapResult.Refused -> problem = getString(Res.string.tools_n2_http_status, result.server, result.status)
                    is RdapResult.Unreadable -> problem = getString(Res.string.tools_n2_rdap_unreadable, result.server)
                    is RdapResult.Silent -> problem = getString(Res.string.tools_no_answer, result.server)
                }
            } finally {
                job = null
            }
        }
    }
    Labeled("") { RunButton(job, target != null, ::start) }
    Text(stringResource(Res.string.tools_n2_rdap_hosts), color = Theme.palette.muted, style = Theme.type.small)
    problem?.let { Problem(it) }
    found?.let { result ->
        Text(stringResource(Res.string.tools_n2_rdap_from, result.server), Modifier.testTag("rdap-server"), color = Theme.palette.muted, style = Theme.type.small)
        Toggle(raw, stringResource(Res.string.tools_n2_raw), null, "rdap-raw") { raw = it }
        if (raw) {
            val text = remember(result) { rdapRaw(result.json) }
            Text(text, Modifier.fillMaxWidth().background(Theme.palette.raised).padding(8.dp).testTag("rdap-raw-answer"), style = Theme.type.mono)
        } else {
            InfoFacts(remember(result) { rdapFacts(result.json) })
        }
    }
}

@Composable
private fun Port43Tab(query: TextFieldState) {
    val scope = rememberCoroutineScope()
    var job by remember { mutableStateOf<Job?>(null) }
    var replies by remember { mutableStateOf<List<WhoisReply>?>(null) }
    val asked = query.text.toString().trim()
    fun start() {
        replies = null
        job = scope.launch {
            try {
                replies = whois(asked)
            } finally {
                job = null
            }
        }
    }
    Labeled("") { RunButton(job, asked.isNotEmpty(), ::start) }
    Text(stringResource(Res.string.tools_n2_whois_43_hosts), color = Theme.palette.muted, style = Theme.type.small)
    replies?.let { list ->
        val text = whoisText(list)
        if (text.isNotEmpty()) Text(text, Modifier.fillMaxWidth().background(Theme.palette.raised).padding(8.dp).testTag("whois-answer"), style = Theme.type.mono)
        for (reply in list) {
            when (reply.end) {
                WhoisEnd.Complete -> Unit
                WhoisEnd.Cut -> Problem(stringResource(Res.string.tools_n2_whois_cut, reply.server))
                WhoisEnd.Silent -> Problem(stringResource(Res.string.tools_n2_whois_silent, reply.server))
                WhoisEnd.Unreachable -> Problem(stringResource(Res.string.tools_n2_whois_unreachable, reply.server))
            }
        }
    }
}

@Composable
internal fun InfoFacts(rows: List<InfoRow>) {
    val yes = stringResource(Res.string.tools_n2_yes)
    val no = stringResource(Res.string.tools_n2_no)
    for (row in rows) {
        val label = infoLabels[row.key]?.let { stringResource(it) } ?: row.key
        val value = when (row.value) {
            "true" -> yes
            "false" -> no
            else -> row.value
        }
        Fact(label, value)
    }
}
