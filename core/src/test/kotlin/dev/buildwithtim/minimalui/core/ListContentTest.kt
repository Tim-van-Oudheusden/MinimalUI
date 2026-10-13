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

    @Test
    fun `without apps on screen no row holds an app`() {
        assertEquals(emptyList<LauncherApp?>(), ListContent.NoMatch.rowApps)
    }
}
