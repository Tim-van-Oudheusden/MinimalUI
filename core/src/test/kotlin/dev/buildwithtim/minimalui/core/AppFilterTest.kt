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

    @Test
    fun `a capital sigma typed at the end of the query still matches a sigma inside a word`() {
        // Lower-casing turns a final Σ into ς, but the same letter inside the label's word lower-cases to σ.
        assertEquals(listOf("Οδοσήμανση", "ΟΔΟΣΗΜΑΝΣΗ"), listOf("Οδοσήμανση", "ΟΔΟΣΗΜΑΝΣΗ").filteredBy("ΟΔΟΣ"))
        assertEquals(listOf("ΟΔΟΣ ΤΑΞΙ"), listOf("ΟΔΟΣ ΤΑΞΙ").filteredBy("οδοσ"))
    }

    @Test
    fun `a vowel sign inside an Indic word does not start a new word`() {
        // ि (U+093F) is a spacing combining mark, not a letter, but it belongs to the letter before it.
        assertEquals(emptyList<String>(), listOf("हिंदी").filteredBy("द"))
        assertEquals(listOf("हिंदी"), listOf("हिंदी").filteredBy("हि"))
    }

    @Test
    fun `a letter outside the Basic Multilingual Plane does not end a word`() {
        // 𠮷 (U+20BB7) is stored as a surrogate pair; neither half is a letter on its own.
        assertEquals(emptyList<String>(), listOf("𠮷野家").filteredBy("野"))
        assertEquals(listOf("𠮷野家"), listOf("𠮷野家").filteredBy("𠮷"))
    }
}
