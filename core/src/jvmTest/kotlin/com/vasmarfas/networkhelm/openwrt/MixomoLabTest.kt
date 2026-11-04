package com.vasmarfas.networkhelm.openwrt

import com.vasmarfas.networkhelm.Lab
import com.vasmarfas.networkhelm.model.DeviceException
import com.vasmarfas.networkhelm.model.ErrorCategory
import com.vasmarfas.networkhelm.model.Item
import com.vasmarfas.networkhelm.model.MenuPath
import com.vasmarfas.networkhelm.session.DeviceSession
import com.vasmarfas.networkhelm.session.rows
import com.vasmarfas.networkhelm.ssh.SshAuth
import com.vasmarfas.networkhelm.ssh.connectSsh
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assume.assumeTrue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class MixomoLabTest {

    private val clients = MenuPath.parse("mixomo/clients")
    private val exclusions = MenuPath.parse("mixomo/exclusions")

    private fun mixomo(block: suspend (DeviceSession, Map<String, String>) -> Unit) = runBlocking {
        val lab = Lab.require("openwrt.host", "openwrt.user", "openwrt.password", "openwrt.ssh.hostKey", "openwrt.lan", "openwrt.mixomo")
        withTimeout(120.seconds) {
            val ssh = connectSsh(
                lab.getValue("openwrt.host"), 22, lab.getValue("openwrt.user"), SshAuth.Password(lab.getValue("openwrt.password")),
                acceptHostKey = { it.fingerprint == lab.getValue("openwrt.ssh.hostKey") },
            )
            try {
                val session = connectOpenWrt(ssh)
                val present = try {
                    session.describe(clients)
                    true
                } catch (e: DeviceException) {
                    if (e.category != ErrorCategory.MissingItem) throw e
                    false
                }
                assumeTrue("mixomo is not installed", present)
                try {
                    block(session, lab)
                } finally {
                    for (path in listOf(clients, exclusions)) {
                        val left = session.rows(path).filter { it["name"].orEmpty().startsWith("rd-test") }.map { it.id }
                        if (left.isNotEmpty()) session.remove(path, left)
                    }
                }
            } finally {
                ssh.close()
            }
        }
    }

    private suspend fun DeviceSession.rows(path: MenuPath) = watch(path).rows().drop(1).first()

    private fun names(rows: List<Item>) = rows.mapNotNull { it["name"] }.filter { it.startsWith("rd-test") }

    @Test
    fun exclusionsAreAddedChangedDraggedAndRemoved() = mixomo { session, _ ->
        assertTrue("move" in session.describe(exclusions).commands)
        val first = session.add(exclusions, mapOf("name" to "rd-test-a", "destination" to "192.0.2.0/24"))
        val second = session.add(exclusions, mapOf("name" to "rd-test-b", "destination" to "198.51.100.0/24"))
        assertEquals(listOf("rd-test-a", "rd-test-b"), names(session.rows(exclusions)))
        session.move(exclusions, listOf(second), first)
        assertEquals(listOf("rd-test-b", "rd-test-a"), names(session.rows(exclusions)))
        session.set(exclusions, first, mapOf("name" to "rd-test-a2", "disabled" to "yes"))
        val changed = session.rows(exclusions).single { it.id == first }
        assertEquals(listOf("rd-test-a2", "192.0.2.0/24", "true"), listOf(changed["name"], changed["destination"], changed["disabled"]))
        val refused = assertFailsWith<DeviceException> { session.add(exclusions, mapOf("name" to "rd-test-c", "destination" to "not-an-address")) }
        assertTrue(refused.message.orEmpty().isNotBlank())
        session.remove(exclusions, listOf(first, second))
        assertTrue(names(session.rows(exclusions)).isEmpty())
    }

    @Test
    fun aClientGoesThroughMihomoAndBack() = mixomo { session, lab ->
        val address = lab.getValue("openwrt.lan").substringBeforeLast('.') + ".250"
        val connection = session.rows(MenuPath.parse("mixomo/routing")).single()["connection-type"]
        val client = session.add(clients, mapOf("name" to "rd-test-client", "source" to address, "connection-type" to connection.orEmpty()))
        val added = session.rows(clients).single { it.id == client }
        assertTrue(added["source"].orEmpty().startsWith(address), added.toString())
        assertEquals(connection, added["connection-type"])
        session.set(clients, client, mapOf("disabled" to "yes"))
        assertEquals("true", session.rows(clients).single { it.id == client }["disabled"])
        session.remove(clients, listOf(client))
        assertTrue(session.rows(clients).none { it.id == client })
    }

    @Test
    fun theRoutingProfilesAndBackupsOfMixomoAreRead() = mixomo { session, _ ->
        val routing = session.rows(MenuPath.parse("mixomo/routing")).single()
        assertTrue(routing["router-traffic"] in setOf("true", "false") && routing["block-quic"] in setOf("true", "false"), routing.toString())
        assertTrue(routing["connection-type"] in setOf("redir-tproxy", "tun-socks5"), routing.toString())
        val profiles = session.rows(MenuPath.parse("mixomo/profiles"))
        assertEquals(1, profiles.count { it["active"] == "true" }, profiles.map { it.id }.toString())
        val backups = MenuPath.parse("mixomo/backups")
        val before = session.rows(backups).map { it.id }.toSet()
        session.run(backups, "create", emptyMap()).toList()
        val made = session.rows(backups).single { it.id !in before }
        assertEquals("all", made["component"])
        assertTrue(made["created"].orEmpty().matches(Regex("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}")), made.toString())
        session.remove(backups, listOf(made.id))
        assertTrue(session.rows(backups).none { it.id == made.id })
    }

    @Test
    fun theOverviewAndTheListsOfThePageAreRead() = mixomo { session, _ ->
        val overview = session.rows(MenuPath.parse("mixomo/overview")).single()
        assertTrue(overview["mihomo"] in setOf("running", "stopped") && overview["magitrickle"] in setOf("running", "stopped"), overview.toString())
        assertTrue(listOf("schedules", "rule-lists", "routing-clients", "dns-servers").all { overview[it]?.toIntOrNull() != null }, overview.toString())
        assertTrue(overview["magitrickle-version"].orEmpty().startsWith("v"), overview.toString())
        val magitrickle = session.rows(MenuPath.parse("mixomo/magitrickle")).single()
        assertTrue(magitrickle["variant"] in setOf("original", "mod") && magitrickle["template"] in setOf("personal", "default"), magitrickle.toString())
        assertEquals(overview["rule-lists"]?.toInt(), session.rows(MenuPath.parse("mixomo/rules")).size)
        assertTrue(session.rows(MenuPath.parse("mixomo/dns")).all { it["name"] != null && it["active"] in setOf("true", "false") })
        assertTrue(session.rows(MenuPath.parse("mixomo/schedule")).all { it["type"] in setOf("time", "trigger") })
    }
}
