package com.vasmarfas.networkhelm.openwrt

import com.vasmarfas.networkhelm.Lab
import com.vasmarfas.networkhelm.model.DeviceException
import com.vasmarfas.networkhelm.model.ListEvent
import com.vasmarfas.networkhelm.model.MenuPath
import com.vasmarfas.networkhelm.session.DeviceSession
import com.vasmarfas.networkhelm.ssh.SshAuth
import com.vasmarfas.networkhelm.ssh.connectSsh
import com.vasmarfas.networkhelm.transport.MemoryTrustStore
import com.vasmarfas.networkhelm.transport.TrustPolicy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

class DescribeConcurrencyLabTest {

    private val configs = listOf("network/interface", "network/interface", "firewall/defaults", "firewall/zone", "dhcp/odhcpd", "system/system", "dhcp/dnsmasq", "network/device")

    private val asked = listOf("firewall/defaults", "dhcp/odhcpd", "system/system")

    @Test
    fun theSamePathAndOthersAskedAtOnce() = scenario("paths") { session, timed ->
        coroutineScope { configs.map { path -> async { timed(path) { session.describe(MenuPath.parse(path)) } } }.awaitAll() }
    }

    @Test
    fun thePathsOfTheLayerAskedAtOnce() = scenario("layer") { session, timed ->
        val layer = RouterOsLayer(session)
        coroutineScope {
            listOf("interface", "ip/address", "ip/firewall/filter", "ip/dhcp-server", "ip/dns", "system/identity", "interface").map { path ->
                async { timed("layer $path") { layer.describe(MenuPath.parse(path)) } }
            }.awaitAll()
        }
    }

    @Test
    fun aPathAskedWhileTheMenuReadsItsApplications() = scenario("packs") { session, timed ->
        coroutineScope {
            val menu = async { timed("packs") { session.packs(null) } }
            delay(300)
            val paths = listOf("network/interface", "firewall/defaults", "dhcp/odhcpd", "system/system").map { path -> async { timed(path) { session.describe(MenuPath.parse(path)) } } }
            (paths + menu).awaitAll()
        }
    }

    @Test
    fun aPathAndRowsAskedBesideListsThatArePolled() = scenario("polled", block = ::polledAndAsked)

    @Test
    fun aPathAndRowsAskedBesideListsThatArePolledOverHttp() = scenario("polled-http", OpenWrtProtocol.Ubus, ::polledAndAsked)

    @Test
    fun aPathAskedAgainBesideListsThatArePolled() = scenario("polled-again") { session, timed ->
        for (path in asked) timed("first $path") { session.describe(MenuPath.parse(path)) }
        coroutineScope {
            val polled = polling(session)
            delay(1500)
            asked.map { path -> async { timed("again $path") { session.describe(MenuPath.parse(path)) } } }.awaitAll()
            polled.forEach { it.cancel() }
        }
    }

    private suspend fun polledAndAsked(session: DeviceSession, timed: suspend (String, suspend () -> Any?) -> Unit) = coroutineScope {
        val polled = polling(session)
        delay(1500)
        val calls = asked.map { path -> async { timed(path) { session.describe(MenuPath.parse(path)) } } } +
            LISTS.map { path ->
                async { timed("rows $path") { session.watch(MenuPath.parse(path)).filterIsInstance<ListEvent.Snapshot>().first().items.size } }
            }
        calls.awaitAll()
        polled.forEach { it.cancel() }
    }

    private fun CoroutineScope.polling(session: DeviceSession): List<Job> =
        LISTS.map { path -> launch(Dispatchers.Default) { session.watch(MenuPath.parse(path)).collect { } } }

    private fun scenario(
        name: String,
        protocol: OpenWrtProtocol = OpenWrtProtocol.Ssh,
        block: suspend (DeviceSession, suspend (String, suspend () -> Any?) -> Unit) -> Unit,
    ) = Lab.forEachOpenWrt("host", "user", "password", "ssh.hostKey") { lab ->
        val report = mutableListOf<String>()
        var slowest = 0L
        runBlocking {
            withTimeout(6.minutes) {
                val ssh = if (protocol == OpenWrtProtocol.Ssh) {
                    connectSsh(
                        lab.getValue("host"), 22, lab.getValue("user"), SshAuth.Password(lab.getValue("password")),
                        acceptHostKey = { it.fingerprint == lab.getValue("ssh.hostKey") },
                    )
                } else {
                    null
                }
                try {
                    val session = if (ssh != null) {
                        connectOpenWrt(ssh) { QuickJsForms(it, ssh) }
                    } else {
                        val login = OpenWrtLogin(lab.getValue("host"), lab.getValue("user"), lab.getValue("password"), protocol)
                        connectOpenWrt(login, TrustPolicy(MemoryTrustStore()) { true }) { QuickJsForms(it, null) }
                    }
                    val timed: suspend (String, suspend () -> Any?) -> Unit = { label, call ->
                        val started = TimeSource.Monotonic.markNow()
                        val outcome = try {
                            withContext(Dispatchers.Default) { withTimeout(60.seconds) { call() } }
                            "ok"
                        } catch (e: DeviceException) {
                            "refused: ${e.message}"
                        } catch (e: Exception) {
                            "failed: ${e::class.simpleName}"
                        }
                        report += "  $label: ${started.elapsedNow()} $outcome"
                        slowest = maxOf(slowest, started.elapsedNow().inWholeMilliseconds)
                    }
                    report += "${lab.getValue("host")} $name"
                    block(session, timed)
                } finally {
                    ssh?.close()
                }
            }
        }
        File("build/concurrency.txt").apply { parentFile.mkdirs() }.appendText(report.joinToString("\n", postfix = "\n"))
        assertTrue(slowest < 30.seconds.inWholeMilliseconds, report.joinToString("\n"))
    }

    private companion object {
        val LISTS = listOf("status/processes", "status/services", "network/interface")
    }
}
