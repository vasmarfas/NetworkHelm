package com.vasmarfas.networkhelm.ui.terminal

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import com.vasmarfas.networkhelm.ssh.ShellChannel
import com.vasmarfas.networkhelm.ui.theme.Theme
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import org.connectbot.terminal.Terminal
import org.connectbot.terminal.TerminalEmulatorFactory

@Composable
fun AndroidTerminal(shell: ShellChannel, modifier: Modifier) {
    val palette = Theme.palette
    val keys = remember(shell) { Channel<ByteArray>(Channel.UNLIMITED) }
    val emulator = remember(shell) {
        TerminalEmulatorFactory.create(
            defaultForeground = palette.text,
            defaultBackground = palette.surface,
            onKeyboardInput = { keys.trySend(it) },
            onResize = { size -> shell.resize(size.columns, size.rows) },
        )
    }
    LaunchedEffect(shell) {
        launch { for (bytes in keys) shell.write(bytes) }
        val buffer = ByteArray(8192)
        while (true) {
            val read = shell.read(buffer, 0, buffer.size)
            if (read < 0) break
            emulator.writeInput(buffer, 0, read)
        }
    }
    // the keyboard the terminal raised would stay over the menu after its window closes
    val keyboard = LocalSoftwareKeyboardController.current
    DisposableEffect(shell) { onDispose { keyboard?.hide() } }
    Terminal(emulator, modifier, backgroundColor = palette.surface, foregroundColor = palette.text, keyboardEnabled = true)
}

// termlib 0.3 has a scrollback size of its own and takes none from outside
internal actual val terminalScrollback = false
