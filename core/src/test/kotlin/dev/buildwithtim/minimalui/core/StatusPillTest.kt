package dev.buildwithtim.minimalui.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StatusPillTest {

    @Test
    fun `the charge is the battery level as a whole percentage of its scale`() {
        assertEquals(Battery(percent = 57, charging = false), battery(level = 57, scale = 100, charging = false))
        assertEquals(67, battery(level = 2, scale = 3, charging = false)?.percent)
        assertEquals(100, battery(level = 255, scale = 255, charging = true)?.percent)
    }

    @Test
    fun `no battery item shows when the level or scale is unknown`() {
        assertNull(battery(level = -1, scale = 100, charging = false))
        assertNull(battery(level = 50, scale = 0, charging = false))
        assertNull(battery(level = 101, scale = 100, charging = false))
    }

    @Test
    fun `the text is the percentage`() {
        assertEquals("8%", Battery(8, charging = false).text)
    }

    @Test
    fun `the fill is proportional to the charge`() {
        assertEquals(0.57f, Battery(57, charging = false).fill, 0.0001f)
        assertEquals(1f, Battery(100, charging = false).fill, 0.0001f)
    }

    @Test
    fun `above 20 percent the battery looks normal`() {
        val battery = Battery(21, charging = false)

        assertFalse(battery.lowFill)
        assertFalse(battery.lowOutline)
        assertFalse(battery.bolt)
    }

    @Test
    fun `at 20 percent and below the fill turns low`() {
        val battery = Battery(20, charging = false)

        assertTrue(battery.lowFill)
        assertFalse(battery.lowOutline)
    }

    @Test
    fun `at 10 percent and below the outline turns low too`() {
        val battery = Battery(10, charging = false)

        assertTrue(battery.lowFill)
        assertTrue(battery.lowOutline)
        assertFalse(Battery(11, charging = false).lowOutline)
    }

    @Test
    fun `while charging the battery shows a bolt instead of the fill and never looks low`() {
        val battery = Battery(5, charging = true)

        assertTrue(battery.bolt)
        assertEquals(0f, battery.fill, 0f)
        assertFalse(battery.lowFill)
        assertFalse(battery.lowOutline)
        assertEquals("5%", battery.text)
    }

    @Test
    fun `the 24-hour clock pads the hour`() {
        assertEquals("07:05", clockText(hour = 7, minute = 5, twentyFourHour = true, am = "AM", pm = "PM"))
        assertEquals("00:00", clockText(hour = 0, minute = 0, twentyFourHour = true, am = "AM", pm = "PM"))
        assertEquals("23:59", clockText(hour = 23, minute = 59, twentyFourHour = true, am = "AM", pm = "PM"))
    }

    @Test
    fun `the 12-hour clock counts from 12 and adds the locale's marker`() {
        assertEquals("12:00 AM", clockText(hour = 0, minute = 0, twentyFourHour = false, am = "AM", pm = "PM"))
        assertEquals("7:05 AM", clockText(hour = 7, minute = 5, twentyFourHour = false, am = "AM", pm = "PM"))
        assertEquals("12:30 PM", clockText(hour = 12, minute = 30, twentyFourHour = false, am = "AM", pm = "PM"))
        assertEquals("11:59 pm", clockText(hour = 23, minute = 59, twentyFourHour = false, am = "am", pm = "pm"))
    }

    @Test
    fun `Wi-Fi and the battery are left out when absent, the clock always shows`() {
        val pill = StatusPill(wifi = false, battery = null, clock = "9:41 AM")

        assertFalse(pill.wifi)
        assertNull(pill.battery)
        assertEquals("9:41 AM", pill.clock)
    }

    private val full = StatusPill(wifi = true, battery = Battery(57, charging = false), clock = "9:41")

    @Test
    fun `with the default settings the pill shows everything`() {
        val pill = full.shownWith(Settings())

        assertEquals(full, pill)
        assertTrue(pill.percentage)
    }

    @Test
    fun `the Clock setting turned off leaves the clock out`() {
        val pill = full.shownWith(Settings().cycled(Setting.Clock, 1))

        assertNull(pill.clock)
        assertTrue(pill.percentage)
    }

    @Test
    fun `the Battery percentage setting turned off keeps the glyph and drops the percentage`() {
        val pill = full.shownWith(Settings().cycled(Setting.BatteryPercentage, 1))

        assertEquals(full.battery, pill.battery)
        assertFalse(pill.percentage)
        assertEquals("9:41", pill.clock)
    }

    @Test
    fun `a pill with no item left is empty`() {
        val noClock = Settings().cycled(Setting.Clock, 1)

        assertTrue(StatusPill(wifi = false, battery = null, clock = "9:41").shownWith(noClock).isEmpty)
        assertFalse(StatusPill(wifi = true, battery = null, clock = null).isEmpty)
        assertFalse(StatusPill(wifi = false, battery = Battery(5, charging = false), clock = null).isEmpty)
        assertFalse(full.isEmpty)
    }
}
