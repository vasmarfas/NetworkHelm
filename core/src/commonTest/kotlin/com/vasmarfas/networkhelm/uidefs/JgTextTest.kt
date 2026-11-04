package com.vasmarfas.networkhelm.uidefs

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class JgTextTest {

    @Test
    fun theLiteralOfAnEntryReadsWithItsOrder() {
        val nodes = uiNodes(
            parseJg(
                "[{name:'Demo',group:'IP',prio:110,c:[{name:'Demo',title:'Demo list',type:'map',path:[43,1],nameval:'Name',c:[" +
                    "{name:'General',type:'tab'},{name:'Name',type:'string',id:'s2',width:100},{type:'separator'}," +
                    "{name:'Mode',type:'enm',id:'u4',values:{type:'static',map:{4294967295:'never',1:'one'}},ro:1,def:0}]}]}]",
            ),
        )
        val entry = nodes.single()
        assertEquals("Demo", entry.name)
        assertEquals("IP", entry.text("group"))
        assertEquals(110L, entry.number("prio"))
        val window = entry.children.single()
        assertEquals("map", window.kind)
        assertEquals("Demo list", window.label)
        assertEquals(listOf("tab", "string", "separator", "enm"), window.children.map { it.kind })
        assertEquals(listOf("General", "Name", null, "Mode"), window.children.map { it.name })
        val mode = window.children.last()
        assertTrue(mode.flag("ro"))
        assertEquals(false, mode.flag("def"))
        assertEquals(100L, window.children[1].number("width"))
        val values = mode.attrs.getValue("values")
        assertIs<JgObject>(values)
        assertEquals(setOf("type", "map"), values.fields.keys)
        assertEquals("never", ((values.fields.getValue("map") as JgObject).fields.getValue("4294967295") as JgString).text)
        assertEquals(listOf(43L, 1L), (window.attrs.getValue("path") as JgList).items.map { (it as JgNumber).value.toLong() })
    }

    @Test
    fun stringsKeepTheirEscapesAndNumbersAndWordsReadAsTheyAre() {
        val list = parseJg("['it\\'s','a\\nb\\u0041',\"two\",-12,3.5,0x1F,1e3,true,false,null,undefined,]") as JgList
        assertEquals(listOf("it's", "a\nbA", "two"), list.items.take(3).map { (it as JgString).text })
        assertEquals(listOf(-12.0, 3.5, 31.0, 1000.0), list.items.drop(3).take(4).map { (it as JgNumber).value })
        assertEquals(listOf(true, false), list.items.drop(7).take(2).map { (it as JgFlag).on })
        assertEquals(listOf(JgNone, JgNone), list.items.drop(9))
    }

    @Test
    fun aNodeOfAnotherShapeHasNoKindAndNoChildren() {
        val node = uiNodes(parseJg("[{title:'Marks'},{name:'X',c:[1,'two',{type:'tab',name:'T'}]}]"))
        assertNull(node[0].kind)
        assertEquals("Marks", node[0].label)
        assertEquals(listOf("tab"), node[1].children.map { it.kind })
    }

    @Test
    fun listsNestedWithoutEndAreRefusedBeforeTheStackIsGone() {
        assertFailsWith<IllegalArgumentException> { parseJg("[".repeat(100_000)) }
        assertIs<JgList>(parseJg("[".repeat(60) + "]".repeat(60)))
    }

    @Test
    fun textThatIsNoLiteralFailsAndSaysWhere() {
        for (broken in listOf("[{name:'a'", "[{name 'a'}]", "['open", "[{name:}]", "[1 2", "<!doctype html>")) {
            assertFailsWith<IllegalArgumentException>(broken) { parseJg(broken) }
        }
    }
}
