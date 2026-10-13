package dev.buildwithtim.minimalui.core

import dev.buildwithtim.minimalui.core.MenuAction.AppInfo
import dev.buildwithtim.minimalui.core.MenuAction.ClearRecents
import dev.buildwithtim.minimalui.core.MenuAction.RemoveFromRecents
import dev.buildwithtim.minimalui.core.MenuAction.Uninstall
import org.junit.Assert.assertEquals
import org.junit.Test

class ContextMenuTest {

    private val userApp = AppFacts(isSystem = false, isSettingsEntry = false, isRecent = false, hasRecents = false)

    @Test
    fun `a user app offers App info and Uninstall`() {
        assertEquals(listOf(MenuItem(AppInfo), MenuItem(Uninstall)), contextMenu(userApp))
    }

    @Test
    fun `Uninstall is shown disabled for a system app`() {
        val menu = contextMenu(userApp.copy(isSystem = true))

        assertEquals(listOf(MenuItem(AppInfo), MenuItem(Uninstall, enabled = false)), menu)
    }

    @Test
    fun `Uninstall is shown disabled for the guaranteed Settings entry`() {
        val menu = contextMenu(userApp.copy(isSettingsEntry = true))

        assertEquals(listOf(MenuItem(AppInfo), MenuItem(Uninstall, enabled = false)), menu)
    }

    @Test
    fun `an app that is not recent can still clear the recent apps of others`() {
        val menu = contextMenu(userApp.copy(hasRecents = true))

        assertEquals(listOf(AppInfo, Uninstall, ClearRecents), menu.map { it.action })
    }

    @Test
    fun `a recent app can be removed from the recent apps, before clearing them all`() {
        val menu = contextMenu(userApp.copy(isRecent = true, hasRecents = true))

        assertEquals(listOf(AppInfo, Uninstall, RemoveFromRecents, ClearRecents), menu.map { it.action })
        assertEquals(true, menu.all { it.enabled })
    }
}
