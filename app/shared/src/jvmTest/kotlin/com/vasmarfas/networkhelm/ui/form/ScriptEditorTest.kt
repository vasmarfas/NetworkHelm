package com.vasmarfas.networkhelm.ui.form

import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SkikoComposeUiTest
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import com.vasmarfas.networkhelm.FakeRouter
import com.vasmarfas.networkhelm.app.MemoryStorage
import com.vasmarfas.networkhelm.model.Argument
import com.vasmarfas.networkhelm.model.Item
import com.vasmarfas.networkhelm.model.MenuPath
import com.vasmarfas.networkhelm.model.MenuSchema
import com.vasmarfas.networkhelm.model.NodeKind
import com.vasmarfas.networkhelm.model.ValueType
import com.vasmarfas.networkhelm.packs.bundledPacks
import com.vasmarfas.networkhelm.ui.main.MainScreen
import com.vasmarfas.networkhelm.ui.main.RouterTab
import com.vasmarfas.networkhelm.ui.table.TableLayouts
import com.vasmarfas.networkhelm.ui.theme.NetworkHelmTheme
import java.io.File
import java.util.Locale
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class ScriptEditorTest {

    private fun SkikoComposeUiTest.picture(name: String) {
        val directory = File("build/screenshots").also { it.mkdirs() }
        ImageIO.write(captureToImage().toAwtImage(), "png", File(directory, "$name.png"))
    }

    @Test
    fun theSourceOfAScriptIsReadByTheRouterAndEnterStartsALine() = runDesktopComposeUiTest(1200, 800) {
        Locale.setDefault(Locale.ENGLISH)
        val source = ":local rdTest 1\n:put \$rdTest\n:foo"
        val classes = listOf("dir") + List(5) { "cmd" } + "none" + List(6) { "variable-local" } + listOf("none", "none", "none", "dir") +
            List(3) { "cmd" } + "none" + "syntax-meta" + List(6) { "variable-local" } + "none" + "dir" + List(3) { "obj-inactive" }
        assertEquals(source.length, classes.size)
        val path = MenuPath.parse("system/script")
        val router = FakeRouter(
            rows = mapOf("system/script" to listOf(Item("*1", mapOf("name" to "rd-test", "source" to source)))),
            schemas = mapOf("system/script" to MenuSchema(path, NodeKind.List, emptyList(), listOf("add", "print", "remove", "set"), listOf(Argument("name", ValueType.Text()), Argument("source", ValueType.Text())))),
            output = mapOf("console/inspect" to listOf(Item("", mapOf("type" to "highlight", "highlight" to classes.joinToString(","))))),
        )
        val storage = MemoryStorage()
        val tab = RouterTab(router, "192.0.2.1", bundledPacks().values, storage, TableLayouts(storage))
        setContent { NetworkHelmTheme(dark = true) { MainScreen(tab, onDisconnect = {}) } }
        tab.openItem("Script", path, "*1")
        waitUntil(timeoutMillis = 5_000) { onAllNodes(hasTestTag("field-source")).fetchSemanticsNodes().isNotEmpty() }
        waitUntil(timeoutMillis = 5_000) { router.ran.any { it.first == "console/inspect" } }
        assertEquals("console/inspect" to mapOf("request" to "highlight", "input" to source), router.ran.first { it.first == "console/inspect" })
        waitForIdle()
        picture("script-editor")
        onNodeWithTag("field-source").performClick()
        onNodeWithTag("field-source").performKeyInput { pressKey(Key.Enter) }
        waitForIdle()
        assertTrue(tab.workspace.windows.any { it.title.startsWith("Script") }, "the dialog stays open")
        assertTrue(router.changed.isEmpty(), "nothing was applied")
    }

    @Test
    fun aSourceThatIsAnAddressIsNoScript() = runDesktopComposeUiTest(1200, 800) {
        Locale.setDefault(Locale.ENGLISH)
        val path = MenuPath.parse("routing/igmp-proxy/mfc")
        val router = FakeRouter(
            rows = mapOf(path.toString() to listOf(Item("*1", mapOf("group" to "239.0.0.1", "source" to "192.0.2.10")))),
            schemas = mapOf(path.toString() to MenuSchema(path, NodeKind.List, emptyList(), listOf("add", "print", "remove", "set"), listOf(Argument("group", ValueType.Ip4), Argument("source", ValueType.Ip4)))),
        )
        val storage = MemoryStorage()
        val tab = RouterTab(router, "192.0.2.1", bundledPacks().values, storage, TableLayouts(storage))
        setContent { NetworkHelmTheme(dark = true) { MainScreen(tab, onDisconnect = {}) } }
        tab.openItem("IGMP Proxy", path, "*1")
        waitUntil(timeoutMillis = 5_000) { onAllNodes(hasTestTag("field-source")).fetchSemanticsNodes().isNotEmpty() }
        waitForIdle()
        assertTrue(router.ran.none { it.first == "console/inspect" && it.second["request"] == "highlight" })
    }

    @Test
    fun theRuleOfARoutingFilterIsLinesTheConsoleDoesNotRead() = runDesktopComposeUiTest(1200, 800) {
        Locale.setDefault(Locale.ENGLISH)
        val path = MenuPath.parse("routing/filter/rule")
        val router = FakeRouter(
            rows = mapOf("routing/filter/rule" to listOf(Item("*1", mapOf("chain" to "rd-test", "rule" to "if (dst in 10.0.0.0/8) {\n accept\n}")))),
            schemas = mapOf("routing/filter/rule" to MenuSchema(path, NodeKind.List, emptyList(), listOf("add", "print", "remove", "set"), listOf(Argument("chain", ValueType.Text()), Argument("rule", ValueType.Unparsed("(if ( [parens] { [braces] ..."))))),
        )
        val storage = MemoryStorage()
        val tab = RouterTab(router, "192.0.2.1", bundledPacks().values, storage, TableLayouts(storage))
        setContent { NetworkHelmTheme(dark = true) { MainScreen(tab, onDisconnect = {}) } }
        tab.openItem("Rule", path, "*1")
        waitUntil(timeoutMillis = 5_000) { onAllNodes(hasTestTag("field-rule")).fetchSemanticsNodes().isNotEmpty() }
        picture("filter-rule")
        assertTrue(onNodeWithTag("field-rule").fetchSemanticsNode().size.height >= 148, "a field of several lines")
        onNodeWithTag("field-rule").performClick()
        onNodeWithTag("field-rule").performKeyInput { pressKey(Key.Enter) }
        waitForIdle()
        assertTrue(router.ran.none { it.first == "console/inspect" }, "the console is not asked to read the rule")
        assertTrue(router.changed.isEmpty(), "nothing was applied")
    }
}
