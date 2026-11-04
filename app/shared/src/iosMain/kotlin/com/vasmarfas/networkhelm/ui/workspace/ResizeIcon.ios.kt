package com.vasmarfas.networkhelm.ui.workspace

import androidx.compose.ui.input.pointer.PointerIcon

// the iPad pointer has no resize shapes
internal actual fun resizeIcon(x: Int, y: Int): PointerIcon = PointerIcon.Default
