package dev.buildwithtim.minimalui

import android.content.pm.PackageManager
import dev.buildwithtim.minimalui.core.AppFacts
import dev.buildwithtim.minimalui.core.CollectionPlace
import dev.buildwithtim.minimalui.core.LauncherApp
import dev.buildwithtim.minimalui.core.MenuAction
import dev.buildwithtim.minimalui.core.contextMenu
import dev.buildwithtim.minimalui.core.isLauncherSettings
import dev.buildwithtim.minimalui.core.isOtherProfile
import dev.buildwithtim.minimalui.core.keepsMenu

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
                isOtherProfile = app.isOtherProfile,
            )
        state.push(Screen.Menu(app, contextMenu(facts), collection, group))
    }

    // The list comes back on the same app: its selection was kept while the menu was up. Add to collection, Add to
    // group and Rename keep the menu below the list or editor they open, so Back returns to it.
    fun choose(
        menu: Screen.Menu,
        action: MenuAction,
    ) {
        if (!action.keepsMenu) state.back()
        perform(menu, action)
    }

    // Every row of the context menu, and what it does to the app or the list the menu was opened in.
    private fun perform(
        menu: Screen.Menu,
        action: MenuAction,
    ) {
        val app = menu.app
        when (action) {
            MenuAction.AppInfo -> starter.showInfo(app)
            MenuAction.Uninstall -> starter.start(app.uninstallIntent())
            MenuAction.AddToCollection -> state.push(Screen.CollectionChoice(app))
            MenuAction.AddToGroup -> state.push(Screen.GroupChoice(app))
            MenuAction.Rename -> state.push(Screen.Rename(app))
            MenuAction.AddToFavourites, MenuAction.RemoveFromFavourites -> stores.save(stores.favourites.toggled(app))
            MenuAction.Hide -> hide(menu)
            MenuAction.Unhide -> stores.save(stores.hidden.unhidden(app))
            MenuAction.RemoveFromRecents -> stores.save(stores.recents.without(app))
            MenuAction.ClearRecents -> stores.save(stores.recents.cleared())
            MenuAction.RemoveFromCollection -> removeFromCollection(menu)
            MenuAction.MoveUp, MenuAction.MoveDown -> moveInCollection(menu, action)
            MenuAction.RemoveFromGroup -> removeFromGroup(menu)
        }
    }

    // The app taken out of the collection the menu was opened in, which closes once it shows no app.
    private fun removeFromCollection(menu: Screen.Menu) {
        menu.collection?.let { name ->
            stores.save(stores.collections.removed(name, menu.app))
            state.leaveCollectionIfGone(name)
        }
    }

    // The collection's list follows the app to its new row, one up or down.
    private fun moveInCollection(
        menu: Screen.Menu,
        action: MenuAction,
    ) {
        val step = if (action == MenuAction.MoveUp) -1 else 1
        menu.collection?.let { stores.save(stores.collections.moved(it, menu.app, step, state.visibleApps)) }
    }

    // The app taken out of the user group the menu was opened in, which closes once it shows no app.
    private fun removeFromGroup(menu: Screen.Menu) {
        menu.group?.let { name ->
            stores.save(stores.userGroups.removed(name, menu.app))
            state.leaveGroupIfGone(name)
        }
    }

    // Hidden from inside a collection, the app leaves its list as if removed, though the collection still holds it.
    private fun hide(menu: Screen.Menu) {
        stores.save(stores.hidden.hidden(menu.app, packageManager.systemSettings()?.packageName))
        menu.collection?.let(state::leaveCollectionIfGone)
        menu.group?.let(state::leaveGroupIfGone)
    }
}
