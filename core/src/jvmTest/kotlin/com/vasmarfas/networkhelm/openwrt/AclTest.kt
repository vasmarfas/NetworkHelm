package com.vasmarfas.networkhelm.openwrt

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

class AclTest {

    @Test
    fun thePackageForOpenWrtCarriesTheAclOfTheApp() {
        assertEquals(ACL, File("../app/webApp/openwrt/networkhelm.json").readText().trim())
    }
}
