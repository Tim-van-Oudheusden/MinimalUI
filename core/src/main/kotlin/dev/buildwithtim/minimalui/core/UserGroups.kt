package dev.buildwithtim.minimalui.core

/**
 * The groups the user made (#69), next to the automatic ones: named sets of apps, kept by component like
 * [AppCollections]. Unlike a collection a group has no order of its own: its apps are listed in the main list's order.
 * A group exists while it holds an app, so adding the first app creates it and removing its last deletes it. A group
 * whose apps are all uninstalled or hidden (#71) is not shown, yet kept until the user deletes it.
 *
 * Names that differ only in letter case are one group, as MinUI's folders are on a case-insensitive SD card (#130): a
 * group is found by its name in any case, and keeps the spelling it was created with.
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
    ): Boolean = byName.keyNamed(name)?.let { app.component in byName.getValue(it) } ?: false

    /**
     * [app] added to the group called [name], made one line and trimmed ([typedName]), which is created when there is
     * none. A blank name creates nothing.
     */
    fun added(
        name: String,
        app: LauncherApp,
    ): UserGroups {
        val trimmed = typedName(name)
        return if (trimmed.isEmpty()) this else merged(trimmed, setOf(app.component))
    }

    /** [app] taken out of the group called [name]; the group goes with its last app. */
    fun removed(
        name: String,
        app: LauncherApp,
    ): UserGroups {
        val key = byName.keyNamed(name) ?: return this
        val left = byName.getValue(key) - app.component
        return UserGroups(if (left.isEmpty()) byName - key else byName + (key to left))
    }

    /**
     * The group called [from] renamed [to], made one line and trimmed: into an existing group of that name, the two
     * merge under that group's spelling. A blank or unchanged name, or a group that does not exist, changes nothing;
     * a change of case alone respells the group.
     */
    fun renamed(
        from: String,
        to: String,
    ): UserGroups {
        val name = typedName(to)
        val key = byName.keyNamed(from)
        if (key == null || name.isEmpty() || name == key) return this
        return UserGroups(byName - key).merged(name, byName.getValue(key))
    }

    /** The group called [name] gone; its apps stay installed and in any other group. */
    fun deleted(name: String): UserGroups = byName.keyNamed(name)?.let { UserGroups(byName - it) } ?: this

    /** The groups [names] lists that [app] could still be added to, those whose apps are not shown included. */
    fun namesWithout(app: LauncherApp): List<String> = names.filterNot { holds(it, app) }

    /** One group per line, for app-private storage: its name, then its components, separated by tabs. */
    fun stored(): String =
        byName.entries.joinToString("\n") { (name, components) ->
            (listOf(name) + components).joinToString("\t")
        }

    // [components] added to the group called [name] in any case, created under [name] when there is none.
    private fun merged(
        name: String,
        components: Set<String>,
    ): UserGroups {
        val key = byName.keyNamed(name) ?: name
        return UserGroups(byName + (key to byName[key].orEmpty() + components))
    }

    companion object {
        /**
         * The groups [stored] wrote; blank names, fields that are not a component and empty groups are skipped, and
         * groups whose names differ only in case are one, under the first spelling.
         */
        fun fromStored(text: String?): UserGroups =
            text.orEmpty().lines().fold(UserGroups()) { groups, line ->
                val fields = line.split('\t')
                val name = fields.first().trim()
                val components = fields.drop(1).filter(::isComponent).toSet()
                if (name.isEmpty() || components.isEmpty()) groups else groups.merged(name, components)
            }
    }
}
