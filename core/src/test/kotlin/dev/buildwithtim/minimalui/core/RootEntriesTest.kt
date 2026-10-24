package dev.buildwithtim.minimalui.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RootEntriesTest {
    private fun app(label: String) = LauncherApp(label, "pkg.$label", "pkg.$label.Main")

    private val apps = listOf("Alarm", "Bell", "Camera", "Drums", "Easel", "Flute").map(::app)

    private fun opened(vararg labels: String) =
        labels.fold(RecentApps()) { recents, label -> recents.opened(app(label), apps) }

    // Cycles through the choices at most once: a choice the setting doesn't offer fails here instead of looping
    // forever, since cycled() wraps around (#216).
    private fun recentlyUsed(choice: Choice): Settings =
        generateSequence(Settings()) { it.cycled(Setting.RecentlyUsed, 1) }
            .take(Setting.RecentlyUsed.choices.size)
            .firstOrNull { it[Setting.RecentlyUsed] == choice }
            ?: throw AssertionError("RecentlyUsed never reaches $choice; it offers ${Setting.RecentlyUsed.choices}")

    private fun recent(label: String) = RootEntry.App(app(label), RootSection.Recent)

    private val all = apps.map { RootEntry.App(it) }

    @Test
    fun `with nothing recent the root is the full list alone, with no blank row`() {
        assertEquals(all, rootEntries(apps, RecentApps(), Settings()))
    }

    @Test
    fun `the newest recents come first, then one blank row, then the full list, which still holds them`() {
        val entries = rootEntries(apps, opened("Bell", "Easel"), Settings())

        assertEquals(listOf(recent("Easel"), recent("Bell"), RootEntry.Blank()) + all, entries)
    }

    @Test
    fun `the block holds as many recents as the setting allows, three by default`() {
        val recents = opened("Alarm", "Bell", "Camera", "Drums", "Easel", "Flute")

        assertEquals(3, Settings().recentlyUsed)
        assertEquals(
            listOf(recent("Flute"), recent("Easel"), recent("Drums"), RootEntry.Blank()),
            rootEntries(apps, recents, Settings()).take(4),
        )
        assertEquals(5, rootEntries(apps, recents, recentlyUsed(Choice.Five)).indexOf(RootEntry.Blank()))
        assertEquals(6, rootEntries(apps, recents, recentlyUsed(Choice.Fifteen)).indexOf(RootEntry.Blank()))
    }

    @Test
    fun `Off hides the block and its blank row`() {
        assertEquals(all, rootEntries(apps, opened("Bell"), recentlyUsed(Choice.Off)))
    }

    @Test
    fun `a recent app that is no longer installed leaves the block`() {
        assertEquals(all.drop(1), rootEntries(apps.drop(1), opened("Alarm"), Settings()))
    }

    @Test
    fun `no apps make no entries`() {
        assertEquals(emptyList<RootEntry>(), rootEntries(emptyList(), opened("Alarm"), Settings()))
    }

    private fun favourites(vararg labels: String) =
        labels.fold(Favourites()) { favourites, label -> favourites.toggled(app(label)) }

    private fun favourite(label: String) = RootEntry.App(app(label), RootSection.Favourite, isFavourite = true)

    private fun allStarred(labels: Set<String>) = apps.map { RootEntry.App(it, isFavourite = it.label in labels) }

    @Test
    fun `favourites come first, in the full list's order, then one blank row, then the full list, starred`() {
        val entries = rootEntries(apps, RecentApps(), Settings(), favourites("Easel", "Bell"))

        assertEquals(
            listOf(favourite("Bell"), favourite("Easel"), RootEntry.Blank()) + allStarred(setOf("Bell", "Easel")),
            entries,
        )
    }

    @Test
    fun `the recents follow the favourites, leaving out favourites, still as many as the setting allows`() {
        val recents = opened("Alarm", "Bell", "Camera", "Drums")
        val entries = rootEntries(apps, recents, Settings(), favourites("Drums", "Bell"))

        assertEquals(
            listOf(favourite("Bell"), favourite("Drums"), recent("Camera"), recent("Alarm"), RootEntry.Blank()),
            entries.take(5),
        )
    }

    @Test
    fun `a favourite that is no longer installed leaves the block, and no block means no blank row`() {
        assertEquals(all.drop(1), rootEntries(apps.drop(1), RecentApps(), Settings(), favourites("Alarm")))
    }

    private val games = AppCollections().added("Games", app("Drums"))

    @Test
    fun `with a collection shown, one Collections row ends the block, above the blank row`() {
        val entries = rootEntries(apps, opened("Bell"), Settings(), favourites("Alarm"), games)

        assertEquals(
            listOf(favourite("Alarm"), recent("Bell"), RootEntry.Collections, RootEntry.Blank()),
            entries.take(4),
        )
    }

    @Test
    fun `the Collections row alone still gets the blank row below it`() {
        assertEquals(
            listOf(RootEntry.Collections, RootEntry.Blank()) + all,
            rootEntries(apps, RecentApps(), Settings(), collections = games),
        )
    }

    @Test
    fun `a collection with no installed app makes no Collections row`() {
        val withoutDrums = apps - app("Drums")
        val entries = rootEntries(withoutDrums, RecentApps(), Settings(), collections = games)

        assertEquals(withoutDrums.map { RootEntry.App(it) }, entries)
    }

    private val chess = LauncherApp("Chess", "pkg.Chess", "pkg.Chess.Main", AppCategory.Games)

    @Test
    fun `with a group shown, one Groups row ends the root below its own blank row, after the full list`() {
        val withChess = apps + chess
        val entries = rootEntries(withChess, opened("Bell"), Settings())

        assertEquals(
            listOf(recent("Bell"), RootEntry.Blank()) + withChess.map { RootEntry.App(it) } +
                RootEntry.Blank("groups-blank") + RootEntry.Groups,
            entries,
        )
        assertEquals(entries.size, entries.map { it.key }.toSet().size)
    }

    @Test
    fun `a user group alone still makes the Groups row, and an empty one does not`() {
        val work = UserGroups().added("Work", app("Bell"))

        assertEquals(
            all + RootEntry.Blank("groups-blank") + RootEntry.Groups,
            rootEntries(apps, RecentApps(), Settings(), groups = work),
        )
        assertEquals(all.drop(2), rootEntries(apps.drop(2), RecentApps(), Settings(), groups = work))
    }

    private val workBell = app("Bell").copy(profile = 10L)

    @Test
    fun `a work app is not in the full list, only in the block where the user put it, and makes the Groups row`() {
        val withWork = apps + workBell
        val recents = RecentApps().opened(workBell, withWork)
        val entries = rootEntries(withWork, recents, Settings(), favourites("Bell"))
        val groupsRow = listOf(RootEntry.Blank("groups-blank"), RootEntry.Groups)

        assertEquals(
            listOf(favourite("Bell"), RootEntry.App(workBell, RootSection.Recent), RootEntry.Blank()) +
                allStarred(setOf("Bell")) + groupsRow,
            entries,
        )
        assertEquals(all + groupsRow, rootEntries(withWork, RecentApps(), Settings()))
        val bothStarred = rootEntries(withWork, recents, Settings(), favourites("Bell").toggled(workBell))
        assertEquals(bothStarred.size, bothStarred.map { it.key }.toSet().size)
    }

    @Test
    fun `an app clone is in the full list after the app it copies, as a personal app is`() {
        val cloneBell = app("Bell").copy(profile = 11L, isClone = true)
        val withClone = apps.take(2) + cloneBell + apps.drop(2)

        assertEquals(withClone.map { RootEntry.App(it) }, rootEntries(withClone, RecentApps(), Settings()))
    }

    @Test
    fun `with only work apps the root is the Groups row alone`() {
        assertEquals(listOf(RootEntry.Groups), rootEntries(listOf(workBell), RecentApps(), Settings()))
    }

    @Test
    fun `the selection follows the very app, not its copy in another profile`() {
        val withWork = listOf(app("Alarm"), app("Bell"), workBell)
        val before = listOf(RootEntry.App(workBell, RootSection.Recent), RootEntry.Blank()) + RootEntry.App(app("Bell"))
        val now = withWork.map { RootEntry.App(it) }

        assertEquals(2, followedRow(before, 0, now))
    }

    @Test
    fun `only the blank row cannot be selected`() {
        assertFalse(RootEntry.Blank().selectable)
        assertTrue(recent("Bell").selectable)
        assertTrue(RootEntry.App(app("Bell")).selectable)
        assertTrue(RootEntry.Collections.selectable)
        assertEquals(null, RootEntry.Collections.app)
        assertTrue(RootEntry.Groups.selectable)
        assertEquals(null, RootEntry.Groups.app)
    }

    @Test
    fun `keys are unique, even for an app listed both in the block and in the full list`() {
        val entries = rootEntries(apps, opened("Alarm", "Bell"), Settings(), favourites("Bell", "Camera"), games)

        assertEquals(entries.size, entries.map { it.key }.toSet().size)
    }

    @Test
    fun `the selection stays on its entry when rows are added or dropped above it`() {
        val before = rootEntries(apps, RecentApps(), Settings())
        val now = rootEntries(apps, opened("Easel"), Settings())

        assertEquals(4, followedRow(before, 2, now))
    }

    @Test
    fun `a recent dropped from the block keeps the selection on the same app in the full list`() {
        val before = rootEntries(apps, opened("Camera"), Settings())
        val now = rootEntries(apps, RecentApps(), Settings())

        assertEquals(2, followedRow(before, 0, now))
    }

    @Test
    fun `an entry gone from the list leaves the selection on the same row number`() {
        val before = rootEntries(apps, RecentApps(), Settings())

        assertEquals(1, followedRow(before, 1, all.filterNot { it.app.label == "Bell" }))
        assertEquals(7, followedRow(before, 7, all))
        assertEquals(1, followedRow(rootEntries(apps, opened("Bell"), Settings()), 1, all))
    }
}
