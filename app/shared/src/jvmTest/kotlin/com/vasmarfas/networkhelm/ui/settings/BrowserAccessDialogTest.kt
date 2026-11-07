package com.vasmarfas.networkhelm.ui.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SkikoComposeUiTest
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import com.vasmarfas.networkhelm.app.AccessProblem
import com.vasmarfas.networkhelm.app.AppSettings
import com.vasmarfas.networkhelm.app.BrowserAccess
import com.vasmarfas.networkhelm.app.MemoryStorage
import com.vasmarfas.networkhelm.ui.theme.NetworkHelmTheme
import java.io.File
import java.util.Locale
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class BrowserAccessDialogTest {

    private fun SkikoComposeUiTest.picture(name: String) {
        val directory = File("build/screenshots").also { it.mkdirs() }
        ImageIO.write(captureToImage().toAwtImage(), "png", File(directory, "$name.png"))
    }

    private class FakeAccess : BrowserAccess {
        override var ready by mutableStateOf(false)
        override var open by mutableStateOf(false)
        override var port by mutableStateOf(8443)
        override var public by mutableStateOf(false)
        override var code by mutableStateOf(false)
        override var otpauth by mutableStateOf<String?>(null)
        override var addresses by mutableStateOf(emptyList<String>())
        override var certificate by mutableStateOf<String?>(null)
        override val webApp = true
        override var problem by mutableStateOf<AccessProblem?>(null)
        val passwords = mutableListOf<String>()

        override suspend fun setPassword(password: String) {
            passwords += password
            ready = true
        }

        override suspend fun newCode() {
            otpauth = OTPAUTH.replace("JBSW", "KRUG")
        }

        override suspend fun change(port: Int, public: Boolean, code: Boolean) {
            this.port = port
            this.public = public
            this.code = code
            otpauth = if (code || public) OTPAUTH else null
        }

        override suspend fun open() {
            open = true
            addresses = listOf("https://198.51.100.100:$port", "https://172.25.48.1:$port")
            certificate = (0 until 32).joinToString(":") { "%02X".format(it * 7) }
        }

        override suspend fun close() {
            open = false
            addresses = emptyList()
        }

        override suspend fun resume() = Unit
    }

    @Test
    fun aPasswordTypedTwiceLetsTheAccessOpen() = runDesktopComposeUiTest(1000, 900) {
        Locale.setDefault(Locale.ENGLISH)
        val access = FakeAccess()
        setContent { NetworkHelmTheme(dark = true) { BrowserAccessDialog(access) {} } }
        onNodeWithTag("access-open").assertIsNotEnabled()
        onNodeWithTag("access-password").performTextInput("rd-test-one")
        onNodeWithTag("access-repeat").performTextInput("rd-test-two")
        onNodeWithText("Set the password").performClick()
        onNodeWithTag("access-differ").assertExists()
        assertTrue(access.passwords.isEmpty())
        onNodeWithTag("access-repeat").performTextClearance()
        onNodeWithTag("access-repeat").performTextInput("rd-test-one")
        onNodeWithText("Set the password").performClick()
        waitUntil(timeoutMillis = 5_000) { access.ready }
        assertEquals(listOf("rd-test-one"), access.passwords)
        onNodeWithText("Change").assertExists()

        onNodeWithTag("access-port").performTextClearance()
        onNodeWithTag("access-port").performTextInput("9443")
        onNodeWithTag("access-open").performClick()
        waitUntil(timeoutMillis = 5_000) { onAllNodes(hasTestTag("access-address-qr")).fetchSemanticsNodes().isNotEmpty() }
        assertEquals(9443, access.port)
        onNodeWithText("https://198.51.100.100:9443").assertExists()
        onNodeWithTag("access-port").assertIsNotEnabled()
        picture("browser-access-open")
    }

    @Test
    fun anyAddressAsksForTheCodeAndShowsTheQrCodeOfTheApp() = runDesktopComposeUiTest(1000, 900) {
        Locale.setDefault(Locale.ENGLISH)
        val access = FakeAccess().apply { ready = true }
        setContent { NetworkHelmTheme(dark = false) { BrowserAccessDialog(access) {} } }
        onNodeWithTag("access-public").performClick()
        waitUntil(timeoutMillis = 5_000) { onAllNodes(hasTestTag("access-qr")).fetchSemanticsNodes().isNotEmpty() }
        assertTrue(access.public)
        onNodeWithTag("access-code").assertIsOn().assertIsNotEnabled()
        onNodeWithText("JBSW Y3DP EHPK 3PXP\nJBSW Y3DP EHPK 3PXP").assertExists()
        picture("browser-access-code")
        onNodeWithText("New code").performClick()
        waitUntil(timeoutMillis = 5_000) { access.otpauth?.contains("KRUG") == true }
        onNodeWithText("Hide").performClick()
        onNodeWithTag("access-show-code").assertExists()
    }

    @Test
    fun aTakenPortIsNamedAndTheSettingsLeadToTheAccess() = runDesktopComposeUiTest(1000, 900) {
        Locale.setDefault(Locale.ENGLISH)
        val access = FakeAccess().apply {
            ready = true
            problem = AccessProblem.PortTaken(8443)
        }
        var shown by mutableStateOf(false)
        setContent {
            NetworkHelmTheme(dark = true) {
                if (shown) BrowserAccessDialog(access) {} else SettingsDialog(AppSettings(MemoryStorage()), access, onBrowserAccess = { shown = true }) {}
            }
        }
        onNodeWithTag("browser-access").performScrollTo().performClick()
        waitUntil(timeoutMillis = 5_000) { onAllNodes(hasTestTag("access-problem")).fetchSemanticsNodes().isNotEmpty() }
        onNodeWithText("Port 8443 is taken by another program; choose another.").assertExists()
    }

    private companion object {
        const val OTPAUTH = "otpauth://totp/NetworkHelm:admin?secret=JBSWY3DPEHPK3PXPJBSWY3DPEHPK3PXP&issuer=NetworkHelm"
    }
}
