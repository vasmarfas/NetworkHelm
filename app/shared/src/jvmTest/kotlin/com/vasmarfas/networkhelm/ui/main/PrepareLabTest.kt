package com.vasmarfas.networkhelm.ui.main

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import com.vasmarfas.networkhelm.App
import com.vasmarfas.networkhelm.Shell
import com.vasmarfas.networkhelm.app.MemoryStorage
import com.vasmarfas.networkhelm.app.Services
import com.vasmarfas.networkhelm.model.Filter
import com.vasmarfas.networkhelm.model.ListEvent
import com.vasmarfas.networkhelm.model.MenuPath
import com.vasmarfas.networkhelm.routeros.RouterOsLogin
import com.vasmarfas.networkhelm.routeros.RouterOsProtocol
import com.vasmarfas.networkhelm.routeros.connectRouterOs
import com.vasmarfas.networkhelm.session.DeviceSession
import com.vasmarfas.networkhelm.transport.MemoryTrustStore
import com.vasmarfas.networkhelm.transport.TrustPolicy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import java.io.File
import java.util.Locale
import java.util.Properties
import kotlin.test.Test
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class PrepareLabTest {

    private val certificates = MenuPath.parse("certificate")
    private val services = MenuPath.parse("ip/service")
    private val groups = MenuPath.parse("user/group")

    private fun lab(): Properties {
        val file = System.getProperty("networkhelm.lab")?.let(::File)?.takeIf { it.isFile }
        assumeTrue("lab.properties is missing", file != null)
        return Properties().apply { file?.inputStream()?.use(::load) }
    }

    @Test
    fun theWizardTurnsApiSslOnAndTheTabOpensAgainOverIt() = runDesktopComposeUiTest(1400, 900) {
        val lab = lab()
        assumeTrue("the lab file does not let the tests change the services of chr", lab.getProperty("chr.disposable").orEmpty().isNotBlank())
        val login = RouterOsLogin(lab.getProperty("chr.host"), lab.getProperty("chr.user"), lab.getProperty("chr.password"), RouterOsProtocol.Api, 8728)
        Locale.setDefault(Locale.ENGLISH)
        val scope = CoroutineScope(Dispatchers.Default)
        val shell = Shell(Services(MemoryStorage(), MemoryTrustStore(), neighbors = null), scope)
        try {
            shell.add(runBlocking { connectRouterOs(login, TrustPolicy(MemoryTrustStore()) { true }, scope) }, login.host, login)
            setContent { App(shell) }
            onNodeWithTag("unencrypted").performClick()
            waitUntil(timeoutMillis = 30_000) { onAllNodes(hasText("/ip service set api-ssl certificate=networkhelm disabled=no", substring = true)).fetchSemanticsNodes().isNotEmpty() }
            onNodeWithText("Apply").performClick()
            waitUntil(timeoutMillis = 60_000) { onAllNodesWithTag("prepare-reconnect").fetchSemanticsNodes().isNotEmpty() }
            onNodeWithTag("prepare-reconnect").performClick()
            waitUntil(timeoutMillis = 30_000) { onAllNodes(hasTestTag("trust")).fetchSemanticsNodes().isNotEmpty() }
            onNodeWithText("Trust").performClick()
            waitUntil(timeoutMillis = 30_000) { shell.routers.singleOrNull()?.session?.capabilities?.encrypted == true }
            waitUntil(timeoutMillis = 30_000) { onAllNodesWithTag("unencrypted").fetchSemanticsNodes().isEmpty() }
            assertTrue(shell.routers.single().login?.protocol == RouterOsProtocol.ApiSsl)
        } finally {
            shell.routers.toList().forEach(shell::disconnect)
            runBlocking { putBack(login, scope) }
            scope.cancel()
        }
    }

    private suspend fun putBack(login: RouterOsLogin, scope: CoroutineScope) {
        val session = connectRouterOs(login, TrustPolicy(MemoryTrustStore()) { true }, scope)
        try {
            val apiSsl = session.watch(services, Filter.Equals("name", "api-ssl")).filterIsInstance<ListEvent.Snapshot>().first().items.single { it["connection"] != "true" }.id
            session.set(services, apiSsl, mapOf("certificate" to "rd-test-api"))
            for (path in listOf(certificates, groups)) {
                ids(session, path, Filter.Equals("name", "networkhelm")).takeIf { it.isNotEmpty() }?.let { session.remove(path, it) }
            }
        } finally {
            session.close()
        }
    }

    private suspend fun ids(session: DeviceSession, path: MenuPath, filter: Filter) =
        session.watch(path, filter).filterIsInstance<ListEvent.Snapshot>().first().items.map { it.id }
}
