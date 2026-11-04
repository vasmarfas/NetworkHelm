package com.vasmarfas.networkhelm.discovery

import com.vasmarfas.networkhelm.Lab
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.time.Duration.Companion.seconds

class MndpLabTest {

    @Test
    fun theRoutersOfTheSegmentAnswerTheRefreshRequest() = runBlocking<Unit> {
        val answering = Lab.require("mndp.segment").getValue("mndp.segment").split(',').map(String::trim).toSet()
        val heard = Channel<MndpSighting>(Channel.UNLIMITED)
        val listening = launch { mndpSightings().collect(heard::send) }
        try {
            delay(1.seconds)
            repeat(2) {
                while (heard.tryReceive().isSuccess) {}
                requestMndpRefresh()
                val families = answering.associateWith { mutableSetOf<String>() }
                val answered = withTimeoutOrNull(3.seconds) {
                    while (families.values.any { it.size < 2 }) {
                        val sighting = heard.receive()
                        assertNotNull(sighting.announcement.mac, "a sighting without a MAC from ${sighting.sourceAddress}")
                        families[sighting.announcement.identity]?.add(if (':' in sighting.sourceAddress) "IPv6" else "IPv4")
                    }
                }
                assertNotNull(answered, "no answer over IPv4 and IPv6 within 3 s: $families")
                delay(4.seconds)
            }
        } finally {
            listening.cancel()
        }
    }
}
