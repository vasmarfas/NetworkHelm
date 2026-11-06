package com.vasmarfas.networkhelm.snmp

import com.vasmarfas.networkhelm.model.DeviceException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

// rows: of each column, as many as show that the table is there; the window reads the rest. raw: varbinds of one subtree the dictionary does not know
internal class WalkBudget(val bindings: Int = 20_000, val time: Duration = 12.seconds, val rows: Int = 20, val raw: Int = 500)

internal class Discovery(
    val scalars: Map<MibObject, SnmpValue>,
    val tables: Map<MibObject, Set<MibObject>>,
    val unknown: Set<Oid>,
    val complete: Boolean,
)

internal val WALKED = listOf(oid("1.3.6.1.2.1"), SnmpMib.ENTERPRISES, oid("1.3.6.1.6.3"))

// a subidentifier is at most 2^32 - 1, so this OID comes after every instance under the object it extends
internal const val LAST_ARC = 0xFFFF_FFFFL

// what a budget leaves unread stays out, and complete says so; an agent that answers an OID not past the last one ends the walk of that root
internal suspend fun SnmpClient.discover(mib: SnmpMib, budget: WalkBudget = WalkBudget(), time: TimeSource = TimeSource.Monotonic, roots: List<Oid> = WALKED): Discovery {
    val started = time.markNow()
    val scalars = linkedMapOf<MibObject, SnmpValue>()
    val tables = linkedMapOf<MibObject, MutableSet<MibObject>>()
    val rows = mutableMapOf<MibObject, Int>()
    val unknown = linkedMapOf<Oid, Int>()
    var count = 0
    var complete = true
    walk@ for (root in roots) {
        var at = root
        next@ while (true) {
            if (count >= budget.bindings || started.elapsedNow() >= budget.time) {
                complete = false
                break@walk
            }
            val answer = try {
                after(at)
            } catch (e: DeviceException) {
                if (count == 0) throw e
                complete = false
                break@walk
            }
            if (answer.isEmpty()) continue@walk
            for (binding in answer) {
                if (binding.value is SnmpValue.Missing || !binding.oid.under(root) || binding.oid <= at) continue@walk
                at = binding.oid
                count++
                val known = mib.find(binding.oid)
                val entry = known?.takeIf { it.kind == MibKind.Column }?.let { mib[Oid(it.oid.arcs.dropLast(1))] }
                when {
                    known != null && known.kind == MibKind.Scalar && binding.oid == known.oid + listOf(0L) -> scalars[known] = binding.value
                    known != null && entry != null -> {
                        tables.getOrPut(entry) { linkedSetOf() } += known
                        val seen = (rows[known] ?: 0) + 1
                        rows[known] = seen
                        if (seen >= budget.rows) {
                            at = known.oid + listOf(LAST_ARC)
                            continue@next
                        }
                    }
                    else -> {
                        val subtree = subtreeOf(binding.oid, known)
                        val seen = (unknown[subtree] ?: 0) + 1
                        unknown[subtree] = seen
                        if (seen >= budget.raw) {
                            at = subtree + listOf(LAST_ARC)
                            continue@next
                        }
                    }
                }
            }
        }
    }
    return Discovery(scalars, tables, unknown.keys, complete)
}

// one arc below the deepest object the dictionary knows, and under enterprises one below the number of the maker
private fun subtreeOf(oid: Oid, known: MibObject?): Oid {
    val vendor = if (oid.under(SnmpMib.ENTERPRISES)) SnmpMib.ENTERPRISES.arcs.size + 2 else 0
    val depth = maxOf((known?.oid?.arcs?.size ?: 0) + 1, vendor)
    return Oid(oid.arcs.take(minOf(depth, oid.arcs.size - 1)))
}
