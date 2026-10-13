package dev.buildwithtim.minimalui.core

import org.junit.Assert.assertEquals
import org.junit.Test

class AppCollectionsTest {

    private fun app(label: String) = LauncherApp(label, "pkg.$label", "pkg.$label.Main")

    private val installed = listOf("Alarm", "Bell", "Camera", "Drums").map(::app)

    private fun AppCollections.labelsIn(name: String, apps: List<LauncherApp> = installed) =
        appsIn(name, apps).map { it.label }

    private val games = AppCollections().added("Games", app("Camera")).added("Games", app("Alarm"))
        .added("Games", app("Drums"))

    @Test
    fun `there are no collections at first`() {
        assertEquals(emptyList<String>(), AppCollections().names(installed))
        assertEquals(emptyList<LauncherApp>(), AppCollections().appsIn("Games", installed))
    }

    @Test
    fun `a collection keeps its apps in the order they were added`() {
        assertEquals(listOf("Games"), games.names(installed))
        assertEquals(listOf("Camera", "Alarm", "Drums"), games.labelsIn("Games"))
    }

    @Test
    fun `adding an app a collection already holds changes nothing`() {
        assertEquals(games, games.added("Games", app("Alarm")))
    }

    @Test
    fun `a name is trimmed, a blank one creates nothing, and an existing name adds to that collection`() {
        val collections = AppCollections().added("  Work ", app("Bell")).added("Work", app("Alarm"))

        assertEquals(listOf("Work"), collections.names(installed))
        assertEquals(listOf("Bell", "Alarm"), collections.labelsIn("Work"))
        assertEquals(collections, collections.added("   ", app("Camera")))
    }

    @Test
    fun `a name keeps spaces and punctuation, its tabs and line breaks become spaces, and it reads back the same`() {
        val collections = AppCollections().added("My games!\tnew\n", app("Bell")).added("Work, etc.", app("Alarm"))

        assertEquals(listOf("My games! new", "Work, etc."), collections.names(installed))
        assertEquals(collections, AppCollections.fromStored(collections.stored()))
    }

    @Test
    fun `collections are named in alphabetical order, ignoring case`() {
        val collections = AppCollections().added("work", app("Bell")).added("Games", app("Alarm"))
            .added("Apps", app("Camera"))

        assertEquals(listOf("Apps", "Games", "work"), collections.names(installed))
    }

    @Test
    fun `removing an app keeps the others in order, and removing the last one deletes the collection`() {
        val withoutAlarm = games.removed("Games", app("Alarm"))

        assertEquals(listOf("Camera", "Drums"), withoutAlarm.labelsIn("Games"))
        val empty = withoutAlarm.removed("Games", app("Camera")).removed("Games", app("Drums"))
        assertEquals(AppCollections(), empty)
    }

    @Test
    fun `removing from a collection that does not exist changes nothing`() {
        assertEquals(games, games.removed("Work", app("Alarm")))
    }

    @Test
    fun `an app moves up and down one row, and not past either end`() {
        fun movedAlarm(step: Int) = games.moved("Games", app("Alarm"), step, installed).labelsIn("Games")

        assertEquals(listOf("Alarm", "Camera", "Drums"), movedAlarm(-1))
        assertEquals(listOf("Camera", "Drums", "Alarm"), movedAlarm(1))
        assertEquals(games, games.moved("Games", app("Camera"), -1, installed))
        assertEquals(games, games.moved("Games", app("Drums"), 1, installed))
        assertEquals(games, games.moved("Games", app("Bell"), 1, installed))
        assertEquals(games, games.moved("Work", app("Alarm"), 1, installed))
    }

    @Test
    fun `an app no longer installed is not shown, and moving past it skips it`() {
        val withoutAlarm = installed - app("Alarm")

        assertEquals(listOf("Camera", "Drums"), games.labelsIn("Games", withoutAlarm))
        val moved = games.moved("Games", app("Drums"), -1, withoutAlarm)
        assertEquals(listOf("Drums", "Camera"), moved.labelsIn("Games", withoutAlarm))
    }

    @Test
    fun `moving past an app not shown keeps that app in the collection, in its place`() {
        val withoutAlarm = installed - app("Alarm")

        val moved = games.moved("Games", app("Drums"), -1, withoutAlarm)
        assertEquals(listOf("Drums", "Alarm", "Camera"), moved.labelsIn("Games"))
    }

    @Test
    fun `a collection with no installed app is not named`() {
        val collections = games.added("Work", app("Bell"))

        assertEquals(listOf("Games"), collections.names(installed - app("Bell")))
    }

    @Test
    fun `the collections an app can be added to are those shown that do not hold it`() {
        val collections = games.added("Work", app("Bell")).added("Old", app("Bell"))
        val withoutBell = installed - app("Bell")

        assertEquals(listOf("Old", "Work"), collections.namesWithout(app("Alarm"), installed))
        assertEquals(listOf("Games"), collections.namesWithout(app("Bell"), installed))
        assertEquals(emptyList<String>(), collections.namesWithout(app("Alarm"), withoutBell))
        assertEquals(listOf("Games"), collections.namesWithout(app("Bell"), withoutBell))
    }

    @Test
    fun `stored collections read back the same`() {
        val collections = games.added("Work", app("Bell"))

        assertEquals(collections, AppCollections.fromStored(collections.stored()))
    }

    @Test
    fun `nothing stored, blank names, and lines without a component read as no collections`() {
        assertEquals(AppCollections(), AppCollections.fromStored(null))
        assertEquals(
            AppCollections().added("Work", app("Bell")),
            AppCollections.fromStored(
                "junk\n\tpkg.Bell/pkg.Bell.Main\nWork\tjunk\tpkg.Bell/pkg.Bell.Main\nEmpty\tjunk\n",
            ),
        )
    }
}
