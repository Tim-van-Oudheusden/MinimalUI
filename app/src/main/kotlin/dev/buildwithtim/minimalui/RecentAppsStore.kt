package dev.buildwithtim.minimalui

import android.content.Context
import androidx.core.content.edit
import dev.buildwithtim.minimalui.core.RecentApps

/** The recent apps in app-private storage, so the list survives the launcher's process being killed. */
class RecentAppsStore(context: Context) {
    private val preferences = context.getSharedPreferences("recent_apps", Context.MODE_PRIVATE)

    fun load(): RecentApps = RecentApps.fromStored(preferences.getString(KEY, null))

    // Written in the background; the framework flushes pending writes before the activity stops.
    fun save(recents: RecentApps) = preferences.edit { putString(KEY, recents.stored()) }

    private companion object {
        const val KEY = "components"
    }
}
