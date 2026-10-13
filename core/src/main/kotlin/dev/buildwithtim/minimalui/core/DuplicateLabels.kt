package dev.buildwithtim.minimalui.core

/**
 * What tells apart the apps of [shown] whose labels, as displayed (aliases applied, #72), are the same under the list's
 * collator (case and accents ignored, as the sort does): each such app's package name, or its activity's short class
 * name when another of them shares its package. Drawn dim and small after the label (Cannoli's style); never part of
 * the label, so type-to-filter never matches it. Apps with a unique label are left out.
 */
fun duplicateSuffixes(shown: List<LauncherApp>): Map<LauncherApp, String> {
    val collator = labelCollator()
    return shown.groupBy { collator.getCollationKey(it.label) }.values.filter { it.size > 1 }.flatMap { same ->
        same.map { app ->
            val sharesPackage = same.count { it.packageName == app.packageName } > 1
            app to if (sharesPackage) app.activityName.substringAfterLast('.') else app.packageName
        }
    }.toMap()
}
