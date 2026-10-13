package dev.buildwithtim.minimalui.core

/** How many apps the recent list keeps, as Cannoli does. */
const val RECENT_APPS_LIMIT = 15

/**
 * The apps most recently opened through MinimalUI, newest first. They are kept by component (package and activity
 * name), not by label, so an app keeps its place when it is relabelled or the list is re-sorted.
 */
data class RecentApps(val components: List<String> = emptyList()) {

    /**
     * The list after opening [app]: it moves to the front without a duplicate, apps no longer [installed] are dropped
     * for good, and the oldest apps past [RECENT_APPS_LIMIT] fall off.
     */
    fun opened(app: LauncherApp, installed: List<LauncherApp>): RecentApps {
        val stillInstalled = installed.mapTo(HashSet()) { it.component }
        val older = components.filter { it != app.component && it in stillInstalled }
        return RecentApps((listOf(app.component) + older).take(RECENT_APPS_LIMIT))
    }

    /** The list without [app]. */
    fun without(app: LauncherApp): RecentApps = RecentApps(components - app.component)

    /** An empty list. */
    fun cleared(): RecentApps = RecentApps()

    /** The recent apps among [installed], newest first, as listed there (so with their current labels). */
    fun shownFrom(installed: List<LauncherApp>): List<LauncherApp> {
        val byComponent = installed.associateBy { it.component }
        return components.mapNotNull(byComponent::get)
    }

    /**
     * The row of [shown] to select when the user comes back from an app by any route but Home: the app opened last,
     * so one press reopens it (MinUI's behaviour). The top row when that app is not in [shown], because it was
     * uninstalled meanwhile or the filter hides it.
     */
    fun returnRow(shown: List<LauncherApp>): Int {
        val last = components.firstOrNull() ?: return 0
        return shown.indexOfFirst { it.component == last }.coerceAtLeast(0)
    }

    /** One component per line, for app-private storage. */
    fun stored(): String = components.joinToString("\n")

    companion object {
        /** The list [stored] wrote; lines that are not a component, repeats and lines past the limit are skipped. */
        fun fromStored(text: String?): RecentApps = RecentApps(
            text.orEmpty().lines().filter(COMPONENT::matches).distinct().take(RECENT_APPS_LIMIT),
        )

        private val COMPONENT = Regex("[^/]+/[^/]+")
    }
}

private val LauncherApp.component: String get() = "$packageName/$activityName"
