package com.vasmarfas.networkhelm.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed interface Filter {
    @Serializable
    @SerialName("equals")
    data class Equals(val property: String, val value: String) : Filter

    @Serializable
    @SerialName("has")
    data class Has(val property: String) : Filter

    @Serializable
    @SerialName("lacks")
    data class Lacks(val property: String) : Filter

    @Serializable
    @SerialName("less")
    data class Less(val property: String, val value: String) : Filter

    @Serializable
    @SerialName("greater")
    data class Greater(val property: String, val value: String) : Filter

    @Serializable
    @SerialName("not")
    data class Not(val filter: Filter) : Filter

    @Serializable
    @SerialName("and")
    data class And(val filters: List<Filter>) : Filter

    @Serializable
    @SerialName("or")
    data class Or(val filters: List<Filter>) : Filter
}

fun Filter.matches(values: Map<String, String>): Boolean = when (this) {
    is Filter.Equals -> values[property] == value
    is Filter.Has -> property in values
    is Filter.Lacks -> property !in values
    is Filter.Less -> values[property]?.let { compare(it, value) < 0 } == true
    is Filter.Greater -> values[property]?.let { compare(it, value) > 0 } == true
    is Filter.Not -> !filter.matches(values)
    is Filter.And -> filters.all { it.matches(values) }
    is Filter.Or -> filters.any { it.matches(values) }
}

fun Filter.matches(item: Item): Boolean = matches(item.values + (".id" to item.id))

private fun compare(a: String, b: String): Int {
    val x = a.toDoubleOrNull()
    val y = b.toDoubleOrNull()
    return if (x != null && y != null) x.compareTo(y) else a.compareTo(b)
}
