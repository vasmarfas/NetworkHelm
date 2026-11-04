package com.vasmarfas.networkhelm.ui.workspace

import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame

class WorkspaceStateTest {

    private fun workspace(vararg titles: String) = WorkspaceState().apply {
        titles.forEach { open(it, DpOffset.Zero, DpSize(200.dp, 100.dp)) }
    }

    private fun WorkspaceState.order() = windows.joinToString(" ") { it.title }

    @Test
    fun aWindowOpensInsideTheWorkspace() {
        val state = WorkspaceState()
        assertEquals(DpSize(1200.dp, 600.dp), state.open("before", DpOffset.Zero, DpSize(1200.dp, 600.dp)).size)
        state.viewport = DpSize(1000.dp, 700.dp)
        assertEquals(DpSize(976.dp, 600.dp), state.open("wide", DpOffset(16.dp, 16.dp), DpSize(1200.dp, 600.dp)).size)
        assertEquals(DpSize(400.dp, 300.dp), state.open("small", DpOffset(16.dp, 16.dp), DpSize(400.dp, 300.dp)).size)
    }

    @Test
    fun closingTellsTheIdOfTheWindow() {
        val closed = mutableListOf<Long>()
        val state = WorkspaceState { closed += it }
        val window = state.open("a", DpOffset.Zero, DpSize(200.dp, 100.dp))
        state.minimize(window)
        state.bringToFront(window)
        state.close(window)
        assertEquals(listOf(window.id), closed)
    }

    @Test
    fun switchToPreviousTogglesBetweenTheTwoLastWindows() {
        val state = workspace("a", "b", "c")
        state.switchToPrevious()
        assertEquals("a c b", state.order())
        state.switchToPrevious()
        assertEquals("a b c", state.order())
    }

    @Test
    fun raiseBottomWalksUpThroughTheStack() {
        val state = workspace("a", "b", "c")
        state.raiseBottom()
        assertEquals("b c a", state.order())
        state.raiseBottom()
        assertEquals("c a b", state.order())
    }

    @Test
    fun minimizedWindowGoesToTheBottomAndComesBackOnTop() {
        val state = workspace("a", "b", "c")
        val c = state.windows.last()
        state.minimize(c)
        assertEquals("c a b", state.order())
        assertEquals("b", state.active?.title)
        state.bringToFront(c)
        assertFalse(c.minimized)
        assertSame(c, state.active)
    }

    @Test
    fun theLeftEdgeMovesTheWindowAndStopsAtTheEdgeOfTheWorkspace() {
        val window = WorkspaceState().open("a", DpOffset(100.dp, 50.dp), DpSize(400.dp, 300.dp))
        window.resize(-1, 0, (-40).dp, 0.dp)
        assertEquals(DpOffset(60.dp, 50.dp), window.position)
        assertEquals(DpSize(440.dp, 300.dp), window.size)
        window.resize(-1, 1, (-200).dp, 20.dp)
        assertEquals(DpOffset(0.dp, 50.dp), window.position, "no further left than the workspace")
        assertEquals(DpSize(500.dp, 320.dp), window.size)
        window.resize(-1, 0, 1000.dp, 0.dp)
        assertEquals(DpSize(160.dp, 320.dp), window.size, "no narrower than the smallest window")
        assertEquals(DpOffset(340.dp, 50.dp), window.position, "the right edge stays where it was")
    }

    @Test
    fun closingTheLastWindowLeavesNoActiveOne() {
        val state = workspace("a")
        state.close(state.windows.single())
        assertNull(state.active)
        state.switchToPrevious()
        state.raiseBottom()
        assertEquals("", state.order())
    }
}
