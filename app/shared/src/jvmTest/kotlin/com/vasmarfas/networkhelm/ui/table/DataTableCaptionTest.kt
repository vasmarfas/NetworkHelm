package com.vasmarfas.networkhelm.ui.table

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import com.vasmarfas.networkhelm.ui.theme.NetworkHelmTheme
import com.vasmarfas.networkhelm.ui.theme.Theme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class DataTableCaptionTest {

    @Test
    fun theCaptionOfAChosenRowIsChosenWithIt() = runDesktopComposeUiTest(600, 300) {
        var selection = Color.Unspecified
        setContent {
            NetworkHelmTheme {
                selection = Theme.palette.selection
                DataTable(
                    listOf("rd-test-a", "rd-test-b"),
                    listOf(TableColumn<String>("name", "Name", 200.dp) { it }),
                    key = { it },
                    caption = { "caption of $it" },
                )
            }
        }
        fun edge(caption: String) = onNodeWithText(caption).captureToImage().let { it.toPixelMap()[it.width - 4, it.height / 2] }
        assertNotEquals(selection, edge("caption of rd-test-b"))
        onNodeWithText("rd-test-b").performClick()
        waitForIdle()
        assertEquals(selection, edge("caption of rd-test-b"))
        assertNotEquals(selection, edge("caption of rd-test-a"))
    }

    @Test
    fun aListWithNoColumnsRunsItsCaptionsAlongTheWidth() = runDesktopComposeUiTest(600, 300) {
        val caption = "a suggestion of the router that takes the width the list has, to be read in one line"
        setContent {
            NetworkHelmTheme { DataTable(listOf("rd-test-a"), emptyList<TableColumn<String>>(), key = { it }, caption = { caption }, modifier = Modifier.fillMaxSize()) }
        }
        waitForIdle()
        assertTrue(onNodeWithText(caption).fetchSemanticsNode().size.width > 500)
    }
}
