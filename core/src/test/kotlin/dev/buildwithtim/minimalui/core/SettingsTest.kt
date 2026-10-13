package dev.buildwithtim.minimalui.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsTest {

    @Test
    fun `every setting starts at its default, and the status items default to on`() {
        val settings = Settings()

        assertEquals(Choice.On, settings[Setting.Clock])
        assertTrue(settings.isOn(Setting.Clock))
        assertTrue(settings.isOn(Setting.BatteryPercentage))
    }

    @Test
    fun `keys are distinct, so no two settings overwrite each other in storage`() {
        assertEquals(Setting.entries.size, Setting.entries.map { it.key }.toSet().size)
    }

    @Test
    fun `every default is one of the setting's own choices`() {
        Setting.entries.forEach { assertTrue(it.name, it.default in it.choices) }
    }

    @Test
    fun `cycling forward moves to the next choice and wraps past the last one`() {
        val off = Settings().cycled(Setting.Clock, 1)

        assertFalse(off.isOn(Setting.Clock))
        assertEquals(Choice.On, off.cycled(Setting.Clock, 1)[Setting.Clock])
    }

    @Test
    fun `cycling back wraps past the first choice`() {
        assertEquals(Choice.Off, Settings().cycled(Setting.Clock, -1)[Setting.Clock])
    }

    @Test
    fun `cycling changes only the one setting`() {
        val changed = Settings().cycled(Setting.Clock, 1)

        assertTrue(changed.isOn(Setting.BatteryPercentage))
    }

    @Test
    fun `settings read back from storage are the ones stored`() {
        val changed = Settings().cycled(Setting.BatteryPercentage, 1)
        val stored = changed.stored()

        assertEquals(changed, Settings.fromStored { stored[it] })
        assertEquals("Off", stored[Setting.BatteryPercentage.key])
        assertEquals("On", stored[Setting.Clock.key])
    }

    @Test
    fun `a missing or unknown stored value falls back to the default`() {
        val settings = Settings.fromStored { key -> if (key == Setting.Clock.key) "Sideways" else null }

        assertEquals(Settings(), settings)
    }

    @Test
    fun `the list shows every setting, then the link rows, with About last`() {
        val rows = settingsRows

        assertEquals(Setting.entries.map { SettingsRow.Value(it) }, rows.take(Setting.entries.size))
        assertEquals(SettingsRow.Link(SettingsLink.About), rows.last())
        assertEquals(Setting.entries.size + SettingsLink.entries.size, rows.size)
    }

    @Test
    fun `Recently used offers Off, 3, 5 and 15 rows, 3 by default`() {
        assertEquals(listOf(Choice.Off, Choice.Three, Choice.Five, Choice.Fifteen), Setting.RecentlyUsed.choices)
        assertEquals(3, Settings().recentlyUsed)

        val counts = generateSequence(Settings()) { it.cycled(Setting.RecentlyUsed, 1) }.take(4).map { it.recentlyUsed }
        assertEquals(listOf(3, 5, 15, 0), counts.toList())
        assertEquals(RECENT_APPS_LIMIT, Choice.Fifteen.count)
    }
}
