package com.vasmarfas.networkhelm.app

import com.vasmarfas.networkhelm.winbox.WinBoxSession
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class WinBoxImportTest {
    private fun session(address: String, user: String = "admin", group: String = "", comment: String = "", ip: String = "") =
        WinBoxSession(address, user, "secret", group, comment, ip)

    @Test
    fun aSessionByNameOrIpAddressKeepsItsPortAndGoesOverPort8291() {
        assertEquals(SavedRouter("router.example.net:8292", "root", Access.Tcp8291, "Lab", "core"), savedRouterOf(session("router.example.net:8292", "root", "Lab", "core")))
        assertEquals(SavedRouter("192.0.2.1", "admin", Access.Tcp8291), savedRouterOf(session("192.0.2.1")))
        assertEquals(SavedRouter("[2001:db8::1]:8291", "admin", Access.Tcp8291), savedRouterOf(session("[2001:db8::1]:8291")))
    }

    @Test
    fun aSessionByMacAddressGoesByTheIpAddressWinBoxKnew() {
        assertEquals(SavedRouter("192.0.2.77:8291", "admin", Access.Tcp8291), savedRouterOf(session("02:00:00:00:00:01", ip = "192.0.2.77:8291")))
    }

    @Test
    fun aSessionByMacAddressWithoutAnIpAddressGoesOverMacTelnet() {
        assertEquals(SavedRouter("02:00:00:00:00:01", "admin", Access.MacTelnet), savedRouterOf(session("02:00:00:00:00:01")))
        assertEquals(SavedRouter("02:00:00:00:00:01", "admin", Access.MacTelnet), savedRouterOf(session("02:00:00:00:00:01", ip = "not an address")))
    }

    @Test
    fun aMissingLoginIsAdminAndAnUnusableAddressIsNoRouter() {
        assertEquals("admin", savedRouterOf(session("192.0.2.2", user = ""))?.user)
        assertNull(savedRouterOf(session("two words")))
        assertNull(savedRouterOf(session("192.0.2.3:99999")))
    }
}
