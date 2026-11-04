package com.vasmarfas.networkhelm.openwrt

import com.vasmarfas.networkhelm.Lab
import com.vasmarfas.networkhelm.model.DeviceException
import com.vasmarfas.networkhelm.model.MenuPath
import com.vasmarfas.networkhelm.ssh.SshAuth
import com.vasmarfas.networkhelm.ssh.connectSsh
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

class DescribeTimingLabTest {

    @Test
    fun theFirstDescribeOfTheFormsOfLuciIsTimed() = Lab.forEachOpenWrt("host", "user", "password", "ssh.hostKey") { lab ->
        val report = mutableListOf<String>()
        var slowest = Duration.ZERO
        runBlocking {
            withTimeout(10.minutes) {
                val ssh = connectSsh(
                    lab.getValue("host"), 22, lab.getValue("user"), SshAuth.Password(lab.getValue("password")),
                    acceptHostKey = { it.fingerprint == lab.getValue("ssh.hostKey") },
                )
                try {
                    val started = TimeSource.Monotonic.markNow()
                    val session = connectOpenWrt(ssh) { QuickJsForms(it, ssh) }
                    report += "${lab.getValue("host")} connected in ${started.elapsedNow()}"
                    for (path in listOf("network/interface", "firewall/defaults", "firewall/zone", "dhcp/odhcpd", "system/system", "upnpd/upnpd", "network/interface")) {
                        val mark = TimeSource.Monotonic.markNow()
                        val outcome = try {
                            "${session.describe(MenuPath.parse(path)).arguments.size} fields"
                        } catch (e: DeviceException) {
                            "refused: ${e.message}"
                        }
                        report += "  $path: ${mark.elapsedNow()} $outcome"
                        slowest = maxOf(slowest, mark.elapsedNow())
                    }
                } finally {
                    ssh.close()
                }
            }
        }
        File("build/describe.txt").apply { parentFile.mkdirs() }.appendText(report.joinToString("\n", postfix = "\n"))
        assertTrue(slowest < 30.seconds, report.joinToString("\n"))
    }
}
