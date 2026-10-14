package dev.buildwithtim.minimalui.core

import java.text.Collator
import java.util.Locale

/**
 * The order the launcher lists apps in: alphabetical by label, ignoring case, with accented letters next to their base
 * letter (`É` between `D` and `F`, not after `Z`).
 * Apps sharing a label fall back to package and activity name, so the list never reshuffles between loads, and an
 * app of the launcher's own user comes before its copies in other profiles (#78).
 */
fun List<LauncherApp>.sortedForDisplay(): List<LauncherApp> =
    sortedWith(
        compareBy<LauncherApp, String>(labelCollator()) { it.label }
            .thenBy { it.packageName }
            .thenBy { it.activityName }
            .thenBy(nullsFirst()) { it.profile },
    )

/**
 * How the launcher compares names, for sorting and for telling duplicates apart: SECONDARY strength, so accents sort
 * next to their base letter and letter case is ignored.
 *
 * `java.text.Collator` is a different engine on a device than on a desktop JVM (#96). Android backs it with ICU and
 * the CLDR root collation, which keeps letters such as `Ø`, `Ł` and `Đ` next to their base letter, sorts fullwidth
 * letters with their ASCII form, puts leading spaces and punctuation before letters, and sorts non-Latin scripts after
 * Latin, grouped by script. The JDK collator, which unit and Robolectric tests run on, has rules for little beyond
 * Latin-1. It sorts every other character by code point after `z`, and it ignores spaces and hyphens. The two
 * engines agree only on ASCII and Latin-1 accented labels, so tests can pin the device's order only for those.
 */
internal fun labelCollator(): Collator = Collator.getInstance(Locale.ROOT).apply { strength = Collator.SECONDARY }

/**
 * The key of [this] map that is the same name as [name] to [labelCollator], so differs from it at most in letter case,
 * as MinUI's folders on a case-insensitive SD card do (#130); null when there is none.
 */
internal fun <V> Map<String, V>.keyNamed(name: String): String? {
    val collator = labelCollator()
    return keys.firstOrNull { collator.compare(it, name) == 0 }
}
