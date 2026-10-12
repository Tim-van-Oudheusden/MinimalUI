package dev.buildwithtim.minimalui.core

/**
 * Guarantees the list can open the system [settings], so a user can always switch the default home app back.
 * Any listed activity of the settings app counts: its launcher entry need not be the activity that [settings] names.
 */
fun List<LauncherApp>.withSystemSettings(settings: LauncherApp): List<LauncherApp> =
    if (any { it.packageName == settings.packageName }) this else this + settings
