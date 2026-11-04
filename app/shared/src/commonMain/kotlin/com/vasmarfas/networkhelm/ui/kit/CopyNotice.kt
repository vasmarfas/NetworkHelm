package com.vasmarfas.networkhelm.ui.kit

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.Clipboard
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.composeunstyled.Text
import com.vasmarfas.networkhelm.ui.theme.Theme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getPluralString
import org.jetbrains.compose.resources.getString
import networkhelm.app.shared.generated.resources.Res
import networkhelm.app.shared.generated.resources.copy_done
import networkhelm.app.shared.generated.resources.copy_done_rows
import networkhelm.app.shared.generated.resources.copy_failed

private const val NOTICE_MILLIS = 1600L

@Stable
class CopyNotice(private val scope: CoroutineScope) {
    var text by mutableStateOf<String?>(null)
        private set

    var next: String? = null
    private var hiding: Job? = null

    fun show(message: String) {
        hiding?.cancel()
        text = message
        hiding = scope.launch {
            delay(NOTICE_MILLIS)
            text = null
        }
    }
}

val LocalCopyNotice = staticCompositionLocalOf<CopyNotice?> { null }

private val LocalCopyClipboard = staticCompositionLocalOf<Clipboard?> { null }

private class NoticingClipboard(private val base: Clipboard, private val notice: CopyNotice) : Clipboard by base {
    override suspend fun setClipEntry(clipEntry: ClipEntry?) {
        base.setClipEntry(clipEntry)
        if (clipEntry != null) notice.show(notice.next?.also { notice.next = null } ?: getString(Res.string.copy_done))
    }
}

// not LocalClipboard: the text toolbar of Android takes the system clipboard out of the instance it is given and fails on a wrapper
@Composable
fun copyClipboard(): Clipboard = LocalCopyClipboard.current ?: LocalClipboard.current

@Composable
fun CopyNoticeHost(content: @Composable () -> Unit) {
    val scope = rememberCoroutineScope()
    val notice = remember(scope) { CopyNotice(scope) }
    val clipboard = LocalClipboard.current
    val noticing = remember(clipboard, notice) { NoticingClipboard(clipboard, notice) }
    CompositionLocalProvider(LocalCopyNotice provides notice, LocalCopyClipboard provides noticing) {
        Box(Modifier.fillMaxSize()) {
            content()
            notice.text?.let { message ->
                val palette = Theme.palette
                val shape = RoundedCornerShape(Theme.metrics.radius)
                Text(
                    message,
                    Modifier.align(Alignment.BottomCenter).padding(bottom = 40.dp).clip(shape).background(palette.surface).border(1.dp, palette.line, shape)
                        .padding(horizontal = 10.dp, vertical = 5.dp).testTag("copy-notice").semantics { liveRegion = LiveRegionMode.Polite },
                    color = palette.text,
                    style = Theme.type.small,
                )
            }
        }
    }
}

class Copier internal constructor(private val clipboard: Clipboard, private val notice: CopyNotice?, private val scope: CoroutineScope) {
    fun copy(text: String, rows: Int = 0) {
        scope.launch {
            try {
                if (rows > 0) notice?.next = getPluralString(Res.plurals.copy_done_rows, rows, rows)
                clipboard.writeText(text)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                notice?.next = null
                notice?.show(getString(Res.string.copy_failed))
            }
        }
    }
}

@Composable
fun rememberCopier(): Copier {
    val clipboard = copyClipboard()
    val notice = LocalCopyNotice.current
    val scope = rememberCoroutineScope()
    return remember(clipboard, notice, scope) { Copier(clipboard, notice, scope) }
}
