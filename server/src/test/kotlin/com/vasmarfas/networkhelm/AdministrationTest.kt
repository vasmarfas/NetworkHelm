package com.vasmarfas.networkhelm

import com.vasmarfas.networkhelm.server.FileGatewayStore
import com.vasmarfas.networkhelm.server.Gateway
import com.vasmarfas.networkhelm.server.Login
import com.vasmarfas.networkhelm.server.Master
import com.vasmarfas.networkhelm.server.administer
import kotlinx.coroutines.runBlocking
import java.io.File
import java.util.Base64
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AdministrationTest {

    private val home = createTempDirectory("rd-test-gateway-admin").toFile()
    private val said = mutableListOf<String>()
    private val asked = mutableListOf<String>()

    private fun console(vararg args: String, environment: Map<String, String> = emptyMap(), typed: List<String>? = emptyList()): Int? {
        val lines = typed?.iterator()
        return administer(home, arrayOf(*args), environment, { prompt ->
            asked += prompt
            lines?.takeIf { it.hasNext() }?.next()
        }, { said += it })
    }

    private fun withPassword(password: String) = console("--set-password", environment = mapOf("NETWORKHELM_GATEWAY_PASSWORD" to password), typed = null)

    private fun loginAs(password: String): Login = Gateway.open(home, public = false, code = false, password = null, announce = {}).login("127.0.0.1", "admin", password, "")

    private fun secrets(password: String): FileGatewayStore {
        val account = assertNotNull(Gateway.account(home))
        return FileGatewayStore(home, Master.of(password, Base64.getDecoder().decode(account.salt)).sealing)
    }

    @Test
    fun noOptionLeavesTheGatewayToStart() {
        assertNull(console("--port", "8443", "--public"))
        assertNull(Gateway.account(home))
    }

    @Test
    fun aPasswordTypedTwiceMakesTheAccountAndLogsIn() {
        assertEquals(0, console("--set-password", typed = listOf("rd-test-password", "rd-test-password")))
        assertTrue(loginAs("rd-test-password") is Login.In)
        assertTrue(loginAs("rd-test-other") is Login.Refused)
        assertEquals(listOf("New password of admin: ", "The same again: "), asked)
    }

    @Test
    fun twoDifferentPasswordsOrAShortOneChangeNothing() {
        assertEquals(1, console("--set-password", typed = listOf("rd-test-one-1", "rd-test-two-2")))
        assertEquals(1, console("--set-password", typed = listOf("short", "short")))
        assertNull(Gateway.account(home))
        assertTrue(said.all { it.startsWith("Nothing is changed") }, said.toString())
    }

    @Test
    fun theEnvironmentGivesThePasswordWhereThereIsNoTerminal() {
        assertEquals(0, withPassword("rd-test-password"))
        assertTrue(asked.isEmpty())
        assertTrue(loginAs("rd-test-password") is Login.In)
        assertEquals(1, console("--set-password", typed = null))
        assertTrue("no terminal" in said.last(), said.last())
    }

    @Test
    fun theCurrentPasswordCarriesTheSavedPasswordsOfTheRouters() {
        withPassword("rd-test-old-one")
        runBlocking { secrets("rd-test-old-one").keep("password/192.0.2.1/rd-test", "rd-test-secret") }
        val environment = mapOf("NETWORKHELM_GATEWAY_PASSWORD" to "rd-test-new-one", "NETWORKHELM_GATEWAY_CURRENT_PASSWORD" to "rd-test-old-one")
        assertEquals(0, console("--set-password", environment = environment, typed = null))
        assertTrue(said.any { "(1)" in it }, said.toString())
        assertTrue(loginAs("rd-test-old-one") is Login.Refused)
        assertTrue(loginAs("rd-test-new-one") is Login.In)
        assertEquals("rd-test-secret", runBlocking { secrets("rd-test-new-one").secret("password/192.0.2.1/rd-test") })
        assertFalse(File(home, "secrets.json.old").exists())
    }

    @Test
    fun aWrongCurrentPasswordChangesNothing() {
        withPassword("rd-test-old-one")
        runBlocking { secrets("rd-test-old-one").keep("key", "value") }
        val environment = mapOf("NETWORKHELM_GATEWAY_PASSWORD" to "rd-test-new-one", "NETWORKHELM_GATEWAY_CURRENT_PASSWORD" to "rd-test-wrong")
        assertEquals(1, console("--set-password", environment = environment, typed = null))
        assertTrue(loginAs("rd-test-old-one") is Login.In)
        assertEquals("value", runBlocking { secrets("rd-test-old-one").secret("key") })
    }

    @Test
    fun aLostCurrentPasswordPutsTheSavedPasswordsAside() {
        withPassword("rd-test-old-one")
        runBlocking { secrets("rd-test-old-one").keep("key", "value") }
        assertEquals(0, console("--set-password", typed = listOf("rd-test-new-one", "rd-test-new-one", "")))
        assertTrue(File(home, "secrets.json.old").isFile)
        assertFalse(File(home, "secrets.json").exists())
        assertTrue(loginAs("rd-test-new-one") is Login.In)
        assertNull(runBlocking { secrets("rd-test-new-one").secret("key") })
    }

    @Test
    fun withoutATerminalTheSavedPasswordsNeedTheCurrentPasswordOrAReset() {
        withPassword("rd-test-old-one")
        runBlocking { secrets("rd-test-old-one").keep("key", "value") }
        assertEquals(1, console("--set-password", environment = mapOf("NETWORKHELM_GATEWAY_PASSWORD" to "rd-test-new-one"), typed = null))
        assertTrue(loginAs("rd-test-old-one") is Login.In)
        assertTrue(File(home, "secrets.json").isFile)
    }

    @Test
    fun aResetMakesUpAPasswordAndShowsItOnce() {
        withPassword("rd-test-old-one")
        runBlocking { secrets("rd-test-old-one").keep("key", "value") }
        assertEquals(0, console("--reset-password"))
        val password = said.single { "shown this once" in it }.substringAfter(": ")
        assertEquals(20, password.length)
        assertTrue(loginAs(password) is Login.In)
        assertTrue(loginAs("rd-test-old-one") is Login.Refused)
        assertTrue(File(home, "secrets.json.old").isFile)
    }

    @Test
    fun aNewCodeNeedsAnAccountAndReplacesTheOldOne() {
        assertEquals(1, console("--new-code"))
        withPassword("rd-test-password")
        assertNull(Gateway.account(home)?.totp)
        assertEquals(0, console("--new-code"))
        val first = assertNotNull(Gateway.account(home)?.totp)
        said.clear()
        assertEquals(0, console("--new-code"))
        val account = assertNotNull(Gateway.account(home))
        assertNotEquals(first, account.totp)
        assertFalse(account.confirmed)
        assertTrue(said.any { account.otpauth.orEmpty() in it }, said.toString())
    }

    @Test
    fun oneOptionAtATime() {
        assertEquals(1, console("--reset-password", "--new-code"))
        assertNull(Gateway.account(home))
    }

    @Test
    fun aGatewayThatRunsTakesTheChange() {
        val gateway = Gateway.open(home, public = false, code = false, password = "rd-test-old-one", announce = {})
        val token = (gateway.login("127.0.0.1", "admin", "rd-test-old-one", "") as Login.In).token
        assertNotNull(gateway.session(token))
        val environment = mapOf("NETWORKHELM_GATEWAY_PASSWORD" to "rd-test-new-one", "NETWORKHELM_GATEWAY_CURRENT_PASSWORD" to "rd-test-old-one")
        assertEquals(0, console("--set-password", environment = environment, typed = null))
        assertNull(gateway.session(token))
        assertTrue(gateway.login("127.0.0.1", "admin", "rd-test-old-one", "") is Login.Refused)
        assertTrue(gateway.login("127.0.0.1", "admin", "rd-test-new-one", "") is Login.In)
    }
}
