package dev.buildwithtim.minimalui.core

/**
 * The names the user gave apps (#72), as MinUI's `map.txt` and Cannoli's Rename do: an app's alias replaces its label
 * wherever the launcher lists, sorts or filters it. Kept by component, like [Favourites], so an alias survives an app
 * update that changes the app's own label. An uninstalled app's alias is never dropped, so reinstalling it keeps it.
 */
data class Aliases(val byComponent: Map<String, String> = emptyMap()) {

    /**
     * [app], as installed (with its system label), called [alias], made one line ([typedName]). A blank alias, or one
     * equal to the system label, clears it, so the app shows its system label again.
     */
    fun renamed(app: LauncherApp, alias: String): Aliases {
        val name = typedName(alias)
        if (name.isEmpty() || name == app.label) return Aliases(byComponent - app.component)
        return Aliases(byComponent + (app.component to name))
    }

    /**
     * [installed] (in display order, with their system labels) with their aliases as labels, sorted again by them.
     * MinimalUI's own settings row keeps its place right after the rows of Android's Settings ([settingsPackage]),
     * as [withLauncherSettings] puts it, whatever either is called.
     */
    fun labelled(installed: List<LauncherApp>, settingsPackage: String?): List<LauncherApp> {
        val (entries, apps) = installed.partition { it.isLauncherSettings }
        val renamed = apps.map { app -> byComponent[app.component]?.let { app.copy(label = it) } ?: app }
        val sorted = renamed.sortedForDisplay()
        return entries.fold(sorted) { list, entry -> list.withLauncherSettings(entry, settingsPackage) }
    }

    /** One alias per line, for app-private storage: the component, a tab, then the alias. */
    fun stored(): String = byComponent.entries.joinToString("\n") { (component, alias) -> "$component\t$alias" }

    companion object {
        /** The aliases [stored] wrote; lines without a component or an alias are skipped. */
        fun fromStored(text: String?): Aliases {
            val byComponent = text.orEmpty().lines().mapNotNull { line ->
                val component = line.substringBefore('\t')
                val alias = typedName(line.substringAfter('\t', missingDelimiterValue = ""))
                if (!isComponent(component) || alias.isEmpty()) null else component to alias
            }
            return Aliases(byComponent.toMap())
        }
    }
}

/**
 * A name the user typed (an alias, a collection's name) as the launcher keeps it: on one line, every tab and line break
 * a space, so it fits a row and the line-based storage, and trimmed.
 */
internal fun typedName(text: String): String = text.replace(CONTROL_CHARACTERS, " ").trim()

private val CONTROL_CHARACTERS = Regex("\\p{Cntrl}")
