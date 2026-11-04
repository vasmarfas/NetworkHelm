package com.vasmarfas.networkhelm.ui.workspace

import android.view.PointerIcon.TYPE_HORIZONTAL_DOUBLE_ARROW
import android.view.PointerIcon.TYPE_TOP_LEFT_DIAGONAL_DOUBLE_ARROW
import android.view.PointerIcon.TYPE_TOP_RIGHT_DIAGONAL_DOUBLE_ARROW
import android.view.PointerIcon.TYPE_VERTICAL_DOUBLE_ARROW
import androidx.compose.ui.input.pointer.PointerIcon

internal actual fun resizeIcon(x: Int, y: Int): PointerIcon = PointerIcon(
    when {
        y == 0 -> TYPE_HORIZONTAL_DOUBLE_ARROW
        x == 0 -> TYPE_VERTICAL_DOUBLE_ARROW
        x == y -> TYPE_TOP_LEFT_DIAGONAL_DOUBLE_ARROW
        else -> TYPE_TOP_RIGHT_DIAGONAL_DOUBLE_ARROW
    },
)
