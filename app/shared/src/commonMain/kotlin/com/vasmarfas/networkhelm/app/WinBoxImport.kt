package com.vasmarfas.networkhelm.app

import com.vasmarfas.networkhelm.winbox.WinBoxSession

fun savedRouterOf(session: WinBoxSession): SavedRouter? {
    val parsed = parseAddress(session.address) ?: return null
    val (address, protocol) = when {
        !isMacAddress(parsed.host) -> session.address to Access.Tcp8291
        parseAddress(session.ip) != null -> session.ip to Access.Tcp8291
        else -> parsed.host to Access.MacTelnet
    }
    return SavedRouter(address, session.user.ifEmpty { "admin" }, protocol, session.group, session.comment)
}
