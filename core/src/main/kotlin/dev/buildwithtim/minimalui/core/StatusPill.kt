package dev.buildwithtim.minimalui.core

/**
 * What the status pill in the top-right corner shows (#65): a Wi-Fi glyph only while the default network is Wi-Fi,
 * the [battery] when its charge is known, with its [percentage] beside it unless turned off, and the [clock] unless
 * turned off (#73).
 */
data class StatusPill(val wifi: Boolean, val battery: Battery?, val clock: String?, val percentage: Boolean = true) {
    /** Nothing left to show: the status row stays, but without a pill. */
    val isEmpty: Boolean get() = !wifi && battery == null && clock == null

    /** The pill as the user's [settings] trim it: no clock with Clock off, no percentage with Battery percentage off. */
    fun shownWith(settings: Settings): StatusPill = copy(
        clock = clock.takeIf { settings.isOn(Setting.Clock) },
        percentage = percentage && settings.isOn(Setting.BatteryPercentage),
    )
}

/** A battery at [percent] (0–100) charge, [charging] or not. Drawn MinUI-style: a glyph plus the percentage. */
data class Battery(val percent: Int, val charging: Boolean) {
    /** The percentage, shown next to the glyph. */
    val text: String get() = "$percent%"

    /** A bolt replaces the fill while charging, as in MinUI. */
    val bolt: Boolean get() = charging

    /** How much of the glyph's body is filled, 0 to 1; empty while the bolt shows. */
    val fill: Float get() = if (charging) 0f else percent / 100f

    /** The fill turns to the warning colour at [LOW_FILL] percent and below, unless charging. */
    val lowFill: Boolean get() = !charging && percent <= LOW_FILL

    /** The outline turns to the warning colour too at [LOW_OUTLINE] percent and below, unless charging. */
    val lowOutline: Boolean get() = !charging && percent <= LOW_OUTLINE

    private companion object {
        const val LOW_FILL = 20
        const val LOW_OUTLINE = 10
    }
}

/**
 * The battery from Android's battery broadcast: [level] out of [scale], rounded to a whole percentage. Null when
 * either is missing or out of range, so the pill leaves the battery out rather than show a wrong charge.
 */
fun battery(level: Int, scale: Int, charging: Boolean): Battery? {
    if (scale <= 0 || level !in 0..scale) return null
    return Battery((level * 100 + scale / 2) / scale, charging)
}

/**
 * The time as the system's 12/24-hour setting shows it: `HH:mm` when [twentyFourHour], else `h:mm` and the
 * locale's [am] or [pm] marker. [hour] runs 0–23.
 */
fun clockText(hour: Int, minute: Int, twentyFourHour: Boolean, am: String, pm: String): String {
    val mm = "%02d".format(minute)
    if (twentyFourHour) return "%02d:%s".format(hour, mm)
    val twelveHour = if (hour % 12 == 0) 12 else hour % 12
    return "$twelveHour:$mm ${if (hour < 12) am else pm}"
}
