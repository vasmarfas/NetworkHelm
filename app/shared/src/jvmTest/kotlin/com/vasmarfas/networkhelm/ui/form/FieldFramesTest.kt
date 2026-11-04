package com.vasmarfas.networkhelm.ui.form

import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import com.vasmarfas.networkhelm.model.Argument
import com.vasmarfas.networkhelm.model.ValueType
import com.vasmarfas.networkhelm.ui.theme.NetworkHelmTheme
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Image
import java.util.concurrent.Executors
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse

@OptIn(ExperimentalComposeUiApi::class)
class FieldFramesTest {

    private fun Image.pixels(): ByteArray = Bitmap.makeFromImage(this).readPixels() ?: ByteArray(0)

    @Test
    fun aValueFromTheRouterIsDrawnInTheFrameThatBringsIt() {
        Executors.newSingleThreadExecutor().asCoroutineDispatcher().use { thread ->
            runBlocking(thread) {
                val time = mutableStateOf("12:00:00")
                val scene = ImageComposeScene(240, 40, coroutineContext = thread) {
                    NetworkHelmTheme { FieldEditor(Argument("time", ValueType.Text()), time.value, { time.value = it }) }
                }
                var now = 0L
                suspend fun frames(): Int {
                    var count = 0
                    while (true) {
                        yield()
                        Snapshot.sendApplyNotifications()
                        yield()
                        if (!scene.hasInvalidations()) return count
                        now += 16_000_000
                        scene.render(now)
                        count++
                    }
                }
                frames()
                val before = scene.render(now).pixels()

                time.value = "12:00:01"
                Snapshot.sendApplyNotifications()
                yield()
                now += 16_000_000
                val first = scene.render(now).pixels()
                assertEquals(0, frames())
                assertFalse(first.contentEquals(before))
                assertContentEquals(first, scene.render(now).pixels())
            }
        }
    }
}
