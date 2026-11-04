package com.vasmarfas.networkhelm.miknative

private val PRIME = limbsOf(0xFFFFFFEDL, 0xFFFFFFFFL, 0xFFFFFFFFL, 0xFFFFFFFFL, 0xFFFFFFFFL, 0xFFFFFFFFL, 0xFFFFFFFFL, 0x7FFFFFFFL)

private fun limbsOf(vararg words: Long) = IntArray(words.size) { words[it].toInt() }

internal class Fe private constructor(private val limbs: IntArray) {
    val isZero: Boolean get() = limbs.all { it == 0 }

    val isOdd: Boolean get() = limbs[0] and 1 != 0

    operator fun plus(other: Fe): Fe {
        val sum = limbs.copyOf()
        put(sum, other.limbs)
        if (!below(sum, PRIME)) take(sum, PRIME)
        return Fe(sum)
    }

    operator fun minus(other: Fe): Fe {
        val difference = limbs.copyOf()
        if (take(difference, other.limbs)) put(difference, PRIME)
        return Fe(difference)
    }

    operator fun unaryMinus(): Fe = ZERO - this

    operator fun times(other: Fe): Fe = Fe(fold(multiply(limbs, other.limbs)))

    fun square(): Fe = this * this

    fun inverse(): Fe {
        val (ones, eleven) = chain()
        return ones.squareTimes(5) * eleven
    }

    // p = 5 (mod 8), so a root is a^((p + 3) / 8) or that times the square root of -1
    fun squareRoot(): Fe? {
        val candidate = chain().first.squareTimes(2) * square()
        for (root in listOf(candidate, candidate * SQUARE_ROOT_OF_MINUS_ONE)) if (root.square() == this) return root
        return null
    }

    fun toBytes(): ByteArray = ByteArray(32) { (limbs[7 - it / 4] ushr (24 - 8 * (it % 4))).toByte() }

    override fun equals(other: Any?) = other is Fe && limbs.contentEquals(other.limbs)

    override fun hashCode() = limbs.contentHashCode()

    private fun squareTimes(count: Int): Fe {
        var power = this
        repeat(count) { power = power.square() }
        return power
    }

    private fun chain(): Pair<Fe, Fe> {
        val a2 = square()
        val a9 = a2.squareTimes(2) * this
        val a11 = a2 * a9
        val ones5 = a9 * a11.square()
        val ones10 = ones5.squareTimes(5) * ones5
        val ones20 = ones10.squareTimes(10) * ones10
        val ones40 = ones20.squareTimes(20) * ones20
        val ones50 = ones40.squareTimes(10) * ones10
        val ones100 = ones50.squareTimes(50) * ones50
        val ones200 = ones100.squareTimes(100) * ones100
        return ones200.squareTimes(50) * ones50 to a11
    }

    companion object {
        val ZERO = Fe(IntArray(LIMBS))
        val ONE = of(1)

        fun of(value: Long) = Fe(IntArray(LIMBS).also {
            it[0] = value.toInt()
            it[1] = (value ushr 32).toInt()
        })

        fun fromBytes(bytes: ByteArray): Fe {
            val value = U256.fromBytes(bytes).limbs.copyOf()
            while (!below(value, PRIME)) take(value, PRIME)
            return Fe(value)
        }

        private val SQUARE_ROOT_OF_MINUS_ONE = of(2).let { two -> two.chain().first.squareTimes(3) * two * two * two }
    }
}

// p = 2^255 - 19, so 2^256 = 38 (mod p): the upper half of a product comes back times 38
private fun fold(wide: IntArray): IntArray {
    val out = IntArray(LIMBS)
    var carry = 0L
    for (i in 0 until LIMBS) {
        val t = (wide[i].toLong() and MASK) + (wide[i + LIMBS].toLong() and MASK) * 38 + carry
        out[i] = t.toInt()
        carry = t ushr 32
    }
    var spill = carry * 38
    while (spill != 0L) {
        for (i in 0 until LIMBS) {
            val t = (out[i].toLong() and MASK) + spill
            out[i] = t.toInt()
            spill = t ushr 32
            if (spill == 0L) break
        }
        spill *= 38
    }
    while (!below(out, PRIME)) take(out, PRIME)
    return out
}
