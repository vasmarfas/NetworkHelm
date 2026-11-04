package com.vasmarfas.networkhelm.openwrt

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlin.test.Test
import kotlin.test.assertEquals

class LuciDependsTest {

    private fun json(text: String): JsonElement = Json.parseToJsonElement(text)

    private val files = mapOf(
        "/sys/class/leds" to FileKind.EmptyDirectory,
        "/sbin/fw3" to FileKind.Executable,
        "/usr/sbin/nft" to FileKind.Executable,
        "/usr/share/fw3/helpers.conf" to FileKind.File,
    )

    private val configs = mapOf(
        "firewall" to listOf(UciSection("cfg01e63d", "defaults", true, mapOf("input" to "REJECT"), emptyMap())),
        "network" to listOf(UciSection("lan", "interface", false, mapOf("proto" to "static"), mapOf("dns" to listOf("192.0.2.53")))),
        "wireless" to emptyList(),
    )

    private fun check(fs: String?, uci: String?) = satisfied(fs?.let(::json), uci?.let(::json), files) { configs[it] }

    @Test
    fun filesAreCheckedAsLuciChecksThem() {
        assertEquals(false, check("""{"/sys/class/leds":"directory"}""", null), "an empty directory hides LED Configuration")
        assertEquals(false, check("""{"/sbin/block":"executable"}""", null), "no block, no Mount Points")
        assertEquals(true, check("""{"/sbin/fw3":"executable"}""", """{"firewall":true}"""))
        assertEquals(true, check("""{"/usr/share/fw3/helpers.conf":"file"}""", null))
        assertEquals(false, check("""[{"/usr/sbin/nft":"absent","/usr/sbin/iptables":"executable"},{"/usr/sbin/nft":"absent","/usr/sbin/ip6tables":"executable"}]""", null))
        assertEquals(true, check("""{"/usr/sbin/nft":"executable"}""", null))
    }

    @Test
    fun configsAndTheirSectionsAreCheckedAsLuciChecksThem() {
        assertEquals(false, check(null, """{"wireless":{"@wifi-device":true}}"""), "no radio, no Wireless")
        assertEquals(false, check(null, """{"wireless":true}"""), "a config without sections")
        assertEquals(false, check(null, """{"dhcp":true}"""), "a config that is not there")
        assertEquals(true, check(null, """{"network":{"lan":{"proto":"static"}}}"""))
        assertEquals(false, check(null, """{"network":{"lan":{"proto":"dhcp"}}}"""))
        assertEquals(true, check(null, """{"network":{"@interface":{"dns":"192.0.2.53"}}}"""), "a value of a list")
        assertEquals(true, check(null, """{"network":{"lan":"interface"}}"""), "the type of the section")
        assertEquals(true, check(null, """[{"dhcp":true},{"firewall":{"@defaults":true}}]"""), "any of the alternatives")
    }
}
