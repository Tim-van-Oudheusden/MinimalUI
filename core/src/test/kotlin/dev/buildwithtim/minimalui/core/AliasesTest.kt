package dev.buildwithtim.minimalui.core

import org.junit.Assert.assertEquals
import org.junit.Test

class AliasesTest {

    private fun app(label: String) = LauncherApp(label, "pkg.$label", "pkg.$label.Main")

    private val installed = listOf("Alarm", "Bell", "Camera").map(::app)

    private fun Aliases.labels(apps: List<LauncherApp> = installed, settingsPackage: String? = null) =
        labelled(apps, settingsPackage).map { it.label }

    @Test
    fun `an alias replaces the label and moves the app to its sort place`() {
        val aliases = Aliases().renamed(app("Alarm"), "Zoo")

        assertEquals(listOf("Bell", "Camera", "Zoo"), aliases.labels())
    }

    @Test
    fun `the alias sorts with the list's collator, ignoring case and accents`() {
        val aliases = Aliases().renamed(app("Camera"), "écran")

        assertEquals(listOf("Alarm", "Bell", "écran"), aliases.labels())
    }

    @Test
    fun `type-to-filter matches the alias, not the system label`() {
        val labelled = Aliases().renamed(app("Alarm"), "Wake up").labelled(installed, settingsPackage = null)

        assertEquals(listOf("Wake up"), labelled.filteredBy("wa").map { it.label })
        assertEquals(emptyList<LauncherApp>(), labelled.filteredBy("ala"))
    }

    @Test
    fun `a blank alias, or the system label, clears it and restores the system label`() {
        val aliases = Aliases().renamed(app("Alarm"), "Zoo")

        assertEquals(Aliases(), aliases.renamed(app("Alarm"), "  "))
        assertEquals(Aliases(), aliases.renamed(app("Alarm"), " Alarm "))
        assertEquals(listOf("Alarm", "Bell", "Camera"), aliases.renamed(app("Alarm"), "").labels())
    }

    @Test
    fun `an alias is trimmed, and tabs and line breaks in it become spaces`() {
        val aliases = Aliases().renamed(app("Alarm"), "  Wake\tme\nup \r\n")

        assertEquals("Wake me up", aliases.labelled(installed, null).last().label)
    }

    @Test
    fun `an alias is kept by component, so it survives an app update that changes the label`() {
        val aliases = Aliases().renamed(app("Alarm"), "Zoo")
        val updated = listOf(app("Alarm").copy(label = "Alarm 2"), app("Bell"))

        assertEquals(listOf("Bell", "Zoo"), aliases.labels(updated))
    }

    @Test
    fun `the MinimalUI settings row stays right after Android's Settings after a re-sort`() {
        val entry = launcherSettings("MinimalUI settings", "dev.buildwithtim.minimalui")
        val settings = LauncherApp("Settings", "com.android.settings", "com.android.settings.Settings")
        val apps = listOf(app("Alarm"), settings, entry, app("Zebra"))

        val renamed = Aliases().renamed(settings, "Android")

        assertEquals(
            listOf("Alarm", "Android", "MinimalUI settings", "Zebra"),
            renamed.labels(apps, settingsPackage = "com.android.settings"),
        )
        val withoutSettings = listOf(app("Alarm"), entry, app("Zebra"))
        assertEquals(listOf("Alarm", "MinimalUI settings", "Zebra"), Aliases().labels(withoutSettings))
    }

    @Test
    fun `stored aliases read back the same, whatever characters they hold`() {
        val aliases = Aliases().renamed(app("Alarm"), "Wake / up: ★ 100% \\ \"x\"").renamed(app("Bell"), "Ring\tring")

        assertEquals(aliases, Aliases.fromStored(aliases.stored()))
    }

    @Test
    fun `nothing stored, and lines without a component or an alias, read as no aliases`() {
        assertEquals(Aliases(), Aliases.fromStored(null))
        val stored = "garbage\npkg.Alarm/pkg.Alarm.Main\npkg.Bell/pkg.Bell.Main\t \nx\tName"
        assertEquals(Aliases(), Aliases.fromStored(stored))
    }
}
