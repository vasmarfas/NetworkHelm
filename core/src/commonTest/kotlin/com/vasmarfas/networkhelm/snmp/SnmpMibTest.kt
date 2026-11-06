package com.vasmarfas.networkhelm.snmp

import com.vasmarfas.networkhelm.model.DeviceException
import com.vasmarfas.networkhelm.model.ErrorCategory
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SnmpMibTest {

    private val tiny = SnmpMib(
        listOf(
            "# oid\tname\tmodule\tkind\ttype\ttc\thint\tenums\taccess\tindex\tunits",
            "1.3.6.1.4.1.99999.1\trdTestObjects\tRD-TEST-MIB\tgroup\t\t\t\t\t\t\t",
            "1.3.6.1.4.1.99999.1.1\trdTestLevel\tRD-TEST-MIB\tscalar\tInteger32\t\t\tlow(1),high(2)\tread-write\t\tsteps",
            "1.3.6.1.4.1.99999.1.2\trdTestTable\tRD-TEST-MIB\ttable\t\t\t\t\tnot-accessible\t\t",
            "1.3.6.1.4.1.99999.1.2.1\trdTestEntry\tRD-TEST-MIB\tentry\t\t\t\t\tnot-accessible\trdTestIndex,*rdTestName\t",
            "1.3.6.1.4.1.99999.1.2.1.1\trdTestIndex\tRD-TEST-MIB\tcolumn\tUnsigned32\t\t\t\tnot-accessible\t\t",
            "1.3.6.1.4.1.99999.1.2.1.2\trdTestName\tRD-TEST-MIB\tcolumn\tOctetString\tDisplayString\t255a\t\tread-create\t\t",
            "1.3.6.1.4.1.99999\tRD Test\t\tvendor\t\t\t\t\t\t\t",
        ).joinToString("\n"),
    )

    @Test
    fun aDictionaryIsReadFromItsLines() {
        val level = assertNotNull(tiny.named("rdTestLevel"))
        assertEquals(listOf(MibKind.Scalar, "Integer32", "steps"), listOf(level.kind, level.type, level.units))
        assertEquals(mapOf(1L to "low", 2L to "high"), level.enums)
        assertTrue(level.writable)
        val entry = assertNotNull(tiny.named("rdTestEntry"))
        assertEquals(listOf("rdTestIndex", "rdTestName"), entry.index)
        assertTrue(entry.implied)
        assertEquals(listOf("rdTestIndex", "rdTestName"), tiny.columns(entry).map { it.name })
        assertEquals("rdTestName", tiny.find(oid("1.3.6.1.4.1.99999.1.2.1.2.7.114.100"))?.name)
        assertEquals("rdTestName.7.114.100", tiny.describe(oid("1.3.6.1.4.1.99999.1.2.1.2.7.114.100")))
        assertEquals("1.3.6.1.4.1.12345.1", tiny.describe(oid("1.3.6.1.4.1.12345.1")))
        assertEquals("RD Test", tiny.vendor(oid("1.3.6.1.4.1.99999.1.2")))
        assertNull(tiny.named("RD Test"))
        assertEquals(oid("1.3.6.1.4.1.99999.1.1.0"), tiny.oidOf("rdTestLevel.0"))
    }

    @Test
    fun theStandardDictionaryKnowsTheCommonMibs() {
        val mib = SnmpMib.standard
        val admin = assertNotNull(mib.named("ifAdminStatus"))
        assertEquals(listOf("1.3.6.1.2.1.2.2.1.7", "IF-MIB", "Integer32", "read-write"), listOf("${admin.oid}", admin.module, admin.type, admin.access))
        assertEquals(mapOf(1L to "up", 2L to "down", 3L to "testing"), admin.enums)
        assertEquals(MibKind.Column, admin.kind)
        assertFalse(assertNotNull(mib.named("ifOperStatus")).writable)
        val status = assertNotNull(mib.named("dot1qVlanStaticRowStatus"))
        assertEquals(listOf(MibKind.Column, "RowStatus", "read-create"), listOf(status.kind, status.tc, status.access))
        val name = assertNotNull(mib.named("sysName"))
        assertEquals(listOf(MibKind.Scalar, "OctetString", "DisplayString", "read-write"), listOf(name.kind, name.type, name.tc, name.access))
        assertEquals(listOf("ifIndex"), mib.named("ifXEntry")?.index)
        val target = assertNotNull(mib.named("snmpTargetAddrEntry"))
        assertEquals(listOf("snmpTargetAddrName"), target.index)
        assertTrue(target.implied)
        assertEquals("MikroTik", mib.vendor(oid("1.3.6.1.4.1.14988.1.1")))
        assertEquals("sysName.0", mib.describe(oid("1.3.6.1.2.1.1.5.0")))
    }

    @Test
    fun valuesAreShownTheWayAPersonReadsThem() {
        val mib = SnmpMib.standard
        fun shown(name: String, value: SnmpValue) = mib.shown(assertNotNull(mib.named(name)), value)
        assertEquals("down", shown("ifAdminStatus", SnmpValue.Number(INTEGER, 2)))
        assertEquals("9", shown("ifAdminStatus", SnmpValue.Number(INTEGER, 9)))
        assertEquals("true", shown("ifPromiscuousMode", SnmpValue.Number(INTEGER, 1)))
        assertEquals("1d3h46m40s", shown("ifLastChange", SnmpValue.Number(TIMETICKS, 10_000_000)))
        assertEquals("00:00:5E:00:53:19", shown("ifPhysAddress", SnmpValue.Octets(byteArrayOf(0, 0, 0x5E, 0, 0x53, 0x19))))
        assertEquals("rack 3", shown("sysLocation", SnmpValue.Octets("rack 3".encodeToByteArray())))
        assertEquals("2026-11-06 14:03:05 +03:00", shown("hrSystemDate", SnmpValue.Octets(byteArrayOf(0x07, 0xEA.toByte(), 11, 6, 14, 3, 5, 0, '+'.code.toByte(), 3, 0))))
        assertEquals("198.51.100.1", shown("ipAdEntAddr", SnmpValue.Address(byteArrayOf(198.toByte(), 51, 100, 1))))
        assertEquals("hrStorageRam", shown("hrStorageType", SnmpValue.Id(oid("1.3.6.1.2.1.25.2.1.2"))))
        assertEquals("80:01", shown("dot1qVlanStaticEgressPorts", SnmpValue.Octets(byteArrayOf(0x80.toByte(), 0x01))))
        assertEquals("18446744073709551615", shown("ifHCInOctets", SnmpValue.Number(COUNTER64, -1)))
        assertEquals("", shown("ifDescr", SnmpValue.Missing(0x81)))
    }

    @Test
    fun aValueIsWrittenInTheTypeOfItsObject() {
        val mib = SnmpMib.standard
        fun written(name: String, value: String?) = mib.written(assertNotNull(mib.named(name)), value)
        assertEquals(SnmpValue.Number(INTEGER, 2), written("ifAdminStatus", "down"))
        assertEquals(SnmpValue.Number(INTEGER, 3), written("ifAdminStatus", "3"))
        assertEquals(SnmpValue.Number(INTEGER, 2), written("ifPromiscuousMode", "false"))
        assertEquals(SnmpValue.Number(GAUGE32, 4094), written("dot1qVlanIndex", "4094"))
        assertEquals(SnmpValue.Id(oid("1.3.6.1.6.1.1")), written("snmpTargetAddrTDomain", "1.3.6.1.6.1.1"))
        assertEquals(SnmpValue.Id(oid("1.3.6.1.2.1.25.2.1.2")), written("hrStorageType", "hrStorageRam"))
        assertContentEquals(byteArrayOf(0x7F, 0, 0, 1, 0, 0xA2.toByte()), (written("snmpTargetAddrTAddress", "7F:00:00:01:00:A2") as SnmpValue.Octets).bytes)
        assertContentEquals("rd-test".encodeToByteArray(), (written("dot1qVlanStaticName", "rd-test") as SnmpValue.Octets).bytes)
        assertContentEquals(byteArrayOf(0, 0, 0x5E, 0, 0x53, 0x19), (written("dot1dStaticAddress", "00-00-5e-00-53-19") as SnmpValue.Octets).bytes)
        assertContentEquals(byteArrayOf(0x07, 0xEA.toByte(), 11, 6, 14, 3, 5, 0), (written("hrSystemDate", "2026-11-06 14:03:05") as SnmpValue.Octets).bytes)
        assertContentEquals(byteArrayOf(198.toByte(), 51, 100, 1), (written("ipAdEntAddr", "198.51.100.1") as SnmpValue.Address).bytes)
        assertEquals(ErrorCategory.ArgumentValue, assertFailsWith<DeviceException> { written("ifAdminStatus", "sideways") }.category)
        assertEquals(ErrorCategory.ArgumentValue, assertFailsWith<DeviceException> { written("dot1qVlanIndex", "-1") }.category)
        assertEquals(ErrorCategory.Unsupported, assertFailsWith<DeviceException> { written("ifInOctets", "0") }.category)
    }

    @Test
    fun aSetCarriesEachTypeInItsOwnTag() {
        fun binding(value: SnmpValue) = setPdu(1, listOf(oid("1.3.6.1.4.1.99999.1.0") to value)).let { it.copyOfRange(it.size - value.size(), it.size) }
        assertContentEquals(byteArrayOf(0x42, 0x02, 0x0F, 0xFE.toByte()), binding(SnmpValue.Number(GAUGE32, 4094)))
        assertContentEquals(byteArrayOf(0x42, 0x05, 0x00, 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte()), binding(SnmpValue.Number(GAUGE32, 0xFFFF_FFFFL)))
        assertContentEquals(byteArrayOf(0x43, 0x02, 0x17, 0x70), binding(SnmpValue.Number(TIMETICKS, 6_000)))
        assertContentEquals(byteArrayOf(0x40, 0x04, 0xC0.toByte(), 0xA8.toByte(), 0x01, 0x01), binding(SnmpValue.Address(byteArrayOf(0xC0.toByte(), 0xA8.toByte(), 1, 1))))
        assertContentEquals(byteArrayOf(0x06, 0x07, 0x2B, 0x06, 0x01, 0x06, 0x01, 0x01, 0x00), binding(SnmpValue.Id(oid("1.3.6.1.6.1.1.0"))).copyOfRange(0, 9))
        assertContentEquals(byteArrayOf(0x02, 0x01, 0xFE.toByte()), binding(SnmpValue.Number(INTEGER, -2)))
    }

    private fun SnmpValue.size() = encoded(this).size
}
