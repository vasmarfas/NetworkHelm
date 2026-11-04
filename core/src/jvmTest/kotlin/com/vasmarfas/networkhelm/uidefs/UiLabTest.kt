package com.vasmarfas.networkhelm.uidefs

import com.vasmarfas.networkhelm.Lab
import com.vasmarfas.networkhelm.packs.bundledPacks
import com.vasmarfas.networkhelm.routeros.RouterOsLogin
import com.vasmarfas.networkhelm.routeros.RouterOsProtocol
import com.vasmarfas.networkhelm.routeros.RouterOsVersion
import com.vasmarfas.networkhelm.routeros.connectRouterOs
import com.vasmarfas.networkhelm.session.TextStore
import com.vasmarfas.networkhelm.transport.MemoryTrustStore
import com.vasmarfas.networkhelm.transport.TrustPolicy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assume.assumeTrue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

class UiLabTest {

    private class Kept : TextStore {
        val files = HashMap<String, String>()

        override suspend fun read(name: String) = files[name]

        override suspend fun write(name: String, text: String) {
            files[name] = text
        }
    }

    private val routers = listOf("chr", "chr725", "chr717", "chr6", "chr649", "chr642")

    @Test
    fun everyRouterOfTheLabGivesItsWindowsAndTheBundledOnesLoseNothing() = runBlocking {
        val present = routers.filter { Lab.optional("$it.host") != null }
        assumeTrue("lab.properties has no RouterOS router", present.isNotEmpty())
        for (prefix in present) {
            val scope = CoroutineScope(Dispatchers.Default)
            val protocol = if (prefix == "chr") RouterOsProtocol.ApiSsl else RouterOsProtocol.Api
            val login = RouterOsLogin(Lab.optional("$prefix.host").orEmpty(), Lab.optional("$prefix.user").orEmpty(), Lab.optional("$prefix.password").orEmpty(), protocol)
            val session = connectRouterOs(login, TrustPolicy(MemoryTrustStore()) { true }, scope)
            try {
                withTimeout(240.seconds) {
                    val store = Kept()
                    val started = TimeSource.Monotonic.markNow()
                    val packs = session.packs(store)
                    println("UIDEFS $prefix ${session.device.version}: ${packs.size} packs in ${started.elapsedNow()}, ${store.files.size} kept")
                    if (packs.isEmpty()) return@withTimeout
                    val again = TimeSource.Monotonic.markNow()
                    assertEquals(packs.size, session.packs(store).size, "$prefix: the packs are read back from what was kept")
                    println("UIDEFS $prefix read back in ${again.elapsedNow()}")
                    if (prefix == "chr") {
                        val controller = packs.filter { it.menu.firstOrNull() == "Controller" }
                        println("UIDEFS controller: ${controller.map { it.menu.last() to it.window.tabs.map { tab -> tab.path } }}")
                        val devices = controller.first { it.window.tabs.first().path == "cmr/device" }
                        println("UIDEFS devices columns: ${devices.paths.getValue("cmr/device").columns}")
                        println("UIDEFS devices form: ${devices.forms.values.firstOrNull()?.tabs?.map { it.title to it.fields }}")
                        println("UIDEFS devices flags ${devices.paths.getValue("cmr/device").flags.map { it.property to it.letter }}, actions ${devices.paths.getValue("cmr/device").actions.map { it.title }}, links ${devices.paths.getValue("cmr/device").links.map { it.title to it.path }}")
                        val updates = packs.filter { "system/package/update" in it.paths }
                        println("UIDEFS update in ${updates.map { it.menu }}: ${updates.firstOrNull()?.paths?.get("system/package/update")}")
                    }
                    val running = RouterOsVersion.parse(session.device.version)
                    val routerOs = bundledPacks().values.filter { it.platform == "routeros" && running?.within(it.since, it.until) != false }
                    var merged = 0
                    var placed = 0
                    for (bundled in routerOs) {
                        val pack = packs.firstOrNull { it.menu == bundled.menu } ?: continue
                        merged++
                        assertEquals(bundled.window.tabs.first().path, pack.window.tabs.first().path, "$prefix: the first tab of ${bundled.menu} stays first")
                        assertEquals(pack.window.tabs.map { it.title }.distinct(), pack.window.tabs.map { it.title }, "$prefix: ${bundled.menu} has two tabs of one title")
                        for ((path, have) in bundled.paths) {
                            val now = pack.paths.getValue(path)
                            assertTrue(now.columns.containsAll(have.columns), "$prefix: no column of $path goes: ${have.columns - now.columns.toSet()}")
                            val before = have.form?.let(bundled.forms::get)?.tabs?.flatMap { it.properties }.orEmpty()
                            val after = now.form?.let(pack.forms::get)?.tabs?.flatMap { it.properties }.orEmpty()
                            assertTrue(after.containsAll(before), "$prefix: no field of $path goes: ${before - after.toSet()}")
                            val added = after - before.toSet()
                            placed += added.size
                            if (prefix == "chr" && added.isNotEmpty()) println("UIDEFS $path adds $added")
                            if (prefix == "chr" && now.columns != have.columns) println("UIDEFS $path columns ${have.columns} -> ${now.columns}")
                        }
                    }
                    println("UIDEFS $prefix: bundled windows the router describes: $merged of ${routerOs.size}, fields they gain: $placed")
                }
            } finally {
                session.close()
                scope.cancel()
            }
        }
    }
}
