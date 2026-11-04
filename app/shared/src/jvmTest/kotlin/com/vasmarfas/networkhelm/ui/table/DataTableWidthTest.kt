package com.vasmarfas.networkhelm.ui.table

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import com.vasmarfas.networkhelm.ui.theme.Legacy
import com.vasmarfas.networkhelm.ui.theme.Palette
import com.vasmarfas.networkhelm.ui.theme.NetworkHelmTheme
import com.vasmarfas.networkhelm.ui.theme.Theme
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class DataTableWidthTest {

    @Test
    fun theHeaderTheChosenRowAndTheLinesReachTheEdgeOfTheWindow() = runDesktopComposeUiTest(800, 200) {
        lateinit var palette: Palette
        var row = 0
        setContent {
            NetworkHelmTheme(Legacy, dark = false) {
                palette = Theme.palette
                row = Theme.metrics.row.value.toInt()
                DataTable(
                    listOf("a", "b"),
                    listOf(TableColumn<String>("name", "Name", 120.dp) { it }),
                    key = { it },
                    modifier = Modifier.fillMaxSize(),
                    picker = { Box(Modifier.size(28.dp).testTag("picker")) },
                )
            }
        }
        waitForIdle()
        val edge = 790
        val open = captureToImage().toPixelMap()
        assertEquals(palette.raised, open[edge, row / 2], "the header")
        assertEquals(palette.grid, open[edge, 2 * row - 1], "the line under the first row")
        assertEquals(800f, onNodeWithTag("picker").fetchSemanticsNode().boundsInRoot.right, 1f)

        onNodeWithText("a").performClick()
        waitForIdle()
        assertEquals(palette.selection, captureToImage().toPixelMap()[edge, row + row / 2], "the chosen row")
    }
}
