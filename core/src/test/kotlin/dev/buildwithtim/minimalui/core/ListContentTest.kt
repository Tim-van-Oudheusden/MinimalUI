package dev.buildwithtim.minimalui.core

import org.junit.Assert.assertEquals
import org.junit.Test

class ListContentTest {

    private fun app(label: String) =
        LauncherApp(label = label, packageName = "pkg.$label", activityName = "pkg.$label.Main")

    private val apps = listOf("Camera", "Mail", "Maps").map(::app)

    @Test
    fun `apps not loaded yet are loading, whatever the query`() {
        assertEquals(ListContent.Loading, listContent(installed = null, query = ""))
        assertEquals(ListContent.Loading, listContent(installed = null, query = "ma"))
    }

    @Test
    fun `no apps installed is empty, whatever the query`() {
        assertEquals(ListContent.NoApps, listContent(installed = emptyList(), query = ""))
        assertEquals(ListContent.NoApps, listContent(installed = emptyList(), query = "ma"))
    }

    @Test
    fun `a query that keeps no app is no match`() {
        assertEquals(ListContent.NoMatch, listContent(installed = apps, query = "x"))
    }

    @Test
    fun `otherwise the apps the query keeps are shown`() {
        assertEquals(ListContent.Apps(apps.map { RootEntry.App(it) }), listContent(installed = apps, query = ""))
        assertEquals(
            ListContent.Apps(apps.drop(1).map { RootEntry.App(it) }),
            listContent(installed = apps, query = "ma"),
        )
    }

    private val mapsOpened = RecentApps().opened(app("Maps"), apps)

    @Test
    fun `without a query the root shows the recently used block above the full list`() {
        val content = listContent(installed = apps, query = "", recents = mapsOpened, settings = Settings())

        assertEquals(ListContent.Apps(rootEntries(apps, mapsOpened, Settings())), content)
        assertEquals(listOf(app("Maps"), null) + apps, content.rowApps)
    }

    @Test
    fun `a query shows only the apps it keeps, from all apps, with no block and no blank row`() {
        val content = listContent(installed = apps, query = "ma", recents = mapsOpened, settings = Settings())

        assertEquals(ListContent.Apps(apps.drop(1).map { RootEntry.App(it) }), content)
    }

    private val mapsStarred = Favourites().toggled(app("Maps"))

    @Test
    fun `without a query the root shows the favourites and the Collections row as rootEntries does`() {
        val games = AppCollections().added("Games", app("Camera"))
        val content = listContent(apps, "", mapsOpened, Settings(), mapsStarred, games)

        assertEquals(ListContent.Apps(rootEntries(apps, mapsOpened, Settings(), mapsStarred, games)), content)
    }

    @Test
    fun `a query lists the favourites it keeps first, each part in its order, and matches their plain label`() {
        val content = listContent(apps, "ma", favourites = mapsStarred)

        assertEquals(
            ListContent.Apps(listOf(RootEntry.App(app("Maps"), isFavourite = true), RootEntry.App(app("Mail")))),
            content,
        )
    }

    @Test
    fun `the star is not part of the label a query matches`() {
        assertEquals(ListContent.NoMatch, listContent(apps, "★", favourites = mapsStarred))
    }

    private val mailHidden = HiddenApps().hidden(app("Mail"), settingsPackage = null)

    @Test
    fun `without a query a hidden app leaves the full list and the blocks, which keep it stored`() {
        val opened = RecentApps().opened(app("Maps"), apps).opened(app("Mail"), apps)
        val starred = Favourites().toggled(app("Mail")).toggled(app("Camera"))
        val content = listContent(apps, "", opened, Settings(), starred, hidden = mailHidden)
        val visible = apps - app("Mail")

        assertEquals(ListContent.Apps(rootEntries(visible, opened, Settings(), starred)), content)
        assertEquals(listOf(app("Camera"), app("Maps"), null, app("Camera"), app("Maps")), content.rowApps)
        assertEquals(
            ListContent.Apps(rootEntries(apps, opened, Settings(), starred)),
            listContent(apps, "", opened, Settings(), starred, hidden = mailHidden.unhidden(app("Mail"))),
        )
    }

    @Test
    fun `without a query a collection holding only hidden apps has no Collections row`() {
        val games = AppCollections().added("Games", app("Mail"))

        assertEquals(
            ListContent.Apps((apps - app("Mail")).map { RootEntry.App(it) }),
            listContent(apps, "", collections = games, hidden = mailHidden),
        )
    }

    @Test
    fun `a query lists the hidden apps it keeps after the visible ones, flagged hidden, favourites first only above`() {
        val more = (apps + app("Marimba")).sortedForDisplay()
        val hidden = mailHidden.hidden(app("Camera"), null).hidden(app("Marimba"), null)
        val starred = Favourites().toggled(app("Marimba")).toggled(app("Maps"))

        assertEquals(
            ListContent.Apps(
                listOf(
                    RootEntry.App(app("Maps"), isFavourite = true),
                    RootEntry.App(app("Mail"), isHidden = true),
                    RootEntry.App(app("Marimba"), isFavourite = true, isHidden = true),
                ),
            ),
            listContent(more, "m", favourites = starred, hidden = hidden),
        )
    }

    @Test
    fun `a query that keeps only hidden apps still lists them`() {
        assertEquals(
            ListContent.Apps(listOf(RootEntry.App(app("Mail"), isHidden = true))),
            listContent(apps, "mai", hidden = mailHidden),
        )
    }

    @Test
    fun `with every app hidden the root has no apps, while a query still finds them`() {
        val allHidden = apps.fold(HiddenApps()) { hidden, app -> hidden.hidden(app, null) }

        assertEquals(ListContent.NoApps, listContent(apps, "", hidden = allHidden))
        assertEquals(
            ListContent.Apps(listOf(RootEntry.App(app("Camera"), isHidden = true))),
            listContent(apps, "cam", hidden = allHidden),
        )
    }

    @Test
    fun `the Groups row ends the root, from the visible apps only, and a query shows neither it nor its blank`() {
        val work = UserGroups().added("Work", app("Mail"))
        val visible = (apps - app("Mail")).map { RootEntry.App(it) }

        assertEquals(
            ListContent.Apps(apps.map { RootEntry.App(it) } + RootEntry.Blank("groups-blank") + RootEntry.Groups),
            listContent(apps, "", groups = work),
        )
        assertEquals(ListContent.Apps(visible), listContent(apps, "", hidden = mailHidden, groups = work))
        assertEquals(ListContent.Apps(listOf(RootEntry.App(app("Mail")))), listContent(apps, "mai", groups = work))
    }

    @Test
    fun `without apps on screen no row holds an app`() {
        assertEquals(emptyList<LauncherApp?>(), ListContent.NoMatch.rowApps)
    }
}
