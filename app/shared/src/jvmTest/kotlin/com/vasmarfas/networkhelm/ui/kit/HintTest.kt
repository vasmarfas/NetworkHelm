package com.vasmarfas.networkhelm.ui.kit

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Redo
import com.composables.icons.lucide.Undo
import com.vasmarfas.networkhelm.ui.theme.NetworkHelmTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class HintTest {

    @Test
    fun anIconButtonNamesItselfUnderThePointer() = runDesktopComposeUiTest(400, 200) {
        setContent {
            NetworkHelmTheme { HintHost { Box(Modifier.fillMaxSize().padding(40.dp)) { IconButton(Lucide.Undo, "Undo", {}) } } }
        }
        assertTrue(onAllNodesWithText("Undo").fetchSemanticsNodes().isEmpty())
        onNodeWithContentDescription("Undo").performMouseInput { moveTo(center) }
        waitUntil(timeoutMillis = 5_000) { onAllNodesWithText("Undo").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithContentDescription("Undo").performMouseInput { moveBy(Offset(200f, 0f)) }
        waitUntil(timeoutMillis = 5_000) { onAllNodesWithText("Undo").fetchSemanticsNodes().isEmpty() }
    }

    @Test
    fun aButtonWithItsLabelInSightHasNoHint() = runDesktopComposeUiTest(400, 200) {
        setContent {
            NetworkHelmTheme { HintHost { Box(Modifier.fillMaxSize().padding(40.dp)) { ToolbarButton("Redo", Lucide.Redo, {}) } } }
        }
        onAllNodesWithText("Redo")[0].performMouseInput { moveTo(center) }
        mainClock.advanceTimeBy(2_000)
        waitForIdle()
        assertEquals(1, onAllNodesWithText("Redo").fetchSemanticsNodes().size)
    }

    @Test
    fun aButtonWithItsLabelCutNamesItself() = runDesktopComposeUiTest(400, 200) {
        val label = "PPP Authentication & Accounting"
        setContent {
            NetworkHelmTheme { HintHost { Box(Modifier.padding(40.dp).width(120.dp)) { ToolbarButton(label, Lucide.Redo, {}) } } }
        }
        onAllNodesWithText(label)[0].performMouseInput { moveTo(center) }
        waitUntil(timeoutMillis = 5_000) { onAllNodesWithText(label).fetchSemanticsNodes().size == 2 }
    }
}
