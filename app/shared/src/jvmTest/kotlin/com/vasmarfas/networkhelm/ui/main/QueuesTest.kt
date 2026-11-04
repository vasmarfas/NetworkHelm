package com.vasmarfas.networkhelm.ui.main

import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SkikoComposeUiTest
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import com.vasmarfas.networkhelm.FakeRouter
import com.vasmarfas.networkhelm.app.MemoryStorage
import com.vasmarfas.networkhelm.model.Argument
import com.vasmarfas.networkhelm.model.Item
import com.vasmarfas.networkhelm.model.MenuPath
import com.vasmarfas.networkhelm.model.MenuSchema
import com.vasmarfas.networkhelm.model.NodeKind
import com.vasmarfas.networkhelm.model.ValueType
import com.vasmarfas.networkhelm.packs.MenuEntry
import com.vasmarfas.networkhelm.packs.bundledPacks
import com.vasmarfas.networkhelm.ui.table.TableLayouts
import com.vasmarfas.networkhelm.ui.theme.NetworkHelmTheme
import java.io.File
import java.util.Locale
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class QueuesTest {

    private val path = MenuPath.parse("queue/simple")

    private val number = ValueType.Integer(0, Long.MAX_VALUE, scaled = true)

    private val schema = MenuSchema(
        path, NodeKind.List, emptyList(), listOf("add", "print", "remove", "set"),
        listOf(
            Argument("name", ValueType.Text()),
            Argument("target", ValueType.Text()),
            Argument("max-limit", number),
            Argument("priority", ValueType.Integer(1, 8)),
            Argument("comment", ValueType.Text()),
            Argument("disabled", ValueType.YesNo),
        ),
    )

    private val queues = listOf(
        Item(
            "*1",
            mapOf(
                "name" to "rd-test-office", "target" to "192.0.2.0/24", "max-limit" to "10000000/20000000", "priority" to "8/8",
                "rate" to "1234/0", "queued-bytes" to "1234/0", "queued-packets" to "2/0", "total-max-limit" to "0",
            ),
        ),
        Item(
            "*2",
            mapOf(
                "name" to "rd-test-guests", "target" to "198.51.100.0/24", "max-limit" to "1500000/0", "priority" to "8/8",
                "rate" to "0/0", "queued-bytes" to "0/1000", "queued-packets" to "0/1", "total-max-limit" to "0",
            ),
        ),
    )

    private fun SkikoComposeUiTest.picture(name: String) {
        val directory = File("build/screenshots").also { it.mkdirs() }
        ImageIO.write(captureToImage().toAwtImage(), "png", File(directory, "$name.png"))
    }

    private fun SkikoComposeUiTest.field(name: String) = onNode(hasSetTextAction() and hasAnyAncestor(hasTestTag("field-$name")))

    @Test
    fun uploadAndDownloadAreColumnsAndFieldsOfTheirOwn() = runDesktopComposeUiTest(1400, 800) {
        Locale.setDefault(Locale.ENGLISH)
        val router = FakeRouter(mapOf(path.toString() to queues), schemas = mapOf(path.toString() to schema))
        val storage = MemoryStorage()
        val tab = RouterTab(router, "192.0.2.1", bundledPacks().values, storage, TableLayouts(storage))
        setContent { NetworkHelmTheme(dark = true) { MainScreen(tab, onDisconnect = {}) } }
        tab.open(MenuEntry("Queues", path.toString()))
        waitUntil(timeoutMillis = 5_000) { onAllNodes(hasText("rd-test-office")).fetchSemanticsNodes().isNotEmpty() }
        for (header in listOf("Upload Max Limit", "Download Max Limit")) onNodeWithText(header).fetchSemanticsNode()
        for (cell in listOf("10M", "20M", "1500k", "1234 bps / 0 bps", "1234 B / 0 B")) onNodeWithText(cell).fetchSemanticsNode()
        onNodeWithTag("total-queued-bytes").assertTextEquals("Total Queued Bytes: 1234 B / 1000 B")
        onNodeWithTag("total-queued-packets").assertTextEquals("Total Queued Packets: 2 / 1")
        picture("queues")

        onNodeWithText("rd-test-office").performMouseInput { doubleClick() }
        waitUntil(timeoutMillis = 5_000) { onAllNodes(hasTestTag("field-download-max-limit")).fetchSemanticsNodes().isNotEmpty() }
        field("upload-max-limit").assertTextEquals("10M")
        field("download-max-limit").assertTextEquals("20M")
        for (half in listOf("Upload", "Download")) onNodeWithText(half).fetchSemanticsNode()
        onNodeWithText("Max Limit").fetchSemanticsNode()
        picture("queue-dialog")
        field("download-max-limit").performTextReplacement("30M")
        field("upload-max-limit").assertTextEquals("10M")
        onNodeWithText("Advanced").performClick()
        waitUntil(timeoutMillis = 5_000) { onAllNodes(hasTestTag("field-upload-priority")).fetchSemanticsNodes().isNotEmpty() }
        field("upload-priority").assertTextEquals("8")
        field("upload-priority").performTextReplacement("1")
        onNodeWithText("Statistics").performClick()
        waitUntil(timeoutMillis = 5_000) { onAllNodes(hasTestTag("value-upload-rate")).fetchSemanticsNodes().isNotEmpty() }
        onNodeWithTag("value-upload-rate").assertTextEquals("1234 bps")
        onNodeWithTag("value-download-rate").assertTextEquals("0 bps")
        onNodeWithTag("value-download-queued-bytes").assertTextEquals("0 B")
        picture("queue-statistics")
        onNodeWithText("Traffic").performClick()
        waitUntil(timeoutMillis = 5_000) { onAllNodes(hasText("Upload Avg. Rate: 1.2 kbps")).fetchSemanticsNodes().isNotEmpty() }
        onNodeWithText("Download Avg. Rate: 0 bps").fetchSemanticsNode()
        picture("queue-traffic")
        onNodeWithText("OK").performClick()
        waitUntil(timeoutMillis = 5_000) { router.changed.isNotEmpty() }
        assertEquals(listOf("*1" to mapOf<String, String?>("max-limit" to "10M/30M", "priority" to "1/8")), router.changed.toList())
    }

    @Test
    fun aQueueTypeHasTheFieldsOfItsKindOnly() = runDesktopComposeUiTest(1200, 800) {
        Locale.setDefault(Locale.ENGLISH)
        val types = MenuPath.parse("queue/type")
        val schema = MenuSchema(
            types, NodeKind.List, emptyList(), listOf("add", "print", "remove", "set"),
            listOf(
                Argument("name", ValueType.Text()),
                Argument("kind", ValueType.Enumeration(), choices = listOf("pcq", "pfifo", "sfq")),
                Argument("pcq-rate", number),
                Argument("pfifo-limit", number),
                Argument("sfq-perturb", number),
            ),
        )
        val pcq = Item("*1", mapOf("name" to "rd-test-pcq", "kind" to "pcq", "pcq-rate" to "0"))
        val router = FakeRouter(mapOf(types.toString() to listOf(pcq)), schemas = mapOf(types.toString() to schema))
        val storage = MemoryStorage()
        val tab = RouterTab(router, "192.0.2.1", bundledPacks().values, storage, TableLayouts(storage))
        setContent { NetworkHelmTheme(dark = true) { MainScreen(tab, onDisconnect = {}) } }
        tab.open(MenuEntry("Queues", types.toString()))
        waitUntil(timeoutMillis = 5_000) { onAllNodes(hasText("rd-test-pcq")).fetchSemanticsNodes().isNotEmpty() }
        onNodeWithText("rd-test-pcq").performMouseInput { doubleClick() }
        waitUntil(timeoutMillis = 5_000) { onAllNodes(hasTestTag("field-pcq-rate")).fetchSemanticsNodes().isNotEmpty() }
        onNodeWithText("Rate").fetchSemanticsNode()
        for (other in listOf("field-pfifo-limit", "field-sfq-perturb")) assertTrue(onAllNodes(hasTestTag(other)).fetchSemanticsNodes().isEmpty(), other)
        onNode(hasClickAction() and hasAnyAncestor(hasTestTag("field-kind"))).performClick()
        waitUntil(timeoutMillis = 5_000) { onAllNodes(hasText("sfq")).fetchSemanticsNodes().isNotEmpty() }
        onAllNodes(hasText("sfq")).onLast().performClick()
        waitUntil(timeoutMillis = 5_000) { onAllNodes(hasTestTag("field-sfq-perturb")).fetchSemanticsNodes().isNotEmpty() }
        onNodeWithText("Perturb").fetchSemanticsNode()
        assertTrue(onAllNodes(hasTestTag("field-pcq-rate")).fetchSemanticsNodes().isEmpty(), "the rate of PCQ")
        picture("queue-type")
    }
}
