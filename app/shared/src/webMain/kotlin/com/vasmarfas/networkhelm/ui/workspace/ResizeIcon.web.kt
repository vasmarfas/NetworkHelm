package com.vasmarfas.networkhelm.ui.workspace

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.fromKeyword

@OptIn(ExperimentalComposeUiApi::class)
internal actual fun resizeIcon(x: Int, y: Int): PointerIcon = PointerIcon.fromKeyword(
    when {
        y == 0 -> "ew-resize"
        x == 0 -> "ns-resize"
        x == y -> "nwse-resize"
        else -> "nesw-resize"
    },
)
