package com.vasmarfas.networkhelm.ui.main

import com.vasmarfas.networkhelm.model.Argument
import com.vasmarfas.networkhelm.model.ValueType
import kotlin.test.Test
import kotlin.test.assertEquals

class ColumnsTest {

    @Test
    fun anEmptyListShowsTheFieldsAnElementCannotLackFirst() {
        val arguments = listOf(
            Argument("action", ValueType.Enumeration()),
            Argument("address-list", ValueType.Text(), optional = true),
            Argument("chain", ValueType.Text()),
            Argument("comment", ValueType.Text()),
            Argument("disabled", ValueType.YesNo),
            Argument("dst-address", ValueType.Ip4Range, optional = true),
            Argument("name", ValueType.Text()),
        )
        assertEquals(listOf("name", "action", "chain", "address-list", "dst-address"), emptyColumns(arguments))
    }

    @Test
    fun aFlagInACellReadsAsTheConsolePrintsIt() {
        assertEquals("no", cell(null, "use-radius", "false"))
        assertEquals("yes", cell(null, "running", "true"))
        assertEquals("yes", cell(null, "only-one", "yes"))
        assertEquals("34.6 MiB", cell(null, "bytes", "36280000"))
    }

    @Test
    fun aListOfUciReadsWholeInACell() {
        assertEquals("0.openwrt.pool.ntp.org, 1.openwrt.pool.ntp.org", cell(null, "server", "0.openwrt.pool.ntp.org\n1.openwrt.pool.ntp.org"))
    }
}
