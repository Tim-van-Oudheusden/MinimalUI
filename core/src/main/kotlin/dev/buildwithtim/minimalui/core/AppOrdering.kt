package dev.buildwithtim.minimalui.core

import java.text.Collator
import java.util.Locale

/**
 * The order the launcher lists apps in: alphabetical by label, ignoring case, with accented letters next to their base
 * letter (`É` between `D` and `F`, not after `Z`).
 * Apps sharing a label fall back to package and activity name, so the list never reshuffles between loads.
 */
fun List<LauncherApp>.sortedForDisplay(): List<LauncherApp> = sortedWith(
    compareBy<LauncherApp, String>(labelCollator()) { it.label }
        .thenBy { it.packageName }
        .thenBy { it.activityName },
)

/**
 * How the launcher compares names, for sorting and for telling duplicates apart: SECONDARY strength, so accents sort
 * next to their base letter and letter case is ignored.
 */
internal fun labelCollator(): Collator = Collator.getInstance(Locale.ROOT).apply { strength = Collator.SECONDARY }
