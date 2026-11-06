package com.vasmarfas.networkhelm.ui.connect

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.composeunstyled.Text
import com.vasmarfas.networkhelm.gateway.LoginPage
import com.vasmarfas.networkhelm.qr.QrCode
import com.vasmarfas.networkhelm.ui.kit.Button
import com.vasmarfas.networkhelm.ui.kit.Labeled
import com.vasmarfas.networkhelm.ui.kit.QrImage
import com.vasmarfas.networkhelm.ui.kit.SecretField
import com.vasmarfas.networkhelm.ui.kit.TextField
import com.vasmarfas.networkhelm.ui.kit.dialogPanel
import com.vasmarfas.networkhelm.ui.theme.Theme
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import networkhelm.app.shared.generated.resources.Res
import networkhelm.app.shared.generated.resources.gateway_code
import networkhelm.app.shared.generated.resources.gateway_hint
import networkhelm.app.shared.generated.resources.gateway_key
import networkhelm.app.shared.generated.resources.gateway_log_in
import networkhelm.app.shared.generated.resources.gateway_logging_in
import networkhelm.app.shared.generated.resources.gateway_password
import networkhelm.app.shared.generated.resources.gateway_refused
import networkhelm.app.shared.generated.resources.gateway_set_up
import networkhelm.app.shared.generated.resources.gateway_set_up_first
import networkhelm.app.shared.generated.resources.gateway_title
import networkhelm.app.shared.generated.resources.gateway_unreachable
import networkhelm.app.shared.generated.resources.gateway_user

sealed interface GatewayAnswer {
    data object In : GatewayAnswer

    data object Refused : GatewayAnswer

    data object Unreachable : GatewayAnswer

    class SetUp(val otpauth: String) : GatewayAnswer
}

@Composable
fun GatewayLogin(page: LoginPage, onLogin: suspend (user: String, password: String, code: String) -> GatewayAnswer) {
    val palette = Theme.palette
    val scope = rememberCoroutineScope()
    val user = remember { TextFieldState("admin") }
    val password = remember { TextFieldState() }
    val code = remember { TextFieldState() }
    var answer by remember { mutableStateOf<GatewayAnswer?>(null) }
    var otpauth by remember { mutableStateOf<String?>(null) }
    var trying by remember { mutableStateOf(false) }
    val asksCode = page.code && !page.setUp || otpauth != null
    val digits = code.text.filter(Char::isDigit).toString()
    val ready = !trying && password.text.isNotEmpty() && (!asksCode || digits.length >= 6)
    fun logIn() {
        if (!ready) return
        trying = true
        scope.launch {
            val result = onLogin(user.text.toString(), password.text.toString(), if (asksCode) digits else "")
            trying = false
            if (result is GatewayAnswer.SetUp) {
                otpauth = result.otpauth
                answer = null
            } else {
                answer = result
            }
            if (result != GatewayAnswer.In) code.clearText()
        }
    }
    Box(Modifier.fillMaxSize().background(palette.workspace), contentAlignment = Alignment.Center) {
        Column(
            Modifier.widthIn(max = 480.dp).padding(16.dp).dialogPanel(16.dp)
                .onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown || event.key != Key.Enter) return@onPreviewKeyEvent false
                    logIn()
                    true
                },
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(stringResource(Res.string.gateway_title), style = Theme.type.strong)
            Text(stringResource(Res.string.gateway_hint), color = palette.muted)
            Labeled(stringResource(Res.string.gateway_user)) { TextField(user, Modifier.width(220.dp).testTag("gateway-user")) }
            Labeled(stringResource(Res.string.gateway_password)) { SecretField(password, Modifier.width(220.dp).testTag("gateway-password")) }
            otpauth?.let { address ->
                Text(stringResource(Res.string.gateway_set_up), color = palette.muted)
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    // 49 modules with the quiet zone of version 6, so a module is whole pixels at the usual scales
                    QrImage(remember(address) { QrCode.encode(address) }, Modifier.size(196.dp).testTag("gateway-qr"))
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(stringResource(Res.string.gateway_key), color = palette.muted, style = Theme.type.small)
                        SelectionContainer {
                            val key = address.substringAfter("secret=").substringBefore('&')
                            Text(key.chunked(4).chunked(4).joinToString("\n") { it.joinToString(" ") }, style = Theme.type.mono)
                        }
                    }
                }
            }
            if (asksCode) {
                Labeled(stringResource(Res.string.gateway_code)) { TextField(code, Modifier.width(120.dp).testTag("gateway-code"), placeholder = "000000") }
            } else if (page.setUp) {
                Text(stringResource(Res.string.gateway_set_up_first), color = palette.muted, style = Theme.type.small)
            }
            Row(Modifier.align(Alignment.End).padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                when (answer) {
                    GatewayAnswer.Refused -> Text(stringResource(Res.string.gateway_refused), Modifier.widthIn(max = 280.dp).testTag("gateway-problem"), color = palette.danger)
                    GatewayAnswer.Unreachable -> Text(stringResource(Res.string.gateway_unreachable), Modifier.testTag("gateway-problem"), color = palette.danger)
                    else -> Unit
                }
                Button(stringResource(if (trying) Res.string.gateway_logging_in else Res.string.gateway_log_in), ::logIn, enabled = ready, primary = true)
            }
        }
    }
}
