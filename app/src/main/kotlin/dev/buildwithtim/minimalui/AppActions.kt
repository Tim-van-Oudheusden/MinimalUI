package dev.buildwithtim.minimalui

import android.content.pm.PackageManager
import dev.buildwithtim.minimalui.core.AppFacts
import dev.buildwithtim.minimalui.core.CollectionPlace
import dev.buildwithtim.minimalui.core.LauncherApp
import dev.buildwithtim.minimalui.core.MenuAction
import dev.buildwithtim.minimalui.core.contextMenu
import dev.buildwithtim.minimalui.core.isLauncherSettings
import dev.buildwithtim.minimalui.core.isWorkApp
import dev.buildwithtim.minimalui.core.keepsMenu
import dev.buildwithtim.minimalui.core.listsClosing

/** What the user does to one app from a list: open it, or open its context menu and choose a row there. */
internal class AppActions(
    private val state: LauncherState,
    private val starter: AppStarter,
    private val packageManager: PackageManager,
) {
    private val stores get() = state.stores

    fun open(app: LauncherApp) {
        // MinimalUI's own settings open in place; they are not an app to start, nor a recent one.
        if (app.isLauncherSettings) {
            state.push(Screen.SettingsList())
            return
        }
        if (starter.launch(app)) {
            stores.save(stores.recents.opened(app, state.apps.orEmpty()))
            state.awayInApp = true
        }
    }

    // The facts are read once, as the menu opens; every action closes it again. A menu opened inside the [collection]
    // can also take the app out of it or move it there; one opened inside a [group] the user put it in can take it out.
    fun openMenu(
        app: LauncherApp,
        collection: String? = null,
        group: String? = null,
    ) {
        val recent = state.recentApps
        val inCollection = collection?.let { stores.collections.appsIn(it, state.visibleApps) }
        val facts =
            AppFacts(
                isSystem = packageManager.isSystemApp(app.packageName),
                // Android's Settings keeps the way back to another home app;
                // MinimalUI's row stands for the launcher itself.
                isSettingsEntry =
                    app.isLauncherSettings || app.packageName == packageManager.systemSettings()?.packageName,
                isRecent = app in recent,
                hasRecents = recent.isNotEmpty(),
                isFavourite = app in stores.favourites,
                place = inCollection?.let { CollectionPlace(it.indexOf(app), it.size) },
                isHidden = app in stores.hidden,
                isLauncherSettings = app.isLauncherSettings,
                isGroupMember = group != null && stores.userGroups.holds(group, app),
                isWorkApp = app.isWorkApp,
            )
        state.push(Screen.Menu(app, contextMenu(facts), collection, group))
    }

    // The list comes back on the same app: its selection was kept while the menu was up. Add to collection, Add to
    // group and Rename keep the menu below the list or editor they open, so Back returns to it.
    fun choose(
        menu: Screen.Menu,
        action: MenuAction,
    ) {
        val app = menu.app
        if (!action.keepsMenu) state.back()
        when (action) {
            MenuAction.AppInfo -> starter.showInfo(app)

            MenuAction.Uninstall -> starter.start(app.uninstallIntent())

            MenuAction.AddToCollection -> state.push(Screen.CollectionChoice(app))

            MenuAction.AddToGroup -> state.push(Screen.GroupChoice(app))

            MenuAction.Rename -> state.push(Screen.Rename(app))

            MenuAction.AddToFavourites, MenuAction.RemoveFromFavourites, MenuAction.Hide, MenuAction.Unhide,
            MenuAction.RemoveFromRecents, MenuAction.ClearRecents,
            -> change(menu, action)

            MenuAction.RemoveFromCollection, MenuAction.MoveUp, MenuAction.MoveDown, MenuAction.RemoveFromGroup,
            -> changeList(menu, action)

            // Only a group's menu lists these, and it has its own handler.
            MenuAction.RenameGroup, MenuAction.DeleteGroup -> Unit
        }
    }

    /** The app's own state changed by [action]: a favourite, hidden, or one of the recently used apps. */
    private fun change(
        menu: Screen.Menu,
        action: MenuAction,
    ) {
        val app = menu.app
        when (action) {
            MenuAction.AddToFavourites, MenuAction.RemoveFromFavourites -> stores.save(stores.favourites.toggled(app))

            MenuAction.Hide -> hide(menu)

            MenuAction.Unhide -> stores.save(stores.hidden.unhidden(app))

            MenuAction.RemoveFromRecents -> stores.save(stores.recents.without(app))

            MenuAction.ClearRecents -> stores.save(stores.recents.cleared())

            // The other actions are chosen in [choose].
            else -> Unit
        }
    }

    /** The collection or user group the menu was opened in changed by [action]: the app taken out, or moved. */
    private fun changeList(
        menu: Screen.Menu,
        action: MenuAction,
    ) {
        val app = menu.app
        when (action) {
            MenuAction.RemoveFromCollection -> {
                menu.collection?.let { name ->
                    stores.save(stores.collections.removed(name, app))
                    leaveCollectionIfGone(name)
                }
            }

            MenuAction.MoveUp -> {
                menu.collection?.let { moveInCollection(it, app, -1) }
            }

            MenuAction.MoveDown -> {
                menu.collection?.let { moveInCollection(it, app, 1) }
            }

            MenuAction.RemoveFromGroup -> {
                menu.group?.let { name ->
                    stores.save(stores.userGroups.removed(name, app))
                    leaveGroupIfGone(name)
                }
            }

            // The other actions are chosen in [choose].
            else -> {}
        }
    }

    // The collection's list follows the app to its new row.
    private fun moveInCollection(
        name: String,
        app: LauncherApp,
        step: Int,
    ) = stores.save(stores.collections.moved(name, app, step, state.visibleApps))

    // Hidden from inside a collection, the app leaves its list as if removed, though the collection still holds it.
    private fun hide(menu: Screen.Menu) {
        stores.save(stores.hidden.hidden(menu.app, packageManager.systemSettings()?.packageName))
        menu.collection?.let(::leaveCollectionIfGone)
        menu.group?.let(::leaveGroupIfGone)
    }

    // The collection's list closes with its last app shown, and the list of collections with the last collection that
    // shows any.
    private fun leaveCollectionIfGone(name: String) =
        state.back(listsClosing(name, stores.collections.names(state.visibleApps)))

    // The group's list closes once it shows no app, and the list of groups once no group shows any.
    private fun leaveGroupIfGone(name: String) = state.back(listsClosing(name, state.shownGroups.map { it.name }))
}
