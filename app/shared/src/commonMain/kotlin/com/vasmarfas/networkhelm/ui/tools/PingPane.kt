package com.vasmarfas.networkhelm.ui.tools

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.composeunstyled.Text
import com.vasmarfas.networkhelm.nettools.HostOps
import com.vasmarfas.networkhelm.nettools.Ipv6Address
import com.vasmarfas.networkhelm.nettools.MtuKind
import com.vasmarfas.networkhelm.nettools.PingReply
import com.vasmarfas.networkhelm.nettools.PingSummary
import com.vasmarfas.networkhelm.nettools.millis
import com.vasmarfas.networkhelm.nettools.mtuKind
import com.vasmarfas.networkhelm.nettools.pathMtu
import com.vasmarfas.networkhelm.nettools.pings
import com.vasmarfas.networkhelm.nettools.tcpConnectMillis
import com.vasmarfas.networkhelm.transport.openTcp
import com.vasmarfas.networkhelm.ui.chart.roundTop
import com.vasmarfas.networkhelm.ui.kit.Button
import com.vasmarfas.networkhelm.ui.kit.Labeled
import com.vasmarfas.networkhelm.ui.kit.Segments
import com.vasmarfas.networkhelm.ui.kit.TextField
import com.vasmarfas.networkhelm.ui.kit.Toggle
import com.vasmarfas.networkhelm.ui.theme.Theme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import networkhelm.app.shared.generated.resources.Res
import networkhelm.app.shared.generated.resources.tools_address
import networkhelm.app.shared.generated.resources.tools_count
import networkhelm.app.shared.generated.resources.tools_host
import networkhelm.app.shared.generated.resources.tools_ms
import networkhelm.app.shared.generated.resources.tools_n1_any
import networkhelm.app.shared.generated.resources.tools_n1_bytes
import networkhelm.app.shared.generated.resources.tools_n1_dont_fragment
import networkhelm.app.shared.generated.resources.tools_n1_endless
import networkhelm.app.shared.generated.resources.tools_n1_family
import networkhelm.app.shared.generated.resources.tools_n1_interval
import networkhelm.app.shared.generated.resources.tools_n1_method
import networkhelm.app.shared.generated.resources.tools_n1_mtu
import networkhelm.app.shared.generated.resources.tools_n1_mtu_ethernet
import networkhelm.app.shared.generated.resources.tools_n1_mtu_failed
import networkhelm.app.shared.generated.resources.tools_n1_mtu_find
import networkhelm.app.shared.generated.resources.tools_n1_mtu_low
import networkhelm.app.shared.generated.resources.tools_n1_mtu_pppoe
import networkhelm.app.shared.generated.resources.tools_n1_mtu_running
import networkhelm.app.shared.generated.resources.tools_n1_mtu_tunnel
import networkhelm.app.shared.generated.resources.tools_n1_needs_fragmentation
import networkhelm.app.shared.generated.resources.tools_n1_no_address
import networkhelm.app.shared.generated.resources.tools_n1_ping_summary
import networkhelm.app.shared.generated.resources.tools_n1_ping_times
import networkhelm.app.shared.generated.resources.tools_n1_tcp_only
import networkhelm.app.shared.generated.resources.tools_n1_ttl
import networkhelm.app.shared.generated.resources.tools_n1_ttl_exceeded
import networkhelm.app.shared.generated.resources.tools_ping_failed
import networkhelm.app.shared.generated.resources.tools_port
import networkhelm.app.shared.generated.resources.tools_size
import networkhelm.app.shared.generated.resources.tools_timeout
import networkhelm.app.shared.generated.resources.tools_timeout_ms

private enum class PingMode(val title: String) { Icmp("ICMP"), Tcp("TCP") }

private enum class PingFamily(val version: String) {
    Auto("IP"),
    V4("IPv4"),
    V6("IPv6");

    fun accepts(address: String) = this == Auto || (':' in address) == (this == V6)
}

private const val CHART_POINTS = 100
private const val SHOWN_REPLIES = 20

// the view is scrolled to the results once, at the first reply, so the next replies have to fit into the room left under them
private val ReplyRoom = 72.dp

@Composable
internal fun PingPane(ops: HostOps?) {
    val scope = rememberCoroutineScope()
    val host = remember { TextFieldState() }
    val port = remember { TextFieldState("443") }
    val count = remember { TextFieldState("4") }
    val interval = remember { TextFieldState("1000") }
    val timeout = remember { TextFieldState("3000") }
    val size = remember { TextFieldState() }
    val ttl = remember { TextFieldState() }
    var mode by remember { mutableStateOf(if (ops != null) PingMode.Icmp else PingMode.Tcp) }
    var family by remember { mutableStateOf(PingFamily.Auto) }
    var endless by remember { mutableStateOf(false) }
    var dontFragment by remember { mutableStateOf(false) }
    val replies = remember { mutableStateListOf<PingReply>() }
    var address by remember { mutableStateOf<String?>(null) }
    var mtu by remember { mutableStateOf<Int?>(null) }
    var finding by remember { mutableStateOf(false) }
    var job by remember { mutableStateOf<Job?>(null) }
    var problem by remember { mutableStateOf<String?>(null) }
    val target = host.text.toString().trim()
    val system = if (mode == PingMode.Icmp) ops else null
    fun waitMillis() = timeout.text.toString().toIntOrNull()?.coerceIn(100, 30_000) ?: 3_000
    fun start() {
        replies.clear()
        problem = null
        mtu = null
        address = null
        val shots = if (endless) null else count.text.toString().toIntOrNull()?.coerceIn(1, 100_000) ?: 4
        val gap = interval.text.toString().toLongOrNull()?.coerceIn(100, 60_000) ?: 1_000
        val remote = port.text.toString().toIntOrNull()?.coerceIn(1, 65_535) ?: 443
        val bytes = size.text.toString().toIntOrNull()?.coerceIn(0, 65_500)
        val hops = ttl.text.toString().toIntOrNull()?.coerceIn(1, 255)
        val wanted = family
        val unfragmented = dontFragment
        val limit = waitMillis()
        job = scope.launch {
            try {
                val found = resolved(ops, target, wanted)
                if (found == null) {
                    problem = getString(Res.string.tools_n1_no_address, target, wanted.version)
                    return@launch
                }
                address = found
                val send = if (system != null) {
                    suspend { system.ping(found, limit, hops, bytes, unfragmented) }
                } else {
                    suspend { PingReply(tcpConnectMillis(found, remote, limit, ::openTcp), null, null) }
                }
                pings(shots, gap, send).collect { replies += it }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                problem = getString(Res.string.tools_ping_failed)
            } finally {
                job = null
            }
        }
    }
    fun findMtu() {
        if (system == null) return
        replies.clear()
        problem = null
        mtu = null
        address = null
        val limit = waitMillis()
        job = scope.launch {
            finding = true
            try {
                val found = resolved(system, target, PingFamily.V4)
                if (found == null) {
                    problem = getString(Res.string.tools_n1_no_address, target, PingFamily.V4.version)
                    return@launch
                }
                address = found
                mtu = pathMtu(system, found, limit)
                if (mtu == null) problem = getString(Res.string.tools_n1_mtu_failed)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                problem = getString(Res.string.tools_ping_failed)
            } finally {
                finding = false
                job = null
            }
        }
    }
    Labeled(stringResource(Res.string.tools_host)) { TextField(host, Modifier.width(FieldWidth).testTag("ping-host"), placeholder = "192.168.88.1", mono = true) }
    if (ops != null) {
        Labeled(stringResource(Res.string.tools_n1_method)) { Segments(mode, PingMode.entries, { it.title }, { mode = it }, Modifier.testTag("ping-mode")) }
        Labeled(stringResource(Res.string.tools_n1_family)) {
            Segments(family, PingFamily.entries, { if (it == PingFamily.Auto) stringResource(Res.string.tools_n1_any) else it.version }, { family = it }, Modifier.testTag("ping-family"))
        }
    } else {
        Text(stringResource(Res.string.tools_n1_tcp_only), color = Theme.palette.muted, style = Theme.type.small)
    }
    if (system == null) Labeled(stringResource(Res.string.tools_port)) { TextField(port, Modifier.width(NumberWidth).testTag("ping-port")) }
    Labeled(stringResource(Res.string.tools_count)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            TextField(count, Modifier.width(NumberWidth).testTag("ping-count"), enabled = !endless)
            Toggle(endless, stringResource(Res.string.tools_n1_endless), null, "ping-endless") { endless = it }
        }
    }
    Labeled(stringResource(Res.string.tools_n1_interval)) { TextField(interval, Modifier.width(NumberWidth).testTag("ping-interval")) }
    Labeled(stringResource(Res.string.tools_timeout_ms)) { TextField(timeout, Modifier.width(NumberWidth).testTag("ping-timeout")) }
    if (system != null) {
        Labeled(stringResource(Res.string.tools_size)) { TextField(size, Modifier.width(NumberWidth).testTag("ping-size")) }
        Labeled(stringResource(Res.string.tools_n1_ttl)) { TextField(ttl, Modifier.width(NumberWidth).testTag("ping-ttl")) }
        Labeled("") { Toggle(dontFragment, stringResource(Res.string.tools_n1_dont_fragment), null, "ping-df") { dontFragment = it } }
    }
    Labeled("") {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            RunButton(job, target.isNotEmpty(), ::start)
            if (system != null) Button(stringResource(Res.string.tools_n1_mtu_find), ::findMtu, Modifier.testTag("ping-mtu"), enabled = job == null && target.isNotEmpty())
        }
    }
    problem?.let { Problem(it) }
    address?.let { found -> Fact(stringResource(Res.string.tools_address), Ipv6Address.parse(found)?.compressed() ?: found) }
    if (finding) Text(stringResource(Res.string.tools_n1_mtu_running), color = Theme.palette.muted, style = Theme.type.small)
    mtu?.let { found ->
        Fact(stringResource(Res.string.tools_n1_mtu), stringResource(Res.string.tools_n1_bytes, found))
        Text(stringResource(mtuAdvice(mtuKind(found))), Modifier.testTag("ping-mtu-advice"), color = Theme.palette.muted, style = Theme.type.small)
    }
    if (replies.isNotEmpty()) {
        val results = remember { BringIntoViewRequester() }
        LaunchedEffect(Unit) { results.bringIntoView() }
        Column(Modifier.bringIntoViewRequester(results).padding(bottom = ReplyRoom), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            PingChart(replies.takeLast(CHART_POINTS), Modifier.fillMaxWidth().height(110.dp).testTag("ping-chart"))
            val silent = stringResource(Res.string.tools_timeout)
            val needsFragmentation = stringResource(Res.string.tools_n1_needs_fragmentation)
            val exceeded = stringResource(Res.string.tools_n1_ttl_exceeded)
            val shown = replies.takeLast(SHOWN_REPLIES)
            val skipped = replies.size - shown.size
            val labelWidth = "#${replies.size}".length
            val lines = shown.mapIndexed { index, reply ->
                val time = reply.timeMillis
                val number = "#${skipped + index + 1}".padEnd(labelWidth)
                when {
                    time != null -> listOfNotNull(number, reply.from, stringResource(Res.string.tools_ms, millis(time)), reply.ttl?.let { "TTL $it" }, exceeded.takeIf { reply.expired }).joinToString("  ")
                    reply.fragmentationNeeded -> listOfNotNull(number, reply.from, needsFragmentation).joinToString("  ")
                    else -> "$number  $silent"
                }
            }
            Text(lines.joinToString("\n"), Modifier.testTag("ping-replies"), style = Theme.type.mono)
            val summary = PingSummary(replies)
            Text(
                stringResource(Res.string.tools_n1_ping_summary, summary.sent, summary.received, summary.lost, "${millis(summary.loss)} %"),
                Modifier.testTag("ping-summary"),
            )
            val min = summary.min
            val average = summary.average
            val max = summary.max
            val jitter = summary.jitter
            if (min != null && average != null && max != null && jitter != null) {
                Text(stringResource(Res.string.tools_n1_ping_times, millis(min), millis(average), millis(max), millis(jitter)), Modifier.testTag("ping-times"))
            }
        }
    }
}

// the first address of the family that the system resolver gives; with no system to ask, the name goes to the socket as typed
private suspend fun resolved(ops: HostOps?, host: String, family: PingFamily): String? {
    if (ops == null) return host
    val addresses = try {
        ops.resolve(host)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        emptyList()
    }
    return addresses.firstOrNull(family::accepts)
}

private fun mtuAdvice(kind: MtuKind): StringResource = when (kind) {
    MtuKind.Ethernet -> Res.string.tools_n1_mtu_ethernet
    MtuKind.Pppoe -> Res.string.tools_n1_mtu_pppoe
    MtuKind.Tunnel -> Res.string.tools_n1_mtu_tunnel
    MtuKind.Low -> Res.string.tools_n1_mtu_low
}

@Composable
private fun PingChart(replies: List<PingReply>, modifier: Modifier) {
    val palette = Theme.palette
    val measurer = rememberTextMeasurer()
    val style = Theme.type.small.copy(color = palette.muted)
    val top = roundTop(replies.maxOfOrNull { it.timeMillis ?: 0.0 } ?: 0.0)
    val label = stringResource(Res.string.tools_ms, millis(top))
    Canvas(modifier) {
        val text = measurer.measure(label, style)
        val head = text.size.height.toFloat()
        for (line in listOf(head, (head + size.height) / 2, size.height - 1)) drawLine(palette.line, Offset(0f, line), Offset(size.width, line))
        drawText(text, topLeft = Offset(size.width - text.size.width - 4.dp.toPx(), 0f))
        val path = Path()
        var drawing = false
        replies.forEachIndexed { index, reply ->
            val x = if (replies.size > 1) size.width * index / (replies.size - 1) else size.width / 2
            val time = reply.timeMillis
            if (time == null) {
                drawLine(palette.danger.copy(alpha = 0.45f), Offset(x, head), Offset(x, size.height), 2.dp.toPx())
                drawing = false
            } else {
                val y = head + (size.height - head) * (1 - (time / top).toFloat())
                if (drawing) path.lineTo(x, y) else path.moveTo(x, y)
                drawing = true
                drawCircle(palette.accent, 2.dp.toPx(), Offset(x, y))
            }
        }
        drawPath(path, palette.accent, style = Stroke(1.5.dp.toPx()))
    }
}
