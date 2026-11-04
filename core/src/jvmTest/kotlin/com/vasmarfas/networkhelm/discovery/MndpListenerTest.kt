package com.vasmarfas.networkhelm.discovery

import com.vasmarfas.networkhelm.Lab
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.time.Duration.Companion.seconds

class MndpListenerTest {

    @Test
    fun receivesAnAnnouncementOnLoopback() = runBlocking {
        val port = DatagramSocket(0).use { it.localPort }
        val packet = byteArrayOf(0, 0, 0, 0, 0, 5, 0, 3) + "lab".encodeToByteArray()
        withTimeout(10.seconds) {
            val sighting = async { mndpSightings(port).first() }
            delay(500)
            DatagramSocket().use { it.send(DatagramPacket(packet, packet.size, InetAddress.getLoopbackAddress(), port)) }
            val received = sighting.await()
            assertEquals("lab", received.announcement.identity)
            assertEquals("127.0.0.1", received.sourceAddress)
        }
    }

    @Test
    fun everyCapturedAnnouncementParses() {
        val capture = Lab.file(Lab.require("mndp.capture").getValue("mndp.capture"))
        capture.readLines().filter { it.isNotBlank() }.forEach { line ->
            val hex = line.substringAfter(' ')
            val announcement = assertNotNull(parseMndp(hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()), line.substringBefore(' '))
            assertNotNull(announcement.mac)
            assertNotNull(announcement.identity)
        }
    }
}
