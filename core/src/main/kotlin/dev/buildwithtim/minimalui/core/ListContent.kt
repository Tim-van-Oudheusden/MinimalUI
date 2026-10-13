package dev.buildwithtim.minimalui.core

/** What the list area shows: a blank screen while loading, a centred message, or the apps. */
sealed interface ListContent {
    /** The apps have not arrived yet: the screen stays blank, so the launcher still opens instantly. */
    data object Loading : ListContent

    /** No launchable app at all. */
    data object NoApps : ListContent

    /** The type-to-filter query keeps no app; the query line stays, so Backspace and Home still work. */
    data object NoMatch : ListContent

    /** The rows to list, in order: the root list, or the apps the query keeps. Never empty. */
    data class Apps(val entries: List<RootEntry>) : ListContent
}

/**
 * What to show for the [installed] apps, `null` until they first load. Without a query, the root list with its block
 * of [recents] as the [settings] allow; with one, only the apps it keeps, from all of them, with no block and no blank
 * row. No apps at all wins over no match: a different query would not help.
 */
fun listContent(
    installed: List<LauncherApp>?,
    query: String,
    recents: RecentApps = RecentApps(),
    settings: Settings = Settings(),
): ListContent {
    if (installed == null) return ListContent.Loading
    if (installed.isEmpty()) return ListContent.NoApps
    if (query.isEmpty()) return ListContent.Apps(rootEntries(installed, recents, settings))
    val shown = installed.filteredBy(query)
    return if (shown.isEmpty()) ListContent.NoMatch else ListContent.Apps(shown.map { RootEntry.App(it) })
}

/** The app on each row on screen, top to bottom, `null` for a row that holds none; empty while no app is listed. */
val ListContent.rowApps: List<LauncherApp?>
    get() = (this as? ListContent.Apps)?.entries.orEmpty().map { it.app }
