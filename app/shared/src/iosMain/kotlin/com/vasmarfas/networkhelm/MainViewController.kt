package com.vasmarfas.networkhelm

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.window.ComposeUIViewController
import com.vasmarfas.networkhelm.app.iosServices
import com.vasmarfas.networkhelm.ui.terminal.IosTerminal
import com.vasmarfas.networkhelm.ui.terminal.LocalTerminal
import com.vasmarfas.networkhelm.ui.terminal.TerminalViews

fun MainViewController(terminals: TerminalViews) = ComposeUIViewController {
    CompositionLocalProvider(LocalTerminal provides { channel, modifier -> IosTerminal(channel, modifier, terminals) }) {
        App(iosServices())
    }
}
