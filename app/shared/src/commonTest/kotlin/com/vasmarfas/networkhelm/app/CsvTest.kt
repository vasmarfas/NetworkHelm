package com.vasmarfas.networkhelm.app

import kotlin.test.Test
import kotlin.test.assertEquals

class CsvTest {

    @Test
    fun quotedFieldsKeepTheirSeparatorsQuotesAndLineBreaks() {
        val text = "address,user,comment\r\n192.0.2.1,admin,\"core, rack 2\"\r\n192.0.2.2,\"say \"\"hi\"\"\",\"two\nlines\"\r\n\r\n"
        assertEquals(
            listOf(
                listOf("address", "user", "comment"),
                listOf("192.0.2.1", "admin", "core, rack 2"),
                listOf("192.0.2.2", "say \"hi\"", "two\nlines"),
            ),
            parseCsv(text),
        )
    }

    @Test
    fun aSemicolonHeaderMakesTheSemicolonTheSeparator() {
        assertEquals(listOf(listOf("address", "comment"), listOf("192.0.2.1", "1,5 Gbit")), parseCsv("address;comment\n192.0.2.1;1,5 Gbit"))
    }
}
