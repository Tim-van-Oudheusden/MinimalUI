package dev.buildwithtim.minimalui

import android.content.Context
import androidx.core.content.edit
import dev.buildwithtim.minimalui.core.UserGroups

/** The groups the user made (#69) in app-private storage, so they survive the launcher's process being killed. */
class GroupsStore(
    context: Context,
) {
    private val preferences = context.getSharedPreferences("groups", Context.MODE_PRIVATE)

    fun load(): UserGroups = UserGroups.fromStored(preferences.getString(GROUPS, null))

    // Written in the background; the framework flushes pending writes before the activity stops.
    fun save(groups: UserGroups) = preferences.edit { putString(GROUPS, groups.stored()) }

    private companion object {
        const val GROUPS = "groups"
    }
}
