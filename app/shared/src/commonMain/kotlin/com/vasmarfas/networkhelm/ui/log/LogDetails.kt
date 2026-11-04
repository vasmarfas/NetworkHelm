package com.vasmarfas.networkhelm.ui.log

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.ChevronDown
import com.composables.icons.lucide.ChevronUp
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.SquareArrowOutUpRight
import com.composables.icons.lucide.X
import com.composeunstyled.Text
import com.vasmarfas.networkhelm.ui.kit.IconButton
import com.vasmarfas.networkhelm.ui.kit.ToolbarButton
import com.vasmarfas.networkhelm.ui.kit.hand
import com.vasmarfas.networkhelm.ui.theme.Theme
import org.jetbrains.compose.resources.stringResource
import networkhelm.app.shared.generated.resources.Res
import networkhelm.app.shared.generated.resources.log_close
import networkhelm.app.shared.generated.resources.log_context
import networkhelm.app.shared.generated.resources.log_copy_all
import networkhelm.app.shared.generated.resources.log_copy_fields
import networkhelm.app.shared.generated.resources.log_copy_line
import networkhelm.app.shared.generated.resources.log_copy_message
import networkhelm.app.shared.generated.resources.log_next_line
import networkhelm.app.shared.generated.resources.log_open_window
import networkhelm.app.shared.generated.resources.log_previous_line
import networkhelm.app.shared.generated.resources.log_repeated
import networkhelm.app.shared.generated.resources.log_same_message
import networkhelm.app.shared.generated.resources.log_show_in_log

@Composable
internal fun LogDetails(
    row: LogRow,
    context: List<LogEntry>,
    now: Long,
    onPrevious: (() -> Unit)?,
    onNext: (() -> Unit)?,
    onCopy: (String) -> Unit,
    modifier: Modifier = Modifier,
    chosen: Int = 0,
    onCopyChosen: () -> Unit = {},
    onTopic: ((String) -> Unit)? = null,
    onSameMessage: (() -> Unit)? = null,
    onShow: (() -> Unit)? = null,
    onWindow: (() -> Unit)? = null,
    onClose: (() -> Unit)? = null,
) {
    val palette = Theme.palette
    val entry = row.last
    val color = entry.severity.color(palette)
    val shape = RoundedCornerShape(Theme.metrics.radius)
    val large = Theme.type.mono.let { it.copy(fontSize = it.fontSize * 1.1f, lineHeight = it.lineHeight * 1.1f) }
    Column(modifier.fillMaxWidth().background(palette.surface).testTag("log-details")) {
        Row(Modifier.fillMaxWidth().background(palette.raised).padding(start = 10.dp, end = 4.dp, top = 3.dp, bottom = 3.dp), verticalAlignment = Alignment.CenterVertically) {
            FlowRow(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp), itemVerticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    Box(Modifier.size(8.dp).background(if (entry.severity == Severity.Info) palette.muted else color, CircleShape))
                    Text(entry.severity.level(), style = Theme.type.strong, color = if (entry.severity == Severity.Info) palette.text else color, singleLine = true)
                }
                Text(entry.stamp?.let { "${it.date} ${it.clock}" } ?: entry.time, Modifier.testTag("log-time"), style = Theme.type.mono, color = palette.text, singleLine = true)
                entry.stamp?.let { Text(agoText(now - it.seconds), style = Theme.type.small, color = palette.muted, singleLine = true) }
                for (word in entry.words) {
                    val pressed = if (onTopic != null) Modifier.clickable { onTopic(word) }.hand() else Modifier
                    TopicTag(word, severityOf(word)?.color(palette), pressed.testTag("log-topic-$word"))
                }
            }
            if (onWindow != null) IconButton(Lucide.SquareArrowOutUpRight, stringResource(Res.string.log_open_window), onWindow, Modifier.testTag("log-window"))
            IconButton(Lucide.ChevronUp, stringResource(Res.string.log_previous_line), { onPrevious?.invoke() }, Modifier.testTag("log-previous-line"), enabled = onPrevious != null)
            IconButton(Lucide.ChevronDown, stringResource(Res.string.log_next_line), { onNext?.invoke() }, Modifier.testTag("log-next-line"), enabled = onNext != null)
            if (onClose != null) IconButton(Lucide.X, stringResource(Res.string.log_close), onClose, Modifier.testTag("log-close"))
        }
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SelectionContainer {
                Text(entry.message, Modifier.fillMaxWidth().background(palette.raised, shape).padding(10.dp).testTag("log-message"), style = large, color = if (entry.severity >= Severity.Error) color else palette.text)
            }
            if (row.count > 1) Text(stringResource(Res.string.log_repeated, row.count, row.first.time, row.last.time), style = Theme.type.small, color = palette.muted)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                ToolbarButton(stringResource(Res.string.log_copy_message), null, { onCopy(entry.message) }, Modifier.testTag("log-copy-message").background(palette.raised, shape))
                ToolbarButton(stringResource(Res.string.log_copy_line), null, { onCopy(entry.line) }, Modifier.testTag("log-copy-line").background(palette.raised, shape))
                ToolbarButton(stringResource(Res.string.log_copy_fields), null, { onCopy(entry.fields) }, Modifier.testTag("log-copy-fields").background(palette.raised, shape))
                if (chosen > 1) ToolbarButton(stringResource(Res.string.log_copy_all, chosen), null, onCopyChosen, Modifier.testTag("log-copy-all").background(palette.raised, shape))
                if (onSameMessage != null) ToolbarButton(stringResource(Res.string.log_same_message), null, onSameMessage, Modifier.testTag("log-same-message").background(palette.raised, shape))
                if (onShow != null) ToolbarButton(stringResource(Res.string.log_show_in_log), null, onShow, Modifier.testTag("log-show").background(palette.raised, shape))
            }
            if (context.size > 1) {
                Text(stringResource(Res.string.log_context), style = Theme.type.section, color = palette.muted)
                SelectionContainer {
                    Column(Modifier.fillMaxWidth().testTag("log-context")) {
                        for (line in context) ContextLine(line, line.id == entry.id)
                    }
                }
            }
        }
    }
}

@Composable
private fun ContextLine(line: LogEntry, current: Boolean) {
    val palette = Theme.palette
    Row(
        Modifier.fillMaxWidth().background(if (current) palette.selection else Color.Transparent).padding(horizontal = 6.dp, vertical = 1.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(line.stamp?.clock ?: line.time, style = Theme.type.mono, color = palette.muted, singleLine = true)
        Text(line.topics, Modifier.widthIn(max = 160.dp), style = Theme.type.mono, color = palette.muted, singleLine = true, overflow = TextOverflow.Ellipsis)
        Text(line.message, Modifier.weight(1f), style = Theme.type.mono, color = if (line.severity >= Severity.Error) line.severity.color(palette) else palette.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
