package com.vasmarfas.networkhelm.ui.workspace

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.vasmarfas.networkhelm.ui.theme.Theme
import kotlin.math.roundToInt

private val Thickness = 8.dp
private val MinThumb = 32.dp

@Composable
internal fun VerticalScrollbar(state: ScrollState, modifier: Modifier = Modifier) {
    if (state.maxValue == 0) return
    val density = LocalDensity.current
    BoxWithConstraints(modifier.width(Thickness).fillMaxHeight()) {
        val thumb = Thumb(constraints.maxHeight.toFloat(), state, with(density) { MinThumb.toPx() })
        val latest by rememberUpdatedState(thumb)
        Box(
            Modifier
                .offset { IntOffset(0, thumb.offset.roundToInt()) }
                .height(with(density) { thumb.length.toDp() })
                .fillMaxWidth()
                .background(Theme.palette.muted.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                .pointerInput(state) {
                    detectVerticalDragGestures { change, dy ->
                        change.consume()
                        state.dispatchRawDelta(dy * latest.contentPerPixel)
                    }
                },
        )
    }
}

@Composable
internal fun HorizontalScrollbar(state: ScrollState, modifier: Modifier = Modifier) {
    if (state.maxValue == 0) return
    val density = LocalDensity.current
    BoxWithConstraints(modifier.height(Thickness).fillMaxWidth()) {
        val thumb = Thumb(constraints.maxWidth.toFloat(), state, with(density) { MinThumb.toPx() })
        val latest by rememberUpdatedState(thumb)
        Box(
            Modifier
                .offset { IntOffset(thumb.offset.roundToInt(), 0) }
                .width(with(density) { thumb.length.toDp() })
                .fillMaxHeight()
                .background(Theme.palette.muted.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                .pointerInput(state) {
                    detectHorizontalDragGestures { change, dx ->
                        change.consume()
                        state.dispatchRawDelta(dx * latest.contentPerPixel)
                    }
                },
        )
    }
}

// BoxWithConstraints builds its content during measuring, when maxValue may already have dropped to zero
private class Thumb(track: Float, state: ScrollState, minLength: Float) {
    val length = if (track <= 0f) 0f else (track * track / (track + state.maxValue)).coerceIn(minOf(minLength, track), track)
    private val travel = track - length
    val offset = if (state.maxValue == 0) 0f else travel * state.value / state.maxValue
    val contentPerPixel = if (travel <= 0f) 0f else state.maxValue / travel
}
