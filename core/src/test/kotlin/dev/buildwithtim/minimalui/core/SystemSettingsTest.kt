package dev.buildwithtim.minimalui.core

import org.junit.Assert.assertEquals
import org.junit.Test

class SystemSettingsTest {
    private val settings = LauncherApp("Settings", "com.android.settings", "com.android.settings.Settings")
    private val clock = LauncherApp("Clock", "com.android.deskclock", "com.android.deskclock.DeskClock")

    @Test
    fun `adds system settings when no listed app opens it`() {
        val apps = listOf(clock).withSystemSettings(settings)

        assertEquals(listOf(clock, settings), apps)
    }

    @Test
    fun `keeps the list as is when the settings app is already listed`() {
        // The launcher entry and the ACTION_SETTINGS target can be different activities of the same app.
        val settingsLauncher = settings.copy(activityName = "com.android.settings.SettingsLauncher")

        val apps = listOf(clock, settingsLauncher).withSystemSettings(settings)

        assertEquals(listOf(clock, settingsLauncher), apps)
    }
}
