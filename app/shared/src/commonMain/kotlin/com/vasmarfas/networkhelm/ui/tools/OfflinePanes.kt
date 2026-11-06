package com.vasmarfas.networkhelm.ui.tools

import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.composeunstyled.Text
import com.vasmarfas.networkhelm.nettools.MacAddress
import com.vasmarfas.networkhelm.nettools.MacRegistry
import com.vasmarfas.networkhelm.ui.kit.Labeled
import com.vasmarfas.networkhelm.ui.kit.TextField
import com.vasmarfas.networkhelm.ui.theme.Theme
import kotlinx.coroutines.CancellationException
import org.jetbrains.compose.resources.stringResource
import networkhelm.app.shared.generated.resources.Res
import networkhelm.app.shared.generated.resources.tools_block
import networkhelm.app.shared.generated.resources.tools_country
import networkhelm.app.shared.generated.resources.tools_loading
import networkhelm.app.shared.generated.resources.tools_mac_address
import networkhelm.app.shared.generated.resources.tools_mac_broadcast
import networkhelm.app.shared.generated.resources.tools_mac_local
import networkhelm.app.shared.generated.resources.tools_mac_not_found
import networkhelm.app.shared.generated.resources.tools_mac_registry
import networkhelm.app.shared.generated.resources.tools_mac_registry_failed
import networkhelm.app.shared.generated.resources.tools_mac_sub_block
import networkhelm.app.shared.generated.resources.tools_vendor

private const val IEEE_AUTHORITY = "IEEE Registration Authority"

@Composable
internal fun MacPane(vendors: suspend () -> MacRegistry) {
    val input = remember { TextFieldState() }
    val loaded by produceState<Result<MacRegistry>?>(null) {
        value = try {
            Result.success(vendors())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    val text = input.text.toString()
    val prefix = MacAddress.prefix(text)
    Labeled(stringResource(Res.string.tools_mac_address)) {
        TextField(input, Modifier.width(260.dp).testTag("mac-input"), placeholder = "00:1A:2B:3C:4D:5E", mono = true, error = text.isNotBlank() && prefix == null)
    }
    val registry = loaded?.getOrNull()
    when {
        loaded == null -> Text(stringResource(Res.string.tools_loading), color = Theme.palette.muted, style = Theme.type.small)
        registry == null -> Problem(stringResource(Res.string.tools_mac_registry_failed))
        prefix != null -> {
            val block = registry.find(MacAddress.individual(prefix))
            val vendor = stringResource(Res.string.tools_vendor)
            when {
                prefix == "FFFFFFFFFFFF" -> Fact(vendor, stringResource(Res.string.tools_mac_broadcast))
                block != null && block.vendor == IEEE_AUTHORITY && prefix.length >= 9 -> Fact(vendor, stringResource(Res.string.tools_mac_sub_block))
                block != null -> {
                    Fact(vendor, block.vendor)
                    block.country?.let { Fact(stringResource(Res.string.tools_country), it) }
                }
                MacAddress.isLocal(prefix) -> Fact(vendor, stringResource(Res.string.tools_mac_local))
                else -> Fact(vendor, stringResource(Res.string.tools_mac_not_found))
            }
            block?.let { Fact(stringResource(Res.string.tools_block), "${it.type} ${MacAddress.colon(it.first)} – ${MacAddress.colon(it.last)}") }
            Text(stringResource(Res.string.tools_mac_registry, registry.date), color = Theme.palette.muted, style = Theme.type.small)
        }
    }
}
