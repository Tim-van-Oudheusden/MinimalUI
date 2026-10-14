package dev.buildwithtim.minimalui

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Locale

// 28 is minSdk, with the oldest ICU the app meets; 35 is the newest Robolectric runs here.
@Config(sdk = [28, 35])
@RunWith(RobolectricTestRunner::class)
class ClockTest {
    private val defaultLocale = Locale.getDefault()

    // On a device the default locale is the clock's too, and String.format writes its digits: set it, as there.
    private fun clock(hour: Int, minute: Int, twentyFourHour: Boolean, tag: String): String {
        val locale = Locale.forLanguageTag(tag)
        Locale.setDefault(locale)
        return clockText(hour, minute, twentyFourHour, locale)
    }

    @After
    fun restoreLocale() = Locale.setDefault(defaultLocale)

    @Test
    fun `the 24-hour clock pads the hour`() {
        assertEquals("07:05", clock(7, 5, twentyFourHour = true, "en-US"))
        assertEquals("00:00", clock(0, 0, twentyFourHour = true, "en-US"))
        assertEquals("23:59", clock(23, 59, twentyFourHour = true, "en-US"))
    }

    @Test
    fun `the 12-hour clock counts from 12 and adds the locale's marker`() {
        // Newer ICU puts a narrow no-break space before the marker, older ICU a plain one.
        assertTrue(clock(0, 0, twentyFourHour = false, "en-US").matches(Regex("12:00[  ]AM")))
        assertTrue(clock(7, 5, twentyFourHour = false, "en-US").matches(Regex("7:05[  ]AM")))
        assertTrue(clock(12, 30, twentyFourHour = false, "en-US").matches(Regex("12:30[  ]PM")))
    }

    @Test
    fun `the hour and the minutes are written in the same digits`() {
        val twelveHour = clock(15, 5, twentyFourHour = false, "ar-EG")
        assertTrue(twelveHour, twelveHour.contains("٣:٠٥"))
        assertTrue(twelveHour, twelveHour.none { it in '0'..'9' })
        assertEquals("١٥:٠٥", clock(15, 5, twentyFourHour = true, "ar-EG"))
        assertTrue(clock(15, 5, twentyFourHour = false, "fa-IR").contains("۳:۰۵"))
    }

    @Test
    fun `the marker goes where the locale puts it`() {
        assertEquals("오후 3:05", clock(15, 5, twentyFourHour = false, "ko-KR"))
        val taiwan = clock(15, 5, twentyFourHour = false, "zh-TW")
        assertTrue(taiwan, taiwan.startsWith("下午") && taiwan.endsWith("3:05"))
    }
}
