package com.vasmarfas.networkhelm.gateway

import com.vasmarfas.networkhelm.discovery.Found
import com.vasmarfas.networkhelm.discovery.MndpSighting
import com.vasmarfas.networkhelm.session.DeviceSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow

class Reach(
    val connectNative: (suspend (NativeLogin, CoroutineScope) -> DeviceSession)? = null,
    val connectMacTelnet: (suspend (MacTelnetLogin, CoroutineScope) -> DeviceSession)? = null,
    val interfaces: () -> List<String> = { emptyList() },
    val neighbors: (() -> Flow<MndpSighting>)? = null,
    val refreshNeighbors: (suspend () -> Unit)? = null,
    val scan: ((List<String>) -> Flow<Found>)? = null,
) {
    fun offers() = Offers(
        native = connectNative != null,
        interfaces = if (connectMacTelnet != null) interfaces() else emptyList(),
        neighbors = neighbors != null,
        scan = scan != null,
    )
}
