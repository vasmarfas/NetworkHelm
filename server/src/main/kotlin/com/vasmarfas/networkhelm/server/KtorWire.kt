package com.vasmarfas.networkhelm.server

import com.vasmarfas.networkhelm.gateway.Wire
import io.ktor.websocket.Frame
import io.ktor.websocket.WebSocketSession
import io.ktor.websocket.readText
import kotlinx.coroutines.cancel

class KtorWire(private val socket: WebSocketSession, private val validate: () -> Unit = {}) : Wire {
    override suspend fun send(text: String) {
        validate()
        socket.send(Frame.Text(text))
    }

    override suspend fun receive(): String? {
        for (frame in socket.incoming) if (frame is Frame.Text) {
            validate()
            return frame.readText()
        }
        return null
    }

    override fun close() = socket.cancel()
}
