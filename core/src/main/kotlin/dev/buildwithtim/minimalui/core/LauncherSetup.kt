package dev.buildwithtim.minimalui.core

// What the file says it is, and the version of its layout: a file of a later version is refused, not read in part.
private const val FORMAT = "minimalui-setup"
private const val VERSION = 1

/**
 * Everything the user set up in MinimalUI (#208): the [settings] and every list of their own. Exported to a file of the
 * user's choosing and imported from one, it carries the setup past what app-private storage cannot survive: an
 * uninstall, a switch between a debug build and a release, a downgrade, a new phone.
 */
data class LauncherSetup(
    val settings: Settings = Settings(),
    val recents: RecentApps = RecentApps(),
    val favourites: Favourites = Favourites(),
    val collections: AppCollections = AppCollections(),
    val hidden: HiddenApps = HiddenApps(),
    val aliases: Aliases = Aliases(),
    val userGroups: UserGroups = UserGroups(),
) {
    /**
     * The setup as a JSON document, which [imported] reads back: the format's name and version, each setting's key
     * with its choice, the apps of each list by component, and each collection, group and alias by its name.
     */
    fun exported(): String =
        jsonText(
            mapOf(
                "format" to FORMAT,
                "version" to VERSION,
                "settings" to settings.stored(),
                "recent" to recents.components,
                "favourites" to favourites.components,
                "collections" to collections.byName,
                "groups" to userGroups.byName,
                "hidden" to hidden.components,
                "aliases" to aliases.byComponent,
            ),
        )

    companion object {
        /**
         * The setup an [exported] file holds, read as the launcher reads its own storage: an entry it would not store
         * (not a component, a blank name, an unknown choice) is skipped, a part missing from the file is empty, and
         * members it does not know are ignored. A file that is not such a document is [SetupImport.NotASetup]; one
         * from a newer MinimalUI is [SetupImport.TooNew].
         */
        fun imported(text: String): SetupImport {
            val document = parseJsonOrNull(text) as? Map<*, *>
            val version = (document?.get("version") as? Double)?.takeIf { it >= 1 && it % 1 == 0.0 }
            return when {
                document?.get("format") != FORMAT || version == null -> SetupImport.NotASetup
                version > VERSION -> SetupImport.TooNew
                else -> SetupImport.Read(setupFrom(document))
            }
        }

        // Each part goes through the line-based text its store keeps, and that store's own reading of it, so what is
        // imported is exactly what the launcher would store. Nothing read may break a line or a field of that text.
        private fun setupFrom(document: Map<*, *>): LauncherSetup {
            val settings = document.members("settings")
            return LauncherSetup(
                settings = Settings.fromStored { settings[it] as? String },
                recents = RecentApps.fromStored(document.components("recent").joinToString("\n")),
                favourites = Favourites.fromStored(document.components("favourites").joinToString("\n")),
                collections = AppCollections.fromStored(document.namedLists("collections")),
                hidden = HiddenApps.fromStored(document.components("hidden").joinToString("\n")),
                aliases = Aliases.fromStored(document.aliases()),
                userGroups = UserGroups.fromStored(document.namedLists("groups")),
            )
        }

        private fun Map<*, *>.members(name: String): Map<*, *> = this[name] as? Map<*, *> ?: emptyMap<Any, Any>()

        private fun Map<*, *>.components(name: String): List<String> = components(this[name])

        private fun components(list: Any?): List<String> =
            (list as? List<*>).orEmpty().filterIsInstance<String>().filterNot { it.any(Char::isISOControl) }

        private fun Map<*, *>.namedLists(name: String): String =
            members(name).entries.joinToString("\n") { (listName, apps) ->
                (listOf(typedName(listName.toString())) + components(apps)).joinToString("\t")
            }

        private fun Map<*, *>.aliases(): String =
            members("aliases").entries.joinToString("\n") { (component, alias) ->
                val app = component.toString().takeUnless { it.any(Char::isISOControl) }.orEmpty()
                "$app\t${typedName(alias as? String ?: "")}"
            }
    }
}

/** What a file chosen for import holds. */
sealed interface SetupImport {
    /** A MinimalUI [setup], ready to replace the current one. */
    data class Read(
        val setup: LauncherSetup,
    ) : SetupImport

    /** Not a setup MinimalUI exported: another file, or a damaged one. */
    data object NotASetup : SetupImport

    /** A setup exported by a newer MinimalUI, in a layout this version does not know. */
    data object TooNew : SetupImport
}
