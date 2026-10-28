package dev.buildwithtim.minimalui

import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.buildwithtim.minimalui.core.AppGroup
import dev.buildwithtim.minimalui.core.LauncherApp
import dev.buildwithtim.minimalui.core.ListContent
import dev.buildwithtim.minimalui.core.ScreenStack
import dev.buildwithtim.minimalui.core.StatusPill
import dev.buildwithtim.minimalui.core.appGroups
import dev.buildwithtim.minimalui.core.duplicateSuffixes
import dev.buildwithtim.minimalui.core.listContent
import dev.buildwithtim.minimalui.core.rowApps
import dev.buildwithtim.minimalui.ui.Returned

/**
 * The launchable [apps] of every profile with their system labels and MinimalUI's settings row, unsorted, the package
 * of Android's Settings, which that row follows however the apps are named, and the [paused] profiles, whose apps are
 * turned off (#78).
 */
internal class Loaded(
    val apps: List<LauncherApp>,
    val settingsPackage: String?,
    val paused: Set<Long>,
)

/**
 * Everything the launcher shows: the installed apps, the user's choices in [stores], the lists on screen, the typed
 * query and the status pill. Each value is Compose state, so the screen follows every change.
 */
internal class LauncherState(
    val stores: LauncherStores,
) {
    // The installed apps with their system labels, null until the first load arrives, so loading (a blank screen) is
    // told apart from having no apps.
    var loaded by mutableStateOf<Loaded?>(null)

    /**
     * The installed apps as every list shows them: renamed ones by their alias, sorted by it (#72). Every list, the
     * filter, the recents, favourites, collections and hidden apps take their apps from here.
     */
    val apps: List<LauncherApp>? by derivedStateOf {
        loaded?.let { stores.aliases.labelled(it.apps, it.settingsPackage) }
    }

    /** What tells apart apps listed under the same name (#72): a dim suffix after the name. */
    val suffixes: Map<LauncherApp, String> by derivedStateOf { duplicateSuffixes(apps.orEmpty()) }

    var homePresses by mutableIntStateOf(0)
        private set
    var query by mutableStateOf("")
    var returned by mutableStateOf<Returned?>(null)
        private set
    var status by mutableStateOf<StatusPill?>(null)

    // The lists on screen (#70): the app list at the root, and the one opened from it on top, which alone shows.
    var screens by mutableStateOf(ScreenStack<Screen>(Screen.Root))
        private set

    // Set once an app opens from here and cleared by Home, so the next resume is a return by Back, Recents or the app
    // exiting, which selects that app again.
    var awayInApp = false

    /** The installed apps the user did not hide, as the root list, the collections and the groups show them. */
    val visibleApps: List<LauncherApp> get() = stores.hidden.visibleFrom(apps.orEmpty())

    /** The groups the Groups list shows: each holds at least one visible app. */
    val shownGroups: List<AppGroup> get() = appGroups(visibleApps, stores.userGroups)

    /**
     * The apps last opened from here, newest first, for the root screen (#70). Derived from the installed list, so an
     * uninstalled app leaves it as soon as the package-change receiver reloads the apps.
     */
    val recentApps: List<LauncherApp> get() = stores.recents.shownFrom(apps.orEmpty())

    /** Whether [app] belongs to a work profile whose apps are turned off (#78): it stays listed, dim. */
    fun isPaused(app: LauncherApp): Boolean = app.profile in loaded?.paused.orEmpty()

    /**
     * What the root list shows: the full list under its block of favourites, recently used apps and the Collections
     * row, and above the Groups row, without the hidden apps, or the apps the query keeps, the hidden ones last.
     */
    fun shownList(): ListContent =
        listContent(
            apps,
            query,
            stores.recents,
            stores.settings,
            stores.favourites,
            stores.collections,
            stores.hidden,
            stores.userGroups,
        )

    /** [screen] opened on top of the one on screen. */
    fun push(screen: Screen) {
        screens = screens.pushed(screen)
    }

    /** Back or Esc on a list opened from the root, [times] over: the list below comes back as it was. */
    fun back(times: Int = 1) {
        repeat(times) { screens = screens.popped() }
    }

    /**
     * An edit opened from a menu is done: the app's or group's menu on top, and every screen opened from it, close,
     * back on the list the menu was opened from.
     */
    fun backPastMenu() {
        screens = screens.poppedPast { it is Screen.Menu || it is Screen.GroupMenu }
    }

    /** Home: back on the root list, unfiltered. */
    fun home() {
        awayInApp = false
        screens = screens.home()
        homePresses++
        query = ""
    }

    /** Back from the app opened from here: on the root, its row in the recently used block is selected. */
    fun cameBack() {
        if (!awayInApp) return
        awayInApp = false
        // Against the list as it shows now: on the root, the app's row in the recently used block. A collection the
        // app was opened from keeps its own selection, on that app.
        if (screens.isRoot) {
            returned = Returned(stores.recents.returnRow(shownList().rowApps), (returned?.count ?: 0) + 1)
        }
    }
}
