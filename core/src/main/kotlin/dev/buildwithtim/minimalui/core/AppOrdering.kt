package dev.buildwithtim.minimalui.core

import java.text.Collator
import java.util.Locale

/**
 * The order the launcher lists apps in: alphabetical by label, ignoring case, with accented letters next to their base
 * letter (`É` between `D` and `F`, not after `Z`).
 * Apps sharing a label fall back to package and activity name, so the list never reshuffles between loads.
 */
fun List<LauncherApp>.sortedForDisplay(): List<LauncherApp> {
    // SECONDARY strength: accents sort next to their base letter, letter case is ignored.
    val collator = Collator.getInstance(Locale.ROOT).apply { strength = Collator.SECONDARY }

    return sortedWith(
        compareBy<LauncherApp, String>(collator) { it.label }
            .thenBy { it.packageName }
            .thenBy { it.activityName },
    )
}
