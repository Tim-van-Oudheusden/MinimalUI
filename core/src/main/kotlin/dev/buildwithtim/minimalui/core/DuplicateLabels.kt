package dev.buildwithtim.minimalui.core

/**
 * What tells apart the apps of [shown] whose labels, as displayed (aliases applied, #72), are the same under the list's
 * collator (case and accents ignored, as the sort does): each such app's package name, or its activity's short class
 * name when another of them shares its package. A work app (#78) is told apart by [WORK_GROUP] instead, as the copy
 * of a personal app usually is the same package; only when another app of its profile has its name too does the
 * package or class name follow. So an app of the launcher's own user whose name only work apps share needs none. An
 * app clone (#201) is told apart by [CLONE_MARK] the same way.
 * Drawn dim and small after the label (Cannoli's style); never part of the label, so type-to-filter never matches it.
 * Apps with a unique label are left out.
 */
fun duplicateSuffixes(shown: List<LauncherApp>): Map<LauncherApp, String> {
    val collator = labelCollator()
    return shown
        .groupBy { collator.getCollationKey(it.label) }
        .values
        .filter { it.size > 1 }
        .flatMap { same ->
            same.mapNotNull { app ->
                val profile = same.filter { it.profile == app.profile }
                val sharesPackage = profile.count { it.packageName == app.packageName } > 1
                val name = if (sharesPackage) app.activityName.substringAfterLast('.') else app.packageName
                val mark = if (app.isClone) CLONE_MARK else WORK_GROUP.takeIf { app.isWorkApp }
                val suffix = if (profile.size > 1) listOfNotNull(mark, name).joinToString(" ") else mark
                suffix?.let { app to it }
            }
        }.toMap()
}
