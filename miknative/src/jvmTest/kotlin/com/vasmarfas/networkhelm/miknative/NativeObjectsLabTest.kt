package com.vasmarfas.networkhelm.miknative

import com.vasmarfas.networkhelm.packs.PackNative
import com.vasmarfas.networkhelm.packs.PackNativeField
import com.vasmarfas.networkhelm.packs.PackNativeRef
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class NativeObjectsLabTest {

    private val devices = PackNative(
        listOf(167, 1),
        listOf(
            PackNativeField("identity", 0x2009, "string"),
            PackNativeField("pairing-status", 0x211c, "string"),
            PackNativeField("connected-time", 0x2118, "interval"),
            PackNativeField("channel", 0x2503, "enm", names = mapOf(2L to "long term", 3L to "stable", 4L to "testing", 5L to "development")),
        ),
    )

    private val resources = PackNative(
        listOf(167, 44),
        listOf(
            PackNativeField("device", 0xFE0001, "enm", refers = PackNativeRef(listOf(167, 1), listOf(0x2009))),
            PackNativeField("cpu", 0x202, "number", postfix = "%"),
            PackNativeField("memory", 0x204, "bignumber", postfix = "%"),
            PackNativeField("hdd", 0x206, "bignumber", postfix = "%"),
        ),
    )

    @Test
    fun theDevicesAndTheirResourcesOfTheControllerAreRead() = runBlocking<Unit> {
        val lab = Lab.require("chr.host", "chr.user", "chr.password")
        withTimeout(60.seconds) {
            val objects = openNativeObjects(lab.getValue("chr.host"), 8291, lab.getValue("chr.user"), lab.getValue("chr.password"), this)
            try {
                val device = checkNotNull(objects.read(devices)).first()
                assertEquals("*1", device.id)
                assertTrue(device["identity"].orEmpty().isNotEmpty(), device.toString())
                assertEquals("paired", device["pairing-status"])
                assertTrue(device["connected-time"].orEmpty().endsWith("s"), device.toString())
                assertEquals("stable", device["channel"])
                val used = checkNotNull(objects.read(resources)).first()
                assertEquals(device["identity"], used["device"])
                for (name in listOf("cpu", "memory", "hdd")) assertTrue(assertNotNull(used[name], "$name of $used").endsWith("%"), used.toString())
            } finally {
                objects.close()
            }
        }
    }
}
