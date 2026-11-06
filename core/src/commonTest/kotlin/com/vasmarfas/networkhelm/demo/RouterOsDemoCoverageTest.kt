package com.vasmarfas.networkhelm.demo

import com.vasmarfas.networkhelm.model.DeviceException
import com.vasmarfas.networkhelm.model.MenuPath
import com.vasmarfas.networkhelm.model.NodeKind
import com.vasmarfas.networkhelm.model.Platform
import com.vasmarfas.networkhelm.packs.MenuEntry
import com.vasmarfas.networkhelm.packs.bundledMenu
import com.vasmarfas.networkhelm.packs.bundledPacks
import com.vasmarfas.networkhelm.packs.packName
import com.vasmarfas.networkhelm.routeros.RouterOsVersion
import com.vasmarfas.networkhelm.session.DeviceSession
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class RouterOsDemoCoverageTest {

    private val session: DeviceSession = assertNotNull(demoRouter(demoHosts.getValue(Platform.RouterOs)))

    private val running = assertNotNull(RouterOsVersion.parse(session.device.version))

    // this router has the WiFi of wifi-qcom, not the legacy wireless package
    private val legacy = setOf("Wireless", "CAPsMAN")

    private fun flatten(entries: List<MenuEntry>): List<MenuEntry> = entries.flatMap { listOf(it) + flatten(it.items) }

    private suspend fun problem(path: MenuPath): String? {
        val schema = try {
            session.describe(path)
        } catch (e: DeviceException) {
            return "no menu"
        }
        return when (schema.kind) {
            NodeKind.List -> "no rows".takeIf { session.count(path) == 0 }
            NodeKind.Settings -> null
            NodeKind.Directory -> "a directory of ${schema.children.size} menus".takeIf { schema.children.isEmpty() }
            NodeKind.Command -> null
        }
    }

    @Test
    fun theDemoRouterHasEveryOptionalPackageSoThatNoEntryHidesForWantOfOne() {
        val needed = flatten(bundledMenu().items).mapNotNull { it.requires }.filter { it.endsWith(" package") }.map { it.removeSuffix(" package") }.toSet()
        assertEquals(emptySet(), needed - session.device.packages)
    }

    @Test
    fun everyEntryOfTheMenuOpensWithRowsOrSettings() = runTest {
        val missing = flatten(bundledMenu().items)
            .filter { it.path != null && it.items.isEmpty() && it.page == null && running.within(it.since, it.until) && it.title !in legacy }
            .mapNotNull { entry -> problem(MenuPath.parse(checkNotNull(entry.path)))?.let { "${entry.title} (${entry.path}): $it" } }
        assertEquals(emptyList(), missing)
    }

    @Test
    fun everyTabOfTheWindowsOfTheMenuOpensWithRowsOrSettings() = runTest {
        val entries = flatten(bundledMenu().items).filter { it.path != null && it.items.isEmpty() && running.within(it.since, it.until) && it.title !in legacy }
        val shown = entries.map { it.path }.toSet()
        val missing = bundledPacks().values
            .filter { it.platform == Platform.RouterOs.packName && running.within(it.since, it.until) && it.window.tabs.any { tab -> tab.path in shown } }
            .flatMap { pack -> pack.window.tabs.filter { running.within(it.since, it.until) }.map { pack.menu.joinToString(" / ") to it } }
            .mapNotNull { (menu, tab) -> problem(MenuPath.parse(tab.path))?.let { "$menu, ${tab.title} (${tab.path}): $it" } }
        assertEquals(emptyList(), missing)
        assertTrue(shown.isNotEmpty())
    }
}
