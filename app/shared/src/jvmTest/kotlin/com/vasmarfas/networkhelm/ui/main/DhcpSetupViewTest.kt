package com.vasmarfas.networkhelm.ui.main

import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.semantics.SemanticsProperties
import com.vasmarfas.networkhelm.FakeRouter
import com.vasmarfas.networkhelm.model.Item
import com.vasmarfas.networkhelm.ui.theme.NetworkHelmTheme
import java.io.File
import java.util.Locale
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class DhcpSetupViewTest {

    private fun SemanticsNodeInteraction.text() = fetchSemanticsNode().config[SemanticsProperties.Text].joinToString("") { it.text }

    @Test
    fun theSetupShowsItsCommandsAndWritesThem() = runDesktopComposeUiTest(560, 520) {
        Locale.setDefault(Locale.ENGLISH)
        val router = FakeRouter(
            rows = mapOf(
                "ip/address" to listOf(
                    Item("*1", mapOf("address" to "192.0.2.1/24", "interface" to "bridge1")),
                    Item("*2", mapOf("address" to "10.0.0.1/32", "interface" to "loop")),
                ),
                "ip/pool" to listOf(Item("*1", mapOf("name" to "dhcp_pool0"))),
            ),
        )
        setContent { NetworkHelmTheme { DhcpSetupView(router) } }
        waitUntil(timeoutMillis = 5_000) { onNodeWithTag("dhcp-commands").text().isNotEmpty() }
        ImageIO.write(captureToImage().toAwtImage(), "png", File("build/screenshots").also { it.mkdirs() }.resolve("dhcp-setup.png"))
        val commands = onNodeWithTag("dhcp-commands").text().lines()
        assertEquals("/ip pool add name=dhcp_pool1 ranges=192.0.2.2-192.0.2.254", commands[0])
        assertEquals("/ip dhcp-server network add address=192.0.2.0/24 gateway=192.0.2.1 dns-server=192.0.2.1", commands[1])
        assertEquals("/ip dhcp-server add name=dhcp1 interface=bridge1 address-pool=dhcp_pool1 lease-time=30m", commands[2])
        onNodeWithText("bridge1").performClick()
        onNodeWithText("loop").performClick()
        waitUntil(timeoutMillis = 5_000) { onNodeWithTag("dhcp-commands").text().isEmpty() }
        onNodeWithTag("dhcp-network").assertTextEquals("")
        onNodeWithTag("dhcp-apply").assertIsNotEnabled()
        onNodeWithText("loop").performClick()
        onNodeWithText("bridge1").performClick()
        waitUntil(timeoutMillis = 5_000) { onNodeWithTag("dhcp-commands").text().isNotEmpty() }
        onNodeWithTag("dhcp-apply").performClick()
        waitUntil(timeoutMillis = 5_000) { router.added.size == 3 }
        assertEquals("dhcp1", router.added.last()["name"])
        waitUntil(timeoutMillis = 5_000) { onNodeWithTag("dhcp-result").text().isNotEmpty() }
        assertTrue("dhcp1" in onNodeWithTag("dhcp-result").text())
    }
}
