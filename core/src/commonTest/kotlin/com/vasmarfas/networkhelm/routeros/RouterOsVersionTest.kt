package com.vasmarfas.networkhelm.routeros

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RouterOsVersionTest {

    @Test
    fun prereleasesSortBeforeTheirRelease() {
        val versions = listOf("6.49.10", "7.17.2", "7.18beta2", "7.18rc1", "7.18", "7.18.1", "7.19beta1")
            .map { assertNotNull(RouterOsVersion.parse(it), it) }
        assertEquals(versions, versions.shuffled().sorted())
        assertEquals(RouterOsVersion(7, 18), RouterOsVersion.parse("7.18 (stable)"))
    }

    @Test
    fun aTabRunsFromItsSinceUpToItsUntil() {
        val running = RouterOsVersion(7, 22)
        assertTrue(running.within("7.22rc4", null))
        assertTrue(running.within(null, "7.22.1"))
        assertFalse(running.within("7.22.1", null))
        assertFalse(running.within(null, "7.22"))
    }

    @Test
    fun somethingElseIsNotAVersion() {
        assertNull(RouterOsVersion.parse("unknown"))
        assertNull(RouterOsVersion.parse(""))
    }
}
