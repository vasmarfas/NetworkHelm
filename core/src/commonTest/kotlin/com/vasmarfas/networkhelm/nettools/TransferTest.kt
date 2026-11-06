package com.vasmarfas.networkhelm.nettools

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class TransferTest {

    private fun text(seconds: Double) = Transfer.timeText(seconds, "h", "min", "s")

    @Test
    fun aSizeTakesItsTimeAtASpeed() {
        assertEquals(8.0, Transfer.seconds(100.0, SizeUnit.MB, 100.0, SpeedUnit.Mbit))
        assertEquals(80.0, Transfer.seconds(1.0, SizeUnit.GB, 100.0, SpeedUnit.Mbit))
        assertEquals(1.0, Transfer.seconds(1.0, SizeUnit.MB, 1.0, SpeedUnit.MBps))
        assertEquals("8.59", Transfer.rounded(assertNotNull(Transfer.seconds(1.0, SizeUnit.GiB, 1.0, SpeedUnit.Gbit))))
    }

    @Test
    fun aSpeedIsWhatMovesASizeInATime() {
        assertEquals(100.0, Transfer.speed(1.0, SizeUnit.GB, 80.0, SpeedUnit.Mbit))
        assertEquals(12.5, Transfer.speed(1.0, SizeUnit.GB, 80.0, SpeedUnit.MBps))
    }

    @Test
    fun aSizeIsWhatASpeedMovesInATime() {
        assertEquals(1.0, Transfer.size(100.0, SpeedUnit.Mbit, 80.0, SizeUnit.GB))
        assertEquals(10.0, Transfer.size(1.0, SpeedUnit.GBps, 10.0, SizeUnit.GB))
    }

    @Test
    fun theThreeFormulasAgreeWithOneAnother() {
        val seconds = assertNotNull(Transfer.seconds(3.7, SizeUnit.GiB, 54.0, SpeedUnit.Mbit, 6.0))
        assertEquals("3.7", Transfer.rounded(assertNotNull(Transfer.size(54.0, SpeedUnit.Mbit, seconds, SizeUnit.GiB, 6.0))))
        assertEquals("54", Transfer.rounded(assertNotNull(Transfer.speed(3.7, SizeUnit.GiB, seconds, SpeedUnit.Mbit, 6.0))))
    }

    @Test
    fun overheadTakesItsShareOfTheSpeed() {
        assertEquals("10", Transfer.rounded(assertNotNull(Transfer.seconds(100.0, SizeUnit.MB, 100.0, SpeedUnit.Mbit, 20.0))))
        assertEquals("200", Transfer.rounded(assertNotNull(Transfer.seconds(100.0, SizeUnit.MB, 100.0, SpeedUnit.Mbit, 96.0))))
        assertEquals("125", Transfer.rounded(assertNotNull(Transfer.speed(1.0, SizeUnit.GB, 80.0, SpeedUnit.Mbit, 20.0))))
        assertEquals("0.8", Transfer.rounded(assertNotNull(Transfer.size(100.0, SpeedUnit.Mbit, 80.0, SizeUnit.GB, 20.0))))
    }

    @Test
    fun nothingIsGivenForValuesThatCannotBe() {
        assertNull(Transfer.seconds(0.0, SizeUnit.GB, 100.0, SpeedUnit.Mbit))
        assertNull(Transfer.seconds(1.0, SizeUnit.GB, 0.0, SpeedUnit.Mbit))
        assertNull(Transfer.seconds(1.0, SizeUnit.GB, -5.0, SpeedUnit.Mbit))
        assertNull(Transfer.seconds(1.0, SizeUnit.GB, 100.0, SpeedUnit.Mbit, 100.0))
        assertNull(Transfer.seconds(1.0, SizeUnit.GB, 100.0, SpeedUnit.Mbit, -1.0))
        assertNull(Transfer.speed(1.0, SizeUnit.GB, 0.0, SpeedUnit.Mbit))
        assertNull(Transfer.size(0.0, SpeedUnit.Mbit, 10.0, SizeUnit.GB))
        assertNotNull(Transfer.seconds(1.0, SizeUnit.GB, 100.0, SpeedUnit.Mbit, 99.9))
    }

    @Test
    fun anAmountIsAPlainDecimalWithAPointOrAComma() {
        assertEquals(12.0, Transfer.amount("12"))
        assertEquals(12.5, Transfer.amount(" 12.5 "))
        assertEquals(12.5, Transfer.amount("12,5"))
        assertEquals(0.5, Transfer.amount(".5"))
        assertEquals(5.0, Transfer.amount("5."))
        for (text in listOf("", " ", "1e5", "NaN", "Infinity", "-1", "+1", "5d", "0x10", "1 2", "1.2.3", "abc", "1".padEnd(400, '0'))) {
            assertNull(Transfer.amount(text), text)
        }
    }

    @Test
    fun aTimeIsHoursMinutesAndSecondsWithEmptyOnesLeftOut() {
        assertEquals(3725.0, Transfer.time("1", "2", "5"))
        assertEquals(60.0, Transfer.time("", "1", ""))
        assertEquals(1800.0, Transfer.time("0.5", "", ""))
        assertEquals(90.5, Transfer.time("", "1", "30,5"))
        assertNull(Transfer.time("", "", ""))
        assertNull(Transfer.time(" ", "", " "))
        assertNull(Transfer.time("1", "x", ""))
    }

    @Test
    fun aNumberKeepsFourSignificantDigitsAndAllTheWholeOnes() {
        assertEquals("13.33", Transfer.rounded(13.3333333))
        assertEquals("750", Transfer.rounded(750.0))
        assertEquals("0.00075", Transfer.rounded(0.00075))
        assertEquals("100", Transfer.rounded(99.996))
        assertEquals("1234568", Transfer.rounded(1234567.8))
        assertEquals("0.3", Transfer.rounded(0.1 + 0.2))
        assertEquals("1", Transfer.rounded(1.0))
    }

    @Test
    fun aTimeIsWrittenInHoursMinutesAndSecondsFromAMinuteOn() {
        assertEquals("8 s", text(8.0))
        assertEquals("0.5 s", text(0.5))
        assertEquals("59.96 s", text(59.96))
        assertEquals("1 min 0 s", text(60.0))
        assertEquals("1 min 20 s", text(80.0))
        assertEquals("1 h 2 min 5 s", text(3725.0))
        assertEquals("1 h 0 min 0 s", text(3599.6))
        assertEquals("2 h 0 min 5 s", text(7205.2))
        assertEquals("48 h 0 min 0 s", text(172800.0))
    }

    @Test
    fun theReferenceSpeedsAreTenAndAHundredMegabitsAndOneGigabit() {
        assertEquals(listOf("10 Mbit/s", "100 Mbit/s", "1 Gbit/s"), Transfer.references.map { (speed, unit) -> "${Transfer.rounded(speed)} ${unit.symbol}" })
    }
}
