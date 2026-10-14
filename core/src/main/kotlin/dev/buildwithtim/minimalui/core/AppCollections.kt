package dev.buildwithtim.minimalui.core

/**
 * The user's collections (#68), MinUI-style: named lists of apps, each in the user's own order (the order the apps
 * were added, then as moved), never nested. A collection exists only while it holds an app, so removing its last app
 * deletes it. Apps are kept by component, like [RecentApps], so they keep their place when relabelled.
 */
data class AppCollections(
    val byName: Map<String, List<String>> = emptyMap(),
) {
    /**
     * [app] added at the end of the collection called [name], made one line and trimmed ([typedName]), which is
     * created when there is none. A blank name creates nothing, and an app the collection already holds stays where it
     * is.
     */
    fun added(
        name: String,
        app: LauncherApp,
    ): AppCollections {
        val trimmed = typedName(name)
        val components = byName[trimmed].orEmpty()
        if (trimmed.isEmpty() || app.component in components) return this
        return AppCollections(byName + (trimmed to components + app.component))
    }

    /** [app] taken out of the collection called [name]; the collection goes with its last app. */
    fun removed(
        name: String,
        app: LauncherApp,
    ): AppCollections {
        val left = byName[name]?.minus(app.component) ?: return this
        return AppCollections(if (left.isEmpty()) byName - name else byName + (name to left))
    }

    /**
     * [app] moved [step] rows (-1 up, 1 down) in the collection called [name] as shown from [installed], so it never
     * swaps with an app the user cannot see (uninstalled, or hidden, #71): it trades places with the next app shown,
     * and those in between keep theirs, so a hidden app comes back where it was. It stays put at either end.
     */
    fun moved(
        name: String,
        app: LauncherApp,
        step: Int,
        installed: List<LauncherApp>,
    ): AppCollections {
        val shown = appsIn(name, installed).map { it.component }
        val from = shown.indexOf(app.component)
        val to = from + step
        if (from < 0 || to !in shown.indices) return this
        val stored = byName.getValue(name).toMutableList()
        val other = stored.indexOf(shown[to])
        stored[stored.indexOf(app.component)] = shown[to]
        stored[other] = app.component
        return AppCollections(byName + (name to stored))
    }

    /** The apps of the collection called [name] that are among [installed], in the collection's order. */
    fun appsIn(
        name: String,
        installed: List<LauncherApp>,
    ): List<LauncherApp> {
        val byComponent = installed.associateBy { it.component }
        return byName[name].orEmpty().mapNotNull(byComponent::get)
    }

    /** The collections holding an app among [installed], alphabetical as apps are: the others have nothing to show. */
    fun names(installed: List<LauncherApp>): List<String> =
        byName.keys.filter { appsIn(it, installed).isNotEmpty() }.sortedWith(labelCollator())

    /** The collections [names] lists that [app] could still be added to. */
    fun namesWithout(
        app: LauncherApp,
        installed: List<LauncherApp>,
    ): List<String> = names(installed).filter { app.component !in byName.getValue(it) }

    /** One collection per line, for app-private storage: its name, then its components, separated by tabs. */
    fun stored(): String =
        byName.entries.joinToString("\n") { (name, components) ->
            (listOf(name) + components).joinToString("\t")
        }

    companion object {
        /** The collections [stored] wrote; blank names, fields that are not a component and empty ones are skipped. */
        fun fromStored(text: String?): AppCollections {
            val byName =
                text.orEmpty().lines().mapNotNull { line ->
                    val fields = line.split('\t')
                    val name = fields.first().trim()
                    val components = fields.drop(1).filter(::isComponent).distinct()
                    if (name.isEmpty() || components.isEmpty()) null else name to components
                }
            return AppCollections(byName.toMap())
        }
    }
}
