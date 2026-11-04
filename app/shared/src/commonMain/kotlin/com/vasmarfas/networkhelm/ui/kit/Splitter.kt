package com.vasmarfas.networkhelm.ui.kit

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.vasmarfas.networkhelm.ui.theme.Theme
import com.vasmarfas.networkhelm.ui.workspace.resizeIcon

private const val Share = 0.6f

@Stable
class SplitState {
    var natural by mutableStateOf(0.dp)
    var first by mutableStateOf<Dp?>(null)
}

// the first part is as high as its content wants until the bar is dragged, a double tap on the bar gives that back
@Composable
fun VerticalSplit(
    state: SplitState,
    modifier: Modifier = Modifier,
    least: Dp = 72.dp,
    first: @Composable () -> Unit,
    second: @Composable () -> Unit,
) {
    val palette = Theme.palette
    val bar = if (LocalCompact.current) 16.dp else 8.dp
    BoxWithConstraints(modifier) {
        val room = (maxHeight - bar).coerceAtLeast(0.dp)
        val low = minOf(least, room / 2)
        val high = room - low
        val height = state.first?.coerceIn(low, high) ?: minOf(state.natural, room * Share, high)
        val bounds by rememberUpdatedState(low to high)
        val shown by rememberUpdatedState(height)
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxWidth().height(height)) { first() }
            Box(
                Modifier.fillMaxWidth().height(bar).background(palette.raised).pointerHoverIcon(resizeIcon(0, 1)).testTag("split-bar")
                    .pointerInput(state) { detectTapGestures(onDoubleTap = { state.first = null }) }
                    .pointerInput(state) {
                        detectVerticalDragGestures { change, drag ->
                            change.consume()
                            state.first = ((state.first ?: shown) + drag.toDp()).coerceIn(bounds.first, bounds.second)
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Box(Modifier.width(36.dp).height(2.dp).background(palette.line, RoundedCornerShape(1.dp)))
            }
            Box(Modifier.fillMaxWidth().weight(1f)) { second() }
        }
    }
}
