package dev.buildwithtim.minimalui.core

/**
 * The apps the user marked as favourites (#68), pinned to the top of the root list with a `★` before their name, as
 * Cannoli marks them. Kept by component, like [RecentApps], so an app stays a favourite when it is relabelled.
 */
data class Favourites(
    val components: Set<String> = emptySet(),
) {
    operator fun contains(app: LauncherApp): Boolean = app.component in components

    /** [app] added, or removed when it already is a favourite: the context menu's one row does both. */
    fun toggled(app: LauncherApp): Favourites =
        if (app in this) Favourites(components - app.component) else Favourites(components + app.component)

    /** The favourites among [installed], in its order (so with their current labels); uninstalled ones are left out. */
    fun shownFrom(installed: List<LauncherApp>): List<LauncherApp> = installed.filter { it in this }

    /** One component per line, for app-private storage. */
    fun stored(): String = components.joinToString("\n")

    companion object {
        /** The favourites [stored] wrote; lines that are not a component are skipped. */
        fun fromStored(text: String?): Favourites =
            Favourites(
                text
                    .orEmpty()
                    .lines()
                    .filter(::isComponent)
                    .toSet(),
            )
    }
}
