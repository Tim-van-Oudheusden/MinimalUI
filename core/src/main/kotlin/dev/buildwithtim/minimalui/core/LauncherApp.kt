package dev.buildwithtim.minimalui.core

/**
 * An installed activity the launcher can open: what the user sees ([label]) and the component that starts it, and the
 * [category] its developer declared, the automatic group it falls in (#69); `null` for a row that belongs to none,
 * as MinimalUI's own settings row.
 */
data class LauncherApp(
    val label: String,
    val packageName: String,
    val activityName: String,
    val category: AppCategory? = null,
)

/** The app's component, `package/activity`: what the launcher stores to remember an app across relabels and loads. */
internal val LauncherApp.component: String get() = "$packageName/$activityName"

/** Whether a stored [line] is a component as [component] writes it. */
internal fun isComponent(line: String): Boolean = COMPONENT.matches(line)

private val COMPONENT = Regex("[^/]+/[^/]+")
