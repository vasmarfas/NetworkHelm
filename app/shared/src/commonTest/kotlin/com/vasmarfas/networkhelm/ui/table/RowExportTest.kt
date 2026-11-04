package com.vasmarfas.networkhelm.ui.table

import com.vasmarfas.networkhelm.app.HiddenValue
import kotlin.test.Test
import kotlin.test.assertEquals

class RowExportTest {

    private val columns = listOf(
        TableColumn<Map<String, String>>("name", "Name", null) { it.getValue("name") },
        TableColumn<Map<String, String>>("rate", "Rate", null, { "$it bps" }) { it.getValue("rate") },
        TableColumn<Map<String, String>>("comment", "Comment", null) { it.getValue("comment") },
    )

    private val rows = listOf(
        mapOf("name" to "ether1", "rate" to "1200", "comment" to "uplink, rack 2"),
        mapOf("name" to "ether2", "rate" to "0", "comment" to "say \"hi\"\nthen\tgo"),
    )

    @Test
    fun tabsCarryWhatTheCellsShowAndKeepEachRowOnALine() {
        assertEquals(
            "Name\tRate\tComment\nether1\t1200 bps\tuplink, rack 2\nether2\t0 bps\tsay \"hi\" then go",
            rowsAsTabs(columns, rows),
        )
    }

    @Test
    fun csvQuotesWhatNeedsQuotesAndCarriesTheValuesAsTheRouterGaveThem() {
        assertEquals(
            "Name,Rate,Comment\nether1,1200,\"uplink, rack 2\"\nether2,0,\"say \"\"hi\"\"\nthen\tgo\"",
            rowsAsCsv(columns, rows),
        )
    }

    @Test
    fun jsonKeysAreTheNamesOfTheProperties() {
        assertEquals(
            "[\n    {\n        \"name\": \"ether1\",\n        \"rate\": \"1200\",\n        \"comment\": \"uplink, rack 2\"\n    }\n]",
            rowsAsJson(columns, rows.take(1)),
        )
    }

    @Test
    fun aColumnThatHidesItsValuesKeepsThemHiddenInEveryForm() {
        val hidden = listOf(
            TableColumn<Map<String, String>>("name", "Name", null) { it.getValue("name") },
            TableColumn<Map<String, String>>("password", "Password", null, { if (it.isEmpty()) it else HiddenValue }) { it.getValue("password") },
        )
        val row = listOf(mapOf("name" to "rd-test", "password" to "secret"))
        assertEquals("Name\tPassword\nrd-test\t$HiddenValue", rowsAsTabs(hidden, row))
        assertEquals("Name,Password\nrd-test,$HiddenValue", rowsAsCsv(hidden, row))
        assertEquals(false, rowsAsJson(hidden, row).contains("secret"))
    }

    @Test
    fun noRowsLeaveTheTitlesAlone() {
        assertEquals("Name\tRate\tComment", rowsAsTabs(columns, emptyList()))
        assertEquals("[]", rowsAsJson(columns, emptyList()))
    }
}
