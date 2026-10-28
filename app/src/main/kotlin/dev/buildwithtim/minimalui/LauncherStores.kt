package dev.buildwithtim.minimalui

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.buildwithtim.minimalui.core.Aliases
import dev.buildwithtim.minimalui.core.AppCollections
import dev.buildwithtim.minimalui.core.Favourites
import dev.buildwithtim.minimalui.core.HiddenApps
import dev.buildwithtim.minimalui.core.LauncherSetup
import dev.buildwithtim.minimalui.core.RecentApps
import dev.buildwithtim.minimalui.core.Settings
import dev.buildwithtim.minimalui.core.UserGroups

/**
 * What the user chose, read from app-private storage as this is made and written back with each change, so it survives
 * the launcher's process being killed. Each value is Compose state: the lists on screen follow every change.
 */
internal class LauncherStores(
    context: Context,
) {
    private val recentStore =
        SingleKeyPreferenceStore(context, "recent_apps", "components", RecentApps::fromStored, RecentApps::stored)
    private val settingsStore = SettingsStore(context)
    private val favouritesStore =
        SingleKeyPreferenceStore(context, "favourites", "favourites", Favourites::fromStored, Favourites::stored)
    private val collectionsStore =
        SingleKeyPreferenceStore(
            context,
            "favourites",
            "collections",
            AppCollections::fromStored,
            AppCollections::stored,
        )
    private val hiddenStore =
        SingleKeyPreferenceStore(context, "hidden_apps", "hidden", HiddenApps::fromStored, HiddenApps::stored)
    private val aliasesStore =
        SingleKeyPreferenceStore(context, "aliases", "aliases", Aliases::fromStored, Aliases::stored)
    private val groupsStore =
        SingleKeyPreferenceStore(context, "groups", "groups", UserGroups::fromStored, UserGroups::stored)

    /** The apps last opened from here, newest first (#70). */
    var recents by mutableStateOf(recentStore.load())
        private set

    var settings by mutableStateOf(settingsStore.load())
        private set

    /** The favourites pinned to the top of the root list and the user's collections (#68). */
    var favourites by mutableStateOf(favouritesStore.load())
        private set
    var collections by mutableStateOf(collectionsStore.load())
        private set

    /** The apps the user hid (#71): off the root list, the collections and the groups, still found by typing. */
    var hidden by mutableStateOf(hiddenStore.load())
        private set

    /** The names the user gave apps (#72). */
    var aliases by mutableStateOf(aliasesStore.load())
        private set

    /** The groups the user made (#69), shown merged with the automatic ones from the apps' categories. */
    var userGroups by mutableStateOf(groupsStore.load())
        private set

    /** Everything above as one setup, for Export setup (#208). */
    val setup: LauncherSetup
        get() = LauncherSetup(settings, recents, favourites, collections, hidden, aliases, userGroups)

    /** Every store replaced by [imported]'s and written back, as a confirmed Import setup does (#208). */
    fun replaceWith(imported: LauncherSetup) {
        save(imported.settings)
        save(imported.recents)
        save(imported.favourites)
        save(imported.collections)
        save(imported.hidden)
        save(imported.aliases)
        save(imported.userGroups)
    }

    fun save(changed: RecentApps) {
        recents = changed
        recentStore.save(changed)
    }

    fun save(changed: Settings) {
        settings = changed
        settingsStore.save(changed)
    }

    fun save(changed: Favourites) {
        favourites = changed
        favouritesStore.save(changed)
    }

    fun save(changed: AppCollections) {
        collections = changed
        collectionsStore.save(changed)
    }

    fun save(changed: HiddenApps) {
        hidden = changed
        hiddenStore.save(changed)
    }

    fun save(changed: Aliases) {
        aliases = changed
        aliasesStore.save(changed)
    }

    fun save(changed: UserGroups) {
        userGroups = changed
        groupsStore.save(changed)
    }
}
