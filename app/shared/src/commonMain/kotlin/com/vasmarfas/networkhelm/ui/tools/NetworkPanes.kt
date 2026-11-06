package com.vasmarfas.networkhelm.ui.tools

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.composeunstyled.Text
import com.vasmarfas.networkhelm.nettools.DnsRecord
import com.vasmarfas.networkhelm.nettools.DnsResponse
import com.vasmarfas.networkhelm.nettools.DnsTypes
import com.vasmarfas.networkhelm.nettools.HostOps
import com.vasmarfas.networkhelm.nettools.PortResult
import com.vasmarfas.networkhelm.nettools.PublicResolvers
import com.vasmarfas.networkhelm.nettools.WellKnownPorts
import com.vasmarfas.networkhelm.nettools.dnsQuery
import com.vasmarfas.networkhelm.nettools.magicPacket
import com.vasmarfas.networkhelm.nettools.parsePorts
import com.vasmarfas.networkhelm.nettools.reverseName
import com.vasmarfas.networkhelm.nettools.scanPorts
import com.vasmarfas.networkhelm.ui.kit.Button
import com.vasmarfas.networkhelm.ui.kit.Labeled
import com.vasmarfas.networkhelm.ui.kit.LocalCompact
import com.vasmarfas.networkhelm.ui.kit.Select
import com.vasmarfas.networkhelm.ui.kit.TextField
import com.vasmarfas.networkhelm.ui.table.DataTable
import com.vasmarfas.networkhelm.ui.table.RowTone
import com.vasmarfas.networkhelm.ui.table.TableColumn
import com.vasmarfas.networkhelm.ui.theme.Theme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import networkhelm.app.shared.generated.resources.Res
import networkhelm.app.shared.generated.resources.tools_broadcast
import networkhelm.app.shared.generated.resources.tools_closed
import networkhelm.app.shared.generated.resources.tools_data
import networkhelm.app.shared.generated.resources.tools_dns_name
import networkhelm.app.shared.generated.resources.tools_dns_status
import networkhelm.app.shared.generated.resources.tools_host
import networkhelm.app.shared.generated.resources.tools_mac_address
import networkhelm.app.shared.generated.resources.tools_name
import networkhelm.app.shared.generated.resources.tools_no_answer
import networkhelm.app.shared.generated.resources.tools_open
import networkhelm.app.shared.generated.resources.tools_parallel
import networkhelm.app.shared.generated.resources.tools_port
import networkhelm.app.shared.generated.resources.tools_ports_done
import networkhelm.app.shared.generated.resources.tools_ports_list
import networkhelm.app.shared.generated.resources.tools_ports_progress
import networkhelm.app.shared.generated.resources.tools_resolver
import networkhelm.app.shared.generated.resources.tools_resolver_other
import networkhelm.app.shared.generated.resources.tools_service
import networkhelm.app.shared.generated.resources.tools_state
import networkhelm.app.shared.generated.resources.tools_timeout_ms
import networkhelm.app.shared.generated.resources.tools_type
import networkhelm.app.shared.generated.resources.tools_wake_send
import networkhelm.app.shared.generated.resources.tools_wol_failed
import networkhelm.app.shared.generated.resources.tools_wol_sent

private typealias DnsRow = IndexedValue<DnsRecord>

@Composable
internal fun PortsPane() {
    val scope = rememberCoroutineScope()
    val host = remember { TextFieldState() }
    val spec = remember { TextFieldState(WellKnownPorts.top.joinToString(",")) }
    val timeout = remember { TextFieldState("800") }
    val parallel = remember { TextFieldState("64") }
    val results = remember { mutableStateListOf<PortResult>() }
    var total by remember { mutableIntStateOf(0) }
    var job by remember { mutableStateOf<Job?>(null) }
    var problem by remember { mutableStateOf<String?>(null) }
    val target = host.text.toString().trim()
    val written = spec.text.toString()
    val ports = remember(written) { parsePorts(written) }
    fun start() {
        results.clear()
        problem = null
        total = ports.size
        val wait = timeout.text.toString().toIntOrNull()?.coerceIn(100, 10_000) ?: 800
        val atOnce = parallel.text.toString().toIntOrNull()?.coerceIn(1, 256) ?: 64
        job = scope.launch {
            try {
                scanPorts(target, ports, atOnce, wait).collect { results += it }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                problem = getString(Res.string.tools_no_answer, target)
            } finally {
                job = null
            }
        }
    }
    Labeled(stringResource(Res.string.tools_host)) { TextField(host, Modifier.width(FieldWidth).testTag("ports-host"), placeholder = "192.168.88.1", mono = true) }
    Labeled(stringResource(Res.string.tools_ports_list)) {
        TextField(spec, Modifier.width(FieldWidth).testTag("ports-list"), placeholder = "22,80,443 1-1024", mono = true, error = written.isNotBlank() && ports.isEmpty())
    }
    Labeled(stringResource(Res.string.tools_timeout_ms)) { TextField(timeout, Modifier.width(NumberWidth)) }
    Labeled(stringResource(Res.string.tools_parallel)) { TextField(parallel, Modifier.width(NumberWidth)) }
    Labeled("") { RunButton(job, target.isNotEmpty() && ports.isNotEmpty(), ::start) }
    problem?.let { Problem(it) }
    if (total > 0) {
        val open = results.count { it.open }
        val status = if (job != null) stringResource(Res.string.tools_ports_progress, results.size, total) else stringResource(Res.string.tools_ports_done, open, total)
        Text(status, Modifier.testTag("ports-status"), color = Theme.palette.muted, style = Theme.type.small)
        val opened = stringResource(Res.string.tools_open)
        val closed = stringResource(Res.string.tools_closed)
        DataTable(
            rows = results.sortedBy { it.port },
            columns = listOf(
                TableColumn("port", stringResource(Res.string.tools_port), 60.dp) { it.port.toString() },
                TableColumn("state", stringResource(Res.string.tools_state), 150.dp) { if (it.open) opened else closed },
                TableColumn("service", stringResource(Res.string.tools_service), 100.dp) { WellKnownPorts.service(it.port).orEmpty() },
            ),
            key = { it.port },
            tone = { if (it.open) RowTone.Normal else RowTone.Faded },
            modifier = Modifier.fillMaxWidth().height(TableHeight).testTag("ports-table"),
        )
    }
}

@Composable
internal fun DnsPane() {
    val scope = rememberCoroutineScope()
    val name = remember { TextFieldState() }
    val other = remember { TextFieldState() }
    var type by remember { mutableStateOf(DnsTypes.queryable.first()) }
    var resolver by remember { mutableStateOf<Pair<String, String>?>(PublicResolvers.first()) }
    var response by remember { mutableStateOf<DnsResponse?>(null) }
    var job by remember { mutableStateOf<Job?>(null) }
    var problem by remember { mutableStateOf<String?>(null) }
    val query = name.text.toString().trim()
    val server = resolver?.second ?: other.text.toString().trim()
    fun start() {
        response = null
        problem = null
        val reverse = reverseName(query)
        val asked = if (reverse != null) DnsTypes.byName.getValue("PTR") else DnsTypes.byName.getValue(type)
        job = scope.launch {
            try {
                response = dnsQuery(server, reverse ?: query, asked) ?: run {
                    problem = getString(Res.string.tools_no_answer, server)
                    null
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                problem = getString(Res.string.tools_no_answer, server)
            } finally {
                job = null
            }
        }
    }
    Labeled(stringResource(Res.string.tools_dns_name)) { TextField(name, Modifier.width(FieldWidth).testTag("dns-name"), placeholder = "example.com", mono = true) }
    Labeled(stringResource(Res.string.tools_type)) { Select(type, DnsTypes.queryable, { it }, { type = it }, Modifier.width(NumberWidth).testTag("dns-type")) }
    val otherLabel = stringResource(Res.string.tools_resolver_other)
    Labeled(stringResource(Res.string.tools_resolver)) {
        Select(resolver, PublicResolvers + null, { it?.let { (title, address) -> "$title ($address)" } ?: otherLabel }, { resolver = it }, Modifier.width(FieldWidth).testTag("dns-resolver"))
    }
    if (resolver == null) Labeled("") { TextField(other, Modifier.width(FieldWidth).testTag("dns-server"), placeholder = "192.168.88.1", mono = true) }
    Labeled("") { RunButton(job, query.isNotEmpty() && server.isNotEmpty(), ::start) }
    problem?.let { Problem(it) }
    response?.let { answer ->
        val records = answer.answers + answer.authority
        Text(stringResource(Res.string.tools_dns_status, answer.rcodeName, answer.answers.size), Modifier.testTag("dns-status"), color = Theme.palette.muted, style = Theme.type.small)
        if (records.isNotEmpty()) {
            val compact = LocalCompact.current
            val name = TableColumn<DnsRow>("name", stringResource(Res.string.tools_name), if (compact) 150.dp else 200.dp) { it.value.name }
            val type = TableColumn<DnsRow>("type", stringResource(Res.string.tools_type), 60.dp) { it.value.typeName }
            val ttl = TableColumn<DnsRow>("ttl", "TTL", 60.dp) { it.value.ttl.toString() }
            val data = TableColumn<DnsRow>("data", stringResource(Res.string.tools_data), if (compact) 230.dp else 320.dp) { it.value.data }
            DataTable(
                rows = records.withIndex().toList(),
                columns = if (compact) listOf(type, data, ttl, name) else listOf(name, type, ttl, data),
                key = { it.index },
                modifier = Modifier.fillMaxWidth().height(TableHeight).testTag("dns-records"),
            )
        }
    }
}

@Composable
internal fun WakePane(ops: HostOps) {
    val scope = rememberCoroutineScope()
    val mac = remember { TextFieldState() }
    val broadcast = remember { TextFieldState("255.255.255.255") }
    val port = remember { TextFieldState("9") }
    var sending by remember { mutableStateOf(false) }
    var done by remember { mutableStateOf<String?>(null) }
    var problem by remember { mutableStateOf<String?>(null) }
    val typed = mac.text.toString()
    val packet = magicPacket(typed)
    fun send(bytes: ByteArray) {
        done = null
        problem = null
        sending = true
        val address = broadcast.text.toString().trim()
        val number = port.text.toString().toIntOrNull()?.coerceIn(1, 65535) ?: 9
        val target = "$address:$number"
        scope.launch {
            try {
                ops.wake(bytes, address, number)
                done = getString(Res.string.tools_wol_sent, typed.trim(), target)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                problem = getString(Res.string.tools_wol_failed, target)
            } finally {
                sending = false
            }
        }
    }
    Labeled(stringResource(Res.string.tools_mac_address)) {
        TextField(mac, Modifier.width(FieldWidth).testTag("wake-mac"), placeholder = "00:1A:2B:3C:4D:5E", mono = true, error = typed.isNotBlank() && packet == null)
    }
    Labeled(stringResource(Res.string.tools_broadcast)) { TextField(broadcast, Modifier.width(FieldWidth).testTag("wake-broadcast"), mono = true) }
    Labeled(stringResource(Res.string.tools_port)) { TextField(port, Modifier.width(NumberWidth).testTag("wake-port")) }
    Labeled("") { Button(stringResource(Res.string.tools_wake_send), { packet?.let(::send) }, Modifier.testTag("wake-send"), enabled = packet != null && !sending, primary = true) }
    problem?.let { Problem(it) }
    done?.let { Text(it, Modifier.testTag("wake-done"), color = Theme.palette.accent, style = Theme.type.small) }
}
