package com.vasmarfas.networkhelm.uidefs

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class UiFormTest {

    private fun window(text: String) = uiNodes(parseJg("[$text]")).single()

    @Test
    fun theFieldsOfAnAutosetAreFieldsOfTheWindow() {
        val form = uiForm(window("{title:'Update',type:'item',c:[{type:'autoset',c:[{name:'Channel',type:'enm',id:'u1'}]},{name:'Mode',type:'enm',id:'u2'}]}"))
        assertEquals(listOf("Channel", "Mode"), form.tabs.single().items.map { it?.label })
        assertEquals(listOf(true, false), form.tabs.single().items.map { it?.autoset })
    }

    @Test
    fun aWindowWithoutTabsStaysOnePageWithItsLinesBetweenTheGroups() {
        val form = uiForm(
            window(
                "{title:'Update',type:'item',c:[{type:'autoset',c:[{name:'Channel',type:'enm',id:'u1'}]},{type:'autoset',c:[{name:'Mode',type:'enm',id:'u2'}]},{type:'separator'}," +
                    "{name:'Installed Version',type:'string',id:'s1',ro:1},{name:'Latest Version',type:'string',id:'s2',ro:1},{type:'separator'}," +
                    "{name:'',type:'multilinestring',id:'s4',ro:1},{type:'statusbar',c:[{type:'string',id:'s3'}]}]}",
            ),
        )
        assertEquals(listOf("General"), form.tabs.map { it.title })
        assertEquals(listOf("Channel", "Mode", null, "Installed Version", "Latest Version", null, ""), form.tabs.single().items.map { it?.label })
    }

    @Test
    fun theButtonsOfOneToolbarShareARowAndASeparatorBetweenToolbarsLeavesAGap() {
        val form = uiForm(
            window(
                "{title:'Quick Set',type:'item',c:[{name:'System',type:'tab',inline:1},{name:'Router Identity',type:'string',id:'s1'}," +
                    "{type:'toolbar',c:[{name:'Check For Updates',type:'contextmenu',open:'Packages'},{name:'Reboot',type:'contextmenu',open:'Reboot'},{type:'contextmenu'}," +
                    "{name:'Reset Configuration',type:'contextmenu',open:'Reset Configuration'}]},{type:'separator'}," +
                    "{type:'toolbar',c:[{name:'Password...',type:'contextmenu',open:'Password'}]},{type:'toolbar',c:[{name:'Again',type:'contextmenu',open:'Again'}]}]}",
            ),
        )
        assertEquals(listOf("Check For Updates", "Reboot", "Reset Configuration", "Password...", "Again"), form.buttons.map { it.label })
        assertEquals(listOf(1, 1, 1, 3, 4), form.buttons.map { it.row })
        assertTrue(form.buttons.all { it.below?.name == "Router Identity" })
    }

    @Test
    fun theFlagsAndTheSwitchOfAListKeepTheirLetters() {
        val form = uiForm(
            window(
                "{title:'Devices',type:'map',c:[{type:'enable'},{type:'dynamic'},{name:'controller',type:'flag',id:'b1',hint:'L'},{name:'upgrade available',type:'flag',id:'b2',hint:'U'}," +
                    "{name:'nameless',type:'flag',id:'b3'},{name:'Identity',type:'string',id:'s9',width:100}]}",
            ),
        )
        assertEquals(listOf("disabled" to "X", "dynamic" to "D", "controller" to "L", "upgrade available" to "U"), form.flags.map { it.name to it.letter })
    }

    @Test
    fun aFieldStandsWhereTheConditionOfItsObjectAndOfTheObjectsAroundItHold() {
        val form = uiForm(
            window(
                "{title:'Quick Set',type:'item',c:[{name:'Mode',type:'enm',id:'u1'},{type:'cond',name:'router',c:[{on:'Mode',pred:{type:'number',value:1}}]}," +
                    "{type:'object',on:'router',c:[{name:'Uplink',type:'enm',id:'u2'},{type:'cond',name:'wan',c:[{on:'Uplink',pred:{type:'number',value:[0,2]}}]}," +
                    "{name:'Address',type:'ipaddr',id:'u3',on:'wan'},{name:'Gateway',type:'ipaddr',id:'u4'}]}]}",
            ),
        )
        val fields = form.tabs.single().items.filterNotNull().associateBy { it.label }
        assertEquals(listOf("Mode", "Uplink", "Address", "Gateway"), fields.keys.toList())
        assertEquals(emptyList(), fields.getValue("Mode").needs.map { it.controller.name })
        assertEquals(listOf("Mode"), fields.getValue("Uplink").needs.map { it.controller.name })
        assertEquals(listOf("Mode", "Uplink"), fields.getValue("Address").needs.map { it.controller.name })
        assertEquals(listOf("Mode"), fields.getValue("Gateway").needs.map { it.controller.name })
    }

    @Test
    fun theFieldsOfATabStandWhereTheConditionOfTheTabHolds() {
        val form = uiForm(
            window(
                "{title:'Quick Set',type:'item',c:[{name:'Mode',type:'enm',id:'u1'},{type:'cond',name:'routing',c:[{on:'Mode',pred:{type:'number',value:[1]}}]}," +
                    "{name:'Internet',type:'tab',on:'routing'},{name:'Port',type:'enm',id:'u2'},{name:'VPN',type:'tab'},{name:'Access',type:'bool',id:'b3'}]}",
            ),
        )
        val fields = form.tabs.flatMap { it.items }.filterNotNull().associateBy { it.label }
        assertEquals(listOf("Mode"), fields.getValue("Port").needs.map { it.controller.name })
        assertEquals(emptyList(), fields.getValue("Access").needs)
    }

    @Test
    fun theButtonsOfAWindowKeepTheNumbersOfTheirCommandsAndWhereTheyStand() {
        val form = uiForm(
            window(
                "{title:'Quick Set',type:'item',c:[{name:'Mode',type:'enm',id:'u1'},{type:'cond',name:'auto',c:[{on:'Mode',pred:{type:'number',value:[1]}}]}," +
                    "{name:'Address',type:'tuple',on:'auto',c:[{type:'ipaddr',id:'u30'},{type:'toolbar',c:[{name:'Renew',type:'doit',cmd:11},{name:'Release',type:'doit',cmd:12,confirm:'Sure?'}]}]}," +
                    "{type:'toolbar',c:[{name:'Run',type:'doit',cmd:14}]}]}",
            ),
        )
        assertEquals(listOf("Renew" to 11, "Release" to 12, "Run" to 14), form.doits.map { it.title to it.number })
        assertEquals(listOf(false, true, false), form.doits.map { it.confirm })
        assertEquals(listOf("General", "General", "General"), form.doits.map { it.tab })
        assertEquals(listOf(listOf("Mode"), listOf("Mode"), emptyList()), form.doits.map { doit -> doit.needs.map { it.controller.name } })
    }

    @Test
    fun theButtonsThatOpenAnotherEntryKeepTheEntryAndItsGroup() {
        val form = uiForm(
            window(
                "{title:'Quick Set',type:'item',c:[{name:'Mode',type:'enm',id:'u1'},{type:'toolbar',c:[{name:'Reboot',type:'contextmenu',group:'System',open:'Reboot',tab:'Reboot'}," +
                    "{name:'Upgrade',type:'contextmenu',globalbutton:1,open:'Upgrades'}]}]}",
            ),
        )
        assertEquals(listOf(Triple("Reboot", "Reboot", "System")), form.buttons.map { Triple(it.label, it.entry, it.group) })
        assertEquals(listOf("Upgrade"), form.links.map { it.label })
    }

    @Test
    fun aTabMarkedInlineIsASectionOfTheWindowAndAnotherTabStaysATab() {
        val form = uiForm(
            window(
                "{title:'Quick Set',type:'item',c:[{name:'Configuration',type:'tab',inline:1},{name:'Mode',type:'enm',id:'u1'},{name:'VPN',type:'tab',inline:1},{name:'Access',type:'bool',id:'b3'}," +
                    "{name:'Extra',type:'tab'},{name:'Note',type:'string',id:'s4'}]}",
            ),
        )
        assertEquals(listOf("Configuration" to true, "VPN" to true, "Extra" to false), form.tabs.map { it.title to it.inline })
    }

    @Test
    fun aButtonStandsBesideTheTupleThatHoldsItAndUnderTheFieldItsToolbarFollows() {
        val form = uiForm(
            window(
                "{title:'Quick Set',type:'item',c:[{name:'Address',type:'tuple',c:[{type:'ipaddr',id:'u30',ro:1},{type:'toolbar',c:[{name:'Renew',type:'doit',cmd:11}," +
                    "{name:'Check',type:'contextmenu',open:'Check'}]}]},{name:'NAT',type:'bool',id:'b9'},{type:'toolbar',c:[{name:'Run',type:'doit',cmd:14},{name:'Port Mapping',type:'contextmenu',open:'Port Mapping'}]}," +
                    "{name:'System',type:'tab',inline:1},{type:'toolbar',c:[{name:'Reset',type:'doit',cmd:15}]}]}",
            ),
        )
        val fields = form.tabs.first().items.filterNotNull().associate { it.label to it.node }
        val doits = form.doits.associateBy { it.title }
        assertSame(fields.getValue("Address"), doits.getValue("Renew").beside)
        assertNull(doits.getValue("Renew").below)
        assertNull(doits.getValue("Run").beside)
        assertSame(fields.getValue("NAT"), doits.getValue("Run").below)
        assertNull(doits.getValue("Reset").below)
        val buttons = form.buttons.associateBy { it.label }
        assertSame(fields.getValue("Address"), buttons.getValue("Check").beside)
        assertSame(fields.getValue("NAT"), buttons.getValue("Port Mapping").below)
    }

    @Test
    fun aButtonOfAToolbarStandsWhereTheConditionOfThatToolbarHolds() {
        val form = uiForm(
            window(
                "{title:'Quick Set',type:'item',c:[{name:'NAT',type:'bool',id:'b9'},{type:'cond',name:'nat',c:[{on:'NAT',pred:{type:'bool',value:1}}]}," +
                    "{type:'toolbar',on:'nat',c:[{name:'Port Mapping',type:'contextmenu',open:'Port Mapping'}]},{type:'toolbar',c:[{name:'Reboot',type:'contextmenu',open:'Reboot'}]}]}",
            ),
        )
        assertEquals(listOf(listOf("NAT"), emptyList()), form.buttons.map { button -> button.needs.map { it.controller.name } })
    }

    @Test
    fun aWindowThatHasTheActiveLabelSaysSo() {
        assertTrue(uiForm(window("{title:'Quick Set',type:'item',c:[{name:'Mode',type:'enm',id:'u1'},{type:'active'}]}")).active)
        assertFalse(uiForm(window("{title:'Quick Set',type:'item',c:[{name:'Mode',type:'enm',id:'u1'}]}")).active)
    }

    @Test
    fun aConditionThatNamesAFieldTheWindowDoesNotHaveAsksNothing() {
        val form = uiForm(window("{title:'Update',type:'item',c:[{type:'cond',name:'late',c:[{on:'Missing',pred:{type:'number',value:1}}]},{name:'Channel',type:'enm',id:'u1',on:'late'}]}"))
        assertEquals(emptyList(), form.tabs.single().items.filterNotNull().single().needs)
    }
}
