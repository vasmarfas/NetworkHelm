package com.vasmarfas.networkhelm.ui.kit

import androidx.compose.foundation.layout.height
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.click
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import com.composeunstyled.Text
import com.vasmarfas.networkhelm.App
import com.vasmarfas.networkhelm.Shell
import com.vasmarfas.networkhelm.app.AppSettings
import com.vasmarfas.networkhelm.app.MemoryStorage
import com.vasmarfas.networkhelm.app.Services
import com.vasmarfas.networkhelm.appVersion
import com.vasmarfas.networkhelm.transport.MemoryTrustStore
import com.vasmarfas.networkhelm.ui.about.AboutDialog
import com.vasmarfas.networkhelm.ui.settings.SettingsDialog
import com.vasmarfas.networkhelm.ui.theme.NetworkHelmTheme
import kotlinx.coroutines.test.TestScope
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

@OptIn(ExperimentalTestApi::class)
class DialogTest {

    @Test
    fun aPressOutsideThePanelClosesTheAboutDialogAndAPressOnThePanelDoesNot() = runDesktopComposeUiTest(900, 700) {
        Locale.setDefault(Locale.ENGLISH)
        var closed = 0
        setContent { NetworkHelmTheme { AboutDialog { closed++ } } }
        onNodeWithText("NetworkHelm $appVersion").performClick()
        assertEquals(0, closed)
        onRoot().performTouchInput { click(Offset(10f, 10f)) }
        assertEquals(1, closed)
        onNodeWithTag("about-cross").performClick()
        assertEquals(2, closed)
    }

    @Test
    fun theSettingsCloseByAPressOutsideAndByTheCrossButTheThemeEditorOnTopOfThemStays() = runDesktopComposeUiTest(900, 700) {
        Locale.setDefault(Locale.ENGLISH)
        var closed = 0
        setContent { NetworkHelmTheme { SettingsDialog(AppSettings(MemoryStorage())) { closed++ } } }
        onRoot().performTouchInput { click(Offset(10f, 10f)) }
        assertEquals(1, closed)
        onNodeWithTag("settings-cross").performClick()
        assertEquals(2, closed)
        onNodeWithText("Edit theme").performClick()
        onNodeWithTag("theme-name").assertExists()
        onRoot().performTouchInput { click(Offset(10f, 10f)) }
        onNodeWithTag("theme-name").assertExists()
        assertEquals(2, closed)
    }

    @Test
    fun theToolsCloseByTheCrossAndByAPressOutside() = runDesktopComposeUiTest(1000, 800) {
        Locale.setDefault(Locale.ENGLISH)
        val shell = Shell(Services(MemoryStorage(), MemoryTrustStore(), neighbors = null), TestScope())
        setContent { App(shell) }
        onNodeWithTag("tools").performClick()
        waitUntil(timeoutMillis = 5_000) { shell.toolsShown }
        onNodeWithTag("tools-cross").performClick()
        waitUntil(timeoutMillis = 5_000) { !shell.toolsShown }
        onNodeWithTag("tools").performClick()
        waitUntil(timeoutMillis = 5_000) { shell.toolsShown }
        onRoot().performTouchInput { click(Offset(10f, 10f)) }
        waitUntil(timeoutMillis = 5_000) { !shell.toolsShown }
        assertFalse(shell.toolsShown)
    }

    @Test
    fun theScrollBarIsThereOnlyWhileTheContentDoesNotFit() = runDesktopComposeUiTest(400, 400) {
        var rows by mutableIntStateOf(2)
        setContent {
            NetworkHelmTheme { ScrollArea(Modifier.height(100.dp)) { repeat(rows) { Text("row $it") } } }
        }
        onAllNodesWithTag("scrollbar-thumb").assertCountEquals(0)
        rows = 40
        waitUntil(timeoutMillis = 5_000) { onAllNodesWithTag("scrollbar-thumb").fetchSemanticsNodes().isNotEmpty() }
        onAllNodesWithTag("scrollbar-thumb").assertCountEquals(1)
        rows = 2
        waitUntil(timeoutMillis = 5_000) { onAllNodesWithTag("scrollbar-thumb").fetchSemanticsNodes().isEmpty() }
    }
}
