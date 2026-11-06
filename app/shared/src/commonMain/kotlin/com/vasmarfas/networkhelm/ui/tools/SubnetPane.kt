package com.vasmarfas.networkhelm.ui.tools

import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.composeunstyled.Text
import com.vasmarfas.networkhelm.nettools.Ipv4
import com.vasmarfas.networkhelm.nettools.Ipv6Address
import com.vasmarfas.networkhelm.ui.kit.Labeled
import com.vasmarfas.networkhelm.ui.kit.TextField
import com.vasmarfas.networkhelm.ui.theme.Theme
import org.jetbrains.compose.resources.stringResource
import networkhelm.app.shared.generated.resources.Res
import networkhelm.app.shared.generated.resources.tools_addresses
import networkhelm.app.shared.generated.resources.tools_b_blocks
import networkhelm.app.shared.generated.resources.tools_b_enclosing
import networkhelm.app.shared.generated.resources.tools_b_first_address
import networkhelm.app.shared.generated.resources.tools_b_last_address
import networkhelm.app.shared.generated.resources.tools_b_subnet_input
import networkhelm.app.shared.generated.resources.tools_binary
import networkhelm.app.shared.generated.resources.tools_broadcast
import networkhelm.app.shared.generated.resources.tools_class
import networkhelm.app.shared.generated.resources.tools_expanded
import networkhelm.app.shared.generated.resources.tools_first_host
import networkhelm.app.shared.generated.resources.tools_hosts
import networkhelm.app.shared.generated.resources.tools_last_host
import networkhelm.app.shared.generated.resources.tools_mask_binary
import networkhelm.app.shared.generated.resources.tools_netmask
import networkhelm.app.shared.generated.resources.tools_network
import networkhelm.app.shared.generated.resources.tools_wildcard

@Composable
internal fun SubnetPane() {
    val input = remember { TextFieldState("192.168.88.1/24") }
    val text = input.text.toString()
    val v4 = remember(text) { Ipv4.parseSubnet(text) }
    val range = remember(text) { if (v4 == null) Ipv4.parseRange(text) else null }
    val v6 = remember(text) { if (v4 == null && range == null) Ipv6Address.parseWithPrefix(text) else null }
    Labeled(stringResource(Res.string.tools_b_subnet_input)) {
        TextField(input, Modifier.width(340.dp).testTag("subnet-input"), placeholder = "10.0.0.1/8 · 10.0.0.5-90 · 2001:db8::/48", mono = true, error = text.isNotBlank() && v4 == null && range == null && v6 == null)
    }
    if (v4 != null) {
        Fact(stringResource(Res.string.tools_network), v4.cidr)
        Fact(stringResource(Res.string.tools_netmask), Ipv4.format(v4.mask))
        Fact(stringResource(Res.string.tools_wildcard), Ipv4.format(v4.wildcard))
        Fact(stringResource(Res.string.tools_broadcast), if (v4.prefix >= 31) "—" else Ipv4.format(v4.broadcast))
        Fact(stringResource(Res.string.tools_first_host), Ipv4.format(v4.firstHost))
        Fact(stringResource(Res.string.tools_last_host), Ipv4.format(v4.lastHost))
        Fact(stringResource(Res.string.tools_hosts), v4.usableHosts.toString())
        Fact(stringResource(Res.string.tools_addresses), v4.totalAddresses.toString())
        Fact(stringResource(Res.string.tools_class), v4.ipClass.toString())
        Fact(stringResource(Res.string.tools_binary), Ipv4.binary(v4.address))
        Fact(stringResource(Res.string.tools_mask_binary), Ipv4.binary(v4.mask))
    }
    if (range != null) RangeFacts(range.first, range.second)
    if (v6 != null) {
        val (address, prefix) = v6
        val hostBits = 128 - prefix
        Fact(stringResource(Res.string.tools_network), "${address.withPrefix(prefix).compressed()}/$prefix")
        Fact(stringResource(Res.string.tools_first_host), address.withPrefix(prefix).expanded())
        Fact(stringResource(Res.string.tools_last_host), address.lastInPrefix(prefix).expanded())
        Fact(stringResource(Res.string.tools_addresses), if (hostBits >= 63) "2^$hostBits" else (1L shl hostBits).toString())
        Fact(stringResource(Res.string.tools_expanded), address.expanded())
    }
}

@Composable
private fun RangeFacts(first: Long, last: Long) {
    val blocks = remember(first, last) { Ipv4.cover(first, last) }
    val enclosing = remember(first, last) { Ipv4.enclosing(first, last) }
    val widest = blocks.maxOf { it.cidr.length }
    Fact(stringResource(Res.string.tools_b_first_address), Ipv4.format(first))
    Fact(stringResource(Res.string.tools_b_last_address), Ipv4.format(last))
    Fact(stringResource(Res.string.tools_addresses), (last - first + 1).toString())
    Labeled(stringResource(Res.string.tools_b_blocks)) {
        Text(blocks.joinToString("\n") { "${it.cidr.padEnd(widest)}  ${it.totalAddresses}" }, Modifier.testTag("subnet-blocks"), style = Theme.type.mono)
    }
    Fact(stringResource(Res.string.tools_b_enclosing), enclosing.cidr)
    Fact(stringResource(Res.string.tools_netmask), Ipv4.format(enclosing.mask))
    Fact(stringResource(Res.string.tools_wildcard), Ipv4.format(enclosing.wildcard))
    Fact(stringResource(Res.string.tools_broadcast), if (enclosing.prefix >= 31) "—" else Ipv4.format(enclosing.broadcast))
}
