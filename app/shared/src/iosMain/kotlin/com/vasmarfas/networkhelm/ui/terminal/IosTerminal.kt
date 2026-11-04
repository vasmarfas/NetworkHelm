package com.vasmarfas.networkhelm.ui.terminal

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.viewinterop.UIKitView
import com.vasmarfas.networkhelm.app.LocalSettings
import com.vasmarfas.networkhelm.ssh.ShellChannel
import com.vasmarfas.networkhelm.ui.theme.Theme
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import platform.Foundation.NSData
import platform.Foundation.create
import platform.UIKit.UIView
import platform.posix.memcpy

interface TerminalViews {
    fun create(foreground: Int, background: Int, scrollback: Int, keys: (NSData) -> Unit, resized: (Int, Int) -> Unit): TerminalScreen
}

interface TerminalScreen {
    val view: UIView

    fun show(bytes: NSData)
}

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
@Composable
fun IosTerminal(shell: ShellChannel, modifier: Modifier, views: TerminalViews) {
    val palette = Theme.palette
    val lines = LocalSettings.current.terminalLines
    val keys = remember(shell) { Channel<ByteArray>(Channel.UNLIMITED) }
    val screen = remember(shell) {
        views.create(palette.text.toArgb(), palette.surface.toArgb(), lines, { data -> keys.trySend(data.toByteArray()) }, { columns, rows -> shell.resize(columns, rows) })
    }
    LaunchedEffect(shell) {
        launch { for (bytes in keys) shell.write(bytes) }
        val buffer = ByteArray(8192)
        while (true) {
            val read = shell.read(buffer, 0, buffer.size)
            if (read < 0) break
            screen.show(buffer.usePinned { NSData.create(bytes = it.addressOf(0), length = read.convert()) })
        }
    }
    UIKitView(factory = { screen.view }, modifier = modifier)
}

@OptIn(ExperimentalForeignApi::class)
private fun NSData.toByteArray(): ByteArray {
    val copy = ByteArray(length.toInt())
    if (copy.isNotEmpty()) copy.usePinned { memcpy(it.addressOf(0), bytes, length) }
    return copy
}
