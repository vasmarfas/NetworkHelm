package com.vasmarfas.networkhelm.ui.connect

import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SkikoComposeUiTest
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import com.vasmarfas.networkhelm.App
import com.vasmarfas.networkhelm.FakeRouter
import com.vasmarfas.networkhelm.app.gatewayServices
import com.vasmarfas.networkhelm.app.passwordKey
import com.vasmarfas.networkhelm.gateway.GatewayClient
import com.vasmarfas.networkhelm.gateway.GatewayHost
import com.vasmarfas.networkhelm.gateway.GatewayStore
import com.vasmarfas.networkhelm.gateway.LoginPage
import com.vasmarfas.networkhelm.gateway.Wire
import com.vasmarfas.networkhelm.transport.MemoryTrustStore
import com.vasmarfas.networkhelm.transport.TrustKind
import com.vasmarfas.networkhelm.ui.theme.NetworkHelmTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.io.File
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class GatewayWebTest {

    private fun SkikoComposeUiTest.picture(name: String) {
        val directory = File("build/screenshots").also { it.mkdirs() }
        ImageIO.write(captureToImage().toAwtImage(), "png", File(directory, "$name.png"))
    }

    private class MemoryWire(private val out: Channel<String>, private val into: Channel<String>) : Wire {
        override suspend fun send(text: String) = out.send(text)

        override suspend fun receive() = into.receiveCatching().getOrNull()

        override fun close() {
            out.close()
            into.close()
        }
    }

    private class MemoryStore : GatewayStore {
        val documents = ConcurrentHashMap<String, String>()
        val secrets = ConcurrentHashMap<String, String>()

        override suspend fun read(name: String) = documents[name]

        override suspend fun write(name: String, text: String) {
            documents[name] = text
        }

        override suspend fun secret(key: String) = secrets[key]

        override suspend fun keep(key: String, value: String) {
            secrets[key] = value
        }

        override suspend fun forget(key: String) {
            secrets.remove(key)
        }
    }

    @Test
    fun theLoginOfTheGatewayTakesThePasswordAndTheCode() = runDesktopComposeUiTest(1100, 760) {
        Locale.setDefault(Locale.ENGLISH)
        var tried: Triple<String, String, String>? = null
        setContent {
            NetworkHelmTheme(dark = true) {
                GatewayLogin(LoginPage(code = true, setUp = false)) { user, password, code ->
                    tried = Triple(user, password, code)
                    GatewayAnswer.Refused
                }
            }
        }
        onNodeWithTag("gateway-password").performTextInput("rd-test-password")
        onNodeWithTag("gateway-code").performTextInput("123 456")
        picture("gateway-login")
        onNodeWithText("Log in").performClick()
        waitUntil(timeoutMillis = 5_000) { onAllNodes(hasTestTag("gateway-problem")).fetchSemanticsNodes().isNotEmpty() }
        assertEquals(Triple("admin", "rd-test-password", "123456"), tried)
        picture("gateway-refused")
    }

    @Test
    fun withoutTheCodeThePasswordIsEnough() = runDesktopComposeUiTest(1100, 760) {
        Locale.setDefault(Locale.ENGLISH)
        var tried: Triple<String, String, String>? = null
        setContent {
            NetworkHelmTheme(dark = false) {
                GatewayLogin(LoginPage(code = false, setUp = false)) { user, password, code ->
                    tried = Triple(user, password, code)
                    GatewayAnswer.In
                }
            }
        }
        assertTrue(onAllNodes(hasTestTag("gateway-code")).fetchSemanticsNodes().isEmpty())
        onNodeWithTag("gateway-password").performTextInput("rd-test-password")
        onNodeWithText("Log in").performClick()
        waitUntil(timeoutMillis = 5_000) { tried != null }
        assertEquals(Triple("admin", "rd-test-password", ""), tried)
    }

    @Test
    fun thePasswordBringsTheQrCodeOfTheAppAndItsCodeLogsIn() = runDesktopComposeUiTest(1100, 760) {
        Locale.setDefault(Locale.ENGLISH)
        val tried = mutableListOf<String>()
        setContent {
            NetworkHelmTheme(dark = true) {
                GatewayLogin(LoginPage(code = true, setUp = true)) { _, _, code ->
                    tried += code
                    if (code.isEmpty()) GatewayAnswer.SetUp("otpauth://totp/NetworkHelm:admin?secret=JBSWY3DPEHPK3PXPJBSWY3DPEHPK3PXP&issuer=NetworkHelm") else GatewayAnswer.In
                }
            }
        }
        assertTrue(onAllNodes(hasTestTag("gateway-code")).fetchSemanticsNodes().isEmpty())
        onNodeWithTag("gateway-password").performTextInput("rd-test-password")
        onNodeWithText("Log in").performClick()
        waitUntil(timeoutMillis = 5_000) { onAllNodes(hasTestTag("gateway-qr")).fetchSemanticsNodes().isNotEmpty() }
        onNodeWithText("JBSW Y3DP EHPK 3PXP\nJBSW Y3DP EHPK 3PXP").assertExists()
        onNodeWithTag("gateway-code").performTextInput("492039")
        picture("gateway-set-up")
        onNodeWithText("Log in").performClick()
        waitUntil(timeoutMillis = 5_000) { tried.size == 2 }
        assertEquals(listOf("", "492039"), tried)
    }

    @Test
    fun theAppGoesThroughTheGatewayAndItsQuestionsComeToThePage() = runDesktopComposeUiTest(1280, 800) {
        Locale.setDefault(Locale.ENGLISH)
        val toGateway = Channel<String>(Channel.UNLIMITED)
        val toBrowser = Channel<String>(Channel.UNLIMITED)
        val store = MemoryStore()
        val trust = MemoryTrustStore()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        scope.launch {
            GatewayHost(
                MemoryWire(toBrowser, toGateway),
                trust,
                { login, policy, _ ->
                    if (!policy.confirm(login.host, login.port, TrustKind.Tls, "4f".repeat(32), null)) error("declined")
                    FakeRouter()
                },
                { _, _ -> error("no OpenWrt here") },
                store,
            ).serve()
        }
        val gateway = GatewayClient(MemoryWire(toGateway, toBrowser), scope)
        runBlocking { gateway.hello() }
        try {
            setContent { App(gatewayServices(gateway)) }
            onNodeWithTag("address").performTextInput("192.0.2.1")
            onNodeWithTag("password").performTextInput("secret")
            onNodeWithText("Remember the password").performClick()
            onNodeWithText("Connect").performClick()
            waitUntil(timeoutMillis = 5_000) { onAllNodes(hasTestTag("trust")).fetchSemanticsNodes().isNotEmpty() }
            onNodeWithText("Trust").performClick()
            waitUntil(timeoutMillis = 10_000) { onAllNodes(hasText("rd-test")).fetchSemanticsNodes().isNotEmpty() }
            picture("gateway-main")
            assertEquals("4f".repeat(32), runBlocking { trust.get("192.0.2.1", 8729, TrustKind.Tls) })
            waitUntil(timeoutMillis = 5_000) { store.secrets[passwordKey("192.0.2.1", "admin")] == "secret" }
        } finally {
            scope.cancel()
        }
    }
}
