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
        assertEquals(ListContent.Apps(apps), listContent(installed = apps, query = ""))
        assertEquals(ListContent.Apps(apps.drop(1)), listContent(installed = apps, query = "ma"))
    }
}
