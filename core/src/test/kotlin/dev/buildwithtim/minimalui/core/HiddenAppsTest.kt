package dev.buildwithtim.minimalui.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HiddenAppsTest {

    private fun app(label: String) = LauncherApp(label, "pkg.$label", "pkg.$label.Main")

    private val installed = listOf("Alarm", "Bell", "Camera").map(::app)

    @Test
    fun `nothing is hidden at first`() {
        assertFalse(app("Alarm") in HiddenApps())
        assertEquals(installed, HiddenApps().visibleFrom(installed))
        assertEquals(emptyList<LauncherApp>(), HiddenApps().shownFrom(installed))
    }

    @Test
    fun `a hidden app leaves the visible apps, and unhiding brings it back in its place`() {
        val hidden = HiddenApps().hidden(app("Bell"), settingsPackage = null)

        assertTrue(app("Bell") in hidden)
        assertEquals(listOf(app("Alarm"), app("Camera")), hidden.visibleFrom(installed))
        assertEquals(installed, hidden.unhidden(app("Bell")).visibleFrom(installed))
    }

    @Test
    fun `an app is hidden by component, whatever its label`() {
        val hidden = HiddenApps().hidden(app("Bell"), settingsPackage = null)

        assertTrue(app("Bell").copy(label = "Ring") in hidden)
    }

    @Test
    fun `the Settings entry and MinimalUI's own settings can never be hidden`() {
        val settings = LauncherApp("Settings", "com.android.settings", "com.android.settings.Settings")
        val own = launcherSettings("MinimalUI settings", "dev.buildwithtim.minimalui")

        assertEquals(HiddenApps(), HiddenApps().hidden(settings, settingsPackage = "com.android.settings"))
        assertEquals(HiddenApps(), HiddenApps().hidden(own, settingsPackage = "com.android.settings"))
        assertEquals(HiddenApps(), HiddenApps().hidden(own, settingsPackage = null))
    }

    @Test
    fun `a hidden app that is uninstalled stays hidden, so reinstalling it keeps it hidden`() {
        val hidden = HiddenApps().hidden(app("Bell"), settingsPackage = null)

        assertEquals(emptyList<LauncherApp>(), hidden.shownFrom(installed - app("Bell")))
        assertEquals(listOf(app("Bell")), hidden.shownFrom(installed))
    }

    @Test
    fun `the hidden apps are listed alphabetically by their shown label`() {
        val hidden = HiddenApps().hidden(app("Camera"), null).hidden(app("Alarm"), null)
        val relabelled = listOf(app("Alarm").copy(label = "zoo"), app("Bell"), app("Camera"))

        assertEquals(listOf(app("Camera"), app("Alarm").copy(label = "zoo")), hidden.shownFrom(relabelled))
    }

    @Test
    fun `stored hidden apps read back the same`() {
        val hidden = HiddenApps().hidden(app("Camera"), null).hidden(app("Alarm"), null)

        assertEquals(hidden, HiddenApps.fromStored(hidden.stored()))
    }

    @Test
    fun `nothing stored, and lines that are not a component, read as nothing hidden`() {
        assertEquals(HiddenApps(), HiddenApps.fromStored(null))
        assertEquals(
            HiddenApps().hidden(app("Bell"), null),
            HiddenApps.fromStored("junk\npkg.Bell/pkg.Bell.Main\n\n"),
        )
    }
}
