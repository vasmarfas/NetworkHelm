package com.vasmarfas.networkhelm.uidefs

import com.vasmarfas.networkhelm.model.MenuPath
import com.vasmarfas.networkhelm.packs.Pack
import com.vasmarfas.networkhelm.packs.PackPath
import com.vasmarfas.networkhelm.packs.PackTab
import com.vasmarfas.networkhelm.packs.PackWindow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UiMenusTest {

    private class Router(val menus: Map<String, Pair<UiMembers, List<String>>>) : UiSchema {
        override suspend fun members(path: MenuPath) = menus[path.toString()]?.first

        override suspend fun properties(path: MenuPath) = menus[path.toString()]?.second.orEmpty()
    }

    private fun members(menus: String = "", commands: String = "") = UiMembers(menus.split(' ').filter { it.isNotEmpty() }, commands.split(' ').filter { it.isNotEmpty() })

    private val router = Router(
        mapOf(
            "" to (members("ip") to emptyList()),
            "ip" to (members("gadget bindings") to emptyList()),
            "ip/gadget" to (members("", "print set add remove") to listOf("name", "address", "mac-address", "interface", "disabled")),
            "ip/bindings" to (members("", "print set add remove") to listOf("name", "address", "mac-address")),
        ),
    )

    private fun entries(text: String) = uiNodes(parseJg(text))

    private val probe = Pack(
        menu = listOf("Tools", "Probe"),
        window = PackWindow("Probe", listOf(PackTab("Probe", "tool/probe"))),
        paths = mapOf("tool/probe" to PackPath()),
    )

    private fun gadgets(items: String, others: String = "") = "[{name:'Gadgets',group:'IP',c:[{name:'Gadget List',title:'Gadgets',type:'map',path:[1,1],nameval:'Name',c:[" +
        "{name:'Name',type:'string',id:'sfe0010',width:100},{name:'Address',type:'network',id:'u1',width:100},{name:'MAC Address',type:'macaddr',id:'u2',width:100},{name:'Interface',type:'enm',id:'u3',width:80},$items]}," +
        "{name:'Binding',title:'Bindings',type:'map',path:[1,2],nameval:'Serial',c:[{name:'Name',type:'string',id:'sfe0010',width:100}$others]}]}]"

    private suspend fun menu(items: String, bundled: List<Pack> = listOf(probe)) =
        uiPacks(entries(gadgets(items)), router, emptyMap(), bundled = bundled).single().paths.getValue("ip/gadget").menu

    @Test
    fun aFieldWithoutASourceTakesTheRowByTheLabelThatTheTableNamesItByUnlessThatIsName() = runTest {
        val others = ",{name:'Probe',type:'contextmenu',group:'Tools',link:[{dst:'Unit'},{dst:'Link',src:'Name'}],open:'Probe',tab:'Probe'}"
        val path = uiPacks(entries(gadgets("{type:'separator'}", others)), router, emptyMap(), bundled = listOf(probe)).single().paths.getValue("ip/bindings")
        assertEquals(listOf("Unit" to "Serial", "Link" to "Name"), path.menu.single().fill.map { it.to to it.from })
    }

    @Test
    fun anItemOpensTheTabItNamesAndFillsItsFieldsFromTheColumnsOfTheRow() = runTest {
        val menu = menu("{name:'Probe',type:'contextmenu',autostart:1,group:'Tools',link:[{dst:'Probe Target',src:'Address'},{dst:'Link',src:'Interface'},{dst:'Self'}],open:'Probe',tab:'Probe'}")
        val item = menu.single()
        assertEquals("Probe" to "tool/probe", item.title to item.path)
        assertEquals(listOf("Probe Target" to "Address", "Link" to "Interface", "Self" to null), item.fill.map { it.to to it.from })
        assertTrue(item.autostart)
        assertEquals(listOf(false, false), listOf(item.global, item.item))
    }

    @Test
    fun anItemWithNothingToFillAndNoAutostartKeepsItsPlainWindow() = runTest {
        val item = menu("{name:'Probe',type:'contextmenu',group:'Tools',link:[],open:'Probe',tab:'Probe'}").single()
        assertEquals(emptyList(), item.fill)
        assertEquals(false, item.autostart)
    }

    @Test
    fun anItemThatFillsATableOpensANewRowOfThatTableAndANamelessGroupIsTheEntryOfTheWindow() = runTest {
        val menu = menu(
            "{name:'Make Binding',type:'contextmenu',group:'IP',link:[{dst:'Name',src:'Name'},{dst:'Address',src:'Address'}],open:'Gadgets',tab:'Bindings'}," +
                "{name:'Bindings',type:'contextmenu',group:'IP',link:[],open:'Gadgets',tab:'Bindings'}",
        )
        assertEquals(listOf("Make Binding" to true, "Bindings" to false), menu.map { it.title to it.item })
        assertEquals(listOf("ip/bindings"), menu.map { it.path }.distinct())
    }

    @Test
    fun anItemWithAConditionForSeveralRowsAnUnknownConversionOrAnUnknownConstantIsLeftOut() = runTest {
        val menu = menu(
            "{name:'Probe',type:'contextmenu',group:'Tools',link:[],open:'Probe',tab:'Probe'}," +
                "{name:'Conditional',type:'contextmenu',group:'Tools',on:'caps',link:[],open:'Probe',tab:'Probe'}," +
                "{name:'Several',type:'contextmenu',group:'Tools',mergeobjs:1,link:[],open:'Probe',tab:'Probe'}," +
                "{name:'Vector',type:'contextmenu',group:'Tools',link:[{conv:'u32vector_u32',dst:'Link',src:'Interface'}],open:'Probe',tab:'Probe'}," +
                "{name:'Type',type:'contextmenu',group:'Tools',link:[{conv:'put',dst:'Type',val:3}],open:'Probe',tab:'Probe'}",
        )
        assertEquals(listOf("Probe"), menu.map { it.title })
    }

    @Test
    fun aFilterThatIsPutOffAddsNoFieldAndTheConversionsThatHandTheValueOverKeepTheirFields() = runTest {
        val item = menu(
            "{name:'Probe',type:'contextmenu',group:'Tools',link:[{dst:'Link',src:'Interface'},{conv:'u32_network',dst:'Source',src:'Address'},{bool:1,conv:'put',dst:'Protocol ',val:0}],open:'Probe',tab:'Probe'}",
        ).single()
        assertEquals(listOf("Link", "Source"), item.fill.map { it.to })
    }

    @Test
    fun theTypeOfATelnetItemTellsWhichWindowOfTheClientItIsAndNoPathIsNeeded() = runTest {
        val menu = menu(
            "{name:'Telnet',type:'contextmenu',autostart:1,group:'Tools',link:[{conv:'put',dst:'Type',val:1},{dst:'Address',src:'Address'}],open:'Telnet',tab:'Telnet'}," +
                "{name:'MAC Telnet',type:'contextmenu',autostart:1,group:'Tools',link:[{conv:'put',dst:'Type',val:3},{dst:'MAC Address',src:'MAC Address'}],open:'Telnet',tab:'Telnet'}," +
                "{name:'Other Telnet',type:'contextmenu',group:'Tools',link:[{conv:'put',dst:'Type',val:2},{dst:'Address',src:'Address'}],open:'Telnet',tab:'Telnet'}",
        )
        assertEquals(listOf("Telnet" to "telnet", "MAC Telnet" to "mac-telnet"), menu.map { it.title to it.window })
        assertEquals(listOf("Address" to "Address"), menu[0].fill.map { it.to to it.from })
        assertEquals(listOf("MAC Address" to "MAC Address"), menu[1].fill.map { it.to to it.from })
        assertEquals(listOf("", ""), menu.map { it.path })
    }

    @Test
    fun anItemWhoseWindowNobodyKnowsIsLeftOutAndAGlobalButtonStaysGlobal() = runTest {
        val menu = menu(
            "{name:'Away',type:'contextmenu',group:'Tools',link:[],open:'Nowhere',tab:'Nowhere'}," +
                "{name:'Wrong Tab',type:'contextmenu',group:'Tools',link:[],open:'Probe',tab:'Elsewhere'}," +
                "{name:'Everywhere',type:'contextmenu',globalbutton:1,group:'Tools',link:[],open:'Probe',tab:'Probe'}",
        )
        assertEquals(listOf("Everywhere"), menu.map { it.title })
        assertTrue(menu.single().global)
    }

    @Test
    fun withoutTheBundledPacksOnlyTheWindowsOfTheRouterAreTargets() = runTest {
        val items = "{name:'Probe',type:'contextmenu',group:'Tools',link:[],open:'Probe',tab:'Probe'},{name:'Bindings',type:'contextmenu',group:'IP',link:[],open:'Gadgets',tab:'Bindings'}"
        assertEquals(listOf("Bindings"), menu(items, emptyList()).map { it.title })
    }

    @Test
    fun theItemsOfARouterMakeItThroughTheMergeWithABundledPackAndThroughTheStoredFile() = runTest {
        val made = uiPacks(entries(gadgets("{name:'Probe',type:'contextmenu',group:'Tools',link:[{dst:'Link',src:'Interface'}],open:'Probe',tab:'Probe'}")), router, emptyMap(), bundled = listOf(probe))
        val bundled = Pack(
            menu = listOf("IP", "Gadgets"),
            window = PackWindow("Gadgets", listOf(PackTab("Gadgets", "ip/gadget"))),
            paths = mapOf("ip/gadget" to PackPath(columns = listOf("name"))),
        )
        val merged = routerFirst(listOf(bundled), made).single()
        assertEquals(listOf("Probe"), merged.paths.getValue("ip/gadget").menu.map { it.title })
        val again = assertNotNull(decodeUiPacks(encodeUiPacks(listOf(merged)))).single().paths.getValue("ip/gadget").menu.single()
        assertEquals(listOf("Link" to "Interface"), again.fill.map { it.to to it.from })
        assertEquals("tool/probe", again.path)
        assertNull(again.window)
    }
}
