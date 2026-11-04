package com.vasmarfas.networkhelm.routeros.console

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ConsoleReplyTest {
    private val prompt = "\r\r\r\u001b[9999B\u001b[K[\u001b[m\u001b[36mtester\u001b[m@\u001b[m\u001b[32mCHR\u001b[m] > "
    private val echo = ":put \"B\"; :put 1; :put \"E\"\u001b[12D\u001b[m\u001b[36m:\u001b[m\u001b[35mput\u001b[m\r\n"

    @Test
    fun theOutputIsWhatLiesBetweenTheMarkersAndTheEchoIsLeftOut() {
        val answer = answer("$echo\rB\r\n[{\".id\":\"*1\"}]\r\nE\r\n$prompt", "B", "E")
        assertTrue(answer.ok)
        assertEquals("[{\".id\":\"*1\"}]", answer.text)
        assertEquals("a\nb", answer("$echo\rB\r\na\r\nb\r\nE\r\n$prompt", "B", "E").text)
        assertEquals("", answer("$echo\rB\r\nE\r\n$prompt", "B", "E").text)
    }

    @Test
    fun withoutTheEndMarkerWhatFollowsTheBeginningIsTheMessage() {
        val missing = answer("$echo\rB\r\nno such item (4)\r\n$prompt", "B", "E")
        assertFalse(missing.ok)
        assertEquals("no such item (4)", missing.text)
        val syntax = answer("$echo\rsyntax error (line 1 column 16)\r\n$prompt", "B", "E")
        assertFalse(syntax.ok)
        assertEquals("syntax error (line 1 column 16)", syntax.text)
    }

    @Test
    fun aLineThatIsNotUtf8IsReadAsCp1251() {
        val bytes = "при\n".encodeToByteArray() + byteArrayOf(0xEF.toByte(), 0xF0.toByte(), 0xE8.toByte(), 0x0A) + "x".encodeToByteArray()
        assertEquals("при\nпри\nx", decode(bytes, 0, bytes.size))
        assertEquals("при\nx", decode(bytes, 7, bytes.size))
    }

    @Test
    fun everyKindOfEscapeIsDroppedFromWhatTheConsoleSends() {
        assertEquals("[a@b] > x", clean("\u001b(B\u001b[?1h\u001b=[\u001b[36ma\u001b[m@b] > \r\u001b>x\u001b[K"))
    }

    @Test
    fun aByteThatIsNoPartOfASequenceIsCp1251WhereverItIs() {
        val bytes = "{\"a\":\"при\",\"b\":\"".encodeToByteArray() + byteArrayOf(0xEF.toByte(), 0xF0.toByte(), 0xE8.toByte()) + "\"}".encodeToByteArray()
        assertEquals("{\"a\":\"при\",\"b\":\"при\"}", decode(bytes, 0, bytes.size))
        assertEquals("Р", decode(byteArrayOf(0xD0.toByte()), 0, 1))
        assertEquals("😀é", decode("😀é".encodeToByteArray(), 0, 6))
    }

    @Test
    fun theRoutersWordsGetTheirCategories() {
        assertEquals(0, trap("no such item (4)").category)
        assertEquals(0, trap("syntax error (line 1 column 16)").category)
        assertEquals(0, trap("expected end of command (line 1 column 34)").category)
        assertEquals(1, trap("invalid value for argument address").category)
        assertEquals(1, trap("invalid time value for argument timeout").category)
        assertEquals(1, trap("bad parameter as-value (line 1 column 57)").category)
        assertEquals(4, trap("failure: already have such entry").category)
        assertNull(trap("something else").category)
        assertEquals("failure: already have such entry", trap("failure: already have such entry").message)
    }

    @Test
    fun rowsKeepTheValuesAsTheApiHasThem() {
        val rows = rows(
            """[{".id":"*1","disabled":false,"port":8291,"servers":["1.1.1.1","8.8.8.8"],"none":[],"gone":null,"comment":"é \" \\ \u0000","load":24.700000,"timeout":"00:59:59","uptime":"1970-01-03 06:11:21"}]""",
        )
        assertEquals(
            mapOf(
                ".id" to "*1", "disabled" to "false", "port" to "8291", "servers" to "1.1.1.1,8.8.8.8", "none" to "", "comment" to "é \" \\ \u0000",
                "load" to "24.7", "timeout" to "00:59:59", "uptime" to "2d6h11m21s",
            ),
            rows.single(),
        )
    }

    @Test
    fun theTablesOfOneCallAreJoinedByTheirIdsOrTheirPlaces() {
        val joined = rows("[{\".id\":\"*1\",\"chain\":\"input\"},{\".id\":\"*2\",\"chain\":\"forward\"}]\n[{\".id\":\"*2\",\"bytes\":5},{\".id\":\"*3\",\"bytes\":7}]\n")
        assertEquals(
            listOf(mapOf(".id" to "*1", "chain" to "input"), mapOf(".id" to "*2", "chain" to "forward", "bytes" to "5"), mapOf(".id" to "*3", "bytes" to "7")),
            joined,
        )
        assertEquals(listOf(mapOf("name" to "a", "load" to "1")), rows("[{\"name\":\"a\"}]\n[{\"load\":1}]"))
    }

    @Test
    fun anAddressOfIpv4LosesTheMappingPrefixThatSerializePutsBeforeIt() {
        val row = rows("""[{"address":"::ffff:192.0.2.5","servers":["::ffff:1.1.1.1","2001:db8::1"],"prefixed":"::ffff:10.0.0.1/24","word":"::ffff:abcd"}]""").single()
        assertEquals("192.0.2.5", row["address"])
        assertEquals("1.1.1.1,2001:db8::1", row["servers"])
        assertEquals("::ffff:10.0.0.1/24", row["prefixed"])
        assertEquals("::ffff:abcd", row["word"])
    }

    @Test
    fun aSingleSettingIsOneRowAndATimeIsConvertedOnlyWhereItCannotBeADate() {
        val row = rows("""{"cache-max-ttl":"1970-01-08 00:00:00","uptime":"1971-02-01 00:00:01","build-time":"2026-10-01 13:29:47","time":"21:35:55","date":"2026-10-26"}""").single()
        assertEquals("1w", row["cache-max-ttl"])
        assertEquals("56w4d1s", row["uptime"])
        assertEquals("2026-10-01 13:29:47", row["build-time"])
        assertEquals("21:35:55", row["time"])
        assertEquals("2026-10-26", row["date"])
        assertEquals(emptyList(), rows("[]"))
    }

    @Test
    fun anArrayPutAsTextIsSplitWhereAKeyComesAgain() {
        val replies = pairs(".id=*0;host=127.0.0.1;seq=0;time=00:00:00.000069;.id=*1;host=127.0.0.1;seq=1;status=timeout\n")
        assertEquals(
            listOf(
                mapOf(".id" to "*0", "host" to "127.0.0.1", "seq" to "0", "time" to "00:00:00.000069"),
                mapOf(".id" to "*1", "host" to "127.0.0.1", "seq" to "1", "status" to "timeout"),
            ),
            replies,
        )
        assertEquals(emptyList(), pairs(""))
    }
}
