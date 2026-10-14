package dev.buildwithtim.minimalui

import android.icu.text.DateFormat
import android.icu.util.TimeZone
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * The time as the system's clock writes it in [locale] (#129): the locale's best pattern for hours and minutes, `Hm`
 * when [twentyFourHour], else `hm`. ICU picks the digits, the AM/PM or day-period marker and where it goes, so the
 * hour and the minutes never mix digit scripts and Korean or Chinese put the marker first. [hour] runs 0–23.
 */
internal fun clockText(hour: Int, minute: Int, twentyFourHour: Boolean, locale: Locale): String {
    val format = DateFormat.getInstanceForSkeleton(if (twentyFourHour) "Hm" else "hm", locale)
    // A fixed zone with no daylight saving, so the instant below reads back as exactly hour:minute.
    format.timeZone = TimeZone.GMT_ZONE
    return format.format(Date(TimeUnit.HOURS.toMillis(hour.toLong()) + TimeUnit.MINUTES.toMillis(minute.toLong())))
}
