package com.vasmarfas.networkhelm.routeros.api

import com.vasmarfas.networkhelm.model.Filter
import kotlin.test.Test
import kotlin.test.assertEquals

class ApiQueryTest {

    @Test
    fun singleConditions() {
        assertEquals(listOf("?list=blocked"), Filter.Equals("list", "blocked").apiQuery())
        assertEquals(listOf("?comment"), Filter.Has("comment").apiQuery())
        assertEquals(listOf("?-comment"), Filter.Lacks("comment").apiQuery())
        assertEquals(listOf("?<.id=*10"), Filter.Less(".id", "*10").apiQuery())
        assertEquals(listOf("?>bytes=1000"), Filter.Greater("bytes", "1000").apiQuery())
    }

    @Test
    fun nestedConditionsBecomeStackOperations() {
        val filter = Filter.And(
            listOf(
                Filter.Equals("list", "blocked"),
                Filter.Not(Filter.Or(listOf(Filter.Equals("address", "192.0.2.1"), Filter.Equals("address", "192.0.2.2")))),
            ),
        )
        assertEquals(listOf("?list=blocked", "?address=192.0.2.1", "?address=192.0.2.2", "?#|", "?#!", "?#&"), filter.apiQuery())
        assertEquals(listOf("?a=1", "?b=2", "?c=3", "?#||"), Filter.Or(listOf(Filter.Equals("a", "1"), Filter.Equals("b", "2"), Filter.Equals("c", "3"))).apiQuery())
        assertEquals(listOf("?a=1"), Filter.And(listOf(Filter.Equals("a", "1"))).apiQuery())
    }
}
