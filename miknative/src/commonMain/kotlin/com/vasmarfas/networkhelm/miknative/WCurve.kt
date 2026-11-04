package com.vasmarfas.networkhelm.miknative

internal class WPoint(val x: Fe, val y: Fe)

// Curve25519 as y^2 = x^3 + ax + b, the form the router multiplies on; the wire carries the Montgomery x
// of a point, which is the Weierstrass x less A/3; the point at infinity is null
internal object WCurve {
    val order = U256.of(0x5CF5D3EDL, 0x5812631AL, 0xA2F79CD6L, 0x14DEF9DEL, 0, 0, 0, 0x10000000L)

    private val montgomeryA = Fe.of(486662)
    private val three = Fe.of(3)
    private val inverseOfThree = three.inverse()
    private val shift = montgomeryA * inverseOfThree

    val a: Fe = Fe.ONE - montgomeryA * montgomeryA * inverseOfThree

    val generator: WPoint = checkNotNull(lift(Fe.of(9), odd = false))

    fun lift(montgomeryX: Fe, odd: Boolean): WPoint? {
        val ySquared = montgomeryX * montgomeryX * montgomeryX + montgomeryA * montgomeryX * montgomeryX + montgomeryX
        val root = ySquared.squareRoot() ?: return null
        return WPoint(montgomeryX + shift, if (root.isOdd == odd) root else -root)
    }

    fun montgomeryX(point: WPoint): ByteArray = (point.x - shift).toBytes()

    fun add(left: WPoint?, right: WPoint?): WPoint? {
        if (left == null) return right
        if (right == null) return left
        val slope = if (left.x == right.x) {
            if ((left.y + right.y).isZero) return null
            (three * left.x * left.x + a) * (left.y + left.y).inverse()
        } else {
            (right.y - left.y) * (right.x - left.x).inverse()
        }
        val x = slope * slope - left.x - right.x
        return WPoint(x, slope * (left.x - x) - left.y)
    }

    fun multiply(point: WPoint, scalar: U256): WPoint? {
        var result: WPoint? = null
        for (bit in scalar.bitLength - 1 downTo 0) {
            result = add(result, result)
            if (scalar.testBit(bit)) result = add(result, point)
        }
        return result
    }
}
