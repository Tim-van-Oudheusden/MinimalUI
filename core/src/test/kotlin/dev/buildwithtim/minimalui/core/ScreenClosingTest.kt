package dev.buildwithtim.minimalui.core

import org.junit.Assert.assertEquals
import org.junit.Test

class ScreenClosingTest {
    @Test
    fun `an action that opens a list or editor on top keeps the menu below it`() {
        val kept = MenuAction.entries.filter { it.keepsMenu }

        assertEquals(
            listOf(MenuAction.AddToCollection, MenuAction.AddToGroup, MenuAction.Rename, MenuAction.RenameGroup),
            kept,
        )
    }

    @Test
    fun `a list that still shows stays open`() {
        assertEquals(0, listsClosing("Work", listOf("Games", "Work")))
    }

    @Test
    fun `a list that no longer shows closes, back on the list of names`() {
        assertEquals(1, listsClosing("Work", listOf("Games")))
    }

    @Test
    fun `the last list gone closes the list of names too`() {
        assertEquals(2, listsClosing("Work", emptyList()))
    }
}
