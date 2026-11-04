package com.vasmarfas.networkhelm.miknative

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RouterInfoLabTest {

    private fun info(prefix: String, block: (M2Message) -> Unit) = runBlocking {
        val lab = Lab.require("$prefix.host", "$prefix.user", "$prefix.password")
        withTimeout(30_000) {
            val connection = NativeConnection.open(lab.getValue("$prefix.host"), lab.getValue("$prefix.user"), lab.getValue("$prefix.password"), this)
            try {
                block(connection.newSource().request(listOf(13, 4), M2Value.U8(7)))
            } finally {
                connection.close()
            }
        }
    }

    @Test
    fun routerOs7NamesItsVersion() = info("chr") { reply ->
        assertTrue(checkNotNull((reply[0xFF001C] as M2Value.Texts).value.single()).startsWith("msg-proxy-7."))
        assertEquals("CHR", reply.text(0x15))
    }

    @Test
    fun routerOs6NamesItsBoard() = info("chr6") { reply ->
        assertEquals("CHR", reply.text(0x15))
    }
}
