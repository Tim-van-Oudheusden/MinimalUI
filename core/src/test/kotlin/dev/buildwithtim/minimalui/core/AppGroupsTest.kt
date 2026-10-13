package dev.buildwithtim.minimalui.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppGroupsTest {

    private fun app(label: String, category: AppCategory? = AppCategory.Other) =
        LauncherApp(label, "pkg.$label", "pkg.$label.Main", category)

    private val chess = app("Chess", AppCategory.Games)
    private val drums = app("Drums", AppCategory.Audio)
    private val mail = app("Mail", AppCategory.Productivity)
    private val notes = app("Notes")
    private val zebra = app("Zebra", AppCategory.Games)

    // In list order, as the main list shows them.
    private val apps = listOf(chess, drums, mail, notes, zebra)

    private fun List<AppGroup>.names() = map { it.name }

    private fun List<AppGroup>.labelsIn(name: String) = single { it.name == name }.apps.map { it.label }

    @Test
    fun `each category with an app is a group, alphabetical with Other last, its apps in list order`() {
        val groups = appGroups(apps)

        assertEquals(listOf("Audio", "Games", "Productivity", "Other"), groups.names())
        assertEquals(listOf("Chess", "Zebra"), groups.labelsIn("Games"))
        assertEquals(listOf("Notes"), groups.labelsIn("Other"))
        assertTrue(groups.none { it.isUserGroup })
    }

    @Test
    fun `a user group lists its apps in list order, by the names they are shown under`() {
        val renamed = mail.copy(label = "Inbox")
        val user = UserGroups().added("Work", notes).added("Work", renamed).added("Work", chess)
        val groups = appGroups(listOf(chess, renamed, notes, zebra), user)

        assertEquals(listOf("Games", "Productivity", "Work", "Other"), groups.names())
        assertEquals(listOf("Chess", "Inbox", "Notes"), groups.labelsIn("Work"))
        assertTrue(groups.single { it.name == "Work" }.isUserGroup)
    }

    @Test
    fun `a user group named as an automatic group is one group holding the apps of both`() {
        val user = UserGroups().added("Games", mail).added("Games", zebra).added("Other", drums)
        val groups = appGroups(apps, user)

        assertEquals(listOf("Audio", "Games", "Productivity", "Other"), groups.names())
        assertEquals(listOf("Chess", "Mail", "Zebra"), groups.labelsIn("Games"))
        assertEquals(listOf("Drums", "Notes"), groups.labelsIn("Other"))
        assertTrue(groups.single { it.name == "Games" }.isUserGroup)
        assertFalse(groups.single { it.name == "Audio" }.isUserGroup)
    }

    @Test
    fun `groups with no listed app are hidden, so uninstalled and hidden apps leave their groups`() {
        val user = UserGroups().added("Work", mail).added("Music", drums)
        val listed = listOf(chess, notes)

        assertEquals(listOf("Games", "Other"), appGroups(listed, user).names())
        assertEquals(emptyList<AppGroup>(), appGroups(emptyList(), user))
    }

    @Test
    fun `MinimalUI's settings row is in no automatic group, only in the user groups that hold it`() {
        val settingsRow = launcherSettings("MinimalUI settings", "dev.minimalui")
        val user = UserGroups().added("Tools", settingsRow)

        assertEquals(emptyList<AppGroup>(), appGroups(listOf(settingsRow)))
        assertEquals(listOf(AppGroup("Tools", listOf(settingsRow), true)), appGroups(listOf(settingsRow), user))
    }

    @Test
    fun `Android's category codes map to their automatic groups, anything else to Other`() {
        val codes = (-1..9).associateWith { appCategory(it, sdk = 35) }

        assertEquals(
            mapOf(
                -1 to AppCategory.Other,
                0 to AppCategory.Games,
                1 to AppCategory.Audio,
                2 to AppCategory.Video,
                3 to AppCategory.Images,
                4 to AppCategory.Social,
                5 to AppCategory.News,
                6 to AppCategory.Maps,
                7 to AppCategory.Productivity,
                8 to AppCategory.Accessibility,
                9 to AppCategory.Other,
            ),
            codes,
        )
    }

    @Test
    fun `Accessibility exists from API 31 on, so below it the code means Other`() {
        assertEquals(AppCategory.Accessibility, appCategory(8, sdk = 31))
        assertEquals(AppCategory.Other, appCategory(8, sdk = 30))
    }
}
