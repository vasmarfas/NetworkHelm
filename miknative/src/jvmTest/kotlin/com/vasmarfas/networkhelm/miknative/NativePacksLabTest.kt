package com.vasmarfas.networkhelm.miknative

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class NativePacksLabTest {

    @Test
    fun theWindowsOfTheRouterAreMadeFromItsDescriptionsOverPort8291() = runBlocking<Unit> {
        val lab = Lab.require("chr.host", "chr.user", "chr.password")
        withTimeout(240.seconds) {
            val session = connectNativeHost(lab.getValue("chr.host"), 8291, lab.getValue("chr.user"), lab.getValue("chr.password"), this)
            try {
                assertTrue(session.packs(null).isNotEmpty())
            } finally {
                session.close()
            }
        }
    }
}
