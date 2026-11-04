package com.vasmarfas.networkhelm.ui.connect

import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import com.vasmarfas.networkhelm.app.MemoryStorage
import com.vasmarfas.networkhelm.app.SavedRouters
import com.vasmarfas.networkhelm.app.Services
import com.vasmarfas.networkhelm.session.DeviceSession
import com.vasmarfas.networkhelm.transport.MemoryTrustStore
import com.vasmarfas.networkhelm.ui.theme.NetworkHelmTheme
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.withContext
import java.net.SocketTimeoutException
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class ConnectSwitchTest {

    @Test
    fun stopLeavesAtOnceAndAnAttemptThatEndsLaterLeavesTheNextOneAlone() = runDesktopComposeUiTest(1100, 760) {
        Locale.setDefault(Locale.ENGLISH)
        val gate = CompletableDeferred<Unit>()
        val never = CompletableDeferred<DeviceSession>()
        var calls = 0
        var ended = false
        val services = Services(MemoryStorage(), MemoryTrustStore(), emptyFlow()) { _, _, _ ->
            if (calls++ == 0) {
                withContext(NonCancellable) { gate.await() }
                ended = true
                throw SocketTimeoutException()
            }
            never.await()
        }
        setContent {
            NetworkHelmTheme(dark = true) {
                ConnectScreen(services, SavedRouters(services.storage), rememberCoroutineScope()) { _, _, _ -> }
            }
        }
        onNodeWithTag("address").performTextInput("192.0.2.7")
        onNodeWithTag("password").performTextInput("secret")
        onNodeWithText("Connect").performClick()
        waitUntil(timeoutMillis = 5_000) { onAllNodes(hasText("Stop")).fetchSemanticsNodes().isNotEmpty() }
        onNodeWithTag("protocol").performClick()
        onAllNodes(hasText("API (8728)")).onLast().performClick()
        onNodeWithText("Stop").performClick()
        waitUntil(timeoutMillis = 2_000) { onAllNodes(hasText("Connect")).fetchSemanticsNodes().isNotEmpty() }

        onNodeWithText("Connect").performClick()
        waitUntil(timeoutMillis = 5_000) { onAllNodes(hasText("Stop")).fetchSemanticsNodes().isNotEmpty() }
        gate.complete(Unit)
        waitUntil(timeoutMillis = 5_000) { ended }
        waitForIdle()
        onNodeWithText("Stop").assertExists()
        onNodeWithText("Stop").performClick()
        waitUntil(timeoutMillis = 2_000) { onAllNodes(hasText("Connect")).fetchSemanticsNodes().isNotEmpty() }
        assertTrue(onAllNodes(hasTestTag("problem")).fetchSemanticsNodes().isEmpty(), "an attempt that was stopped says nothing")
    }
}
