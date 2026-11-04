package com.vasmarfas.networkhelm.openwrt

import com.vasmarfas.networkhelm.Lab
import com.vasmarfas.networkhelm.ssh.SshAuth
import com.vasmarfas.networkhelm.ssh.connectSsh
import com.vasmarfas.networkhelm.transport.MemoryTrustStore
import com.vasmarfas.networkhelm.transport.TrustPolicy
import com.vasmarfas.networkhelm.transport.openTcp
import com.vasmarfas.networkhelm.transport.openTlsTrusted
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

class UbusBurstLabTest {

    @Test
    fun aBurstOfCallsGoesThroughOverEveryWay() = Lab.forEachOpenWrt("host", "user", "password", "ssh.hostKey") { lab ->
        val host = lab.getValue("host")
        val trust = TrustPolicy(MemoryTrustStore()) { true }
        val report = mutableListOf<String>()
        runBlocking {
            val ssh = connectSsh(
                host, 22, lab.getValue("user"), SshAuth.Password(lab.getValue("password")),
                acceptHostKey = { it.fingerprint == lab.getValue("ssh.hostKey") },
            )
            try {
                val ways = mapOf(
                    "https" to HttpUbus(host, lab.getValue("user"), lab.getValue("password")) { openTlsTrusted(host, 443, trust) },
                    "http" to HttpUbus(host, lab.getValue("user"), lab.getValue("password")) { openTcp(host, 80) },
                    "ssh" to SshUbus(ssh),
                )
                for ((way, ubus) in ways) {
                    repeat(3) { round -> report += "$host $way round ${round + 1}: ${burst(ubus, 30)}" }
                }
            } finally {
                ssh.close()
            }
        }
        File("build/burst.txt").apply { parentFile.mkdirs() }.appendText(report.joinToString("\n", postfix = "\n"))
        assertTrue(report.all { ", failed 0 " in it }, report.joinToString("\n"))
    }

    private suspend fun burst(ubus: Ubus, calls: Int): String {
        val started = TimeSource.Monotonic.markNow()
        val failures = withTimeout(120.seconds) {
            coroutineScope {
                (1..calls).map {
                    async(Dispatchers.Default) {
                        try {
                            ubus.call("uci", "get", JsonObject(mapOf("config" to JsonPrimitive("network"))))
                            null
                        } catch (e: Exception) {
                            "${e::class.simpleName}: ${e.message}"
                        }
                    }
                }.awaitAll().filterNotNull()
            }
        }
        return "${started.elapsedNow()} for $calls calls, failed ${failures.size} ${failures.groupingBy { it }.eachCount()}"
    }
}
