package com.vasmarfas.networkhelm.ui.table

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SkikoComposeUiTest
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import com.vasmarfas.networkhelm.ui.theme.Legacy
import com.vasmarfas.networkhelm.ui.theme.Modern
import com.vasmarfas.networkhelm.ui.theme.NetworkHelmTheme
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class DataTableScrollBarTest {

    private val wide = listOf(
        TableColumn<String>("a", "Alpha", 200.dp) { it },
        TableColumn<String>("b", "Bravo", 200.dp) { it },
        TableColumn<String>("c", "Charlie", 200.dp) { it },
    )

    @Test
    fun theStripStandsOnlyWhereThereIsSomethingToScroll() = runDesktopComposeUiTest(300, 200) {
        setContent { NetworkHelmTheme(Modern, dark = true) { DataTable(listOf("x", "y"), wide, key = { it }, modifier = Modifier.fillMaxSize()) } }
        waitForIdle()
        onNodeWithTag("scroll-bar").assertExists()
        onNodeWithTag("scroll-left").assertExists()
        onNodeWithTag("scroll-right").assertExists()
        onNodeWithTag("scroll-thumb").assertExists()
    }

    @Test
    fun aTableThatFitsHasNoStrip() = runDesktopComposeUiTest(800, 200) {
        setContent { NetworkHelmTheme(Modern, dark = true) { DataTable(listOf("x", "y"), wide.take(1), key = { it }, modifier = Modifier.fillMaxSize()) } }
        waitForIdle()
        onAllNodesWithTag("scroll-bar").assertCountEquals(0)
    }

    private fun SkikoComposeUiTest.picture(name: String) {
        val directory = File("build/screenshots").also { it.mkdirs() }
        ImageIO.write(captureToImage().toAwtImage(), "png", File(directory, "$name.png"))
    }

    private fun SkikoComposeUiTest.left() = onNodeWithText("Alpha").fetchSemanticsNode().positionInRoot.x

    @Test
    fun anArrowMovesTheTableByAStepInBothLooks() {
        for (look in listOf(Modern, Legacy)) {
            runDesktopComposeUiTest(300, 200) {
                setContent { NetworkHelmTheme(look, dark = false) { DataTable(listOf("x", "y"), wide, key = { it }, modifier = Modifier.fillMaxSize()) } }
                waitForIdle()
                picture("scroll-bar-${look.name.lowercase()}")
                val before = left()
                onNodeWithTag("scroll-right").performClick()
                waitForIdle()
                val moved = before - left()
                assertTrue(moved > 50f, "${look.name}: the right arrow moved the table by $moved")
                onNodeWithTag("scroll-left").performClick()
                waitForIdle()
                assertEquals(before, left(), 1f, "${look.name}: back where it was")
            }
        }
    }

    @Test
    fun theThumbIsDraggedAndThePressBesideItMovesAPage() = runDesktopComposeUiTest(300, 200) {
        setContent { NetworkHelmTheme(Modern, dark = true) { DataTable(listOf("x", "y"), wide, key = { it }, modifier = Modifier.fillMaxSize()) } }
        waitForIdle()
        val start = left()
        onNodeWithTag("scroll-thumb").performMouseInput {
            moveTo(center)
            press()
            repeat(4) { moveBy(Offset(10f, 0f)) }
            release()
        }
        waitForIdle()
        val dragged = start - left()
        assertTrue(dragged > 50f, "the thumb moved the table by $dragged")
        onNodeWithTag("scroll-bar").performMouseInput { click(Offset(width - 30f, height / 2f)) }
        waitForIdle()
        assertTrue(start - left() > dragged + 100f, "a page after the drag: ${start - left()} against $dragged")
    }

    private val rows = (1..100).map { "row $it" }

    @Test
    fun theThumbStandsOnlyWhereTheRowsDoNotFit() = runDesktopComposeUiTest(800, 200) {
        setContent { NetworkHelmTheme(Modern, dark = true) { DataTable(rows.take(2), wide.take(1), key = { it }, modifier = Modifier.fillMaxSize()) } }
        waitForIdle()
        onAllNodesWithTag("list-scroll-bar").assertCountEquals(0)
    }

    @Test
    fun theThumbIsDrawnInBothLooks() {
        for (look in listOf(Modern, Legacy)) {
            runDesktopComposeUiTest(500, 200) {
                setContent { NetworkHelmTheme(look, dark = false) { DataTable(rows, wide.take(2), key = { it }, modifier = Modifier.fillMaxSize()) } }
                waitForIdle()
                onNodeWithTag("list-scroll-thumb").assertExists()
                picture("table-thumb-${look.name.lowercase()}")
            }
        }
    }

    @Test
    fun theThumbDragsTheRowsAndFollowsThem() = runDesktopComposeUiTest(800, 200) {
        setContent { NetworkHelmTheme(Modern, dark = true) { DataTable(rows, wide.take(1), key = { it }, modifier = Modifier.fillMaxSize()) } }
        waitForIdle()
        onNodeWithTag("list-scroll-bar").assertExists()
        onNodeWithText("row 1").assertExists()
        val top = onNodeWithTag("list-scroll-thumb").fetchSemanticsNode().positionInRoot.y
        onNodeWithTag("list-scroll-thumb").performMouseInput {
            moveTo(center)
            press()
            repeat(4) { moveBy(Offset(0f, 20f)) }
            release()
        }
        waitForIdle()
        onAllNodes(hasText("row 1")).assertCountEquals(0)
        val moved = onNodeWithTag("list-scroll-thumb").fetchSemanticsNode().positionInRoot.y - top
        assertTrue(moved > 40f, "the thumb moved by $moved")
    }
}
