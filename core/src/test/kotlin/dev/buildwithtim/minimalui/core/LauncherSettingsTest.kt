package dev.buildwithtim.minimalui.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LauncherSettingsTest {

    private val entry = launcherSettings("MinimalUI settings", "dev.buildwithtim.minimalui")
    private val alarm = LauncherApp("Alarm", "com.example.alarm", "com.example.alarm.Main")
    private val settings = LauncherApp("Settings", "com.android.settings", "com.android.settings.Settings")
    private val zebra = LauncherApp("Zebra", "com.example.zebra", "com.example.zebra.Main")

    @Test
    fun `the entry is told apart from every real app`() {
        assertTrue(entry.isLauncherSettings)
        assertFalse(alarm.isLauncherSettings)
        assertFalse(LauncherApp("MinimalUI", "dev.buildwithtim.minimalui", "dev.buildwithtim.minimalui.MainActivity").isLauncherSettings)
    }

    @Test
    fun `the entry goes directly after the system settings row`() {
        val apps = listOf(alarm, settings, zebra).withLauncherSettings(entry, settingsPackage = "com.android.settings")

        assertEquals(listOf(alarm, settings, entry, zebra), apps)
    }

    @Test
    fun `the entry follows the last row of the settings app when it lists more than one`() {
        val more = settings.copy(label = "Settings 2", activityName = "com.android.settings.Other")

        val apps = listOf(settings, more, zebra).withLauncherSettings(entry, settingsPackage = "com.android.settings")

        assertEquals(listOf(settings, more, entry, zebra), apps)
    }

    @Test
    fun `without a system settings row the entry takes its alphabetical place`() {
        assertEquals(listOf(alarm, entry, zebra), listOf(alarm, zebra).withLauncherSettings(entry, settingsPackage = null))
        assertEquals(listOf(alarm, entry, zebra), listOf(alarm, zebra).withLauncherSettings(entry, "com.missing"))
    }

    @Test
    fun `the entry survives filtering like any app`() {
        val apps = listOf(alarm, settings, zebra).withLauncherSettings(entry, "com.android.settings")

        assertEquals(listOf(entry), apps.filteredBy("min"))
    }
}
