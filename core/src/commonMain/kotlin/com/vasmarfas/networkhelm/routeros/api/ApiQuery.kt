package com.vasmarfas.networkhelm.routeros.api

import com.vasmarfas.networkhelm.model.Filter

fun Filter.apiQuery(): List<String> = when (this) {
    is Filter.Equals -> listOf("?$property=$value")
    is Filter.Has -> listOf("?$property")
    is Filter.Lacks -> listOf("?-$property")
    is Filter.Less -> listOf("?<$property=$value")
    is Filter.Greater -> listOf("?>$property=$value")
    is Filter.Not -> filter.apiQuery() + "?#!"
    is Filter.And -> combine(filters, '&')
    is Filter.Or -> combine(filters, '|')
}

private fun combine(filters: List<Filter>, operation: Char): List<String> {
    val words = filters.flatMap { it.apiQuery() }
    return if (filters.size < 2) words else words + ("?#" + operation.toString().repeat(filters.size - 1))
}
