package com.vasmarfas.networkhelm.uidefs

import com.vasmarfas.networkhelm.model.MenuPath
import com.vasmarfas.networkhelm.packs.PackNativeRef
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UiLogTest {

    private val router = object : UiSchema {
        override suspend fun members(path: MenuPath) = when (path.toString()) {
            "" -> UiMembers(listOf("journal"), emptyList())
            "journal" -> UiMembers(emptyList(), listOf("print"))
            else -> null
        }

        override suspend fun properties(path: MenuPath) = if (path.toString() == "journal") listOf("time", "topics", "message") else emptyList()
    }

    private fun journal(style: String) = uiNodes(
        parseJg(
            "[{name:'Journal',c:[{name:'Journal Entry',title:'Journal',type:'map',path:[9,4],$style" + "ro:1,c:[" +
                "{name:'Time',type:'dateandtime',id:'u2',width:120},{name:'Buffer',type:'string',id:'s5',width:60}," +
                "{name:'Topics',type:'multinumber',id:'U7',width:115,c:[{type:'enm',values:{type:'dynamic',path:[9,3]}}]}," +
                "{name:'Message',type:'multilinestring',id:'s6',lines:6,width:350}]}," +
                "{type:'map',path:[9,3],nameval:'Name',c:[{name:'Name',type:'string',id:'sfe0010'}]}]}]",
        ),
    )

    @Test
    fun aWindowTheRouterCallsALogIsReadWholeFromTheObjectModelWithTheFieldsTheConsoleHasToo() = runTest {
        val pack = uiPacks(journal("logstyle:1,ordered:1,"), router, emptyMap(), natively = true).single()
        val native = assertNotNull(pack.paths.getValue("journal").native)
        assertTrue(native.log)
        assertEquals(listOf(9L, 4L), native.handler)
        assertEquals(listOf("time", "buffer", "topics", "message"), native.fields.map { it.name })
        assertEquals(listOf("dateandtime", "string", "multinumber", "multilinestring"), native.fields.map { it.kind })
        assertEquals(listOf(2, 5, 7, 6), native.fields.map { it.key })
        assertEquals(PackNativeRef(listOf(9L, 3L), listOf(0xFE0010)), native.fields[2].refers)
        assertFalse(native.edit || native.add || native.remove || native.move)
    }

    @Test
    fun aTableThatIsNoLogKeepsOnlyTheFieldsTheConsoleDoesNotPrint() = runTest {
        val pack = uiPacks(journal(""), router, emptyMap(), natively = true).single()
        val native = assertNotNull(pack.paths.getValue("journal").native)
        assertFalse(native.log)
        assertEquals(listOf("buffer"), native.fields.map { it.name })
    }

    @Test
    fun aRouterWithoutTheNativeChannelGetsNoSpecForTheLog() = runTest {
        val pack = uiPacks(journal("logstyle:1,"), router, emptyMap()).single()
        assertNull(pack.paths.getValue("journal").native)
    }
}
