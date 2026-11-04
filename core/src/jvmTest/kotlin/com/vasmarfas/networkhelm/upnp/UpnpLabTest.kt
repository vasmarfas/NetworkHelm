package com.vasmarfas.networkhelm.upnp

import com.vasmarfas.networkhelm.Lab
import com.vasmarfas.networkhelm.model.ListEvent
import com.vasmarfas.networkhelm.model.MenuPath
import com.vasmarfas.networkhelm.ssh.SshAuth
import com.vasmarfas.networkhelm.ssh.connectSsh
import com.vasmarfas.networkhelm.transport.ByteStream
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class UpnpLabTest {

    private val connection = "urn:schemas-upnp-org:service:WANIPConnection:1"
    private val nat = MenuPath.parse("ip/firewall/nat")

    private fun upnp(block: suspend (UpnpSession, UpnpClient, String, String) -> Unit) = runBlocking {
        val lab = Lab.require("openwrtwifi.host", "openwrtwifi.user", "openwrtwifi.password", "openwrtwifi.ssh.hostKey")
        withTimeout(60.seconds) {
            val ssh = connectSsh(
                lab.getValue("openwrtwifi.host"), 22, lab.getValue("openwrtwifi.user"), SshAuth.Password(lab.getValue("openwrtwifi.password")),
                acceptHostKey = { it.fingerprint == lab.getValue("openwrtwifi.ssh.hostKey") },
            )
            try {
                val lan = ssh.exec("uci get network.lan.ipaddr").output.trim()
                val open: suspend (String, Int) -> ByteStream = { host, port -> ssh.forward(host, port) }
                block(connectUpnp(UpnpLogin(lan, 5000), open), UpnpClient(lan, 5000, open), lan, lab.getValue("openwrtwifi.host"))
            } finally {
                ssh.close()
            }
        }
    }

    @Test
    fun theGatewayTellsItsWanAndItsExternalAddress() = upnp { session, _, _, wanAddress ->
        assertEquals("OpenWrt router", session.device.identity)
        val wan = session.get(MenuPath.parse("interface"), "wan")
        assertEquals("true", wan?.get("running"), wan.toString())
        assertTrue((wan?.get("rx-byte")?.toLongOrNull() ?: 0) > 0, wan.toString())
        val resource = session.get(MenuPath.parse("system/resource"), null)
        assertEquals(listOf("OpenWrt", "Connected", "true"), listOf("manufacturer", "connection-status", "nat").map { resource?.get(it) }, resource.toString())
        assertTrue(resource?.get("external-address") in setOf(wanAddress, ""), resource.toString())
    }

    @Test
    fun aPortMappingShowsAsADynamicDstNatRuleAndGoesWithRemoval() = upnp { session, client, lan, _ ->
        val mapping = mapOf("NewRemoteHost" to "", "NewExternalPort" to "50999", "NewProtocol" to "TCP")
        try {
            client.call(connection, "/ctl/IPConn", "DeletePortMapping", mapping)
        } catch (e: UpnpFault) {
            assertEquals(714, e.code)
        }
        val before = (session.watch(nat).first() as ListEvent.Snapshot).items.size
        var removed = false
        try {
            client.call(
                connection, "/ctl/IPConn", "AddPortMapping",
                mapping + mapOf("NewInternalPort" to "50999", "NewInternalClient" to lan, "NewEnabled" to "1", "NewPortMappingDescription" to "rd-test", "NewLeaseDuration" to "600"),
            )
            val rules = (session.watch(nat).first() as ListEvent.Snapshot).items
            assertEquals(before + 1, rules.size)
            val rule = rules.single { it["comment"] == "rd-test" }
            assertEquals(listOf("dstnat", "dst-nat", "tcp", "50999", lan, "50999", "true"), listOf("chain", "action", "protocol", "dst-port", "to-addresses", "to-ports", "dynamic").map { rule[it] })
            assertTrue(rule["lease"].orEmpty().matches(Regex("(\\d+[wdhms])+")), rule.toString())
            session.remove(nat, listOf(rule.id))
            removed = true
        } finally {
            if (!removed) client.call(connection, "/ctl/IPConn", "DeletePortMapping", mapping)
        }
        assertTrue((session.watch(nat).first() as ListEvent.Snapshot).items.none { it["comment"] == "rd-test" })
    }

    @Test
    fun monitorTrafficGivesTheRatesOfTheWan() = upnp { session, _, _, _ ->
        val rounds = session.run(MenuPath.parse("interface"), "monitor-traffic", mapOf("interface" to "wan")).take(2).toList()
        assertTrue(rounds.all { it["name"] == "wan" && it["rx-bits-per-second"]?.toLongOrNull() != null }, rounds.toString())
    }
}
