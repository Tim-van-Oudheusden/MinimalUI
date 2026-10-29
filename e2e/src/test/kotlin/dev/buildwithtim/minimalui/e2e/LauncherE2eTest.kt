package dev.buildwithtim.minimalui.e2e

import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test
import java.io.File
import java.util.Locale

/**
 * MinimalUI as the home app of a real device: the debug APK installed, Home pressed, keys sent through adb. Each test
 * starts from a fresh install's storage, so none depends on another's favourites or recent apps.
 */
class LauncherE2eTest {
    companion object {
        @BeforeClass
        @JvmStatic
        fun installTheDebugBuild() {
            Device.install(File(checkNotNull(System.getProperty("minimalui.apk")) { "minimalui.apk is not set" }))
        }
    }

    // The home screen as Home first shows it, with the installed apps loaded.
    private lateinit var home: Screen

    @Before
    fun startFromAFreshHomeScreen() {
        Device.freshHome()
        home = Device.waitForLauncher("the home screen's apps") { it.rows.isNotEmpty() }
    }

    // A word of the label starts with the query, as the filter matches (core's AppFilter).
    private fun String.hasWordStartingWith(query: String): Boolean =
        lowercase(Locale.ROOT).split(Regex("""[^\p{L}\p{N}]+""")).any { it.startsWith(query) }

    private fun filterBy(query: String): Screen {
        Device.type(query)
        return Device.waitForLauncher("only apps matching '$query'") { screen ->
            screen.rows.isNotEmpty() && screen.labels.all { it.hasWordStartingWith(query) }
        }
    }

    @Test
    fun `typing keeps the apps a word of whose name starts with it, names starting with it first`() {
        val filtered = filterBy("settings")

        assertTrue(
            filtered.labels.toString(),
            filtered.labels
                .first()
                .lowercase(Locale.ROOT)
                .startsWith("settings"),
        )
        assertTrue(filtered.labels.toString(), "MinimalUI settings" in filtered.labels)
    }

    @Test
    fun `typing what no app is called says No match`() {
        Device.type("zqxzqx")

        Device.waitForLauncher("No match and no rows") { "No match" in it.texts && it.rows.isEmpty() }
    }

    @Test
    fun `Home clears the filter`() {
        Device.type("zqxzqx")
        Device.waitForLauncher("No match") { "No match" in it.texts }

        Device.press("HOME")

        Device.waitForLauncher("the full list again") { "No match" !in it.texts && it.labels == home.labels }
    }

    @Test
    fun `Enter opens the app on the top row, and Home comes back to the launcher unfiltered`() {
        filterBy("settings")

        Device.press("ENTER")
        Device.waitForForeground(Device.settingsPackage)
        Device.press("HOME")

        Device.waitForLauncher("the list without the filter") { screen ->
            screen.labels.any { !it.hasWordStartingWith("settings") }
        }
    }

    @Test
    fun `an app opened from the list heads it after the launcher's process is killed`() {
        val opened = filterBy("settings").labels.first()
        assertNotEquals("a fresh home screen already starts with $opened: $home", opened, home.labels.first())
        Device.press("ENTER")
        Device.waitForForeground(Device.settingsPackage)

        Device.killMinimalUi()
        Device.press("HOME")

        Device.waitForLauncher("$opened first, as a recently used app") { it.labels.firstOrNull() == opened }
    }

    @Test
    fun `Menu opens the selected app's menu and Back closes it`() {
        val filtered = filterBy("settings")

        Device.press("MENU")
        Device.waitForLauncher("the app menu") { "App info" in it.labels && "Add to favourites" in it.labels }
        Device.press("BACK")

        Device.waitForLauncher("the filtered list again") { it.labels == filtered.labels }
    }

    @Test
    fun `Down then Enter on the MinimalUI settings row opens MinimalUI's settings`() {
        filterBy("settings")

        Device.press("DPAD_DOWN")
        Device.waitForLauncher("MinimalUI settings selected") { screen ->
            screen.rows.any { it.focused && it.label == "MinimalUI settings" }
        }
        Device.press("ENTER")

        Device.waitForLauncher("MinimalUI's settings") { screen ->
            listOf("Clock", "Battery percentage", "Recently used", "Hidden apps").all { setting ->
                screen.labels.any { it.startsWith(setting) }
            }
        }
    }
}
