package dev.buildwithtim.minimalui.core

/**
 * An installed activity the launcher can open: what the user sees ([label]) and the component that starts it, and the
 * [category] its developer declared, the automatic group it falls in (#69); `null` for a row that belongs to none,
 * as MinimalUI's own settings row. [profile] is the serial number of the user profile it belongs to (a work profile,
 * #78), as Android's `UserManager.getSerialNumberForUser` gives it, stable across reboots; `null` for the launcher's
 * own user.
 */
data class LauncherApp(
    val label: String,
    val packageName: String,
    val activityName: String,
    val category: AppCategory? = null,
    val profile: Long? = null,
)

/** Whether [this] app belongs to another profile than the launcher's own user, a work profile (#78). */
val LauncherApp.isWorkApp: Boolean get() = profile != null

/**
 * The app's component, `package/activity`: what the launcher stores to remember an app across relabels and loads, and
 * keys its rows by. An app of another profile has its [LauncherApp.profile] and a colon before it, so its work and
 * personal copies are two apps everywhere, while every key stored for the launcher's own user stays as it was.
 */
val LauncherApp.component: String
    get() = profile?.let { "$it:$packageName/$activityName" } ?: "$packageName/$activityName"

/** Whether a stored [line] is a component as [component] writes it. */
internal fun isComponent(line: String): Boolean = COMPONENT.matches(line)

private val COMPONENT = Regex("[^/]+/[^/]+")
