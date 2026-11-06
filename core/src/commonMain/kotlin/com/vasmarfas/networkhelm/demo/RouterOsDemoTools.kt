package com.vasmarfas.networkhelm.demo

import com.vasmarfas.networkhelm.model.Item
import com.vasmarfas.networkhelm.model.duration
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

private class ToolsFlow(val protocol: String, val source: String, val sourcePort: String, val target: String, val targetPort: String, val tx: Long, val rx: Long)

private val toolsFlows = listOf(
    ToolsFlow("tcp", "192.168.88.101", "51944", "140.82.121.5", "443", 21_000, 411_000),
    ToolsFlow("udp", "192.168.88.130", "49228", "203.0.113.77", "3478", 82_000, 410_000),
    ToolsFlow("tcp", "192.168.88.102", "60112", "1.1.1.1", "853", 3_000, 6_000),
    ToolsFlow("tcp", "192.168.88.110", "42118", "198.51.100.140", "443", 22_000, 3_600_000),
    ToolsFlow("udp", "192.168.88.121", "40533", "192.168.88.1", "53", 1_200, 2_100),
    ToolsFlow("icmp", "192.168.88.20", "", "1.1.1.1", "", 800, 800),
)

private val toolsProcesses = listOf(
    "ethernet" to 62, "bridging" to 34, "firewall" to 41, "networking" to 87, "wifi" to 124, "management" to 9, "console" to 3, "profiling" to 4, "routing" to 6,
    "dns" to 5, "unclassified" to 11,
)

private val toolsHosts = listOf(
    listOf("address=192.168.88.1", "mac-address=00:00:5E:00:53:02", "time=0ms", "dns=router.lan", "snmp=demo-hap", "netbios="),
    listOf("address=192.168.88.2", "mac-address=00:00:5E:00:53:A2", "time=1ms", "dns=", "snmp=switch-closet", "netbios="),
    listOf("address=192.168.88.3", "mac-address=00:00:5E:00:53:A3", "time=2ms", "dns=", "snmp=cap-attic", "netbios="),
    listOf("address=192.168.88.20", "mac-address=00:00:5E:00:53:20", "time=1ms", "dns=nas.lan", "snmp=nas", "netbios=NAS"),
    listOf("address=192.168.88.30", "mac-address=00:00:5E:00:53:30", "time=3ms", "dns=printer.lan", "snmp=printer-hp", "netbios="),
    listOf("address=192.168.88.101", "mac-address=00:00:5E:00:53:65", "time=2ms", "dns=macbook-pro.lan", "snmp=", "netbios="),
    listOf("address=192.168.88.102", "mac-address=00:00:5E:00:53:66", "time=4ms", "dns=iphone-anna.lan", "snmp=", "netbios="),
    listOf("address=192.168.88.110", "mac-address=00:00:5E:00:53:6E", "time=6ms", "dns=tv-livingroom.lan", "snmp=", "netbios="),
    listOf("address=192.168.88.121", "mac-address=00:00:5E:00:53:79", "time=9ms", "dns=esp32-sensor.lan", "snmp=", "netbios="),
    listOf("address=192.168.88.130", "mac-address=00:00:5E:00:53:82", "time=3ms", "dns=ps5.lan", "snmp=", "netbios="),
)

private val toolsPackets = listOf(
    listOf("interface=wifi2", "direction=rx", "src-mac=00:00:5E:00:53:65", "dst-mac=00:00:5E:00:53:02", "protocol=ip", "src-address=192.168.88.101:51944", "dst-address=140.82.121.5:443", "ip-protocol=tcp", "size=66", "cpu=1"),
    listOf("interface=wifi2", "direction=tx", "src-mac=00:00:5E:00:53:02", "dst-mac=00:00:5E:00:53:65", "protocol=ip", "src-address=140.82.121.5:443", "dst-address=192.168.88.101:51944", "ip-protocol=tcp", "size=1514", "cpu=1"),
    listOf("interface=wifi2", "direction=rx", "src-mac=00:00:5E:00:53:66", "dst-mac=FF:FF:FF:FF:FF:FF", "protocol=arp", "src-address=192.168.88.102", "dst-address=192.168.88.1", "ip-protocol=", "size=42", "cpu=2"),
    listOf("interface=wifi2", "direction=tx", "src-mac=00:00:5E:00:53:02", "dst-mac=00:00:5E:00:53:66", "protocol=arp", "src-address=192.168.88.1", "dst-address=192.168.88.102", "ip-protocol=", "size=42", "cpu=2"),
    listOf("interface=wifi2", "direction=rx", "src-mac=00:00:5E:00:53:67", "dst-mac=00:00:5E:00:53:02", "protocol=ip", "src-address=192.168.88.103:55012", "dst-address=192.168.88.1:53", "ip-protocol=udp", "size=74", "cpu=0"),
    listOf("interface=wifi2", "direction=tx", "src-mac=00:00:5E:00:53:02", "dst-mac=00:00:5E:00:53:67", "protocol=ip", "src-address=192.168.88.1:53", "dst-address=192.168.88.103:55012", "ip-protocol=udp", "size=106", "cpu=0"),
    listOf("interface=wifi2", "direction=rx", "src-mac=00:00:5E:00:53:66", "dst-mac=01:00:5E:00:00:FB", "protocol=ip", "src-address=192.168.88.102:5353", "dst-address=224.0.0.251:5353", "ip-protocol=udp", "size=96", "cpu=3"),
    listOf("interface=wifi2", "direction=rx", "src-mac=00:00:5E:00:53:65", "dst-mac=00:00:5E:00:53:02", "protocol=ip", "src-address=192.168.88.101", "dst-address=1.1.1.1", "ip-protocol=icmp", "size=98", "cpu=1"),
    listOf("interface=wifi2", "direction=tx", "src-mac=00:00:5E:00:53:02", "dst-mac=00:00:5E:00:53:65", "protocol=ip", "src-address=1.1.1.1", "dst-address=192.168.88.101", "ip-protocol=icmp", "size=98", "cpu=1"),
    listOf("interface=wifi2", "direction=rx", "src-mac=00:00:5E:00:53:65", "dst-mac=00:00:5E:00:53:02", "protocol=ip", "src-address=192.168.88.101:51944", "dst-address=140.82.121.5:443", "ip-protocol=tcp", "size=1514", "cpu=1"),
    listOf("interface=wifi2", "direction=tx", "src-mac=00:00:5E:00:53:02", "dst-mac=00:00:5E:00:53:65", "protocol=ip", "src-address=140.82.121.5:443", "dst-address=192.168.88.101:51944", "ip-protocol=tcp", "size=66", "cpu=1"),
    listOf("interface=wifi2", "direction=rx", "src-mac=00:00:5E:00:53:67", "dst-mac=00:00:5E:00:53:02", "protocol=ip", "src-address=192.168.88.103", "dst-address=192.168.88.1", "ip-protocol=udp", "size=342", "cpu=2"),
)

private fun toolsItem(fields: List<String>) = Item("", fields.associate { it.substringBefore('=') to it.substringAfter('=') })

private fun toolsRate(bits: Long) = "${bits / 1_000_000}.${bits % 1_000_000 / 100_000}Mbps"

private fun toolsSeconds(millis: Long) = "${millis / 1000}.${(millis % 1000).toString().padStart(3, '0')}"

internal fun DemoBuilder.toolsDemo() {
    toolsServersDemo()
    toolsMonitoringDemo()
    toolsSnifferDemo()
    toolsGeneratorDemo()
    toolsCommandsDemo()
}

private fun DemoBuilder.toolsServersDemo() {
    settings("tool/bandwidth-server", "enabled=true", "authenticate=true", "allocate-udp-ports-from=2000", "max-sessions=100")
    list(
        "tool/bandwidth-server/session",
        writable = false,
        live = { row, tick ->
            val salt = row.id.hashCode()
            mapOf(
                "tx-current" to "${wander(412_000_000, tick, salt)}", "rx-current" to "${wander(388_000_000, tick, salt + 1)}",
                "tx-10-second-average" to "${wander(405_000_000, tick / 10, salt + 2)}", "rx-10-second-average" to "${wander(391_000_000, tick / 10, salt + 3)}",
                "tx-total-average" to "${wander(408_000_000, tick / 30, salt + 4)}", "rx-total-average" to "${wander(390_000_000, tick / 30, salt + 5)}",
            )
        },
    ) {
        row("client=192.168.88.101", "protocol=tcp", "direction=both", "user=admin", "random-data=false", "tx-current=412000000", "rx-current=388000000", "tx-10-second-average=405000000", "rx-10-second-average=391000000", "tx-total-average=408000000", "rx-total-average=390000000", "tcp-connection-count=10")
        row("client=10.66.66.3", "protocol=udp", "direction=receive", "user=admin", "random-data=false", "tx-current=0", "rx-current=94000000", "tx-10-second-average=0", "rx-10-second-average=93000000", "tx-total-average=0", "rx-total-average=92000000", "tcp-connection-count=0")
    }
    settings("tool/mac-server", "allowed-interface-list=LAN")
    settings("tool/mac-server/mac-winbox", "allowed-interface-list=LAN")
    settings("tool/mac-server/ping", "enabled=true")
    list("tool/mac-server/sessions", writable = false, live = { _, tick -> mapOf("uptime" to duration(204L + tick)) }) {
        row("interface=bridge", "src-address=00:00:5E:00:53:65", "uptime=3m24s")
    }
    settings(
        "tool/romon",
        "enabled=true", "id=00:00:00:00:00:00", "secrets=", "current-id=00:00:5E:00:53:02",
    )
    list("tool/romon/port") {
        row("interface=all", "forbid=false", "cost=100", "secrets=")
        row("interface=ether1", "forbid=true", "cost=100", "secrets=")
    }
    settings("tool/e-mail", "server=smtp.example.org", "port=587", "tls=starttls", "vrf=main", "from=demo-hap@example.org", "user=demo-hap@example.org", "password=", "certificate-verification=true", "last-status=succeeded", "last-address=198.51.100.25", fields = listOf(choice("tls", "no", "yes", "starttls")))
    settings("tool/sms", "port=none", "receive-enabled=false", "allowed-number=", "secret=", "channel=0", "auto-erase=false")
}

private fun DemoBuilder.toolsMonitoringDemo() {
    settings("tool/graphing", "page-refresh=300", "store-every=5min", fields = listOf(choice("store-every", "5min", "hour", "24hours")))
    list("tool/graphing/interface") {
        row("interface=all", "allow-address=192.168.88.0/24", "store-on-disk=true", "disabled=false")
        row("interface=ether1", "allow-address=0.0.0.0/0", "store-on-disk=false", "disabled=false")
    }
    list("tool/graphing/queue") {
        row("simple-queue=ps5", "allow-address=192.168.88.0/24", "store-on-disk=true", "allow-target=true", "disabled=false")
        row("simple-queue=guest", "allow-address=192.168.88.0/24", "store-on-disk=false", "allow-target=false", "disabled=false")
    }
    list("tool/graphing/resource") {
        row("allow-address=192.168.88.0/24", "store-on-disk=true", "disabled=false")
    }
    list("tool/apptraffic", writable = false) {
        row("application=https", "category=web", "in-minute=3145728", "out-minute=262144", "in-hour=188743680", "out-hour=15728640", "in-day=2684354560", "out-day=188743680", "in-week=15032385536", "out-week=1073741824")
        row("application=quic", "category=web", "in-minute=1572864", "out-minute=131072", "in-hour=94371840", "out-hour=7864320", "in-day=1342177280", "out-day=94371840", "in-week=7516192768", "out-week=536870912")
        row("application=stun", "category=gaming", "in-minute=2621440", "out-minute=491520", "in-hour=62914560", "out-hour=11534336", "in-day=419430400", "out-day=83886080", "in-week=2097152000", "out-week=419430400")
        row("application=wireguard", "category=vpn", "in-minute=65536", "out-minute=32768", "in-hour=3932160", "out-hour=1966080", "in-day=94371840", "out-day=31457280", "in-week=524288000", "out-week=157286400")
        row("application=dns", "category=infrastructure", "in-minute=12288", "out-minute=8192", "in-hour=737280", "out-hour=491520", "in-day=17825792", "out-day=11534336", "in-week=100663296", "out-week=67108864")
        row("application=ntp", "category=infrastructure", "in-minute=0", "out-minute=0", "in-hour=3072", "out-hour=3072", "in-day=73728", "out-day=73728", "in-week=516096", "out-week=516096")
    }
    list("tool/traffic-monitor", fields = listOf(choice("traffic", "received", "transmitted"), choice("trigger", "above", "always", "below"))) {
        row("name=wan-busy", "interface=ether1", "traffic=received", "trigger=above", "threshold=50000000", "on-event=:log warning \"wan receive above 50M\"", "disabled=false")
        row("name=wan-idle", "interface=ether1", "traffic=received", "trigger=below", "threshold=1000", "on-event=:log info \"wan is idle\"", "disabled=true")
    }
}

private fun DemoBuilder.toolsSnifferDemo() {
    settings(
        "tool/sniffer",
        "memory-limit=100KiB", "only-headers=false", "memory-scroll=true", "max-packet-size=2048", "file-name=wifi-5g-capture", "file-limit=1000KiB", "quick-rows=20",
        "quick-show-frame=false", "running=false", "streaming-enabled=false", "streaming-server=0.0.0.0:37008", "filter-stream=false", "filter-interface=wifi2",
        "filter-mac-address=", "filter-src-mac-address=", "filter-dst-mac-address=", "filter-mac-protocol=", "filter-ip-address=", "filter-src-ip-address=",
        "filter-dst-ip-address=", "filter-ipv6-address=", "filter-src-ipv6-address=", "filter-dst-ipv6-address=", "filter-ip-protocol=", "filter-port=", "filter-src-port=",
        "filter-dst-port=", "filter-vlan=", "filter-cpu=", "filter-size=", "filter-direction=any", "filter-operator-between-entries=or",
        fields = listOf(choice("filter-direction", "any", "rx", "tx"), choice("filter-operator-between-entries", "or", "and")),
    )
    list("tool/sniffer/packet", writable = false) {
        toolsPackets.forEachIndexed { index, packet ->
            row(*(packet + listOf("num=${index + 1}", "time=${toolsSeconds(index * 37L)}")).toTypedArray())
        }
    }
    list("tool/sniffer/connection", writable = false) {
        row("src-address=192.168.88.101:51944", "dst-address=140.82.121.5:443", "bytes=21904/411220", "resends=0/0", "mss=1460/1460", "active=true")
        row("src-address=192.168.88.102:60112", "dst-address=1.1.1.1:853", "bytes=4811/9220", "resends=0/1", "mss=1460/1452", "active=true")
        row("src-address=192.168.88.103:55012", "dst-address=192.168.88.1:53", "bytes=74/106", "resends=0/0", "mss=0/0", "active=false")
    }
    list("tool/sniffer/host", writable = false) {
        row("address=192.168.88.101", "rate=1288/18400", "peak-rate=95000/2400000", "total=2209388/48118203")
        row("address=192.168.88.102", "rate=240/1200", "peak-rate=18000/64000", "total=412030/3201088")
        row("address=192.168.88.103", "rate=0/0", "peak-rate=1200/2400", "total=23010/48122")
        row("address=140.82.121.5", "rate=18400/1288", "peak-rate=2400000/95000", "total=48118203/2209388")
    }
    list("tool/sniffer/protocol", writable = false, live = counting()) {
        row("protocol=ip:tcp", "packets=38210", "bytes=48118203", "share=88.1%")
        row("protocol=ip:udp", "packets=4120", "bytes=3201088", "share=9.4%")
        row("protocol=arp", "packets=212", "bytes=8904", "share=0.5%")
        row("protocol=ip:icmp", "packets=344", "bytes=33712", "share=0.8%")
    }
    command("tool/sniffer/quick", choice("direction", "any", "rx", "tx"), choice("operator-between-entries", "or", "and"), run = { toolsQuickSniff() })
}

private fun DemoBuilder.toolsGeneratorDemo() {
    settings("tool/traffic-generator", "latency-distribution-max=100us", "measure-out-of-order=false", "stats-samples-to-keep=100", "test-id=0")
    list("tool/traffic-generator/packet-template") {
        row("name=lan-udp", "header-stack=mac,ip,udp", "interface=ether5", "mac-src=00:00:5E:00:53:05", "mac-dst=00:00:5E:00:53:20", "ip-src=192.168.88.200", "ip-dst=192.168.88.20")
    }
    list("tool/traffic-generator/port") {
        row("name=gen-ether5", "interface=ether5", "first-header=mac", "dynamic=true", "disabled=false")
    }
    list("tool/traffic-generator/stream") {
        row("id=1", "tx-template=lan-udp", "port=gen-ether5", "mbps=100", "packet-size=1400", "packet-count=0", "cpu-core=auto", "default-port=gen-ether5")
    }
}

private fun DemoBuilder.toolsCommandsDemo() {
    command("tool/bandwidth-test", choice("protocol", "udp", "tcp"), choice("direction", "receive", "send", "both"), choice("random-data", "yes", "no"), run = ::toolsBandwidthTest)
    command("tool/e-mail/send", text("to"), text("cc"), text("subject"), text("body"), text("file"), run = ::toolsEmailSend)
    command("tool/flood-ping", run = ::toolsFloodPing)
    command("tool/ip-scan", run = { toolsIpScan() })
    command("tool/ping-speed", text("address"), text("interval"), text("size"), run = { toolsPingSpeed() })
    command("tool/profile", run = ::toolsProfile)
    command("tool/speed-test", run = ::toolsSpeedTest)
    command("tool/torch", run = ::toolsTorch)
    command("tool/wol", run = ::toolsWakeOnLan)
}

private fun toolsBandwidthTest(arguments: Map<String, String>): Flow<Item> = flow {
    val tcp = arguments["protocol"] == "tcp"
    val direction = arguments["direction"] ?: "receive"
    var seconds = 0
    var txTotal = 0L
    var rxTotal = 0L
    while (true) {
        seconds++
        val tx = if (direction == "receive") 0L else wander(if (tcp) 912_000_000L else 874_000_000L, seconds, 3)
        val rx = if (direction == "send") 0L else wander(if (tcp) 935_000_000L else 902_000_000L, seconds, 5)
        txTotal += tx
        rxTotal += rx
        emit(
            Item(
                "",
                mapOf(
                    "status" to "running", "duration" to duration(seconds.toLong()),
                    "tx-current" to "$tx", "tx-10-second-average" to "${txTotal / seconds}", "tx-total-average" to "${txTotal / seconds}",
                    "rx-current" to "$rx", "rx-10-second-average" to "${rxTotal / seconds}", "rx-total-average" to "${rxTotal / seconds}",
                    "lost-packets" to if (tcp) "0" else "${seconds * 3 % 11}", "local-cpu-load" to "${wander(24, seconds, 1)}%", "remote-cpu-load" to "${wander(9, seconds, 2)}%",
                ),
            ),
        )
        delay(1000)
    }
}

private fun toolsEmailSend(arguments: Map<String, String>): Flow<Item> = flow {
    delay(800)
    emit(Item("", mapOf("to" to arguments["to"].orEmpty(), "subject" to arguments["subject"].orEmpty(), "status" to "queued")))
}

private fun toolsFloodPing(arguments: Map<String, String>): Flow<Item> = flow {
    val count = arguments["count"]?.toIntOrNull() ?: 100_000
    var sent = 0
    var step = 0
    while (sent < count) {
        step++
        sent = minOf(count, sent + 4_200)
        emit(Item("", mapOf("sent" to "$sent", "received" to "$sent", "min-rtt" to "10ms", "avg-rtt" to "${wander(11, step, 2)}ms", "max-rtt" to "${wander(17, step, 3)}ms")))
        delay(300)
    }
}

private fun toolsIpScan(): Flow<Item> = flow {
    for (host in toolsHosts) {
        delay(350)
        emit(toolsItem(host))
    }
}

private fun toolsPingSpeed(): Flow<Item> = flow {
    var seconds = 0
    var total = 0L
    while (true) {
        seconds++
        val current = wander(938_000_000, seconds, 11)
        total += current
        emit(Item("", mapOf("current" to toolsRate(current), "average" to toolsRate(total / seconds))))
        delay(1000)
    }
}

private fun toolsProfile(arguments: Map<String, String>): Flow<Item> = flow {
    val cpu = arguments["cpu"] ?: "all"
    var round = 0
    while (true) {
        val usage = toolsProcesses.mapIndexed { index, (name, base) -> name to wander(base.toLong(), round, index) }
        val idle = 1000 - usage.sumOf { it.second }
        for ((name, tenths) in usage + ("idle" to idle)) {
            emit(Item("", mapOf(".section" to "$round", "name" to name, "cpu" to cpu, "usage" to "${tenths / 10}.${tenths % 10}")))
        }
        round++
        delay(1000)
    }
}

private fun toolsQuickSniff(): Flow<Item> = flow {
    var number = 0
    while (true) {
        for (packet in toolsPackets) {
            number++
            val values = toolsItem(packet).values
            val stack = listOf(values["protocol"], values["ip-protocol"]).filter { !it.isNullOrEmpty() }.joinToString(":")
            emit(Item("", values - "direction" - "ip-protocol" + mapOf("protocol" to stack, "dir" to values.getValue("direction"), "num" to "$number", "time" to toolsSeconds(number * 37L), "fp" to "false")))
            delay(250)
        }
    }
}

private fun toolsSpeedTest(arguments: Map<String, String>): Flow<Item> = flow {
    val total = arguments["test-duration"]?.removeSuffix("s")?.toIntOrNull() ?: 10
    val measured = mutableMapOf<String, String>()
    for (second in 1..total) {
        delay(1000)
        val step = second * 5 / total
        if (step >= 1) {
            measured["ping-min-avg-max"] = "10.4ms / 11.2ms / 13.9ms"
            measured["jitter-min-avg-max"] = "0.2ms / 0.8ms / 2.1ms"
            measured["loss"] = "0%"
        }
        if (step >= 2) measured["tcp-download"] = toolsRate(wander(931_000_000, second, 1))
        if (step >= 3) measured["tcp-upload"] = toolsRate(wander(918_000_000, second, 2))
        if (step >= 4) measured["udp-download"] = toolsRate(wander(947_000_000, second, 3))
        if (step >= 5) measured["udp-upload"] = toolsRate(wander(941_000_000, second, 4))
        emit(Item("", measured + mapOf("status" to if (second == total) "done" else "running", "time-remaining" to duration((total - second).toLong()))))
    }
}

private fun toolsTorch(arguments: Map<String, String>): Flow<Item> = flow {
    val wan = arguments["interface"] == "ether1"
    val shown = if (wan) toolsFlows.filter { !it.target.startsWith("192.168.") } else toolsFlows
    var round = 0
    while (true) {
        round++
        shown.forEachIndexed { index, entry ->
            val tx = wander(entry.tx, round, index)
            val rx = wander(entry.rx, round, index + 7)
            emit(
                Item(
                    "",
                    buildMap {
                        put(".section", "$round")
                        put("mac-protocol", "ip")
                        put("ip-protocol", entry.protocol)
                        put("src-address", if (wan) "203.0.113.50" else entry.source)
                        put("dst-address", entry.target)
                        if (entry.sourcePort.isNotEmpty()) {
                            put("src-port", entry.sourcePort)
                            put("dst-port", entry.targetPort)
                        }
                        put("dscp", "0")
                        put("tx", "$tx")
                        put("rx", "$rx")
                        put("tx-packets", "${maxOf(1L, tx / 9_600)}")
                        put("rx-packets", "${maxOf(1L, rx / 9_600)}")
                    },
                ),
            )
        }
        delay(1000)
    }
}

private fun toolsWakeOnLan(arguments: Map<String, String>): Flow<Item> = flow {
    emit(Item("", mapOf("mac" to arguments["mac"].orEmpty(), "interface" to (arguments["interface"] ?: "bridge"))))
}
