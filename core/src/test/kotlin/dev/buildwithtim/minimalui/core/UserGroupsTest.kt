package dev.buildwithtim.minimalui.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UserGroupsTest {
    private fun app(label: String) = LauncherApp(label, "pkg.$label", "pkg.$label.Main")

    private val work = UserGroups().added("Work", app("Mail")).added("Work", app("Notes"))

    @Test
    fun `adding the first app creates the group, which then holds it`() {
        assertEquals(listOf("Work"), work.names)
        assertTrue(work.holds("Work", app("Mail")))
        assertTrue(work.holds("Work", app("Notes")))
        assertFalse(work.holds("Work", app("Bell")))
        assertFalse(work.holds("Play", app("Mail")))
    }

    @Test
    fun `a name is trimmed, and a blank one creates nothing`() {
        assertEquals(work, work.added("  Work ", app("Notes")))
        assertEquals(work, work.added(" \t", app("Bell")))
    }

    @Test
    fun `removing an app takes it out, and the last one out deletes the group`() {
        val left = work.removed("Work", app("Mail"))

        assertEquals(listOf("Work"), left.names)
        assertFalse(left.holds("Work", app("Mail")))
        assertEquals(UserGroups(), left.removed("Work", app("Notes")))
        assertEquals(work, work.removed("Play", app("Mail")))
    }

    @Test
    fun `renaming keeps the apps under the new, trimmed name, and a blank or unknown name changes nothing`() {
        val renamed = work.renamed("Work", " Office ")

        assertEquals(listOf("Office"), renamed.names)
        assertTrue(renamed.holds("Office", app("Mail")))
        assertTrue(renamed.holds("Office", app("Notes")))
        assertEquals(work, work.renamed("Work", "  "))
        assertEquals(work, work.renamed("Work", "Work"))
        assertEquals(work, work.renamed("Play", "Games"))
    }

    @Test
    fun `renaming to an existing group's name merges the two`() {
        val both = work.added("Play", app("Drums")).added("Play", app("Mail")).renamed("Play", "Work")

        assertEquals(listOf("Work"), both.names)
        val members = listOf("Mail", "Notes", "Drums").map { "pkg.$it/pkg.$it.Main" }.toSet()
        assertEquals(members, both.byName["Work"])
    }

    @Test
    fun `deleting a group drops it with its apps, and the others stay`() {
        val both = work.added("Play", app("Drums"))

        assertEquals(listOf("Play", "Work"), both.names)
        assertEquals(listOf("Play"), both.deleted("Work").names)
        assertEquals(both, both.deleted("Games"))
    }

    @Test
    fun `an app can be added to the groups that do not hold it, alphabetical`() {
        val groups = work.added("play", app("Drums")).added("Art", app("Easel"))

        assertEquals(listOf("Art", "play"), groups.namesWithout(app("Mail")))
        assertEquals(listOf("Art", "play", "Work"), groups.namesWithout(app("Bell")))
    }

    @Test
    fun `groups survive storage, and broken lines are skipped`() {
        val groups = work.added("Play", app("Drums"))

        assertEquals(groups, UserGroups.fromStored(groups.stored()))
        assertEquals(UserGroups(), UserGroups.fromStored(null))
        assertEquals(
            UserGroups(mapOf("Play" to setOf("pkg.Drums/pkg.Drums.Main"))),
            UserGroups.fromStored(" \tpkg.A/pkg.A.Main\nPlay\tnot-a-component\tpkg.Drums/pkg.Drums.Main\nEmpty\n"),
        )
    }

    @Test
    fun `a name that differs only in case adds to the existing group, which keeps its spelling`() {
        val groups = work.added("work", app("Bell"))

        assertEquals(listOf("Work"), groups.names)
        assertTrue(groups.holds("Work", app("Bell")))
    }

    @Test
    fun `a group is found by its name in any case`() {
        assertTrue(work.holds("WORK", app("Mail")))
        assertFalse(work.removed("work", app("Mail")).holds("Work", app("Mail")))
        assertEquals(UserGroups(), work.deleted("work"))
        assertEquals(listOf("Office"), work.renamed("work", "Office").names)
    }

    @Test
    fun `renaming to another group's name in another case merges into that group`() {
        val both = work.added("Play", app("Drums")).renamed("Play", "WORK")

        assertEquals(listOf("Work"), both.names)
        assertTrue(both.holds("Work", app("Drums")))
    }

    @Test
    fun `renaming a group to its own name in another case changes its spelling`() {
        val renamed = work.renamed("Work", "WORK")

        assertEquals(listOf("WORK"), renamed.names)
        assertTrue(renamed.holds("WORK", app("Mail")))
    }

    @Test
    fun `stored groups whose names differ only in case load as one, under the first spelling`() {
        assertEquals(
            UserGroups(mapOf("Fun" to setOf("pkg.A/pkg.A.Main", "pkg.B/pkg.B.Main"))),
            UserGroups.fromStored("Fun\tpkg.A/pkg.A.Main\nfun\tpkg.B/pkg.B.Main"),
        )
    }
}
