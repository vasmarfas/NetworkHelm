package com.vasmarfas.networkhelm.ui.form

import com.vasmarfas.networkhelm.model.ValueType
import kotlin.test.Test
import kotlin.test.assertEquals

class FieldTypesTest {

    private fun fitting(type: ValueType, vararg values: String) = values.filter { fits(type, it) }

    @Test
    fun addressFieldsMarkOnlyWhatIsNoAddress() {
        val written = arrayOf("192.0.2.1", "192.0.2.0/24", "192.0.2.10-192.0.2.20", "192.0.2.0/255.255.255.0", "10.0.0.2-10.0.0.9,10.0.1.0/24", "192.0.2", "192.0.2.256", "192.0.2.0/33", "2001:db8::1", "")
        assertEquals(written.take(5), fitting(ValueType.Ip4, *written))
        assertEquals(listOf("2001:db8::1", "2001:db8::/64", "fe80::1%ether1"), fitting(ValueType.Ip6Prefix, "2001:db8::1", "2001:db8::/64", "fe80::1%ether1", "2001:db8::/129", "192.0.2.1", "2001:db8:::1"))
        assertEquals(listOf("192.0.2.1", "2001:db8::1"), fitting(ValueType.Ip, "192.0.2.1", "2001:db8::1", "dns.example"))
        assertEquals(listOf("0.0.0.0:0", "192.0.2.1:37008"), fitting(ValueType.Ip4, "0.0.0.0:0", "192.0.2.1:37008", "192.0.2.1:", "192.0.2.1:123456"))
    }

    @Test
    fun macAndNumbers() {
        assertEquals(listOf("B8:69:F4:12:34:56", "b8-69-f4-12-34-56", "b869.f412.3456", "b869f4123456"), fitting(ValueType.Mac, "B8:69:F4:12:34:56", "b8-69-f4-12-34-56", "b869.f412.3456", "b869f4123456", "B8:69:F4:12:34", "G8:69:F4:12:34:56"))
        assertEquals(listOf("01:80:C2:00:00:00/FF:FF:FF:FF:FF:F0"), fitting(ValueType.Mac, "01:80:C2:00:00:00/FF:FF:FF:FF:FF:F0", "01:80:C2:00:00:00/24", "a/b/c"))
        assertEquals(listOf("1", "4094", "4k"), fitting(ValueType.Integer(1, 4094, scaled = true), "1", "4094", "4k", "0", "4095", "ten"))
        assertEquals(listOf("anything"), fitting(ValueType.Text(), "anything"))
    }

    // RouterOS 7.25 takes 192.168.1 and makes 192.168.0.1 of it
    @Test
    fun gatewaysAndHosts() {
        val gateway = ValueType.Host(ValueType.Ip, names = true, "Address[%Interface][@VRF]")
        val written = arrayOf("10.0.2.2", "ether1", "10.0.2.2%ether1", "10.0.2.2@main", "ether1@main", "fe80::1%ether1", "ether1.100", "192.168.1", "10.0.2.300", "fe80:::1")
        assertEquals(written.take(7), fitting(gateway, *written))
        assertEquals(listOf("1.1.1.1", "2606:4700::1111"), fitting(ValueType.Host(ValueType.Ip, names = false, "Address[@VRF]"), "1.1.1.1", "2606:4700::1111", "dns.example"))
        assertEquals(listOf("192.0.2.1", "vpn.example.com", "192.0.2.1@vrf1"), fitting(ValueType.Host(ValueType.Ip4, names = true, "Address[@VRF]"), "192.0.2.1", "vpn.example.com", "192.0.2.1@vrf1", "192.0.2", "2001:db8::1"))
    }

    // unasked, RouterOS 7.25 moves 2026-02-30 to March 2 and 25:00:00 to 01:00:00
    @Test
    fun daysAndTimesOfTheDay() {
        val days = arrayOf("2026-10-25", "2026-1-5", "oct/25/2026", "2028-02-29", "2026-02-30", "2026/10/25", "25.10.2026", "1969-12-31")
        assertEquals(days.take(4), fitting(ValueType.Date, *days))
        val times = arrayOf("03:00:00", "3:0:0", "03:00", "24:00:00", "25:00:00", "03:60", "3h")
        assertEquals(times.take(4), fitting(ValueType.TimeOfDay, *times))
        val offsets = arrayOf("+01:00", "1:00", "-05:30", "+3", "+14:00", "abc", "+01:60", "+25:00")
        assertEquals(offsets.take(5), fitting(ValueType.Offset, *offsets))
    }

    @Test
    fun spans() {
        val vlans = ValueType.Span(ValueType.Integer(1, 4094, scaled = true), listOf("start", "end"), open = true)
        assertEquals(listOf("10", "20-30", "4094"), fitting(vlans, "10", "20-30", "4094", "0", "20-5000", "20-", "1-2-3", "ten"))
        val mss = ValueType.Span(ValueType.Integer(0, 65535, scaled = true), listOf("min", "max"), open = false)
        assertEquals(listOf("1400-1500"), fitting(mss, "1400-1500", "1400"))
        val signal = ValueType.Span(ValueType.Integer(-120, 120, scaled = true), listOf("min", "max"), open = true, separator = "..")
        assertEquals(listOf("-80..-40", "-80", "-80..120"), fitting(signal, "-80..-40", "-80", "-80..120", "-130..0", "-80-40"))
        val tos = ValueType.Span(ValueType.Integer(0, 255, scaled = true), listOf("tos", "mask"), open = true, separator = "/")
        assertEquals(listOf("0x10/0xFC", "16", "16/252"), fitting(tos, "0x10/0xFC", "16", "16/252", "300", "16/"))
    }

    @Test
    fun routeDistinguishers() {
        val values = arrayOf("65530:100", "192.0.2.9:7", "4200000000:1", "100", "abc", "300.1.1.1:5", "65530:")
        assertEquals(values.take(3), fitting(ValueType.RouteDistinguisher, *values))
    }

    @Test
    fun decimals() {
        val values = arrayOf("12.5", "12.5%", "100", "150", "abc")
        assertEquals(values.take(3), fitting(ValueType.Decimal(0.0, 100.0, "%"), *values))
    }

    @Test
    fun hexBytes() {
        assertEquals(listOf("0A1B", "0x0a1b"), fitting(ValueType.Hex, "0A1B", "0x0a1b", "abc", "zz"))
    }
}
