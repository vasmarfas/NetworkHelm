package com.vasmarfas.networkhelm.ui.theme

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import org.jetbrains.compose.resources.decodeToSvgPainter

internal actual fun svgPicture(bytes: ByteArray, side: Int): ImageBitmap? {
    if ("<svg" !in bytes.decodeToString(0, minOf(bytes.size, 1024))) return null
    val painter = bytes.decodeToSvgPainter(Density(1f))
    val square = Size(side.toFloat(), side.toFloat())
    val intrinsic = painter.intrinsicSize
    val drawn = if (intrinsic.isSpecified && intrinsic.width > 0 && intrinsic.height > 0) {
        val scale = minOf(side / intrinsic.width, side / intrinsic.height)
        Size(intrinsic.width * scale, intrinsic.height * scale)
    } else {
        square
    }
    val bitmap = ImageBitmap(side, side)
    CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(bitmap), square) {
        translate((side - drawn.width) / 2, (side - drawn.height) / 2) {
            with(painter) { draw(drawn) }
        }
    }
    return bitmap
}
