package dev.buildwithtim.minimalui.core

/**
 * The automatic group an app falls in (#69), from the category its developer declared (Android's
 * `ApplicationInfo.category`); [Other] for an app that declared none, or one this launcher does not know. The enum
 * name is the group's display name.
 */
enum class AppCategory { Games, Audio, Video, Images, Social, News, Maps, Productivity, Accessibility, Other }

// Android's CATEGORY_ACCESSIBILITY, the only category code newer than the launcher's minimum SDK.
private const val ACCESSIBILITY_CODE = 8
private const val ACCESSIBILITY_SDK = 31

/**
 * The [AppCategory] for Android's category [code] (`ApplicationInfo.CATEGORY_GAME` is 0, up to `ACCESSIBILITY`, 8)
 * on a device running [sdk]: Accessibility exists from API 31 on only, so below that its code means nothing known.
 */
fun appCategory(code: Int, sdk: Int): AppCategory {
    val known = code in 0..<ACCESSIBILITY_CODE || code == ACCESSIBILITY_CODE && sdk >= ACCESSIBILITY_SDK
    return if (known) AppCategory.entries[code] else AppCategory.Other
}

/**
 * One group as the Groups list shows it: its [name], its [apps] in the main list's order, and whether the user made a
 * group of that name ([isUserGroup]), which the group's menu can rename or delete.
 */
data class AppGroup(val name: String, val apps: List<LauncherApp>, val isUserGroup: Boolean)

/**
 * The groups to list for [apps] (the installed apps the user did not hide, in the main list's order, by their shown
 * names): every automatic group and every one of the [user] groups that holds one of them, alphabetical as apps are,
 * with Other last. A user group named as an automatic group is one group with the apps of both, as MinUI merges
 * folders that share a name. A group with none of [apps] is left out.
 */
fun appGroups(apps: List<LauncherApp>, user: UserGroups = UserGroups()): List<AppGroup> {
    val names = (apps.flatMap { it.automaticGroups } + user.byName.keys).distinct()
    val groups = names.map { name ->
        AppGroup(name, apps.filter { name in it.automaticGroups || user.holds(name, it) }, name in user.byName)
    }
    return groups.filter { it.apps.isNotEmpty() }
        .sortedWith(compareBy<AppGroup> { it.name == AppCategory.Other.name }.thenBy(labelCollator()) { it.name })
}

/** The automatic groups [this] app falls in, by name: its category's, if any (#78 adds the work profile's). */
internal val LauncherApp.automaticGroups: List<String> get() = listOfNotNull(category?.name)
