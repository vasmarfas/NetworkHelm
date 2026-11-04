package com.vasmarfas.networkhelm.model

import kotlin.test.Test
import kotlin.test.assertEquals

class FilterTest {

    @Test
    fun anElementMatchesByItsId() {
        val lan = Item("lan", mapOf("proto" to "static"))
        val filters = listOf(
            Filter.Equals(".id", "lan"),
            Filter.Equals(".id", "wan"),
            Filter.And(listOf(Filter.Equals(".id", "lan"), Filter.Equals("proto", "static"))),
            Filter.Equals("proto", "dhcp"),
        )
        assertEquals(listOf(true, false, true, false), filters.map { it.matches(lan) })
    }
}
