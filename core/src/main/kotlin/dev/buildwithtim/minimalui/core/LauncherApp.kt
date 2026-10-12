package dev.buildwithtim.minimalui.core

/** An installed activity the launcher can open: what the user sees ([label]) and the component that starts it. */
data class LauncherApp(
    val label: String,
    val packageName: String,
    val activityName: String,
)
