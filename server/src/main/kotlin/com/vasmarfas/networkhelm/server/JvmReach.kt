package com.vasmarfas.networkhelm.server

import com.vasmarfas.networkhelm.discovery.mndpSightings
import com.vasmarfas.networkhelm.discovery.requestMndpRefresh
import com.vasmarfas.networkhelm.discovery.scan
import com.vasmarfas.networkhelm.gateway.Reach
import com.vasmarfas.networkhelm.miknative.connectMacTelnetOnInterface
import com.vasmarfas.networkhelm.miknative.connectNativeHost
import com.vasmarfas.networkhelm.miknative.macTelnetInterfaces
import com.vasmarfas.networkhelm.routeros.schema.SchemaCache

fun jvmReach(schemas: SchemaCache) = Reach(
    connectNative = { login, scope -> connectNativeHost(login.host, login.port, login.user, login.password, login.encoding, scope, schemas) },
    connectMacTelnet = { login, scope -> connectMacTelnetOnInterface(login.mac, login.user, login.password, login.interfaceName, login.encoding, scope, schemas) },
    interfaces = ::macTelnetInterfaces,
    neighbors = { mndpSightings() },
    refreshNeighbors = ::requestMndpRefresh,
    scan = { scan(it) },
)
