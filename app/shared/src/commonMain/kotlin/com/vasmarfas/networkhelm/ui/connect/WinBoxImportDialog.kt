package com.vasmarfas.networkhelm.ui.connect

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.composeunstyled.Text
import com.vasmarfas.networkhelm.ui.kit.Button
import com.vasmarfas.networkhelm.ui.kit.Checkbox
import com.vasmarfas.networkhelm.ui.kit.ListScrollBar
import com.vasmarfas.networkhelm.ui.kit.LocalCompact
import com.vasmarfas.networkhelm.ui.kit.Toggle
import com.vasmarfas.networkhelm.ui.kit.hand
import com.vasmarfas.networkhelm.ui.kit.scrim
import com.vasmarfas.networkhelm.ui.theme.Theme
import networkhelm.app.shared.generated.resources.Res
import networkhelm.app.shared.generated.resources.connect_address
import networkhelm.app.shared.generated.resources.connect_comment
import networkhelm.app.shared.generated.resources.connect_group
import networkhelm.app.shared.generated.resources.connect_protocol
import networkhelm.app.shared.generated.resources.connect_user
import networkhelm.app.shared.generated.resources.list_cancel
import networkhelm.app.shared.generated.resources.winbox_import
import networkhelm.app.shared.generated.resources.winbox_intro
import networkhelm.app.shared.generated.resources.winbox_known
import networkhelm.app.shared.generated.resources.winbox_new
import networkhelm.app.shared.generated.resources.winbox_no_passwords
import networkhelm.app.shared.generated.resources.winbox_passwords
import networkhelm.app.shared.generated.resources.winbox_passwords_hint
import networkhelm.app.shared.generated.resources.winbox_status
import networkhelm.app.shared.generated.resources.winbox_title
import networkhelm.app.shared.generated.resources.winbox_unusable
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun WinBoxImportDialog(book: WinBoxBook, onCancel: () -> Unit, onImport: () -> Unit) {
    val palette = Theme.palette
    val shape = RoundedCornerShape(Theme.metrics.radius)
    Box(Modifier.fillMaxSize().scrim(palette.workspace.copy(alpha = 0.6f)).testTag("winbox-import"), contentAlignment = Alignment.Center) {
        Column(
            Modifier.padding(12.dp).widthIn(max = 800.dp).heightIn(max = 560.dp).background(palette.surface, shape).border(1.dp, palette.line, shape).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(stringResource(Res.string.winbox_title), style = Theme.type.strong)
            Text(stringResource(Res.string.winbox_intro, book.rows.size), color = palette.muted)
            Sessions(book, Modifier.weight(1f, fill = false))
            if (book.unusable > 0) Text(stringResource(Res.string.winbox_unusable, book.unusable), color = palette.muted, style = Theme.type.small)
            val withPassword = book.withPassword
            Toggle(
                on = book.passwords && withPassword > 0,
                title = stringResource(Res.string.winbox_passwords),
                hint = if (withPassword > 0) stringResource(Res.string.winbox_passwords_hint, withPassword) else stringResource(Res.string.winbox_no_passwords),
                tag = "winbox-passwords",
                enabled = withPassword > 0,
            ) { book.passwords = it }
            Row(Modifier.align(Alignment.End).padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(stringResource(Res.string.list_cancel), onCancel, Modifier.testTag("winbox-cancel"))
                Button(stringResource(Res.string.winbox_import, book.chosen), onImport, Modifier.testTag("winbox-ok"), enabled = book.chosen > 0, primary = true)
            }
        }
    }
}

@Composable
private fun Sessions(book: WinBoxBook, modifier: Modifier) {
    val palette = Theme.palette
    val compact = LocalCompact.current
    val list = rememberLazyListState()
    Column(modifier.fillMaxWidth().border(1.dp, palette.line)) {
        Row(
            Modifier.fillMaxWidth().background(palette.raised).heightIn(min = Theme.metrics.row).padding(horizontal = 8.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Checkbox(book.rows.isNotEmpty() && book.rows.all { it.ticked }, { all -> book.rows.forEach { it.ticked = all } }, Modifier.testTag("winbox-all"))
            if (compact) {
                Cell(stringResource(Res.string.connect_address), 1f, palette.muted, Theme.type.small)
            } else {
                Cell(stringResource(Res.string.connect_address), 1.5f, palette.muted, Theme.type.small)
                Cell(stringResource(Res.string.connect_user), 0.9f, palette.muted, Theme.type.small)
                Cell(stringResource(Res.string.connect_group), 0.9f, palette.muted, Theme.type.small)
                Cell(stringResource(Res.string.connect_comment), 1.2f, palette.muted, Theme.type.small)
                Cell(stringResource(Res.string.connect_protocol), 1.9f, palette.muted, Theme.type.small)
                Cell(stringResource(Res.string.winbox_status), 0.8f, palette.muted, Theme.type.small)
            }
        }
        Box(Modifier.weight(1f, fill = false)) {
            LazyColumn(Modifier.fillMaxWidth(), list) {
                itemsIndexed(book.rows) { index, row -> Session(row, index, compact) }
            }
            ListScrollBar(list, Modifier.align(Alignment.CenterEnd))
        }
    }
}

@Composable
private fun Session(row: WinBoxBook.Row, index: Int, compact: Boolean) {
    val palette = Theme.palette
    val color = if (row.ticked) palette.text else palette.faint
    val router = row.router
    val protocol = protocolLabel(router.protocol)
    val status = stringResource(if (row.known) Res.string.winbox_known else Res.string.winbox_new)
    Row(
        Modifier.fillMaxWidth().clickable { row.ticked = !row.ticked }.hand().heightIn(min = Theme.metrics.row).padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Checkbox(row.ticked, { row.ticked = it }, Modifier.testTag("winbox-row-$index"))
        if (compact) {
            Column(Modifier.weight(1f)) {
                Text(router.address, color = color, style = Theme.type.mono, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(listOf(router.user, protocol, status).joinToString(" · "), color = palette.muted, style = Theme.type.small, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        } else {
            Cell(router.address, 1.5f, color, Theme.type.mono)
            Cell(router.user, 0.9f, color)
            Cell(router.group, 0.9f, color)
            Cell(router.comment, 1.2f, color)
            Cell(protocol, 1.9f, color)
            Cell(status, 0.8f, palette.muted)
        }
    }
}

@Composable
private fun RowScope.Cell(text: String, weight: Float, color: Color, style: TextStyle = Theme.type.body) {
    Text(text, Modifier.weight(weight), color = color, style = style, maxLines = 1, overflow = TextOverflow.Ellipsis)
}
