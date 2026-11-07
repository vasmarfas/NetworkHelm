package com.vasmarfas.networkhelm.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.Globe
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.X
import com.composeunstyled.Text
import com.composeunstyled.UnstyledIcon
import com.vasmarfas.networkhelm.app.AccessProblem
import com.vasmarfas.networkhelm.app.BrowserAccess
import com.vasmarfas.networkhelm.qr.QrCode
import com.vasmarfas.networkhelm.ui.kit.Button
import com.vasmarfas.networkhelm.ui.kit.IconButton
import com.vasmarfas.networkhelm.ui.kit.Labeled
import com.vasmarfas.networkhelm.ui.kit.QrImage
import com.vasmarfas.networkhelm.ui.kit.ScrollArea
import com.vasmarfas.networkhelm.ui.kit.SecretField
import com.vasmarfas.networkhelm.ui.kit.TextField
import com.vasmarfas.networkhelm.ui.kit.Toggle
import com.vasmarfas.networkhelm.ui.kit.dialogPanel
import com.vasmarfas.networkhelm.ui.kit.hand
import com.vasmarfas.networkhelm.ui.kit.scrim
import com.vasmarfas.networkhelm.ui.theme.Theme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import networkhelm.app.shared.generated.resources.Res
import networkhelm.app.shared.generated.resources.access_addresses
import networkhelm.app.shared.generated.resources.access_certificate
import networkhelm.app.shared.generated.resources.access_certificate_hint
import networkhelm.app.shared.generated.resources.access_change_password
import networkhelm.app.shared.generated.resources.access_code
import networkhelm.app.shared.generated.resources.access_code_hint
import networkhelm.app.shared.generated.resources.access_failed
import networkhelm.app.shared.generated.resources.access_files_failed
import networkhelm.app.shared.generated.resources.access_hide_code
import networkhelm.app.shared.generated.resources.access_hint
import networkhelm.app.shared.generated.resources.access_mark
import networkhelm.app.shared.generated.resources.access_new_code
import networkhelm.app.shared.generated.resources.access_no_web_app
import networkhelm.app.shared.generated.resources.access_open
import networkhelm.app.shared.generated.resources.access_password
import networkhelm.app.shared.generated.resources.access_password_set
import networkhelm.app.shared.generated.resources.access_passwords_differ
import networkhelm.app.shared.generated.resources.access_port
import networkhelm.app.shared.generated.resources.access_port_taken
import networkhelm.app.shared.generated.resources.access_public
import networkhelm.app.shared.generated.resources.access_public_hint
import networkhelm.app.shared.generated.resources.access_repeat
import networkhelm.app.shared.generated.resources.access_scan
import networkhelm.app.shared.generated.resources.access_set_password
import networkhelm.app.shared.generated.resources.access_show_code
import networkhelm.app.shared.generated.resources.access_title
import networkhelm.app.shared.generated.resources.gateway_key
import networkhelm.app.shared.generated.resources.settings_close

@Composable
fun BrowserAccessDialog(access: BrowserAccess, onClose: () -> Unit) {
    val palette = Theme.palette
    val scope = rememberCoroutineScope()
    val password = remember { TextFieldState() }
    val repeat = remember { TextFieldState() }
    val port = remember { TextFieldState(access.port.toString()) }
    var changing by remember { mutableStateOf(!access.ready) }
    var differ by remember { mutableStateOf(false) }
    var showCode by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var failure by remember { mutableStateOf<String?>(null) }
    val chosenPort = port.text.toString().toIntOrNull()?.takeIf { it in 1..65535 }
    fun act(action: suspend () -> Unit) {
        busy = true
        failure = null
        scope.launch {
            try {
                action()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                failure = e.message ?: e::class.simpleName
            } finally {
                busy = false
            }
        }
    }
    Box(Modifier.fillMaxSize().scrim(palette.workspace.copy(alpha = 0.6f), onClose), contentAlignment = Alignment.Center) {
        Column(
            Modifier.widthIn(max = 640.dp).padding(16.dp).dialogPanel(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(Res.string.access_title), Modifier.weight(1f), style = Theme.type.strong)
                IconButton(Lucide.X, stringResource(Res.string.settings_close), onClose, Modifier.testTag("access-cross"))
            }
            ScrollArea(Modifier.weight(1f, fill = false), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(Res.string.access_hint), color = palette.muted)
                if (!access.webApp) Text(stringResource(Res.string.access_no_web_app), color = palette.danger, style = Theme.type.small)
                if (changing) {
                    Labeled(stringResource(Res.string.access_password)) { SecretField(password, Modifier.width(220.dp).testTag("access-password")) }
                    Labeled(stringResource(Res.string.access_repeat)) { SecretField(repeat, Modifier.width(220.dp).testTag("access-repeat")) }
                    Labeled("") {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(stringResource(Res.string.access_set_password), {
                                differ = password.text.toString() != repeat.text.toString()
                                if (!differ) {
                                    act {
                                        access.setPassword(password.text.toString())
                                        password.clearText()
                                        repeat.clearText()
                                        changing = false
                                    }
                                }
                            }, enabled = password.text.isNotEmpty() && !busy, primary = true)
                            if (differ) Text(stringResource(Res.string.access_passwords_differ), Modifier.testTag("access-differ"), color = palette.danger)
                        }
                    }
                } else {
                    Labeled(stringResource(Res.string.access_password)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(stringResource(Res.string.access_password_set), color = palette.muted)
                            Button(stringResource(Res.string.access_change_password), { changing = true })
                        }
                    }
                }
                Toggle(access.open, stringResource(Res.string.access_open), null, "access-open", enabled = access.ready && chosenPort != null && !busy) { on ->
                    act {
                        if (on) {
                            access.change(chosenPort ?: access.port, access.public, access.code)
                            access.open()
                        } else {
                            access.close()
                        }
                    }
                }
                Labeled(stringResource(Res.string.access_port)) { TextField(port, Modifier.width(90.dp).testTag("access-port"), enabled = !access.open) }
                Toggle(access.public, stringResource(Res.string.access_public), stringResource(Res.string.access_public_hint), "access-public", enabled = !busy) { on ->
                    showCode = on
                    act { access.change(chosenPort ?: access.port, on, access.code) }
                }
                Toggle(access.code || access.public, stringResource(Res.string.access_code), stringResource(Res.string.access_code_hint), "access-code", enabled = !access.public && !busy) { on ->
                    showCode = on
                    act { access.change(chosenPort ?: access.port, access.public, on) }
                }
                access.otpauth?.let { otpauth ->
                    if (showCode) {
                        Text(stringResource(Res.string.access_scan), color = palette.muted)
                        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            QrImage(remember(otpauth) { QrCode.encode(otpauth) }, Modifier.size(196.dp).testTag("access-qr"))
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(stringResource(Res.string.gateway_key), color = palette.muted, style = Theme.type.small)
                                SelectionContainer {
                                    val key = otpauth.substringAfter("secret=").substringBefore('&')
                                    Text(key.chunked(4).chunked(4).joinToString("\n") { it.joinToString(" ") }, style = Theme.type.mono)
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(stringResource(Res.string.access_new_code), { act { access.newCode() } }, enabled = !busy)
                                    Button(stringResource(Res.string.access_hide_code), { showCode = false })
                                }
                            }
                        }
                    } else {
                        Labeled("") { Button(stringResource(Res.string.access_show_code), { showCode = true }, Modifier.testTag("access-show-code")) }
                    }
                }
                if (access.open) {
                    Labeled(stringResource(Res.string.access_addresses)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            access.addresses.firstOrNull()?.let { first -> QrImage(remember(first) { QrCode.encode(first) }, Modifier.size(120.dp).testTag("access-address-qr")) }
                            SelectionContainer {
                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) { for (address in access.addresses) Text(address, style = Theme.type.mono) }
                            }
                        }
                    }
                    access.certificate?.let { certificate ->
                        Labeled(stringResource(Res.string.access_certificate)) {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                SelectionContainer { Text(certificate.split(':').chunked(16).joinToString("\n") { it.joinToString(":") }, style = Theme.type.mono) }
                                Text(stringResource(Res.string.access_certificate_hint), color = palette.muted, style = Theme.type.small)
                            }
                        }
                    }
                }
            }
            when (val problem = access.problem) {
                is AccessProblem.PortTaken -> Text(stringResource(Res.string.access_port_taken, problem.port), Modifier.testTag("access-problem"), color = palette.danger)
                is AccessProblem.Failed -> Text(stringResource(Res.string.access_failed, problem.reason), Modifier.testTag("access-problem"), color = palette.danger)
                null -> Unit
            }
            failure?.let { Text(stringResource(Res.string.access_files_failed, it), Modifier.testTag("access-problem"), color = palette.danger) }
            Row(Modifier.align(Alignment.End)) { Button(stringResource(Res.string.settings_close), onClose, primary = true) }
        }
    }
}

@Composable
fun BrowserAccessMark(access: BrowserAccess, modifier: Modifier, onClick: () -> Unit) {
    if (!access.open) return
    Row(
        modifier.padding(horizontal = 6.dp).clip(RoundedCornerShape(Theme.metrics.radius)).clickable(onClick = onClick).hand()
            .padding(horizontal = 8.dp, vertical = 3.dp).testTag("access-mark"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        UnstyledIcon(Lucide.Globe, null, Modifier.size(14.dp), tint = Theme.palette.accent)
        Text(stringResource(Res.string.access_mark, access.port), style = Theme.type.small, maxLines = 1)
    }
}
