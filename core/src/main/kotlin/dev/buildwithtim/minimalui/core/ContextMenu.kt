package dev.buildwithtim.minimalui.core

/** What a row of the per-app context menu does. Later features add their own kinds (Favourite, Hide, Rename, …). */
enum class MenuAction {
    /** Android's App info screen for the app. */
    AppInfo,

    /** Android's uninstall prompt for the app. */
    Uninstall,

    /** Drops the app from the recently used apps. */
    RemoveFromRecents,

    /** Empties the recently used apps. */
    ClearRecents,
}

/** One row of the context menu. A disabled row is still listed, drawn dim, and does nothing when chosen. */
data class MenuItem(val action: MenuAction, val enabled: Boolean = true)

/**
 * What the menu needs to know about the app it was opened on: whether it is a system app ([isSystem]), the Settings
 * entry the list always keeps ([isSettingsEntry]), one of the recently used apps ([isRecent]), and whether there are
 * any recently used apps at all ([hasRecents]).
 */
data class AppFacts(val isSystem: Boolean, val isSettingsEntry: Boolean, val isRecent: Boolean, val hasRecents: Boolean)

/**
 * The context menu for an app, top to bottom. Uninstall stays listed but disabled for a system app, which cannot be
 * removed, and for the Settings entry, which keeps the way back to another home app open. The recents rows show only
 * when they would change something.
 */
fun contextMenu(facts: AppFacts): List<MenuItem> = buildList {
    add(MenuItem(MenuAction.AppInfo))
    add(MenuItem(MenuAction.Uninstall, enabled = !facts.isSystem && !facts.isSettingsEntry))
    if (facts.isRecent) add(MenuItem(MenuAction.RemoveFromRecents))
    if (facts.hasRecents) add(MenuItem(MenuAction.ClearRecents))
}
