package dev.buildwithtim.minimalui.core

import org.junit.Assert.assertEquals
import org.junit.Test

class RecentAppsTest {

    private fun app(label: String) = LauncherApp(label, "pkg.$label", "pkg.$label.Main")

    private val installed = (1..20).map { app("App$it") }

    private fun RecentApps.labelsFrom(apps: List<LauncherApp>) = shownFrom(apps).map { it.label }

    private fun openedInOrder(vararg labels: String) =
        labels.fold(RecentApps()) { recents, label -> recents.opened(app(label), installed) }

    @Test
    fun `nothing is recent at first`() {
        assertEquals(emptyList<String>(), RecentApps().labelsFrom(installed))
    }

    @Test
    fun `the most recently opened app comes first`() {
        assertEquals(listOf("App3", "App2", "App1"), openedInOrder("App1", "App2", "App3").labelsFrom(installed))
    }

    @Test
    fun `opening a recent app again moves it to the front without a duplicate`() {
        val recents = openedInOrder("App1", "App2", "App3", "App1")

        assertEquals(listOf("App1", "App3", "App2"), recents.labelsFrom(installed))
    }

    @Test
    fun `only the 15 most recent apps are kept`() {
        val recents = openedInOrder(*Array(20) { "App${it + 1}" })

        assertEquals(15, RECENT_APPS_LIMIT)
        assertEquals((20 downTo 6).map { "App$it" }, recents.labelsFrom(installed))
    }

    @Test
    fun `an uninstalled app is not shown, and is dropped at the next launch`() {
        val recents = openedInOrder("App1", "App2")
        val withoutApp1 = installed - app("App1")

        assertEquals(listOf("App2"), recents.labelsFrom(withoutApp1))
        // Dropped for good, so it frees its place and does not come back if a package of that name returns.
        val afterNextLaunch = recents.opened(app("App3"), withoutApp1)
        assertEquals(listOf("App3", "App2"), afterNextLaunch.labelsFrom(installed))
    }

    @Test
    fun `recent apps are matched by component, so they show the current label`() {
        val recents = openedInOrder("App1")
        val relabelled = listOf(LauncherApp("Renamed", "pkg.App1", "pkg.App1.Main"))

        assertEquals(listOf("Renamed"), recents.labelsFrom(relabelled))
    }

    @Test
    fun `the guaranteed Settings entry is recent like any other app`() {
        val settings = LauncherApp("Settings", "com.android.settings", "com.android.settings.Settings")
        val apps = installed.withSystemSettings(settings)

        val recents = RecentApps().opened(settings, apps).opened(app("App1"), apps)

        assertEquals(listOf("App1", "Settings"), recents.labelsFrom(apps))
    }

    @Test
    fun `one recent app can be removed, or all of them`() {
        val recents = openedInOrder("App1", "App2", "App3")

        assertEquals(listOf("App3", "App1"), recents.without(app("App2")).labelsFrom(installed))
        assertEquals(emptyList<String>(), recents.cleared().labelsFrom(installed))
    }

    @Test
    fun `the list survives a round trip through storage`() {
        val recents = openedInOrder("App1", "App2", "App3")

        assertEquals(recents, RecentApps.fromStored(recents.stored()))
        assertEquals(RecentApps(), RecentApps.fromStored(null))
        assertEquals(RecentApps(), RecentApps.fromStored(""))
    }

    @Test
    fun `damaged storage is read as far as it makes sense`() {
        val stored = (listOf("pkg.App1/pkg.App1.Main", "", "pkg.App1/pkg.App1.Main", "garbage") +
            (2..20).map { "pkg.App$it/pkg.App$it.Main" }).joinToString("\n")

        val recents = RecentApps.fromStored(stored)

        assertEquals(listOf("App1") + (2..15).map { "App$it" }, recents.labelsFrom(installed))
    }

    @Test
    fun `a return selects the row of the app opened last`() {
        val recents = openedInOrder("App1", "App3")

        assertEquals(2, recents.returnRow(installed))
    }

    @Test
    fun `a return selects the top when nothing was opened yet`() {
        assertEquals(0, RecentApps().returnRow(installed))
    }

    @Test
    fun `a return selects the top when the app opened last was uninstalled meanwhile`() {
        val recents = openedInOrder("App1", "App3")

        assertEquals(0, recents.returnRow(installed - app("App3")))
    }

    @Test
    fun `a return selects the top when the filter hides the app opened last`() {
        // App12, opened earlier, still matches the filter, but only the app opened last counts.
        val recents = openedInOrder("App12", "App3")

        assertEquals(0, recents.returnRow(installed.filteredBy("App1")))
    }

    @Test
    fun `on the root a return selects the app's row in the recently used block, the top row`() {
        val recents = openedInOrder("App1", "App3")
        val off = Settings().cycled(Setting.RecentlyUsed, -1)

        assertEquals(0, recents.returnRow(listContent(installed, "", recents, Settings()).rowApps))
        assertEquals(2, recents.returnRow(listContent(installed, "", recents, off).rowApps))
    }
}
