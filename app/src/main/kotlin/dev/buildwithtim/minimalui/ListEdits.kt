package dev.buildwithtim.minimalui

import dev.buildwithtim.minimalui.core.LauncherApp
import dev.buildwithtim.minimalui.core.MenuAction
import dev.buildwithtim.minimalui.core.component
import dev.buildwithtim.minimalui.core.groupMenu

/**
 * What the lists and text editors opened from a menu do: add an app to a collection or user group, rename an app or a
 * user group; and a group's own menu.
 */
internal class ListEdits(
    private val state: LauncherState,
) {
    private val stores get() = state.stores

    // A purely automatic group's menu still opens, its rows disabled, so the key never does nothing.
    fun openGroupMenu(name: String) {
        val isUserGroup = state.shownGroups.firstOrNull { it.name == name }?.isUserGroup == true
        state.push(Screen.GroupMenu(name, groupMenu(isUserGroup)))
    }

    // Rename group keeps the menu below the editor, so Back returns to it; Delete group closes it, back on the groups,
    // and those close too when no group is left to show. A merged automatic group of that name stays.
    fun chooseForGroup(
        menu: Screen.GroupMenu,
        action: MenuAction,
    ) {
        if (action == MenuAction.RenameGroup) {
            state.push(Screen.RenameGroup(menu.name))
            return
        }
        state.back()
        stores.save(stores.userGroups.deleted(menu.name))
        if (state.shownGroups.isEmpty()) state.back()
    }

    /**
     * The user group called [from] renamed [to] (merged into a group of that name), then the editor and menu closed.
     */
    fun renameGroup(
        from: String,
        to: String,
    ) {
        stores.save(stores.userGroups.renamed(from, to))
        state.back(times = 2)
    }

    /** [app] added to the user group called [name], then [closing] lists closed, back to where the menu was opened. */
    fun addToGroup(
        name: String,
        app: LauncherApp,
        closing: Int,
    ) {
        stores.save(stores.userGroups.added(name, app))
        state.back(closing)
    }

    /** [app] added to the collection called [name], then [closing] lists closed, back to where the menu was opened. */
    fun addToCollection(
        name: String,
        app: LauncherApp,
        closing: Int,
    ) {
        stores.save(stores.collections.added(name, app))
        state.back(closing)
    }

    /**
     * [app], as listed, renamed [name] (a blank name, or its own label, gives it back its system label), then the
     * editor and the menu below it closed: the list it was opened from shows it at its new place, selected.
     */
    fun rename(
        app: LauncherApp,
        name: String,
    ) {
        val installed =
            state.loaded
                ?.apps
                .orEmpty()
                .firstOrNull { it.component == app.component }
        if (installed != null) stores.save(stores.aliases.renamed(installed, name))
        state.back(times = 2)
    }
}
