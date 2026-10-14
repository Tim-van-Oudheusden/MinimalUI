package dev.buildwithtim.minimalui.core

/**
 * The groups the user made (#69), next to the automatic ones: named sets of apps, kept by component like
 * [AppCollections]. Unlike a collection a group has no order of its own: its apps are listed in the main list's order.
 * A group exists while it holds an app, so adding the first app creates it and removing its last deletes it. A group
 * whose apps are all uninstalled or hidden (#71) is not shown, yet kept until the user deletes it.
 */
data class UserGroups(
    val byName: Map<String, Set<String>> = emptyMap(),
) {
    /** The groups' names, alphabetical as apps are. */
    val names: List<String> get() = byName.keys.sortedWith(labelCollator())

    /** Whether the group called [name] holds [app]. */
    fun holds(
        name: String,
        app: LauncherApp,
    ): Boolean = app.component in byName[name].orEmpty()

    /**
     * [app] added to the group called [name], made one line and trimmed ([typedName]), which is created when there is
     * none. A blank name creates nothing.
     */
    fun added(
        name: String,
        app: LauncherApp,
    ): UserGroups {
        val trimmed = typedName(name)
        if (trimmed.isEmpty()) return this
        return UserGroups(byName + (trimmed to byName[trimmed].orEmpty() + app.component))
    }

    /** [app] taken out of the group called [name]; the group goes with its last app. */
    fun removed(
        name: String,
        app: LauncherApp,
    ): UserGroups {
        val left = byName[name]?.minus(app.component) ?: return this
        return UserGroups(if (left.isEmpty()) byName - name else byName + (name to left))
    }

    /**
     * The group called [from] renamed [to], made one line and trimmed: into an existing group of that name, the two
     * merge. A blank or unchanged name, or a group that does not exist, changes nothing.
     */
    fun renamed(
        from: String,
        to: String,
    ): UserGroups {
        val name = typedName(to)
        val members = byName[from]
        if (members == null || name.isEmpty() || name == from) return this
        return UserGroups(byName - from + (name to byName[name].orEmpty() + members))
    }

    /** The group called [name] gone; its apps stay installed and in any other group. */
    fun deleted(name: String): UserGroups = UserGroups(byName - name)

    /** The groups [names] lists that [app] could still be added to, those whose apps are not shown included. */
    fun namesWithout(app: LauncherApp): List<String> = names.filterNot { holds(it, app) }

    /** One group per line, for app-private storage: its name, then its components, separated by tabs. */
    fun stored(): String =
        byName.entries.joinToString("\n") { (name, components) ->
            (listOf(name) + components).joinToString("\t")
        }

    companion object {
        /** The groups [stored] wrote; blank names, fields that are not a component and empty groups are skipped. */
        fun fromStored(text: String?): UserGroups {
            val byName =
                text.orEmpty().lines().mapNotNull { line ->
                    val fields = line.split('\t')
                    val name = fields.first().trim()
                    val components = fields.drop(1).filter(::isComponent).toSet()
                    if (name.isEmpty() || components.isEmpty()) null else name to components
                }
            return UserGroups(byName.toMap())
        }
    }
}
