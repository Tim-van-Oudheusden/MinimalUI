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

    private fun recentlyUsed(choice: Choice): Settings {
        var settings = Settings()
        while (settings[Setting.RecentlyUsed] != choice) settings = settings.cycled(Setting.RecentlyUsed, 1)
        return settings
    }

    private fun recent(label: String) = RootEntry.App(app(label), RootSection.Recent)

    private val all = apps.map { RootEntry.App(it) }

    @Test
    fun `with nothing recent the root is the full list alone, with no blank row`() {
        assertEquals(all, rootEntries(apps, RecentApps(), Settings()))
    }

    @Test
    fun `the newest recents come first, then one blank row, then the full list, which still holds them`() {
        val entries = rootEntries(apps, opened("Bell", "Easel"), Settings())

        assertEquals(listOf(recent("Easel"), recent("Bell"), RootEntry.Blank) + all, entries)
    }

    @Test
    fun `the block holds as many recents as the setting allows, three by default`() {
        val recents = opened("Alarm", "Bell", "Camera", "Drums", "Easel", "Flute")

        assertEquals(3, Settings().recentlyUsed)
        assertEquals(
            listOf(recent("Flute"), recent("Easel"), recent("Drums"), RootEntry.Blank),
            rootEntries(apps, recents, Settings()).take(4),
        )
        assertEquals(5, rootEntries(apps, recents, recentlyUsed(Choice.Five)).indexOf(RootEntry.Blank))
        assertEquals(6, rootEntries(apps, recents, recentlyUsed(Choice.Fifteen)).indexOf(RootEntry.Blank))
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

    @Test
    fun `only the blank row cannot be selected`() {
        assertFalse(RootEntry.Blank.selectable)
        assertTrue(recent("Bell").selectable)
        assertTrue(RootEntry.App(app("Bell")).selectable)
    }

    @Test
    fun `keys are unique, even for an app listed both in the block and in the full list`() {
        val entries = rootEntries(apps, opened("Alarm", "Bell"), Settings())

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
