package com.vasmarfas.networkhelm.ui.main

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SkikoComposeUiTest
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import com.vasmarfas.networkhelm.app.MemoryStorage
import com.vasmarfas.networkhelm.app.fetchChangelog
import com.vasmarfas.networkhelm.miknative.connectNativeHost
import com.vasmarfas.networkhelm.miknative.nativeSide
import com.vasmarfas.networkhelm.model.MenuPath
import com.vasmarfas.networkhelm.packs.MenuEntry
import com.vasmarfas.networkhelm.packs.bundledPacks
import com.vasmarfas.networkhelm.routeros.RouterOsLogin
import com.vasmarfas.networkhelm.routeros.RouterOsProtocol
import com.vasmarfas.networkhelm.routeros.connectRouterOs
import com.vasmarfas.networkhelm.session.DeviceSession
import com.vasmarfas.networkhelm.transport.MemoryTrustStore
import com.vasmarfas.networkhelm.transport.TrustPolicy
import com.vasmarfas.networkhelm.ui.table.TableLayouts
import com.vasmarfas.networkhelm.ui.theme.NetworkHelmTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import java.io.File
import java.util.Locale
import java.util.Properties
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.minutes

@OptIn(ExperimentalTestApi::class)
class UpdateWindowLabTest {
    private val update = MenuPath.parse("system/package/update")

    private fun SkikoComposeUiTest.picture(name: String) {
        val directory = File("build/screenshots").also { it.mkdirs() }
        ImageIO.write(captureToImage().toAwtImage(), "png", File(directory, "$name.png"))
    }

    private fun SkikoComposeUiTest.await(timeoutMillis: Long = 40_000, condition: () -> Boolean) {
        val end = System.nanoTime() + timeoutMillis * 1_000_000
        while (!condition()) {
            check(System.nanoTime() < end) { "the condition did not hold in $timeoutMillis ms" }
            mainClock.advanceTimeByFrame()
            Thread.sleep(40)
        }
    }

    private fun SkikoComposeUiTest.frames(count: Int) = repeat(count) {
        mainClock.advanceTimeByFrame()
        Thread.sleep(25)
    }

    private fun SkikoComposeUiTest.shown(text: String) = onAllNodes(hasText(text, substring = true)).fetchSemanticsNodes().isNotEmpty()

    private fun lab(): Properties {
        val file = System.getProperty("networkhelm.lab")?.let(::File)?.takeIf { it.isFile }
        assumeTrue("lab.properties is missing", file != null)
        return Properties().apply { file?.inputStream()?.use(::load) }
    }

    // the channel of the lab router is put back to development at the end and read back
    private fun follow(name: String, changelogs: Boolean, open: (CoroutineScope, RouterOsLogin) -> DeviceSession) {
        val (host, user, password) = System.getenv("NETWORKHELM_LAB_CHR")?.takeIf { it.isNotEmpty() }?.split('|')
            ?: lab().let { listOf(it.getProperty("chr.host").orEmpty(), it.getProperty("chr.user").orEmpty(), it.getProperty("chr.password").orEmpty()) }
        assumeTrue("chr.host is empty", host.isNotEmpty())
        Locale.setDefault(Locale.ENGLISH)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val session = open(scope, RouterOsLogin(host, user, password, RouterOsProtocol.Api))
        val storage = MemoryStorage()
        try {
            runBlocking { session.packs(storage) }
            val tab = RouterTab(session, session.device.identity, bundledPacks().values, storage, TableLayouts(storage))
            val vendor: (suspend (String) -> String?)? = if (changelogs) ::fetchChangelog else null
            runDesktopComposeUiTest(1100, 820, testTimeout = 10.minutes) {
                setContent { CompositionLocalProvider(LocalChangelog provides vendor) { NetworkHelmTheme(dark = true) { MainScreen(tab, onDisconnect = {}) } } }
                mainClock.autoAdvance = false
                await { tab.menu != null }
                tab.open(MenuEntry("Check For Updates", update.toString()))
                await(60_000) { shown("Installed Version") }
                await(60_000) { shown("New version is available") }
                frames(80)
                picture("update-$name-1-first")
                onNodeWithTag("field-channel").performClick()
                frames(15)
                onNodeWithText("long-term").performClick()
                await(60_000) { shown("Status: New version is available") && shown("What's new in 7.24.6") }
                frames(120)
                picture("update-$name-2-long-term")
            }
        } finally {
            val back = runBlocking {
                session.set(update, null, mapOf("channel" to "development"))
                var channel: String? = null
                repeat(20) {
                    channel = session.get(update, null)?.get("channel")
                    if (channel != "development") delay(500)
                }
                channel
            }
            session.close()
            scope.cancel()
            assertEquals("development", back)
        }
    }

    @Test
    fun theUpdateWindowOfTheLabRouterFollowsTheChannelOverThe8291Channel() = follow("8291", changelogs = false) { scope, login ->
        runBlocking { connectNativeHost(login.host, 8291, login.user, login.password, scope) }
    }

    @Test
    fun theUpdateWindowOfTheLabRouterFollowsTheChannelOverTheApiWithTheNativeChannelBesideIt() = follow("api-native", changelogs = false) { scope, login ->
        runBlocking { connectRouterOs(login, TrustPolicy(MemoryTrustStore()) { true }, scope, native = login.nativeSide(scope)) }
    }

    @Test
    fun theUpdateWindowOfTheLabRouterFollowsTheChannelOverThePlainApiAndTheVendorGivesTheList() = follow("api", changelogs = true) { scope, login ->
        runBlocking { connectRouterOs(login, TrustPolicy(MemoryTrustStore()) { true }, scope) }
    }
}
