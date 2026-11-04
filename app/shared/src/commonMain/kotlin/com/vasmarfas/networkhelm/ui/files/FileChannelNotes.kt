package com.vasmarfas.networkhelm.ui.files

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.composeunstyled.Text
import com.vasmarfas.networkhelm.model.Platform
import com.vasmarfas.networkhelm.session.DeviceSession
import com.vasmarfas.networkhelm.ui.kit.Button
import com.vasmarfas.networkhelm.ui.main.Confirm
import com.vasmarfas.networkhelm.ui.theme.Theme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import networkhelm.app.shared.generated.resources.Res
import networkhelm.app.shared.generated.resources.files_closed_ssh
import networkhelm.app.shared.generated.resources.files_enable_ssh
import networkhelm.app.shared.generated.resources.files_enable_ssh_question
import networkhelm.app.shared.generated.resources.files_key_refused
import networkhelm.app.shared.generated.resources.files_no_ftp
import networkhelm.app.shared.generated.resources.files_no_ssh
import networkhelm.app.shared.generated.resources.files_off_ssh
import networkhelm.app.shared.generated.resources.files_openwrt_ssh
import networkhelm.app.shared.generated.resources.files_retry
import networkhelm.app.shared.generated.resources.files_too_big_rpcd
import networkhelm.app.shared.generated.resources.files_via
import networkhelm.app.shared.generated.resources.files_via_api
import networkhelm.app.shared.generated.resources.files_via_native
import networkhelm.app.shared.generated.resources.files_via_rpcd
import networkhelm.app.shared.generated.resources.files_via_sftp

@Composable
internal fun viaName(via: FileVia): String = stringResource(
    when (via) {
        FileVia.Api -> Res.string.files_via_api
        FileVia.Sftp -> Res.string.files_via_sftp
        FileVia.Native -> Res.string.files_via_native
        FileVia.Rpcd -> Res.string.files_via_rpcd
    },
)

@Composable
internal fun viaText(via: FileVia): String = stringResource(Res.string.files_via, viaName(via))

@Composable
internal fun BulkNote(session: DeviceSession, backend: FileBackend, why: Why, port: Int?, onProblem: (String?) -> Unit, modifier: Modifier = Modifier) {
    val palette = Theme.palette
    val scope = rememberCoroutineScope()
    var enabling by remember { mutableStateOf(false) }
    val routerOs = session.device.platform == Platform.RouterOs
    val off = why == Why.SshOff || why == Why.SshClosed
    val reason = when (why) {
        Why.Rights -> stringResource(Res.string.files_no_ftp)
        Why.SshOff -> stringResource(Res.string.files_off_ssh)
        Why.SshClosed -> stringResource(Res.string.files_closed_ssh, port ?: 22)
        Why.KeyRefused -> stringResource(Res.string.files_key_refused)
        Why.NoSsh -> stringResource(Res.string.files_no_ssh)
        Why.TooBig -> stringResource(Res.string.files_too_big_rpcd)
    }
    Row(
        modifier.fillMaxWidth().background(palette.raised).padding(horizontal = 8.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            if (off && !routerOs) reason + " " + stringResource(Res.string.files_openwrt_ssh) else reason,
            Modifier.weight(1f, fill = false).testTag("files-bulk-note"),
            style = Theme.type.small,
            color = palette.muted,
        )
        if (why == Why.SshOff && routerOs && session.device.canWrite) {
            Button(stringResource(Res.string.files_enable_ssh), { enabling = true }, Modifier.testTag("files-enable-ssh"))
        }
        if (why == Why.KeyRefused) {
            Button(stringResource(Res.string.files_retry), { scope.launch { backend.probe(fresh = true) } }, Modifier.testTag("files-retry"))
        }
    }
    if (enabling) {
        Confirm(stringResource(Res.string.files_enable_ssh_question), stringResource(Res.string.files_enable_ssh), onCancel = { enabling = false }) {
            enabling = false
            scope.launch {
                onProblem(
                    try {
                        backend.enableSsh()
                        null
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        fileFailure(e)
                    },
                )
            }
        }
    }
}
