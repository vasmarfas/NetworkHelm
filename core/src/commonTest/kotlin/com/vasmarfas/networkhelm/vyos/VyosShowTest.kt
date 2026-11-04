package com.vasmarfas.networkhelm.vyos

import kotlin.test.Test
import kotlin.test.assertEquals

class VyosShowTest {

    private val interfaces = """
        Codes: S - State, L - Link, u - Up, D - Down, A - Admin Down
        Interface    IP Address    MAC                VRF        MTU  S/L    Description
        -----------  ------------  -----------------  -------  -----  -----  -------------
        eth0         10.0.2.15/24  52:54:00:12:34:56  default   1500  u/u
        lo           127.0.0.1/8   00:00:00:00:00:00  default  65536  u/u
                     ::1/128
    """.trimIndent()

    private val counters = """
        Interface    Rx Packets    Rx Bytes    Tx Packets    Tx Bytes    Rx Dropped    Tx Dropped    Rx Errors    Tx Errors
        -----------  ------------  ----------  ------------  ----------  ------------  ------------  -----------  -----------
        eth0         7732          993268      6784          1873907     0             0             0            0
        lo           336           24864       336           24864       0             0             0            0
    """.trimIndent()

    @Test
    fun aTableOfOpModeBecomesRows() {
        val rows = table(interfaces)
        assertEquals(listOf("eth0", "lo"), rows.map { it["interface"] })
        assertEquals("127.0.0.1/8,::1/128", rows[1]["ip-address"])
        assertEquals(listOf("52:54:00:12:34:56", "1500", "u/u", ""), listOf(rows[0]["mac"], rows[0]["mtu"], rows[0]["s-l"], rows[0]["description"]))
        val twice = table(counters + "\n" + counters)
        assertEquals(2, twice.size)
        assertEquals(listOf("993268", "1873907"), listOf(twice[0]["rx-bytes"], twice[0]["tx-bytes"]))
        assertEquals(emptyList(), table("DHCP server is not configured"))
    }

    @Test
    fun memoryUptimeAndLoadFillTheBarAlongTheBottom() {
        val meminfo = """
            MemTotal:        2028568 kB
            MemFree:         1043960 kB
            MemAvailable:    1337272 kB
            Buffers:            2108 kB
            HugePages_Total:       0
        """.trimIndent()
        val uptime = """
            Uptime: 1w 2d 7h 49m 8s

            Load averages:
            1  minute:   0.5%
            5  minutes:  1.0%
            15 minutes:  0.0%
        """.trimIndent()
        val values = resource(meminfo, uptime)
        assertEquals(listOf("2077253632", "1369366528", "1w2d7h49m8s", "0.5%"), listOf("total-memory", "free-memory", "uptime", "load").map { values[it] })
        assertEquals("1068986368", resource("MemTotal: 2028568 kB\nMemFree: 1043932 kB", "")["free-memory"])
        assertEquals(emptyMap(), resource("", ""))
    }

    @Test
    fun theRoutesOfFrrBecomeRows() {
        val json = """{"10.0.2.0/24":[{"protocol":"connected","selected":true,"distance":0,"metric":0,"uptime":"07:29:28","vrfName":"default",""" +
            """"nexthops":[{"flags":3,"fib":true,"directlyConnected":true,"interfaceIndex":2,"interfaceName":"eth0","active":true,"weight":1}]}],""" +
            """"0.0.0.0/0":[{"protocol":"static","selected":true,"distance":1,"metric":0,"uptime":"00:00:05","vrfName":"default",""" +
            """"nexthops":[{"ip":"10.0.2.2","interfaceName":"eth0","active":true}]}]}"""
        val rows = routes(json)
        assertEquals(listOf("10.0.2.0/24", "0.0.0.0/0"), rows.map { it["dst-address"] })
        assertEquals(listOf("connected", "eth0", "eth0"), listOf(rows[0]["protocol"], rows[0]["gateway"], rows[0]["interface"]))
        assertEquals(listOf("true", "true", "true", "false"), listOf("dynamic", "active", "connect", "static").map { rows[0][it] })
        assertEquals(listOf("10.0.2.2", "1", "true", "false"), listOf(rows[1]["gateway"], rows[1]["distance"], rows[1]["static"], rows[1]["dynamic"]))
        assertEquals(emptyList(), routes("{}"))
    }

    @Test
    fun theLogComesInTheOrderItWasWritten() {
        val text = """
            Oct 25 04:17:01 vyos CRON[27254]: pam_unix(cron:session): session closed for user root
            Oct 25 04:11:46 vyos vyos-http-api[2391]: processing form data
            Oct 25 04:11:46 vyos vyos-http-api[2391]: processing form data
        """.trimIndent()
        val rows = log(text + "\n")
        assertEquals(listOf("Oct 25 04:11:46", "Oct 25 04:11:46", "Oct 25 04:17:01"), rows.map { it["time"] })
        assertEquals(3, rows.map { it.id }.distinct().size)
        assertEquals(listOf("CRON", "pam_unix(cron:session): session closed for user root"), listOf(rows[2]["program"], rows[2]["message"]))
        assertEquals(listOf(mapOf("text" to "-- No entries --")), log("-- No entries --").map { it.values })
    }
}
