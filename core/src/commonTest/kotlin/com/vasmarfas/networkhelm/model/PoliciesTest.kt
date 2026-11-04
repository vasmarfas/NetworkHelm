package com.vasmarfas.networkhelm.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PoliciesTest {

    private fun user(vararg policies: String, platform: Platform = Platform.RouterOs) =
        DeviceInfo(platform, "lab", "7.25", "x86_64", "CHR", setOf("routeros"), setOf("read", "write") + policies)

    @Test
    fun aCommandNeedsItsPoliciesAndUsersChangeWithPolicy() {
        assertEquals(listOf("test", "sniff"), user().lacks(MenuPath.parse("tool/torch")))
        assertEquals(listOf("test"), user("sniff").lacks(MenuPath.parse("tool/torch")))
        assertEquals(emptyList(), user("test", "sniff").lacks(MenuPath.parse("tool/torch")))
        assertEquals(listOf("reboot"), user("test").lacks(MenuPath.parse("system/reboot")))
        assertEquals(listOf("policy"), user().lacks(MenuPath.parse("user/group")))
        assertEquals(emptyList(), user().lacks(MenuPath.parse("tool/sniffer")), "the settings of the sniffer change with write")
        assertEquals(emptyList(), user().lacks(MenuPath.parse("users")), "a menu is matched by whole segments")
        assertEquals(emptyList(), user(platform = Platform.OpenWrt).lacks(MenuPath.parse("tool/ping")), "the view of RouterOS on OpenWrt")
    }

    @Test
    fun filesTravelOverSftpOnlyWithFtpOnRouterOs() {
        assertFalse(user().canTransfer)
        assertTrue(user("ftp").canTransfer)
        assertTrue(user(platform = Platform.Ssh).canTransfer)
    }
}
