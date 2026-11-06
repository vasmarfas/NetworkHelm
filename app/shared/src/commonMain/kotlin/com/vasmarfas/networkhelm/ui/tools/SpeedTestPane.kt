package com.vasmarfas.networkhelm.ui.tools

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
import com.composeunstyled.Text
import com.vasmarfas.networkhelm.nettools.HttpStatusException
import com.vasmarfas.networkhelm.nettools.PublicHttp
import com.vasmarfas.networkhelm.nettools.SpeedEvent
import com.vasmarfas.networkhelm.nettools.SpeedLatency
import com.vasmarfas.networkhelm.nettools.SpeedMeta
import com.vasmarfas.networkhelm.nettools.SpeedPhase
import com.vasmarfas.networkhelm.nettools.hostOf
import com.vasmarfas.networkhelm.nettools.millis
import com.vasmarfas.networkhelm.nettools.speedBase
import com.vasmarfas.networkhelm.nettools.speedTest
import com.vasmarfas.networkhelm.ui.kit.Labeled
import com.vasmarfas.networkhelm.ui.kit.TextField
import com.vasmarfas.networkhelm.ui.theme.Theme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import networkhelm.app.shared.generated.resources.Res
import networkhelm.app.shared.generated.resources.tools_n2_http_status
import networkhelm.app.shared.generated.resources.tools_n2_mbit
import networkhelm.app.shared.generated.resources.tools_n2_speed_download
import networkhelm.app.shared.generated.resources.tools_n2_speed_download_running
import networkhelm.app.shared.generated.resources.tools_n2_speed_hosts
import networkhelm.app.shared.generated.resources.tools_n2_speed_latency
import networkhelm.app.shared.generated.resources.tools_n2_speed_latency_running
import networkhelm.app.shared.generated.resources.tools_n2_speed_latency_value
import networkhelm.app.shared.generated.resources.tools_n2_speed_network
import networkhelm.app.shared.generated.resources.tools_n2_speed_server
import networkhelm.app.shared.generated.resources.tools_n2_speed_upload
import networkhelm.app.shared.generated.resources.tools_n2_speed_upload_running
import networkhelm.app.shared.generated.resources.tools_n2_speed_url
import networkhelm.app.shared.generated.resources.tools_no_answer

@Composable
internal fun SpeedTestPane(http: PublicHttp) {
    val scope = rememberCoroutineScope()
    val server = remember { TextFieldState("https://speed.cloudflare.com") }
    var job by remember { mutableStateOf<Job?>(null) }
    var problem by remember { mutableStateOf<String?>(null) }
    var place by remember { mutableStateOf<SpeedMeta?>(null) }
    var running by remember { mutableStateOf<SpeedEvent.Running?>(null) }
    var latency by remember { mutableStateOf<SpeedLatency?>(null) }
    var download by remember { mutableStateOf<Double?>(null) }
    var upload by remember { mutableStateOf<Double?>(null) }
    val typed = server.text.toString().trim()
    val base = speedBase(typed)
    val host = hostOf(base)
    fun start() {
        problem = null
        place = null
        latency = null
        download = null
        upload = null
        job = scope.launch {
            try {
                speedTest(http, base).collect { event ->
                    when (event) {
                        is SpeedEvent.Place -> place = event.meta
                        is SpeedEvent.Running -> running = event
                        is SpeedEvent.Latency -> latency = event.latency
                        is SpeedEvent.Download -> download = event.mbps
                        is SpeedEvent.Upload -> upload = event.mbps
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: HttpStatusException) {
                problem = getString(Res.string.tools_n2_http_status, e.host, e.status)
            } catch (e: Exception) {
                problem = getString(Res.string.tools_no_answer, host)
            } finally {
                running = null
                job = null
            }
        }
    }
    Labeled(stringResource(Res.string.tools_n2_speed_url)) { TextField(server, Modifier.width(FieldWidth).testTag("speed-server"), placeholder = "https://speed.cloudflare.com", mono = true) }
    Labeled("") { RunButton(job, typed.isNotEmpty(), ::start) }
    Text(stringResource(Res.string.tools_n2_speed_hosts, host), color = Theme.palette.muted, style = Theme.type.small)
    problem?.let { Problem(it) }
    running?.let { state ->
        val speed = state.mbps?.let(::millis) ?: "…"
        val status = when (state.phase) {
            SpeedPhase.Latency -> stringResource(Res.string.tools_n2_speed_latency_running)
            SpeedPhase.Download -> stringResource(Res.string.tools_n2_speed_download_running, speed)
            SpeedPhase.Upload -> stringResource(Res.string.tools_n2_speed_upload_running, speed)
        }
        Text(status, Modifier.testTag("speed-status"), style = Theme.type.strong)
    }
    place?.let { meta ->
        Fact(stringResource(Res.string.tools_n2_speed_server), listOfNotNull(host, meta.colo).joinToString(" · "))
        meta.network?.let { Fact(stringResource(Res.string.tools_n2_speed_network), it) }
    }
    latency?.let { Fact(stringResource(Res.string.tools_n2_speed_latency), stringResource(Res.string.tools_n2_speed_latency_value, millis(it.medianMillis), millis(it.jitterMillis))) }
    download?.let { Fact(stringResource(Res.string.tools_n2_speed_download), stringResource(Res.string.tools_n2_mbit, millis(it))) }
    upload?.let { Fact(stringResource(Res.string.tools_n2_speed_upload), stringResource(Res.string.tools_n2_mbit, millis(it))) }
}
