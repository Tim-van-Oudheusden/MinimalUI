package dev.buildwithtim.minimalui.core

import dev.buildwithtim.minimalui.core.MenuAction.AddToCollection
import dev.buildwithtim.minimalui.core.MenuAction.AddToFavourites
import dev.buildwithtim.minimalui.core.MenuAction.AppInfo
import dev.buildwithtim.minimalui.core.MenuAction.ClearRecents
import dev.buildwithtim.minimalui.core.MenuAction.Hide
import dev.buildwithtim.minimalui.core.MenuAction.MoveDown
import dev.buildwithtim.minimalui.core.MenuAction.MoveUp
import dev.buildwithtim.minimalui.core.MenuAction.RemoveFromCollection
import dev.buildwithtim.minimalui.core.MenuAction.RemoveFromFavourites
import dev.buildwithtim.minimalui.core.MenuAction.RemoveFromRecents
import dev.buildwithtim.minimalui.core.MenuAction.Rename
import dev.buildwithtim.minimalui.core.MenuAction.Unhide
import dev.buildwithtim.minimalui.core.MenuAction.Uninstall
import org.junit.Assert.assertEquals
import org.junit.Test

class ContextMenuTest {

    private val userApp = AppFacts(isSystem = false, isSettingsEntry = false, isRecent = false, hasRecents = false)

    // Every app's menu ends its own part with these, before any recents rows.
    private val listRows =
        listOf(MenuItem(AddToFavourites), MenuItem(AddToCollection), MenuItem(Rename), MenuItem(Hide))

    @Test
    fun `a user app offers App info and Uninstall, then the favourite, collection, Rename and Hide rows`() {
        assertEquals(listOf(MenuItem(AppInfo), MenuItem(Uninstall)) + listRows, contextMenu(userApp))
    }

    @Test
    fun `Rename is disabled for MinimalUI's own settings row, though Android's Settings can be renamed`() {
        val own = contextMenu(userApp.copy(isSettingsEntry = true, isLauncherSettings = true))
        val android = contextMenu(userApp.copy(isSettingsEntry = true))

        assertEquals(MenuItem(Rename, enabled = false), own.single { it.action == Rename })
        assertEquals(MenuItem(Rename), android.single { it.action == Rename })
    }

    @Test
    fun `Uninstall is shown disabled for a system app`() {
        val menu = contextMenu(userApp.copy(isSystem = true))

        assertEquals(listOf(MenuItem(AppInfo), MenuItem(Uninstall, enabled = false)) + listRows, menu)
    }

    @Test
    fun `Uninstall and Hide are shown disabled for the guaranteed Settings entry`() {
        val menu = contextMenu(userApp.copy(isSettingsEntry = true))

        assertEquals(
            listOf(MenuItem(AppInfo), MenuItem(Uninstall, enabled = false)) + listRows.dropLast(1) +
                MenuItem(Hide, enabled = false),
            menu,
        )
    }

    @Test
    fun `a hidden app offers Unhide in the Hide row`() {
        val menu = contextMenu(userApp.copy(isHidden = true))

        assertEquals(
            listOf(AppInfo, Uninstall, AddToFavourites, AddToCollection, Rename, Unhide),
            menu.map { it.action },
        )
    }

    @Test
    fun `an app that is not recent can still clear the recent apps of others`() {
        val menu = contextMenu(userApp.copy(hasRecents = true))

        assertEquals(
            listOf(AppInfo, Uninstall, AddToFavourites, AddToCollection, Rename, Hide, ClearRecents),
            menu.map { it.action },
        )
    }

    @Test
    fun `a recent app can be removed from the recent apps, before clearing them all`() {
        val menu = contextMenu(userApp.copy(isRecent = true, hasRecents = true))

        assertEquals(
            listOf(
                AppInfo, Uninstall, AddToFavourites, AddToCollection, Rename, Hide, RemoveFromRecents, ClearRecents,
            ),
            menu.map { it.action },
        )
        assertEquals(true, menu.all { it.enabled })
    }

    @Test
    fun `a favourite offers to be removed from the favourites in the same row`() {
        val menu = contextMenu(userApp.copy(isFavourite = true))

        assertEquals(
            listOf(AppInfo, Uninstall, RemoveFromFavourites, AddToCollection, Rename, Hide),
            menu.map { it.action },
        )
    }

    @Test
    fun `inside a collection an app can be removed from it and moved, but not past either end, before Rename`() {
        fun menuAt(row: Int, count: Int) = contextMenu(userApp.copy(place = CollectionPlace(row, count)))
        val inCollection = listOf(AppInfo, Uninstall, AddToFavourites, AddToCollection, RemoveFromCollection)

        assertEquals(
            inCollection.map(::MenuItem) + MenuItem(MoveUp) + MenuItem(MoveDown) + MenuItem(Rename) + MenuItem(Hide),
            menuAt(row = 1, count = 3),
        )
        assertEquals(
            listOf(MenuItem(MoveUp, enabled = false), MenuItem(MoveDown), MenuItem(Rename), MenuItem(Hide)),
            menuAt(0, 3).takeLast(4),
        )
        assertEquals(
            listOf(MenuItem(MoveUp), MenuItem(MoveDown, enabled = false), MenuItem(Rename), MenuItem(Hide)),
            menuAt(2, 3).takeLast(4),
        )
    }
}
