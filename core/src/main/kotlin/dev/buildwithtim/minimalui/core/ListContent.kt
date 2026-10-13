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
 * of [favourites], [recents] as the [settings] allow and the [collections] row, all without the [hidden] apps (#71);
 * with one, only the apps it keeps, from all of them: the visible ones first, the favourites first among them, then
 * the hidden ones, flagged so they are drawn dim, each part in list order. With no block and no blank row. The query
 * matches the plain label: the `★` is only drawn. No apps at all wins over no match: a different query would not help;
 * so does a root with every app hidden.
 */
fun listContent(
    installed: List<LauncherApp>?,
    query: String,
    recents: RecentApps = RecentApps(),
    settings: Settings = Settings(),
    favourites: Favourites = Favourites(),
    collections: AppCollections = AppCollections(),
    hidden: HiddenApps = HiddenApps(),
): ListContent {
    if (installed == null) return ListContent.Loading
    val visible = hidden.visibleFrom(installed)
    if (installed.isEmpty() || query.isEmpty() && visible.isEmpty()) return ListContent.NoApps
    if (query.isEmpty()) return ListContent.Apps(rootEntries(visible, recents, settings, favourites, collections))
    val (starred, others) = visible.filteredBy(query).partition { it in favourites }
    val found = installed.filteredBy(query).filter { it in hidden }
    val shown = starred.map { RootEntry.App(it, isFavourite = true) } + others.map { RootEntry.App(it) } +
        found.map { RootEntry.App(it, isFavourite = it in favourites, isHidden = true) }
    return if (shown.isEmpty()) ListContent.NoMatch else ListContent.Apps(shown)
}

/** The app on each row on screen, top to bottom, `null` for a row that holds none; empty while no app is listed. */
val ListContent.rowApps: List<LauncherApp?>
    get() = (this as? ListContent.Apps)?.entries.orEmpty().map { it.app }
