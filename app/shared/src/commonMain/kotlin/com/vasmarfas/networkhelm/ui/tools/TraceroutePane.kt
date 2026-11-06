package com.vasmarfas.networkhelm.ui.tools

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.vasmarfas.networkhelm.nettools.Hop
import com.vasmarfas.networkhelm.nettools.HostOps
import com.vasmarfas.networkhelm.nettools.Ipv4
import com.vasmarfas.networkhelm.nettools.millis
import com.vasmarfas.networkhelm.nettools.traceroute
import com.vasmarfas.networkhelm.ui.kit.Labeled
import com.vasmarfas.networkhelm.ui.kit.LocalCompact
import com.vasmarfas.networkhelm.ui.kit.TextField
import com.vasmarfas.networkhelm.ui.kit.Toggle
import com.vasmarfas.networkhelm.ui.table.DataTable
import com.vasmarfas.networkhelm.ui.table.TableColumn
import com.vasmarfas.networkhelm.ui.theme.Theme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import networkhelm.app.shared.generated.resources.Res
import networkhelm.app.shared.generated.resources.tools_address
import networkhelm.app.shared.generated.resources.tools_host
import networkhelm.app.shared.generated.resources.tools_n1_resolve_names
import networkhelm.app.shared.generated.resources.tools_name
import networkhelm.app.shared.generated.resources.tools_p_ms
import networkhelm.app.shared.generated.resources.tools_ping_failed
import networkhelm.app.shared.generated.resources.tools_time_ms
import networkhelm.app.shared.generated.resources.tools_unresolved

private class TraceRow(val hop: Hop, val name: String)

@Composable
internal fun TraceroutePane(ops: HostOps) {
    val scope = rememberCoroutineScope()
    val host = remember { TextFieldState() }
    val hops = remember { mutableStateListOf<Hop>() }
    val names = remember { mutableStateMapOf<Int, String>() }
    var lookup by remember { mutableStateOf(true) }
    var job by remember { mutableStateOf<Job?>(null) }
    var problem by remember { mutableStateOf<String?>(null) }
    val target = host.text.toString().trim()
    fun start() {
        hops.clear()
        names.clear()
        problem = null
        job = scope.launch {
            try {
                val addresses = try {
                    ops.resolve(target)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    emptyList()
                }
                val address = addresses.firstOrNull { Ipv4.parse(it) != null }
                if (address == null) {
                    problem = getString(Res.string.tools_unresolved, target)
                    return@launch
                }
                coroutineScope {
                    traceroute(ops, address).collect { hop ->
                        hops += hop
                        val found = hop.address
                        if (lookup && found != null) launch { ops.reverse(found)?.let { names[hop.number] = it } }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                problem = getString(Res.string.tools_ping_failed)
            } finally {
                job = null
            }
        }
    }
    Labeled(stringResource(Res.string.tools_host)) { TextField(host, Modifier.width(FieldWidth).testTag("trace-host"), placeholder = "example.com", mono = true) }
    Labeled("") { Toggle(lookup, stringResource(Res.string.tools_n1_resolve_names), null, "trace-names") { lookup = it } }
    Labeled("") { RunButton(job, target.isNotEmpty(), ::start) }
    problem?.let { Problem(it) }
    if (hops.isNotEmpty()) {
        val compact = LocalCompact.current
        val measurer = rememberTextMeasurer()
        val density = LocalDensity.current
        val body = Theme.type.body
        val named = names.values.maxByOrNull { it.length }?.let { with(density) { measurer.measure(it, body, maxLines = 1, softWrap = false).size.width.toDp() } + 16.dp } ?: 0.dp
        val number = TableColumn<TraceRow>("hop", "#", if (compact) 30.dp else 44.dp) { it.hop.number.toString() }
        val address = TableColumn<TraceRow>("address", stringResource(Res.string.tools_address), if (compact) 116.dp else 150.dp) { it.hop.address ?: "*" }
        val name = TableColumn<TraceRow>("name", stringResource(Res.string.tools_name), maxOf(if (compact) 76.dp else 300.dp, named)) { it.name }
        val time = TableColumn<TraceRow>("time", stringResource(if (compact) Res.string.tools_p_ms else Res.string.tools_time_ms), if (compact) 58.dp else 100.dp) { row -> row.hop.timeMillis?.let(::millis) ?: "*" }
        DataTable(
            rows = hops.map { TraceRow(it, names[it.number].orEmpty()) },
            columns = if (compact) listOf(number, time, address, name) else listOf(number, address, name, time),
            key = { it.hop.number },
            modifier = Modifier.fillMaxWidth().height(TableHeight).testTag("trace-hops"),
        )
    }
}
