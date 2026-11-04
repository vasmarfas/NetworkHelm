package com.vasmarfas.networkhelm.ui.kit

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import com.vasmarfas.networkhelm.qr.QrCode
import kotlin.math.floor

// dark on white in any theme, many readers take no inverted code, and they want four light modules around it
// whole pixels per module, or seams show between modules
@Composable
fun QrImage(code: QrCode, modifier: Modifier = Modifier) {
    Canvas(modifier.aspectRatio(1f)) {
        val modules = code.size + 8
        val module = floor(size.minDimension / modules)
        val start = (size.minDimension - module * modules) / 2 + 4 * module
        drawRect(Color.White)
        for (row in 0 until code.size) for (column in 0 until code.size) {
            if (code[row, column]) drawRect(Color.Black, Offset(start + column * module, start + row * module), Size(module, module))
        }
    }
}
