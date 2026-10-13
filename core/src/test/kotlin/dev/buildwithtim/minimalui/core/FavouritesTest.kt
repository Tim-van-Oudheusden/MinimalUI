package dev.buildwithtim.minimalui.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FavouritesTest {

    private fun app(label: String) = LauncherApp(label, "pkg.$label", "pkg.$label.Main")

    private val installed = listOf("Alarm", "Bell", "Camera").map(::app)

    @Test
    fun `nothing is a favourite at first`() {
        assertFalse(app("Alarm") in Favourites())
        assertEquals(emptyList<LauncherApp>(), Favourites().shownFrom(installed))
    }

    @Test
    fun `toggling adds an app, and toggling it again removes it`() {
        val added = Favourites().toggled(app("Bell"))

        assertTrue(app("Bell") in added)
        assertFalse(app("Alarm") in added)
        assertFalse(app("Bell") in added.toggled(app("Bell")))
    }

    @Test
    fun `an app is a favourite by component, whatever its label`() {
        val favourites = Favourites().toggled(app("Bell"))

        assertTrue(app("Bell").copy(label = "Ring") in favourites)
    }

    @Test
    fun `favourites are shown in the order of the list they are taken from, not the order they were added`() {
        val favourites = Favourites().toggled(app("Camera")).toggled(app("Alarm"))

        assertEquals(listOf(app("Alarm"), app("Camera")), favourites.shownFrom(installed))
    }

    @Test
    fun `a favourite that is no longer installed is not shown`() {
        val favourites = Favourites().toggled(app("Alarm")).toggled(app("Bell"))

        assertEquals(listOf(app("Bell")), favourites.shownFrom(installed.drop(1)))
    }

    @Test
    fun `stored favourites read back the same`() {
        val favourites = Favourites().toggled(app("Camera")).toggled(app("Alarm"))

        assertEquals(favourites, Favourites.fromStored(favourites.stored()))
    }

    @Test
    fun `nothing stored, and lines that are not a component, read as no favourites`() {
        assertEquals(Favourites(), Favourites.fromStored(null))
        assertEquals(Favourites().toggled(app("Bell")), Favourites.fromStored("junk\npkg.Bell/pkg.Bell.Main\n\n"))
    }
}
