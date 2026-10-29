package dev.buildwithtim.minimalui.core

/** What a menu row does: in an app's context menu, a group's menu (#69) or the import-setup question (#208). */
enum class MenuAction {
    /** Android's App info screen for the app. */
    AppInfo,

    /** Android's uninstall prompt for the app. */
    Uninstall,

    /** Marks the app as a favourite, pinned to the top of the root list. */
    AddToFavourites,

    /** Takes the app off the favourites. */
    RemoveFromFavourites,

    /** Opens the list of collections the app can be added to, or a new one. */
    AddToCollection,

    /** Takes the app out of the collection the menu was opened in. */
    RemoveFromCollection,

    /** Moves the app one row up in the collection the menu was opened in. */
    MoveUp,

    /** Moves the app one row down in the collection the menu was opened in. */
    MoveDown,

    /** Opens the list of user groups the app can be added to, or a new one (#69). */
    AddToGroup,

    /** Takes the app out of the user group the menu was opened in. */
    RemoveFromGroup,

    /** Opens the text editor to give the app a name of the user's own (#72). */
    Rename,

    /** Hides the app from the root list, the collections and the groups; type-to-filter still finds it. */
    Hide,

    /** Shows a hidden app again. */
    Unhide,

    /** Drops the app from the recently used apps. */
    RemoveFromRecents,

    /** Empties the recently used apps. */
    ClearRecents,

    /** In a group's menu: opens the text editor to give the user group another name, or merge it into another. */
    RenameGroup,

    /** In a group's menu: deletes the user group; its apps stay installed, and in any automatic group of its name. */
    DeleteGroup,

    /**
     * Confirming an imported setup (#208): it replaces every favourite, collection, rename, hidden app, group, recently
     * used app and setting.
     */
    ReplaceSetup,

    /** Turning an imported setup down: the current one stays as it is. */
    KeepSetup,
}

/** One row of the context menu. A disabled row is still listed, drawn dim, and does nothing when chosen. */
data class MenuItem(
    val action: MenuAction,
    val enabled: Boolean = true,
)

/** The app's [row] in a collection of [count] apps, as listed on screen. */
data class CollectionPlace(
    val row: Int,
    val count: Int,
)

/**
 * What the menu needs to know about the app it was opened on: whether it is a system app ([isSystem]), the Settings
 * entry the list always keeps ([isSettingsEntry]), MinimalUI's own settings row ([isLauncherSettings], one of the two),
 * one of the recently used apps ([isRecent]), whether there are any recently used apps at all ([hasRecents]), whether
 * it is a favourite ([isFavourite]), its [place] when the menu was opened inside a collection, and whether it is
 * hidden ([isHidden]), whether the menu was opened inside a user group that holds it ([isGroupMember], #69), and
 * whether it belongs to another profile, a work (#78) or app-clone (#201) one ([isOtherProfile]).
 */
data class AppFacts(
    val isSystem: Boolean,
    val isSettingsEntry: Boolean,
    val isRecent: Boolean,
    val hasRecents: Boolean,
    val isFavourite: Boolean = false,
    val place: CollectionPlace? = null,
    val isHidden: Boolean = false,
    val isLauncherSettings: Boolean = false,
    val isGroupMember: Boolean = false,
    val isOtherProfile: Boolean = false,
)

/**
 * The context menu for an app, top to bottom. Uninstall stays listed but disabled for a system app, which cannot be
 * removed, for an app of another profile, which the launcher cannot remove from it, and for the Settings entry, which
 * keeps the way back to another home app open. One row adds the app to the favourites or removes it, by its state.
 * Inside a collection the app can leave it or move, though not past either end. Any app can be added to a user group
 * (#69), and taken out of the one the menu was opened in. Rename is disabled
 * for MinimalUI's own settings row, which is the launcher's, not an app; Android's Settings can be renamed. One row
 * hides the app or shows it again, by its state; disabled for the Settings entry, which is never hidden.
 * The recents rows show only when they would change something.
 */
fun contextMenu(facts: AppFacts): List<MenuItem> =
    buildList {
        add(MenuItem(MenuAction.AppInfo))
        add(
            MenuItem(
                MenuAction.Uninstall,
                enabled = !facts.isSystem && !facts.isSettingsEntry && !facts.isOtherProfile,
            ),
        )
        add(MenuItem(if (facts.isFavourite) MenuAction.RemoveFromFavourites else MenuAction.AddToFavourites))
        add(MenuItem(MenuAction.AddToCollection))
        add(MenuItem(MenuAction.AddToGroup))
        facts.place?.let { place ->
            add(MenuItem(MenuAction.RemoveFromCollection))
            add(MenuItem(MenuAction.MoveUp, enabled = place.row > 0))
            add(MenuItem(MenuAction.MoveDown, enabled = place.row < place.count - 1))
        }
        if (facts.isGroupMember) add(MenuItem(MenuAction.RemoveFromGroup))
        add(MenuItem(MenuAction.Rename, enabled = !facts.isLauncherSettings))
        add(MenuItem(if (facts.isHidden) MenuAction.Unhide else MenuAction.Hide, enabled = !facts.isSettingsEntry))
        if (facts.isRecent) add(MenuItem(MenuAction.RemoveFromRecents))
        if (facts.hasRecents) add(MenuItem(MenuAction.ClearRecents))
    }

/**
 * The menu of a group in the Groups list (#69): Rename group and Delete group. A purely automatic group has nothing
 * to rename or delete, so both rows show disabled rather than the key doing nothing.
 */
fun groupMenu(isUserGroup: Boolean): List<MenuItem> =
    listOf(MenuItem(MenuAction.RenameGroup, isUserGroup), MenuItem(MenuAction.DeleteGroup, isUserGroup))

/**
 * The question an imported setup asks before it replaces the current one (#208): replace it, the first row, or keep it.
 * Nothing changes until the user confirms.
 */
val importSetupMenu: List<MenuItem> = listOf(MenuItem(MenuAction.ReplaceSetup), MenuItem(MenuAction.KeepSetup))
