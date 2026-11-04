package com.vasmarfas.networkhelm.app

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

class SavedRoutersTest {

    @Test
    fun savedRoutersSurviveAndTheSameAddressAndUserIsReplaced() = runTest {
        val storage = MemoryStorage()
        val routers = SavedRouters(storage)
        routers.save(SavedRouter("192.0.2.1", "admin", group = "lab"))
        routers.save(SavedRouter("192.0.2.2", "admin", Access.Rest))
        routers.save(SavedRouter("192.0.2.1", "admin", comment = "core", group = "lab"))
        val reread = SavedRouters(storage).also { it.load() }
        assertEquals(
            listOf(SavedRouter("192.0.2.2", "admin", Access.Rest), SavedRouter("192.0.2.1", "admin", group = "lab", comment = "core")),
            reread.routers.value,
        )
    }

    @Test
    fun aSaveReadsTheListAgainFirst() = runTest {
        val storage = MemoryStorage()
        val desktop = SavedRouters(storage).also { it.load() }
        val browser = SavedRouters(storage).also { it.load() }
        desktop.save(SavedRouter("192.0.2.1", "admin"))
        browser.save(SavedRouter("192.0.2.2", "admin"))
        desktop.save(SavedRouter("192.0.2.3", "admin"))
        assertEquals(listOf("192.0.2.1", "192.0.2.2", "192.0.2.3"), SavedRouters(storage).also { it.load() }.routers.value.map { it.address })
    }

    @Test
    fun unreadableFileIsNotOverwritten() = runTest {
        val storage = MemoryStorage()
        storage.write("routers.json", "[{\"address\": ")
        val routers = SavedRouters(storage).also { it.load() }
        assertNotNull(routers.failure)
        assertFailsWith<IllegalStateException> { routers.save(SavedRouter("192.0.2.1", "admin")) }
        assertEquals("[{\"address\": ", storage.read("routers.json"))
    }
}
