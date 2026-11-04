package com.vasmarfas.networkhelm.miknative

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

private fun hex(text: String) = text.chunked(2).map { it.toInt(16).toByte() }.toByteArray()

class NumbersTest {
    private class FieldVector(val a: String, val b: String, val reduced: String, val sum: String, val difference: String, val product: String, val inverse: String)

    private class ProductVector(val a: String, val b: String, val c: String, val result: String)

    private val fields = listOf(
        FieldVector(
            "15898b0e60e1e535f452c6050ca5dfe27dbb9a61c386c23f85047d7252e6ea24", "b30265a32827e09f679118bc29dc81de1c102d57d558a16bf7c5591108885407",
            "15898b0e60e1e535f452c6050ca5dfe27dbb9a61c386c23f85047d7252e6ea24", "488bf0b18909c5d55be3dec1368261c099cbc7b998df63ab7cc9d6835b6f3e3e",
            "6287256b38ba04968cc1ad48e2c95e0461ab6d09ee2e20d38d3f24614a5e95f7", "776529cb1a2e2feabebf19cac1430f1aefb9845aae3555a2038fc90c32169544",
            "299a6ae7065c12f73b4083f55f53f656f71a80c6cad05e2842cb1fa03f427754",
        ),
        FieldVector(
            "231457cd90b91ae0357b79d2a335ab940b3959cf0e012f2101996799909e9ed9", "618c17eb47e0c6a813593af505844063f492843184596026eba7da58a93d9a66",
            "231457cd90b91ae0357b79d2a335ab940b3959cf0e012f2101996799909e9ed9", "04a06fb8d899e18848d4b4c7a8b9ebf7ffcbde00925a8f47ed4141f239dc3952",
            "41883fe248d8543822223edd9db16b3016a6d59d89a7cefa15f18d40e7610460", "336db71e9a39ed2839a9ef781bf800149cdf09aa1293b7f678f25e17f30608b6",
            "404668f1d02b49868b46e0ba1b8eefae5bdb7c6fc947aaada525df01db9e9751",
        ),
        FieldVector(
            "03dfa8d7c81f4739522c197beb20f3a535ad37b6142b09db82635490190cee03", "faecff5e9c7abdc02267747e9d2b71f008ce1cb0b25793e36dc72fa0b9779cad",
            "03dfa8d7c81f4739522c197beb20f3a535ad37b6142b09db82635490190cee03", "7ecca836649a04f974938dfa884c65953e7b5466c6829dbef02a8430d2848ac3",
            "08f2a9792ba489792fc4a4fd4df581b52cdf1b0561d375f8149c24ef5f955130", "7b5a2a88ff43e1ef7e2cf22512b909a9a8207a78049b929d2f9372dde219532e",
            "15a6327a5f5957ac76b9f7b21f227bdeb1b7df3317e1bba5538d77a68f4a314e",
        ),
        FieldVector(
            "8cdc53d1862448c742573cf4cdc316aeb246c32000b1da0dd0392f4d5beb7165", "a25064bd2140dbe935f1000d5752fe570adc43d7417d0da7f4d632f0fc9ac432",
            "0cdc53d1862448c742573cf4cdc316aeb246c32000b1da0dd0392f4d5beb7178", "2f2cb88ea76524b078483d0225161505bd2306f7422ee7b5c50f623e588635bd",
            "6a8bef1464e36cde0c663ce776701857a76a7f48bf34cc65db62fc5c5f50ad20", "4d776c36b733a5d960af23041caab774bd87ba3d01b522f71d4f9a2034571368",
            "398afd7d3347a9dcbc5916d98b89dcfbaca81d649f4b2f5e2ac4a75b4750f210",
        ),
    )

    private val products = listOf(
        ProductVector(
            "d32821124de6e332f2e734eea75852df827e65018426c59d2ff6c46dba78dcc4", "6cd8938c4cc379582574124f5f96c34f03759b7142cac86ae092c331ea9c9b34",
            "d9d700d888c48b7b1711a2bb3cbdb1ba5801137624c989b8a09f89826a610b80", "05addd08077a1922d501c8dc70df57ae5183ff92b092ea55af3409c4a4c1cd69",
        ),
        ProductVector(
            "bff030c2c4379408a69169f220f32f844e072ac2efd1bfd3907db7ff804515e2", "e62b07e4ca6964081753679be51b9bc3778a8db339f151ebbf15a2b2a2beb82d",
            "0e84c11fe355796d8df2eb8758b33a9f4a5d9c0bfebc06964928b31efeb46917", "06f6c4d6ffc648a73def10a799a11e144aeee2b6f2d57d15570fe699556c56ac",
        ),
        ProductVector(
            "ae4bb884c49447a54a6b313adb855f58267b0244366e88ec0846651b81fd49d4", "a6b078f9552a4be67cf9ae3dff1f5909a185e319ce05e16bc42aea0b8a856ae1",
            "e0339bad37fe92cbd8a7e08e7644a8b46566ed8de7c23198f3fe4525a8d771b1", "0a8d955a6c03bf3fe2c20d76d5ef8e781c09cb5cd7e3876f386df1dae84c9894",
        ),
        ProductVector(
            "633ddcc2b7dfc42c5383e68d9a35e7f399a6e564fef343b55aa5f063010e3f08", "af1b011e45986657b24a46b7a73d1cf458fb6a47f73e00e3f8d7b1052cf8e28b",
            "cb072e3407caa2935ef686456eca1b39b2457596e487c68bcc80a77e6b7501ca", "0d00bbb20e271d3cb077506e582bc914dcfdf8d682024724c2cacf3c518fb760",
        ),
        ProductVector(
            "ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff", "ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff",
            "ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff", "0399411b7c309a3dceec73d217f5be671dfdb99197ff60ad252c438913f94dd1",
        ),
    )

    @Test
    fun fieldElementsAddSubtractMultiplyAndInvertAsIntegersDo() {
        for (vector in fields) {
            val a = Fe.fromBytes(hex(vector.a))
            val b = Fe.fromBytes(hex(vector.b))
            assertContentEquals(hex(vector.reduced), a.toBytes())
            assertContentEquals(hex(vector.sum), (a + b).toBytes())
            assertContentEquals(hex(vector.difference), (a - b).toBytes())
            assertContentEquals(hex(vector.product), (a * b).toBytes())
            assertContentEquals(hex(vector.inverse), a.inverse().toBytes())
        }
    }

    @Test
    fun theTopOfTheRangeWrapsAround() {
        assertEquals(Fe.of(37), Fe.fromBytes(ByteArray(32) { -1 }))
        val minusOne = -Fe.ONE
        assertContentEquals(hex("7fffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffec"), minusOne.toBytes())
        assertEquals(Fe.ZERO, minusOne + Fe.ONE)
        assertEquals(Fe.ONE, minusOne * minusOne)
        assertEquals(minusOne, Fe.ZERO - Fe.ONE)
    }

    @Test
    fun aNumberTimesItsInverseIsOne() {
        for (value in listOf(1L, 2L, 3L, 9L, 486662L, Long.MAX_VALUE)) {
            val number = Fe.of(value)
            assertEquals(Fe.ONE, number * number.inverse(), "inverse of $value")
        }
    }

    @Test
    fun aSquareHasARootAndAboutHalfOfTheNumbersHaveNone() {
        for (value in 2L..40L) {
            val square = Fe.of(value).square()
            assertEquals(square, assertNotNull(square.squareRoot(), "root of the square of $value").square())
        }
        val withRoot = (2L..40L).count { Fe.of(it).squareRoot() != null }
        assertTrue(withRoot in 10..30, "$withRoot of 39 numbers have a root")
        val minusOne = -Fe.ONE
        assertEquals(minusOne, assertNotNull(minusOne.squareRoot()).square())
    }

    @Test
    fun aNumberKnowsItsBits() {
        val number = U256.fromBytes(hex("0100"))
        assertEquals(9, number.bitLength)
        assertTrue(number.testBit(8))
        assertFalse(number.testBit(0))
        assertEquals(0, U256.fromBytes(ByteArray(32)).bitLength)
        assertEquals(256, U256.fromBytes(ByteArray(32) { -1 }).bitLength)
        assertFailsWith<IllegalArgumentException> { U256.fromBytes(ByteArray(33)) }
    }

    @Test
    fun aProductAndANumberAreReducedByTheModulus() {
        for (vector in products) {
            val result = mulAddMod(U256.fromBytes(hex(vector.a)), U256.fromBytes(hex(vector.b)), U256.fromBytes(hex(vector.c)), WCurve.order)
            assertEquals(U256.fromBytes(hex(vector.result)), result)
        }
        assertEquals(U256.of(5), mulAddMod(U256.of(7), U256.of(8), U256.of(9), U256.of(10)))
    }
}
