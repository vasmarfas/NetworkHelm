package com.vasmarfas.networkhelm.ui.main

import com.vasmarfas.networkhelm.model.Item
import com.vasmarfas.networkhelm.packs.PackPath
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PairsTest {

    private val pack = PackPath(
        pairs = mapOf("max-limit" to listOf("upload-max-limit", "download-max-limit")),
        limits = listOf("max-limit"),
        bits = listOf("rate"),
        bytes = listOf("size"),
    )

    @Test
    fun theHalvesOfAPairAreColumnsInTheUnitOfThePair() {
        val row = halved(Item("*1", mapOf("max-limit" to "10000000/20000000", "rate" to "1234/0")), pack.pairs)
        assertEquals(listOf("10000000", "20000000"), listOf(row["upload-max-limit"], row["download-max-limit"]))
        assertEquals("10M", formatted(pack, "upload-max-limit", row["upload-max-limit"].orEmpty()))
        assertEquals("1234 bps / 0 bps", formatted(pack, "rate", "1234/0"))
        assertEquals(setOf("max-limit"), halved(Item("*2", mapOf("max-limit" to "1/2/3")), pack.pairs).values.keys)
    }

    @Test
    fun sizesTheRouterGivesInBytesReadInTheirUnit() {
        assertEquals("19.9 MiB", formatted(pack, "size", "20910944"))
        assertEquals("1500", formatted(PackPath(), "size", "1500"))
    }

    @Test
    fun aSizeInKibCountsInKibUntil5000AndABigNumberIsWrittenInGroupsOfThreeDigits() {
        val native = PackPath(kibi = listOf("free"), groups = listOf("packets", "load"))
        assertEquals("4999 KiB", formatted(native, "free", (4999 * 1024).toString()))
        assertEquals("4.9 MiB", formatted(native, "free", "5120000"))
        assertEquals("4.8 GiB", formatted(native, "free", "5120000000"))
        assertEquals("18 446 744 073 709 551 615", formatted(native, "packets", "18446744073709551615"))
        assertEquals("-9 876.5", formatted(native, "load", "-9876.5"))
        assertEquals("12", formatted(native, "packets", "12"))
        assertEquals("n/a", formatted(native, "packets", "n/a"))
    }

    @Test
    fun totalsCountEachHalfAndLeaveParentsOut() {
        val queues = listOf(
            Item("*1", mapOf("name" to "all", "queued-bytes" to "1000/2000")),
            Item("*2", mapOf("name" to "a", "parent" to "all", "queued-bytes" to "400/500")),
            Item("*3", mapOf("name" to "b", "parent" to "all", "queued-bytes" to "600/1500")),
            Item("*4", mapOf("name" to "c", "parent" to "none", "queued-bytes" to "1/1")),
        )
        assertEquals("1001/2001", total(queues, "queued-bytes"))
        assertNull(total(queues + Item("*5", mapOf("name" to "d")), "queued-bytes"))
        assertEquals("3", total(listOf(Item("", mapOf("tx" to "1")), Item("", mapOf("tx" to "2"))), "tx"))
        assertNull(total(emptyList(), "tx"))
    }
}
