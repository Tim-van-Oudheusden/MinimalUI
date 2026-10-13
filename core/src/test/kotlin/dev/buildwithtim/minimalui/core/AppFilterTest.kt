package dev.buildwithtim.minimalui.core

import org.junit.Assert.assertEquals
import org.junit.Test

class AppFilterTest {

    private fun app(label: String) =
        LauncherApp(label = label, packageName = "pkg.$label", activityName = "pkg.$label.Main")

    private fun List<String>.filteredBy(query: String) = map(::app).filteredBy(query).map { it.label }

    @Test
    fun `an empty query keeps every app in order`() {
        assertEquals(listOf("Camera", "Mail", "Maps"), listOf("Camera", "Mail", "Maps").filteredBy(""))
    }

    @Test
    fun `keeps apps whose label starts with the query regardless of letter case`() {
        assertEquals(listOf("Mail", "Maps"), listOf("Camera", "Mail", "Maps", "Phone").filteredBy("mA"))
    }

    @Test
    fun `keeps apps with a later word starting with the query`() {
        assertEquals(listOf("Google Maps"), listOf("Google Maps", "Phone").filteredBy("maps"))
    }

    @Test
    fun `drops apps that only contain the query inside a word`() {
        assertEquals(emptyList<String>(), listOf("Camera", "Gmail").filteredBy("ma"))
    }

    @Test
    fun `lists apps whose label starts with the query before apps with a later word starting with it`() {
        assertEquals(
            listOf("Mail", "Maps", "Google Maps"),
            listOf("Google Maps", "Mail", "Maps").filteredBy("ma"),
        )
    }

    @Test
    fun `ignores accents in the label and the query`() {
        assertEquals(listOf("Éclair"), listOf("Duo", "Éclair").filteredBy("ecl"))
        assertEquals(listOf("Eclair"), listOf("Duo", "Eclair").filteredBy("écl"))
    }

    @Test
    fun `treats punctuation as a word break`() {
        assertEquals(listOf("K-9 Mail"), listOf("Camera", "K-9 Mail").filteredBy("9"))
    }
}
