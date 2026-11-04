package com.vasmarfas.networkhelm.ui.kit

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicSecureTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.TextObfuscationMode
import androidx.compose.foundation.text.selection.DisableSelection
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.editableText
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.Check
import com.composables.icons.lucide.Eye
import com.composables.icons.lucide.EyeOff
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Minus
import com.composeunstyled.AnchorSide
import com.composeunstyled.CheckedIndicator
import com.composeunstyled.DropdownMenuPanel
import com.composeunstyled.DropdownMenuPanelScope
import com.composeunstyled.DropdownMenuScope
import com.composeunstyled.Text
import com.composeunstyled.TextInput
import com.composeunstyled.TooltipHost
import com.composeunstyled.TooltipPanel
import com.composeunstyled.UnstyledButton
import com.composeunstyled.UnstyledCheckbox
import com.composeunstyled.UnstyledDropdownMenuItem
import com.composeunstyled.UnstyledIcon
import com.composeunstyled.UnstyledTextField
import com.composeunstyled.UnstyledTooltip
import com.vasmarfas.networkhelm.ui.theme.Palette
import com.vasmarfas.networkhelm.ui.theme.Theme
import com.vasmarfas.networkhelm.ui.theme.lit
import com.vasmarfas.networkhelm.ui.theme.shaded
import org.jetbrains.compose.resources.stringResource
import networkhelm.app.shared.generated.resources.Res
import networkhelm.app.shared.generated.resources.form_hide_secret
import networkhelm.app.shared.generated.resources.form_show_secret

fun Modifier.hand(enabled: Boolean = true): Modifier = if (enabled) pointerHoverIcon(PointerIcon.Hand, overrideDescendants = true) else this

fun Modifier.arrow(): Modifier = pointerHoverIcon(PointerIcon.Default, overrideDescendants = true)

val LocalSubmit = compositionLocalOf<(() -> Unit)?> { null }

private fun Modifier.onEnter(action: (() -> Unit)?): Modifier = if (action == null) this else onPreviewKeyEvent { event ->
    val enter = event.type == KeyEventType.KeyDown && (event.key == Key.Enter || event.key == Key.NumPadEnter)
    if (enter) action()
    enter
}

@Composable
fun ToolbarButton(
    label: String,
    icon: ImageVector?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    active: Boolean = false,
    iconOnly: Boolean = false,
    tint: Color? = null,
) {
    val palette = Theme.palette
    val color = if (enabled) palette.text else palette.faint
    val classic = Theme.classic
    val shown = iconOnly && icon != null
    var cut by remember { mutableStateOf(false) }
    Hint(label, shown || cut) {
        UnstyledButton(
            onClick = onClick,
            enabled = enabled,
            contentPadding = PaddingValues(horizontal = 6.dp),
            modifier = modifier
                .height(Theme.metrics.toolbar)
                .then(if (classic) Modifier.padding(vertical = 1.dp) else Modifier)
                .clip(RoundedCornerShape(Theme.metrics.radius))
                .background(if (active) palette.selection else if (classic) palette.raised else palette.surface.copy(alpha = 0f))
                .then(if (classic) Modifier.bevel(palette) else Modifier)
                .hand(enabled),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (icon != null) UnstyledIcon(icon, if (shown) label else null, Modifier.size(Theme.metrics.icon), tint = if (enabled && tint != null) tint else color)
                if (!shown) DisableSelection { Text(label, color = color, singleLine = true, overflow = TextOverflow.Ellipsis, onTextLayout = { cut = it.hasVisualOverflow }) }
            }
        }
    }
}

@Composable
fun IconButton(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    active: Boolean = false,
    enabled: Boolean = true,
    hint: (@Composable () -> String)? = null,
) {
    val palette = Theme.palette
    val classic = Theme.classic
    Hint(description, live = hint) {
        UnstyledButton(
            onClick = onClick,
            enabled = enabled,
            modifier = modifier
                .size(28.dp, Theme.metrics.row)
                .clip(RoundedCornerShape(Theme.metrics.radius))
                .background(if (active) palette.selection else if (classic) palette.raised else palette.surface.copy(alpha = 0f))
                .then(if (classic) Modifier.bevel(palette) else Modifier)
                .hand(enabled),
        ) {
            UnstyledIcon(icon, description, Modifier.size(14.dp), tint = if (!enabled) palette.faint else if (active) palette.text else palette.muted)
        }
    }
}

@Composable
fun HintHost(content: @Composable () -> Unit) = TooltipHost(Modifier.fillMaxSize()) { CopyNoticeHost { SelectionContainer(content = content) } }

@Composable
fun Hint(text: String, enabled: Boolean = true, live: (@Composable () -> String)? = null, anchor: @Composable () -> Unit) {
    val palette = Theme.palette
    val shape = RoundedCornerShape(Theme.metrics.radius)
    UnstyledTooltip(
        enabled = enabled,
        panel = {
            TooltipPanel(Modifier.clip(shape).background(palette.surface).border(1.dp, palette.line, shape).padding(horizontal = 6.dp, vertical = 3.dp)) {
                DisableSelection { Text(live?.invoke() ?: text, color = palette.text, style = Theme.type.small, singleLine = true) }
            }
        },
        side = AnchorSide.Bottom,
        sideOffset = 4.dp,
        hoverDelayMillis = 600,
        anchor = anchor,
    )
}

@Composable
fun Button(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, primary: Boolean = false, danger: Boolean = false) {
    val palette = Theme.palette
    val shape = RoundedCornerShape(Theme.metrics.radius)
    val classic = Theme.classic
    val filled = (primary || danger) && enabled && !classic
    val fill = if (danger) palette.danger else palette.accent
    UnstyledButton(
        onClick = onClick,
        enabled = enabled,
        contentPadding = PaddingValues(horizontal = if (LocalCompact.current) 8.dp else 14.dp),
        modifier = modifier
            .height(Theme.metrics.control)
            .widthIn(min = 72.dp)
            .clip(shape)
            .then(
                when {
                    classic -> Modifier.background(palette.raised).bevel(palette, if (primary && enabled) palette.text else palette.line)
                    else -> Modifier.background(if (filled) fill else palette.surface).border(1.dp, if (filled) fill else palette.line, shape)
                },
            )
            .hand(enabled),
    ) {
        DisableSelection {
            Text(
                label,
                color = when {
                    !enabled -> palette.faint
                    filled -> palette.onAccent
                    danger -> palette.danger
                    else -> palette.text
                },
                singleLine = true,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
fun TextField(
    state: TextFieldState,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    placeholder: String? = null,
    mono: Boolean = false,
    error: Boolean = false,
    inputTransformation: InputTransformation? = null,
) {
    val palette = Theme.palette
    val shape = RoundedCornerShape(Theme.metrics.radius)
    var focused by remember { mutableStateOf(false) }
    UnstyledTextField(
        state = state,
        enabled = enabled,
        readOnly = readOnly,
        textStyle = if (mono) Theme.type.mono else Theme.type.body,
        textColor = if (enabled) palette.text else palette.faint,
        cursorBrush = SolidColor(palette.accent),
        selectionColors = LocalTextSelectionColors.current,
        lineLimits = TextFieldLineLimits.SingleLine,
        inputTransformation = inputTransformation,
        modifier = modifier
            .onEnter(LocalSubmit.current.takeIf { enabled && !readOnly })
            .onFocusChanged { focused = it.isFocused }
            .height(Theme.metrics.control)
            .clip(shape)
            .background(if (readOnly) palette.raised else palette.surface)
            .border(1.dp, if (error) palette.danger else if (focused && !readOnly) palette.accent else palette.line, shape)
            .padding(horizontal = 6.dp),
    ) {
        TextInput(
            Modifier.padding(vertical = if (Theme.classic) 0.dp else 4.dp),
            placeholder = placeholder?.let { { Text(it, color = palette.faint) } },
        )
    }
}

// a layer that only draws lets presses through to what lies under it
fun Modifier.scrim(color: Color) = background(color).pointerInput(Unit) { detectTapGestures { } }

@Composable
fun SecretField(state: TextFieldState, modifier: Modifier = Modifier, enabled: Boolean = true, onGo: (() -> Unit)? = null) {
    val palette = Theme.palette
    val shape = RoundedCornerShape(Theme.metrics.radius)
    var shown by remember { mutableStateOf(false) }
    BasicSecureTextField(
        state = state,
        enabled = enabled,
        keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, keyboardType = KeyboardType.Password, imeAction = if (onGo != null) ImeAction.Go else ImeAction.Default),
        onKeyboardAction = onGo?.let { go -> { go() } },
        textObfuscationMode = if (shown) TextObfuscationMode.Visible else TextObfuscationMode.RevealLastTyped,
        textStyle = Theme.type.body.copy(color = if (enabled) palette.text else palette.faint),
        cursorBrush = SolidColor(palette.accent),
        modifier = modifier
            .onEnter(LocalSubmit.current.takeIf { enabled })
            // the field masks the text on screen only and gives the real text to the accessibility tree,
            // which Android passes on to every accessibility service; the text set here replaces it
            .semantics { if (!shown) editableText = AnnotatedString("•".repeat(state.text.length)) }
            .height(Theme.metrics.control)
            .clip(shape)
            .background(palette.surface)
            .border(1.dp, palette.line, shape)
            .padding(start = 6.dp, end = 2.dp),
        decorator = { input ->
            Row(Modifier.fillMaxHeight(), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) { input() }
                UnstyledButton(onClick = { shown = !shown }, modifier = Modifier.size(20.dp).hand()) {
                    val description = stringResource(if (shown) Res.string.form_hide_secret else Res.string.form_show_secret)
                    UnstyledIcon(if (shown) Lucide.EyeOff else Lucide.Eye, description, Modifier.size(14.dp), tint = palette.muted)
                }
            }
        },
    )
}

@Composable
fun Checkbox(checked: Boolean, onCheckedChange: (Boolean) -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, unset: Boolean = false) {
    val palette = Theme.palette
    val filled = checked && enabled && !Theme.classic
    val shape = RoundedCornerShape(if (Theme.classic) 0.dp else 3.dp)
    UnstyledCheckbox(
        checked = checked,
        onCheckedChange = onCheckedChange,
        enabled = enabled,
        modifier = modifier
            .size(16.dp)
            .clip(shape)
            .background(if (filled) palette.accent else palette.surface)
            .border(1.dp, if (filled) palette.accent else palette.line, shape)
            .hand(enabled),
    ) {
        if (unset && !checked) {
            Box(Modifier.size(16.dp), contentAlignment = Alignment.Center) {
                UnstyledIcon(Lucide.Minus, null, Modifier.size(12.dp), tint = palette.faint)
            }
        }
        CheckedIndicator {
            Box(Modifier.size(16.dp), contentAlignment = Alignment.Center) {
                UnstyledIcon(Lucide.Check, null, Modifier.size(12.dp), tint = if (filled) palette.onAccent else if (enabled) palette.text else palette.faint)
            }
        }
    }
}

@Composable
fun Toggle(on: Boolean, title: String, hint: String?, tag: String, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Checkbox(on, onChange, Modifier.testTag(tag), enabled)
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            DisableSelection { Text(title, Modifier.clickable(enabled) { onChange(!on) }.hand(enabled), color = if (enabled) Theme.palette.text else Theme.palette.faint) }
            if (hint != null) Text(hint, color = Theme.palette.muted, style = Theme.type.small)
        }
    }
}

fun Modifier.bevel(palette: Palette, edge: Color = palette.line): Modifier = drawBehind {
    val px = 1.dp.toPx()
    val right = size.width - px
    val bottom = size.height - px
    drawRect(edge, Offset(px / 2, px / 2), Size(size.width - px, size.height - px), style = Stroke(px))
    drawLine(palette.lit, Offset(px, px * 1.5f), Offset(right - px, px * 1.5f), px)
    drawLine(palette.lit, Offset(px * 1.5f, px), Offset(px * 1.5f, bottom - px), px)
    drawLine(palette.shaded, Offset(px, bottom - px / 2), Offset(right, bottom - px / 2), px)
    drawLine(palette.shaded, Offset(right - px / 2, px), Offset(right - px / 2, bottom), px)
}

fun Modifier.sunken(palette: Palette): Modifier = drawBehind {
    val px = 1.dp.toPx()
    drawLine(palette.line, Offset(0f, px / 2), Offset(size.width, px / 2), px)
    drawLine(palette.line, Offset(px / 2, 0f), Offset(px / 2, size.height), px)
    drawLine(palette.lit, Offset(0f, size.height - px / 2), Offset(size.width, size.height - px / 2), px)
    drawLine(palette.lit, Offset(size.width - px / 2, 0f), Offset(size.width - px / 2, size.height), px)
}

val LabelWidth = 110.dp

@Composable
fun Labeled(label: String, field: @Composable () -> Unit) {
    if (LocalCompact.current) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            if (label.isNotEmpty()) Text(label, color = Theme.palette.muted)
            field()
        }
        return
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(label, Modifier.width(LabelWidth), color = Theme.palette.muted, textAlign = TextAlign.End)
        field()
    }
}

// the popup leaves width and height unbounded and pushes a panel past the window back over the phone bars;
// the cap to the room left and the padding under the panel keep it above the bars and the keyboard
@Composable
fun DropdownMenuScope.MenuPanel(
    scroll: ScrollState = rememberScrollState(),
    focus: FocusRequester? = null,
    header: (@Composable () -> Unit)? = null,
    minWidth: Dp = 0.dp,
    content: @Composable DropdownMenuPanelScope.() -> Unit,
) {
    val palette = Theme.palette
    val shape = RoundedCornerShape(Theme.metrics.radius)
    val bars = WindowInsets.safeDrawing.asPaddingValues()
    val below = bars.calculateBottomPadding() + 8.dp
    val room = LocalWindowInfo.current.containerDpSize.height - bars.calculateTopPadding() - below - 8.dp
    val panel = Modifier.widthIn(min = minWidth).width(IntrinsicSize.Max).heightIn(max = room).clip(shape).background(palette.surface).border(1.dp, palette.line, shape)
        .then(if (focus != null) Modifier.onFocusChanged { if (it.isFocused) focus.requestFocus() } else Modifier)
    val inset = if (Theme.classic) 0.dp else 4.dp
    DisableSelection {
        Box(Modifier.padding(bottom = below)) {
            if (header == null) {
                DropdownMenuPanel(panel.verticalScroll(scroll).padding(vertical = inset), content = content)
            } else {
                DropdownMenuPanel(panel.padding(vertical = inset)) {
                    Column {
                        header()
                        Column(Modifier.verticalScroll(scroll)) { content() }
                    }
                }
            }
        }
    }
}

@Composable
fun DropdownItem(onClick: () -> Unit, modifier: Modifier = Modifier, selected: Boolean = false, content: @Composable () -> Unit) {
    val palette = Theme.palette
    val line = if (Theme.classic) Modifier.drawBehind { drawLine(palette.divider ?: palette.line, Offset(0f, size.height - 0.5f), Offset(size.width, size.height - 0.5f), 1.dp.toPx()) } else Modifier
    UnstyledDropdownMenuItem(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().background(if (selected) palette.selection else palette.surface).then(line).hand(),
        indication = LocalIndication.current,
    ) {
        Box(Modifier.heightIn(min = if (LocalCompact.current) 44.dp else 0.dp).padding(horizontal = 10.dp, vertical = 4.dp), contentAlignment = Alignment.CenterStart) {
            content()
        }
    }
}

@Composable
fun MenuSeparator() {
    Box(Modifier.fillMaxWidth().padding(vertical = 3.dp).height(1.dp).background(Theme.palette.line))
}
