package com.vasmarfas.networkhelm.miknative

internal const val LIMBS = 8
internal const val MASK = 0xFFFFFFFFL

internal class U256(internal val limbs: IntArray) {
    val bitLength: Int
        get() {
            for (i in LIMBS - 1 downTo 0) if (limbs[i] != 0) return 32 * i + 32 - limbs[i].countLeadingZeroBits()
            return 0
        }

    fun testBit(index: Int): Boolean = (limbs[index / 32] ushr (index % 32)) and 1 != 0

    override fun equals(other: Any?) = other is U256 && limbs.contentEquals(other.limbs)

    override fun hashCode() = limbs.contentHashCode()

    companion object {
        fun of(vararg words: Long) = U256(IntArray(LIMBS) { words.getOrElse(it) { 0 }.toInt() })

        fun fromBytes(bytes: ByteArray): U256 {
            require(bytes.size <= 32) { "${bytes.size} bytes do not fit 256 bits" }
            val limbs = IntArray(LIMBS)
            for (i in bytes.indices) {
                val position = bytes.size - 1 - i
                limbs[position / 4] = limbs[position / 4] or ((bytes[i].toInt() and 0xFF) shl (8 * (position % 4)))
            }
            return U256(limbs)
        }
    }
}

// the modulus has to stay below 2^255, or the shift in remainder overflows 256 bits
internal fun mulAddMod(a: U256, b: U256, c: U256, modulus: U256): U256 {
    val wide = multiply(a.limbs, b.limbs)
    var carry = 0L
    for (i in wide.indices) {
        val t = (wide[i].toLong() and MASK) + (if (i < LIMBS) c.limbs[i].toLong() and MASK else 0L) + carry
        wide[i] = t.toInt()
        carry = t ushr 32
    }
    return U256(remainder(wide, modulus.limbs))
}

internal fun multiply(a: IntArray, b: IntArray): IntArray {
    val out = IntArray(2 * LIMBS)
    for (i in 0 until LIMBS) {
        val x = a[i].toLong() and MASK
        var carry = 0L
        for (j in 0 until LIMBS) {
            val t = x * (b[j].toLong() and MASK) + (out[i + j].toLong() and MASK) + carry
            out[i + j] = t.toInt()
            carry = t ushr 32
        }
        out[i + LIMBS] = carry.toInt()
    }
    return out
}

private fun remainder(wide: IntArray, modulus: IntArray): IntArray {
    val out = IntArray(LIMBS)
    for (bit in wide.size * 32 - 1 downTo 0) {
        var carry = (wide[bit / 32] ushr (bit % 32)) and 1
        for (i in 0 until LIMBS) {
            val top = out[i] ushr 31
            out[i] = (out[i] shl 1) or carry
            carry = top
        }
        if (!below(out, modulus)) take(out, modulus)
    }
    return out
}

internal fun below(a: IntArray, b: IntArray): Boolean {
    for (i in LIMBS - 1 downTo 0) {
        val x = a[i].toLong() and MASK
        val y = b[i].toLong() and MASK
        if (x != y) return x < y
    }
    return false
}

internal fun put(a: IntArray, b: IntArray) {
    var carry = 0L
    for (i in 0 until LIMBS) {
        val t = (a[i].toLong() and MASK) + (b[i].toLong() and MASK) + carry
        a[i] = t.toInt()
        carry = t ushr 32
    }
}

internal fun take(a: IntArray, b: IntArray): Boolean {
    var borrow = 0L
    for (i in 0 until LIMBS) {
        val t = (a[i].toLong() and MASK) - (b[i].toLong() and MASK) - borrow
        a[i] = t.toInt()
        borrow = t ushr 63
    }
    return borrow != 0L
}
