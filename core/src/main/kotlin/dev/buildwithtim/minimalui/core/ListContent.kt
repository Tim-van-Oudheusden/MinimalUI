package dev.buildwithtim.minimalui.core

/** What the list area shows: a blank screen while loading, a centred message, or the apps. */
sealed interface ListContent {
    /** The apps have not arrived yet: the screen stays blank, so the launcher still opens instantly. */
    data object Loading : ListContent

    /** No launchable app at all. */
    data object NoApps : ListContent

    /** The type-to-filter query keeps no app; the query line stays, so Backspace and Home still work. */
    data object NoMatch : ListContent

    /** The apps to list, in order. Never empty. */
    data class Apps(val apps: List<LauncherApp>) : ListContent
}

/**
 * What to show for the [installed] apps, `null` until they first load, filtered by [query]. No apps at all wins over
 * no match: a different query would not help.
 */
fun listContent(installed: List<LauncherApp>?, query: String): ListContent {
    if (installed == null) return ListContent.Loading
    if (installed.isEmpty()) return ListContent.NoApps
    val shown = installed.filteredBy(query)
    return if (shown.isEmpty()) ListContent.NoMatch else ListContent.Apps(shown)
}
