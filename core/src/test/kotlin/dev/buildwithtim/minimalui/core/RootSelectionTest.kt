package dev.buildwithtim.minimalui.core

import org.junit.Assert.assertEquals
import org.junit.Test

class RootSelectionTest {
    private fun app(label: String) = LauncherApp(label, "pkg.$label", "pkg.$label.Main")

    private val alarm = RootEntry.App(app("Alarm"))
    private val bell = RootEntry.App(app("Bell"))
    private val camera = RootEntry.App(app("Camera"))
    private val recentBell = RootEntry.App(app("Bell"), RootSection.Recent)
    private val rows = listOf(alarm, bell, camera)
    private val cause = TopCause(hasApps = true, homePresses = 0, query = "")

    @Test
    fun `the first rows to arrive select the top`() {
        assertEquals(SelectionMove.Top, selectionMove(cause, null, null, emptyList(), 0, rows))
    }

    @Test
    fun `another Home press or query selects the top again, even with a return waiting`() {
        assertEquals(SelectionMove.Top, selectionMove(cause.copy(homePresses = 1), cause, 2, rows, 1, rows))
        assertEquals(SelectionMove.Top, selectionMove(cause.copy(query = "b"), cause, null, rows, 1, rows))
    }

    @Test
    fun `losing every app is a new cause too`() {
        val none = cause.copy(hasApps = false)

        assertEquals(SelectionMove.Top, selectionMove(none, cause, null, rows, 1, emptyList()))
    }

    @Test
    fun `a new return selects its row`() {
        assertEquals(SelectionMove.Return(2), selectionMove(cause, cause, 2, rows, 0, rows))
    }

    @Test
    fun `a return with no apps listed waits`() {
        val none = cause.copy(hasApps = false)

        assertEquals(SelectionMove.Stay, selectionMove(none, none, 2, emptyList(), 0, emptyList()))
    }

    @Test
    fun `unchanged rows keep the selection`() {
        assertEquals(SelectionMove.Stay, selectionMove(cause, cause, null, rows, 1, rows))
    }

    @Test
    fun `a block growing above the selection follows its entry`() {
        val grown = listOf(recentBell, RootEntry.Blank()) + rows

        assertEquals(SelectionMove.Follow(3), selectionMove(cause, cause, null, rows, 1, grown))
    }

    @Test
    fun `rows changing below the selection keep it where it is`() {
        assertEquals(SelectionMove.Stay, selectionMove(cause, cause, null, rows, 1, listOf(alarm, bell)))
    }

    @Test
    fun `a selection whose row is now blank moves on`() {
        val before = listOf(alarm, RootEntry.Collections, RootEntry.Blank(), bell)
        val now = listOf(alarm, RootEntry.Blank(), bell)

        assertEquals(SelectionMove.Follow(1), selectionMove(cause, cause, null, before, 1, now))
    }
}
