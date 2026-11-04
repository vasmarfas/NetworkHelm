package com.vasmarfas.networkhelm

import com.vasmarfas.networkhelm.gateway.GatewayClient
import com.vasmarfas.networkhelm.gateway.NativeLogin
import com.vasmarfas.networkhelm.model.ListEvent
import com.vasmarfas.networkhelm.model.MenuPath
import com.vasmarfas.networkhelm.routeros.RouterOsLogin
import com.vasmarfas.networkhelm.routeros.RouterOsProtocol
import com.vasmarfas.networkhelm.server.Gateway
import com.vasmarfas.networkhelm.server.KtorWire
import com.vasmarfas.networkhelm.server.Totp
import com.vasmarfas.networkhelm.server.fromBase32
import com.vasmarfas.networkhelm.transport.MemoryTrustStore
import com.vasmarfas.networkhelm.transport.TrustPolicy
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.HttpHeaders
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.flow.first
import org.junit.Assume.assumeTrue
import java.io.File
import java.util.Properties
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class GatewayLabTest {

    private val lab = System.getProperty("networkhelm.lab")?.let(::File)?.takeIf { it.isFile }?.let { file -> Properties().apply { file.inputStream().use(::load) } }

    private fun chr(): Triple<String, String, String> {
        val host = lab?.getProperty("chr.host").orEmpty()
        val user = lab?.getProperty("chr.user").orEmpty()
        val password = lab?.getProperty("chr.password").orEmpty()
        assumeTrue("lab.properties has no chr", host.isNotEmpty() && user.isNotEmpty() && password.isNotEmpty())
        return Triple(host, user, password)
    }

    private suspend fun ApplicationTestBuilder.browser(block: suspend (GatewayClient) -> Unit): File {
        val home = createTempDirectory("rd-test-gateway").toFile()
        val gateway = Gateway.open(home, public = false, code = true, password = "rd-test-password", announce = {})
        val totp = Totp(fromBase32(assertNotNull(Gateway.account(home)?.totp)))
        application { gateway(gateway) }
        val client = createClient { install(WebSockets) }
        val login = client.post("/login") {
            setBody("{\"user\":\"admin\",\"password\":\"rd-test-password\",\"code\":\"${totp.code(System.currentTimeMillis() / 30_000)}\"}")
        }
        val cookie = assertNotNull(login.headers[HttpHeaders.SetCookie]).substringBefore(';')
        client.webSocket("/session", request = { header(HttpHeaders.Cookie, cookie) }) { block(GatewayClient(KtorWire(this), this)) }
        return home
    }

    @Test
    fun aRouterOfTheLabAnswersThroughTheGateway() = testApplication {
        val (host, user, password) = chr()
        val home = browser { remote ->
            var asked = 0
            val session = remote.connect(RouterOsLogin(host, user, password, RouterOsProtocol.ApiSsl), TrustPolicy(MemoryTrustStore()) { asked++; true })
            assertEquals(1, asked)
            assertNotNull(session.get(MenuPath.parse("system/identity"), null)?.get("name"))
            val interfaces = session.watch(MenuPath.parse("interface")).first()
            assertTrue(interfaces is ListEvent.Snapshot && interfaces.items.isNotEmpty())
            session.close()
        }
        assertTrue(host in File(home, "known-routers").readText())
    }

    @Test
    fun theLabRouterOverPort8291AnswersThroughTheGateway() = testApplication {
        val (host, user, password) = chr()
        browser { remote ->
            val session = remote.connect(NativeLogin(host, 8291, user, password))
            assertNotNull(session.get(MenuPath.parse("system/identity"), null)?.get("name"))
            val ssh = assertNotNull(session.ssh)
            assertTrue(ssh.fileStore)
            assertFalse(ssh.canForward)
            session.close()
        }
    }
}
