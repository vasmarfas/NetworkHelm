package com.vasmarfas.networkhelm.uidefs

import com.vasmarfas.networkhelm.session.RouterFacts
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class ConditionsTest {
    private val chr = RouterFacts("CHR", "x86", "i386", listOf(0x24481C87, 0x20102010, 0x7FBF8C00, 0x00011000), 0x40, listOf("ipv6"))

    private fun answer(text: String, facts: RouterFacts = chr) = holds(parseJg(text), facts)

    @Test
    fun aCapabilityIsABitOfTheWordItsNumberDividesInto() {
        fun cap(number: Int) = answer("{type:'syscap',value:$number}")
        assertEquals(listOf(true, true), listOf(cap(52), cap(112)))
        assertEquals(listOf(false, false, false, false, false, false, false), listOf(cap(34), cap(42), cap(53), cap(54), cap(57), cap(18), cap(200)))
        assertEquals(false, answer("{type:'syscap',value:52}", chr.copy(capabilities = emptyList())))
    }

    @Test
    fun aBoardIsTheFamilyOrTheNameAndAPatternAsksTheNameOnly() {
        fun board(value: String, re: Int = 0) = answer("{type:'board',value:'$value',re:$re}")
        assertEquals(listOf(true, true, false), listOf(board("x86"), board("CHR"), board("RB4011")))
        assertEquals(listOf(true, false), listOf(board("^CH", 1), board("^x86", 1)))
        assertNull(board("(", 1))
    }

    @Test
    fun anArchitectureAndAQuickSetModeAndAnAddonAreNamesAndBitsOfTheFacts() {
        assertEquals(listOf(true, false), listOf(answer("{type:'arch',value:'i386'}"), answer("{type:'arch',value:'smips'}")))
        assertEquals(listOf(true, false), listOf(answer("{type:'quickset',bit:6}"), answer("{type:'quickset',bit:1}")))
        assertEquals(false, answer("{type:'quickset',bit:6}", chr.copy(quickSet = null)))
        assertEquals(listOf(true, false), listOf(answer("{type:'addon',value:'ipv6'}"), answer("{type:'addon',value:'wlan6'}")))
        assertEquals(listOf(true, true), listOf(answer("{type:'supportmsgid'}"), answer("{type:'winbox'}")))
    }

    @Test
    fun anAnswerIsLeftOpenWhereAConditionAsksAboutTheFieldsOfAForm() {
        assertEquals(true, answer("{type:'not',pred:{type:'arch',value:'smips'}}"))
        assertEquals(true, answer("{type:'or',pred:[{type:'syscap',value:34},{type:'syscap',value:52}]}"))
        assertEquals(false, answer("{type:'or',pred:[{type:'syscap',value:34},{type:'syscap',value:42}]}"))
        assertEquals(true, answer("{type:'or',pred:[{type:'number',value:[5]},{type:'syscap',value:52}]}"))
        assertNull(answer("{type:'or',pred:[{type:'syscap',value:34},{type:'number',value:[5]}]}"))
        assertNull(answer("{type:'not',pred:{type:'bool',value:1}}"))
        assertNull(answer("{type:'string',value:''}"))
    }

    @Test
    fun aWindowStaysWhereItsConditionHoldsAndThenNoLongerCarriesIt() {
        val nodes = uiNodes(parseJg("[{title:'A',type:'item',pred:{type:'quickset',bit:6}},{title:'B',type:'item',pred:{type:'quickset',bit:1}},{title:'C',type:'item'},{title:'D',type:'item',pred:{type:'number',value:[3]}}]"))
        assertEquals(listOf("A" to false, "C" to false), nodes.mapNotNull { it.settled(chr) }.map { it.text("title") to ("pred" in it.attrs) })
        assertEquals(listOf("C"), nodes.mapNotNull { it.settled(null) }.map { it.text("title") })
        assertEquals(listOf(false, true), listOf(nodes[0].shown(null), nodes[2].shown(null)))
        assertEquals(listOf(false, false), listOf(assertNotNull(nodes[0].settled(chr)).flag("ro"), assertNotNull(nodes[2].settled(chr)).flag("ro")))
    }
}
