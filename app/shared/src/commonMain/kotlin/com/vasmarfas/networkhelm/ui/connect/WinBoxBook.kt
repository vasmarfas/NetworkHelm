package com.vasmarfas.networkhelm.ui.connect

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.vasmarfas.networkhelm.app.SavedRouter
import com.vasmarfas.networkhelm.winbox.WinBoxSession

@Stable
class WinBoxBook(val rows: List<Row>, val unusable: Int) {
    @Stable
    class Row(val session: WinBoxSession, val router: SavedRouter, val known: Boolean) {
        var ticked by mutableStateOf(!known)
    }

    var passwords by mutableStateOf(true)

    val withPassword get() = rows.count { it.session.password.isNotEmpty() }

    val chosen get() = rows.count { it.ticked }
}
