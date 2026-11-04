package com.vasmarfas.networkhelm.miknative

import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.SocketException
import java.util.concurrent.atomic.AtomicBoolean

interface MacTelnetTransport {
    fun send(bytes: ByteArray, destination: InetAddress = InetAddress.getByName("255.255.255.255"), port: Int = MAC_TELNET_PORT)
    fun onMessage(handler: (bytes: ByteArray, source: InetAddress, port: Int) -> Unit)
    fun close()
}

class MacBroadcastInterface(
    val name: String,
    val displayName: String,
    val address: InetAddress,
    val broadcast: InetAddress,
    val mac: MacAddress,
)

fun listBroadcastInterfaces(): List<MacBroadcastInterface> {
    val interfaces = try {
        NetworkInterface.getNetworkInterfaces()
    } catch (e: SocketException) {
        null
    } ?: return emptyList()
    val result = mutableListOf<MacBroadcastInterface>()
    for (iface in interfaces) {
        try {
            if (!iface.isUp || iface.isLoopback) continue
            val hw = iface.hardwareAddress ?: continue
            if (hw.size != 6 || hw.all { it == 0.toByte() }) continue
            val mac = MacAddress(hw)
            for (binding in iface.interfaceAddresses) {
                val broadcast = binding.broadcast ?: continue
                val address = binding.address ?: continue
                if (address.address.size != 4) continue
                result.add(MacBroadcastInterface(iface.name, iface.displayName ?: iface.name, address, broadcast, mac))
            }
        } catch (e: SocketException) {
            continue
        }
    }
    return result
}

class UdpMacTelnetTransport(
    private val socket: DatagramSocket = DatagramSocket(null).apply {
        reuseAddress = true
        broadcast = true
        bind(InetSocketAddress(0))
    },
) : MacTelnetTransport {
    private val closed = AtomicBoolean(false)
    private var messageHandler: ((ByteArray, InetAddress, Int) -> Unit)? = null
    private val readerThread = Thread {
        val buffer = ByteArray(65535)
        while (!closed.get()) {
            try {
                val packet = DatagramPacket(buffer, buffer.size)
                socket.receive(packet)
                val bytes = packet.data.copyOfRange(packet.offset, packet.offset + packet.length)
                messageHandler?.invoke(bytes, packet.address, packet.port)
            } catch (e: Exception) {
                if (closed.get() || socket.isClosed) break
                close()
                break
            }
        }
    }.apply {
        isDaemon = true
        name = "mactelnet-udp-reader"
        start()
    }

    override fun onMessage(handler: (bytes: ByteArray, source: InetAddress, port: Int) -> Unit) {
        messageHandler = handler
    }

    override fun send(bytes: ByteArray, destination: InetAddress, port: Int) {
        if (closed.get()) return
        val packet = DatagramPacket(bytes, bytes.size, destination, port)
        socket.send(packet)
    }

    override fun close() {
        if (closed.compareAndSet(false, true)) {
            socket.close()
        }
    }
}
