package com.vasmarfas.networkhelm.app

import com.vasmarfas.networkhelm.openwrt.OpenWrtLogin
import com.vasmarfas.networkhelm.openwrt.QuickJsForms
import com.vasmarfas.networkhelm.session.DeviceSession
import com.vasmarfas.networkhelm.session.connectOpenWrtWithSsh
import com.vasmarfas.networkhelm.transport.TrustPolicy

suspend fun connectOpenWrtWithForms(login: OpenWrtLogin, trust: TrustPolicy): DeviceSession = connectOpenWrtWithSsh(login, trust, ::QuickJsForms)
