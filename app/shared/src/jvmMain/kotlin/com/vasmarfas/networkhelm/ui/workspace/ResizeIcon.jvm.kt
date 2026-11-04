package com.vasmarfas.networkhelm.ui.workspace

import androidx.compose.ui.input.pointer.PointerIcon
import java.awt.Cursor

internal actual fun resizeIcon(x: Int, y: Int): PointerIcon = PointerIcon(
    Cursor(
        when {
            y == 0 -> Cursor.E_RESIZE_CURSOR
            x == 0 -> Cursor.S_RESIZE_CURSOR
            x == y -> Cursor.SE_RESIZE_CURSOR
            else -> Cursor.SW_RESIZE_CURSOR
        },
    ),
)
