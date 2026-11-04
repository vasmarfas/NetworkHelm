package com.vasmarfas.networkhelm.ui.files

import kotlin.test.Test
import kotlin.test.assertEquals

class UtcTextTest {

    @Test
    fun aTimeOfAHostIsWrittenAsTheTimeOfARouter() {
        assertEquals("1970-01-01 00:00:00", utcText(0))
        assertEquals("2027-01-15 08:00:00", utcText(1_800_000_000))
        assertEquals("2026-10-27 09:12:01", utcText(1_793_092_321))
        assertEquals("2000-02-29 23:59:59", utcText(951_868_799))
        assertEquals("1969-12-31 23:59:59", utcText(-1))
    }
}
