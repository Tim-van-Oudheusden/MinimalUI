package dev.buildwithtim.minimalui.core

import org.junit.Assert.assertEquals
import org.junit.Test

class ButtonHintsTest {
    private val maps = LauncherApp(label = "Maps", packageName = "pkg.maps", activityName = "pkg.maps.Main")
    private val listed = ListContent.Apps(listOf(RootEntry.App(maps), RootEntry.Blank(), RootEntry.Collections))

    private val menu = Hint(HintKey.Menu, HintAction.Menu)
    private val delete = Hint(HintKey.Delete, HintAction.Delete)
    private val back = Hint(HintKey.Back, HintAction.Back)
    private val open = Hint(HintKey.Enter, HintAction.Open)
    private val select = Hint(HintKey.Enter, HintAction.Select)

    @Test
    fun `the root with an app selected hints its menu on the left and opening it on the right`() {
        val hints = buttonHints(HintedScreen.Root(listed, query = "", selected = listed.entries[0]))

        assertEquals(Hints(left = listOf(menu), right = listOf(open)), hints)
    }

    @Test
    fun `the Collections or Groups row has no menu, so the root hints only opening it`() {
        assertEquals(
            Hints(right = listOf(open)),
            buttonHints(HintedScreen.Root(listed, query = "", selected = RootEntry.Collections)),
        )
        assertEquals(
            Hints(right = listOf(open)),
            buttonHints(HintedScreen.Root(listed, query = "", selected = RootEntry.Groups)),
        )
    }

    @Test
    fun `filtering hints Backspace on the left instead of the menu`() {
        val found = ListContent.Apps(listOf(RootEntry.App(maps)))

        assertEquals(
            Hints(left = listOf(delete), right = listOf(open)),
            buttonHints(HintedScreen.Root(found, query = "ma", selected = found.entries[0])),
        )
    }

    @Test
    fun `a query that matches nothing hints only Backspace`() {
        assertEquals(
            Hints(left = listOf(delete)),
            buttonHints(HintedScreen.Root(ListContent.NoMatch, query = "x", selected = null)),
        )
    }

    @Test
    fun `a root with no apps hints nothing, unless there is a query to delete`() {
        assertEquals(Hints(), buttonHints(HintedScreen.Root(ListContent.NoApps, query = "", selected = null)))
        assertEquals(
            Hints(left = listOf(delete)),
            buttonHints(HintedScreen.Root(ListContent.NoApps, query = "x", selected = null)),
        )
    }

    @Test
    fun `while the apps load the row stays blank, like the rest of the screen`() {
        assertEquals(Hints(), buttonHints(HintedScreen.Root(ListContent.Loading, query = "", selected = null)))
        assertEquals(Hints(), buttonHints(HintedScreen.Root(ListContent.Loading, query = "x", selected = null)))
    }

    @Test
    fun `an app list opened from the root hints the menu, going back and opening an app`() {
        assertEquals(
            Hints(left = listOf(menu), right = listOf(back, open)),
            buttonHints(HintedScreen.SubList(hasMenu = true, isEmpty = false)),
        )
    }

    @Test
    fun `the collections list has no menu`() {
        assertEquals(
            Hints(right = listOf(back, open)),
            buttonHints(HintedScreen.SubList(hasMenu = false, isEmpty = false)),
        )
    }

    @Test
    fun `an empty list hints only going back`() {
        assertEquals(Hints(right = listOf(back)), buttonHints(HintedScreen.SubList(hasMenu = true, isEmpty = true)))
        assertEquals(Hints(right = listOf(back)), buttonHints(HintedScreen.SubList(hasMenu = false, isEmpty = true)))
    }

    @Test
    fun `a menu and a list to add an app to hint going back and choosing a row`() {
        assertEquals(Hints(right = listOf(back, select)), buttonHints(HintedScreen.Menu))
        assertEquals(Hints(right = listOf(back, select)), buttonHints(HintedScreen.Choice))
    }

    @Test
    fun `the settings hint changing a value row and opening a link row`() {
        assertEquals(
            Hints(right = listOf(back, Hint(HintKey.Enter, HintAction.Change))),
            buttonHints(HintedScreen.Settings(SettingsRow.Value(Setting.Clock))),
        )
        assertEquals(
            Hints(right = listOf(back, open)),
            buttonHints(HintedScreen.Settings(SettingsRow.Link(SettingsLink.About))),
        )
    }

    @Test
    fun `About has nothing to choose, so it hints only going back`() {
        assertEquals(Hints(right = listOf(back)), buttonHints(HintedScreen.About))
    }

    @Test
    fun `the hidden apps hint unhiding one, or only going back when there are none`() {
        assertEquals(
            Hints(right = listOf(back, Hint(HintKey.Enter, HintAction.Unhide))),
            buttonHints(HintedScreen.HiddenApps(isEmpty = false)),
        )
        assertEquals(Hints(right = listOf(back)), buttonHints(HintedScreen.HiddenApps(isEmpty = true)))
    }

    @Test
    fun `the text editor hints cancelling and saving`() {
        assertEquals(
            Hints(right = listOf(Hint(HintKey.Back, HintAction.Cancel), Hint(HintKey.Enter, HintAction.Save))),
            buttonHints(HintedScreen.Editor),
        )
    }

    @Test
    fun `each key is drawn as its glyph`() {
        assertEquals(listOf("☰", "⌫", "⎋", "⏎"), HintKey.entries.map { it.glyph })
    }

    @Test
    fun `a screen narrower than 320 dp keeps only the right group`() {
        val hints = Hints(left = listOf(menu), right = listOf(back, open))

        assertEquals(Hints(right = listOf(back, open)), hints.fitting(widthDp = 319))
        assertEquals(hints, hints.fitting(widthDp = 320))
    }
}
