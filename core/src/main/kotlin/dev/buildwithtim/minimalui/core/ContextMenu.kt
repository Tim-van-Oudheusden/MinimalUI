package dev.buildwithtim.minimalui.core

/** What a row of the per-app context menu does. Later features add their own kinds (Hide, Rename, …). */
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

    /** Hides the app from the root list and the collections; type-to-filter still finds it. */
    Hide,

    /** Shows a hidden app again. */
    Unhide,

    /** Drops the app from the recently used apps. */
    RemoveFromRecents,

    /** Empties the recently used apps. */
    ClearRecents,
}

/** One row of the context menu. A disabled row is still listed, drawn dim, and does nothing when chosen. */
data class MenuItem(val action: MenuAction, val enabled: Boolean = true)

/** The app's [row] in a collection of [count] apps, as listed on screen. */
data class CollectionPlace(val row: Int, val count: Int)

/**
 * What the menu needs to know about the app it was opened on: whether it is a system app ([isSystem]), the Settings
 * entry the list always keeps ([isSettingsEntry]), one of the recently used apps ([isRecent]), whether there are
 * any recently used apps at all ([hasRecents]), whether it is a favourite ([isFavourite]), its [place] when the
 * menu was opened inside a collection, and whether it is hidden ([isHidden]).
 */
data class AppFacts(
    val isSystem: Boolean,
    val isSettingsEntry: Boolean,
    val isRecent: Boolean,
    val hasRecents: Boolean,
    val isFavourite: Boolean = false,
    val place: CollectionPlace? = null,
    val isHidden: Boolean = false,
)

/**
 * The context menu for an app, top to bottom. Uninstall stays listed but disabled for a system app, which cannot be
 * removed, and for the Settings entry, which keeps the way back to another home app open. One row adds the app to the
 * favourites or removes it, by its state. Inside a collection the app can leave it or move, though not past either
 * end. One row hides the app or shows it again, by its state; disabled for the Settings entry, which is never hidden.
 * The recents rows show only when they would change something.
 */
fun contextMenu(facts: AppFacts): List<MenuItem> = buildList {
    add(MenuItem(MenuAction.AppInfo))
    add(MenuItem(MenuAction.Uninstall, enabled = !facts.isSystem && !facts.isSettingsEntry))
    add(MenuItem(if (facts.isFavourite) MenuAction.RemoveFromFavourites else MenuAction.AddToFavourites))
    add(MenuItem(MenuAction.AddToCollection))
    facts.place?.let { place ->
        add(MenuItem(MenuAction.RemoveFromCollection))
        add(MenuItem(MenuAction.MoveUp, enabled = place.row > 0))
        add(MenuItem(MenuAction.MoveDown, enabled = place.row < place.count - 1))
    }
    add(MenuItem(if (facts.isHidden) MenuAction.Unhide else MenuAction.Hide, enabled = !facts.isSettingsEntry))
    if (facts.isRecent) add(MenuItem(MenuAction.RemoveFromRecents))
    if (facts.hasRecents) add(MenuItem(MenuAction.ClearRecents))
}
