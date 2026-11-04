package com.vasmarfas.networkhelm.ui.theme

import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.decodeToImageBitmap
import kotlin.io.encoding.Base64
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor

internal const val PICTURE = "image"
internal const val MASK = 48

private const val WORK = MASK * 4

suspend fun pictureSpec(bytes: ByteArray): String? = withContext(Dispatchers.Default) {
    val picture = svgPicture(bytes, WORK) ?: try {
        bytes.decodeToImageBitmap()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        return@withContext null
    }
    val bitmap = fitted(picture, WORK)
    val pixels = IntArray(bitmap.width * bitmap.height)
    bitmap.readPixels(pixels)
    "$PICTURE:$MASK:" + Base64.encode(maskOf(pixels, bitmap.width, bitmap.height, MASK))
}

private fun fitted(picture: ImageBitmap, side: Int): ImageBitmap {
    val longest = maxOf(picture.width, picture.height)
    if (longest <= side) return picture
    val size = IntSize((picture.width * side / longest).coerceAtLeast(1), (picture.height * side / longest).coerceAtLeast(1))
    val smaller = ImageBitmap(size.width, size.height)
    Canvas(smaller).drawImageRect(picture, dstSize = size, paint = Paint().apply { filterQuality = FilterQuality.High })
    return smaller
}

internal expect fun svgPicture(bytes: ByteArray, side: Int): ImageBitmap?

internal fun maskOf(pixels: IntArray, width: Int, height: Int, side: Int): ByteArray {
    val transparent = pixels.count { it ushr 24 < 250 } > pixels.size / 20
    val shape = if (transparent) {
        IntArray(pixels.size) { pixels[it] ushr 24 }
    } else {
        val edges = (0 until width).flatMap { listOf(it, (height - 1) * width + it) } + (0 until height).flatMap { listOf(it * width, it * width + width - 1) }
        val back = edges.map { luminance(pixels[it]) }.average()
        val range = maxOf(back, 255 - back).coerceAtLeast(1.0)
        IntArray(pixels.size) { (abs(luminance(pixels[it]) - back) * 255 / range).toInt().coerceIn(0, 255) }
    }
    val scale = maxOf(width, height).toDouble() / side
    val left = (side - width / scale) / 2
    val top = (side - height / scale) / 2
    val cells = IntArray(side * side)
    for (cy in 0 until side) {
        for (cx in 0 until side) {
            val x0 = ((cx - left) * scale).coerceIn(0.0, width.toDouble())
            val x1 = ((cx + 1 - left) * scale).coerceIn(0.0, width.toDouble())
            val y0 = ((cy - top) * scale).coerceIn(0.0, height.toDouble())
            val y1 = ((cy + 1 - top) * scale).coerceIn(0.0, height.toDouble())
            if (x1 <= x0 || y1 <= y0) continue
            val xs = floor(x0).toInt() until maxOf(floor(x0).toInt() + 1, ceil(x1).toInt()).coerceAtMost(width)
            val ys = floor(y0).toInt() until maxOf(floor(y0).toInt() + 1, ceil(y1).toInt()).coerceAtMost(height)
            var sum = 0
            for (y in ys) for (x in xs) sum += shape[y * width + x]
            cells[cy * side + cx] = sum / (xs.count() * ys.count())
        }
    }
    val strongest = cells.maxOrNull()?.coerceAtLeast(1) ?: 1
    return ByteArray(cells.size) { (cells[it] * 255 / strongest).toByte() }
}

internal fun pictureVector(side: String?, data: String?): ImageVector? {
    val size = side?.toIntOrNull()?.takeIf { it in 8..128 } ?: return null
    val mask = try {
        Base64.decode(data.orEmpty())
    } catch (e: IllegalArgumentException) {
        return null
    }
    return if (mask.size == size * size) maskVector(mask, size) else null
}

internal fun maskVector(mask: ByteArray, side: Int): ImageVector {
    val builder = ImageVector.Builder(defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = side.toFloat(), viewportHeight = side.toFloat())
    for (level in 1..4) {
        val path = PathBuilder()
        for (y in 0 until side) {
            var x = 0
            while (x < side) {
                if (depth(mask[y * side + x]) != level) {
                    x++
                    continue
                }
                val start = x
                while (x < side && depth(mask[y * side + x]) == level) x++
                path.moveTo(start.toFloat(), y.toFloat()).horizontalLineTo(x.toFloat()).verticalLineTo(y + 1f).horizontalLineTo(start.toFloat()).close()
            }
        }
        builder.addPath(path.nodes, fill = SolidColor(Color.Black), fillAlpha = level / 4f)
    }
    return builder.build()
}

private fun depth(value: Byte) = ((value.toInt() and 0xFF) + 31) / 64

private fun luminance(argb: Int) = 0.299 * (argb shr 16 and 0xFF) + 0.587 * (argb shr 8 and 0xFF) + 0.114 * (argb and 0xFF)
