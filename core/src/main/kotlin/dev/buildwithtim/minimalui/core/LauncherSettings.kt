package dev.buildwithtim.minimalui.core

// Stands in for the activity name: no class can be called this, so the entry never matches a real app.
private const val SETTINGS_ENTRY = "#settings"

/**
 * The row that opens MinimalUI's own settings (#73), labelled [label], of the launcher's own [packageName]. It is
 * listed, sorted and filtered like an app, but opens the settings list in place instead of being started.
 */
fun launcherSettings(
    label: String,
    packageName: String,
): LauncherApp = LauncherApp(label, packageName, SETTINGS_ENTRY)

/** Whether this row is the [launcherSettings] entry rather than an installed app. */
val LauncherApp.isLauncherSettings: Boolean get() = activityName == SETTINGS_ENTRY

/**
 * Adds the [entry] to a list already in display order: directly after the rows of the system settings app
 * ([settingsPackage]), so both settings sit together, or at its alphabetical place when that app is not listed.
 */
fun List<LauncherApp>.withLauncherSettings(
    entry: LauncherApp,
    settingsPackage: String?,
): List<LauncherApp> {
    val after = indexOfLast { it.packageName == settingsPackage } + 1
    if (after == 0) return (this + entry).sortedForDisplay()
    return take(after) + entry + drop(after)
}
