package com.vasmarfas.networkhelm.ui.tools

import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.composeunstyled.Text
import com.vasmarfas.networkhelm.nettools.HostOps
import com.vasmarfas.networkhelm.nettools.IpPart
import com.vasmarfas.networkhelm.nettools.IpSection
import com.vasmarfas.networkhelm.nettools.PublicHttp
import com.vasmarfas.networkhelm.nettools.ipInfo
import com.vasmarfas.networkhelm.ui.kit.Labeled
import com.vasmarfas.networkhelm.ui.kit.TextField
import com.vasmarfas.networkhelm.ui.theme.Theme
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import networkhelm.app.shared.generated.resources.Res
import networkhelm.app.shared.generated.resources.tools_n2_ip_address
import networkhelm.app.shared.generated.resources.tools_n2_ip_connection
import networkhelm.app.shared.generated.resources.tools_n2_ip_hosts
import networkhelm.app.shared.generated.resources.tools_n2_ip_location
import networkhelm.app.shared.generated.resources.tools_n2_ip_mine
import networkhelm.app.shared.generated.resources.tools_n2_ip_network
import networkhelm.app.shared.generated.resources.tools_n2_ip_nothing
import networkhelm.app.shared.generated.resources.tools_n2_ip_query
import networkhelm.app.shared.generated.resources.tools_n2_ip_reverse
import networkhelm.app.shared.generated.resources.tools_n2_ip_routing
import networkhelm.app.shared.generated.resources.tools_n2_source
import networkhelm.app.shared.generated.resources.tools_n2_source_system
import networkhelm.app.shared.generated.resources.tools_no_answer

@Composable
internal fun IpInfoPane(http: PublicHttp, ops: HostOps?) {
    val scope = rememberCoroutineScope()
    val query = remember { TextFieldState() }
    val sections = remember { mutableStateListOf<IpSection>() }
    var job by remember { mutableStateOf<Job?>(null) }
    val asked = query.text.toString().trim()
    fun start() {
        sections.clear()
        job = scope.launch {
            try {
                ipInfo(http, ops, asked).collect { sections += it }
            } finally {
                job = null
            }
        }
    }
    Labeled(stringResource(Res.string.tools_n2_ip_query)) {
        TextField(query, Modifier.width(FieldWidth).testTag("ip-query"), placeholder = stringResource(Res.string.tools_n2_ip_mine), mono = true)
    }
    Labeled("") { RunButton(job, true, ::start) }
    Text(stringResource(Res.string.tools_n2_ip_hosts), color = Theme.palette.muted, style = Theme.type.small)
    for (section in sections.sortedBy { it.part.ordinal }) IpBlock(section)
}

@Composable
private fun IpBlock(section: IpSection) {
    Text(stringResource(section.part.title()), Modifier.testTag("ip-${section.part.name}"), style = Theme.type.strong)
    val source = if (section.hosts.isEmpty()) stringResource(Res.string.tools_n2_source_system) else section.hosts.joinToString(", ")
    when {
        section.failed -> Problem(stringResource(Res.string.tools_no_answer, source))
        section.rows.isEmpty() -> Text(stringResource(Res.string.tools_n2_ip_nothing, source), color = Theme.palette.muted, style = Theme.type.small)
        else -> {
            InfoFacts(section.rows)
            Text(stringResource(Res.string.tools_n2_source, source), color = Theme.palette.muted, style = Theme.type.small)
        }
    }
}

private fun IpPart.title(): StringResource = when (this) {
    IpPart.Address -> Res.string.tools_n2_ip_address
    IpPart.Reverse -> Res.string.tools_n2_ip_reverse
    IpPart.Routing -> Res.string.tools_n2_ip_routing
    IpPart.Network -> Res.string.tools_n2_ip_network
    IpPart.Location -> Res.string.tools_n2_ip_location
    IpPart.Connection -> Res.string.tools_n2_ip_connection
}
