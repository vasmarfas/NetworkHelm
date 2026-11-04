package com.vasmarfas.networkhelm.ui.main

import com.vasmarfas.networkhelm.packs.PackPath
import kotlin.test.Test
import kotlin.test.assertEquals

class PingTimesTest {

    private val path = PackPath(millis = listOf("time", "avg-rtt"))

    @Test
    fun theTimesOfAPingAreMillisecondsWithThreeDecimals() {
        val times = listOf("31us", "120us", "1ms396us", "2ms", "120ms255us", "1s", "1m1s500ms")
        assertEquals(listOf("0.031", "0.120", "1.396", "2.000", "120.255", "1000.000", "61500.000"), times.map { formatted(path, "time", it) })
        assertEquals("0.057", formatted(path, "avg-rtt", "57us"))
    }

    @Test
    fun aValueThatIsNoTimeStaysAsItCame() {
        val values = listOf("timeout", "", "1.5", "10 ms")
        assertEquals(values, values.map { formatted(path, "time", it) })
    }

    @Test
    fun otherPropertiesKeepTheirForm() {
        assertEquals("31us", formatted(path, "size", "31us"))
        assertEquals("31us", formatted(PackPath(), "time", "31us"))
    }
}
