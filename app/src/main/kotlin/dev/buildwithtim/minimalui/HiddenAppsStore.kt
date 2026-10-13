package dev.buildwithtim.minimalui

import android.content.Context
import androidx.core.content.edit
import dev.buildwithtim.minimalui.core.HiddenApps

/** The hidden apps (#71) in app-private storage, so they stay hidden after the launcher's process is killed. */
class HiddenAppsStore(context: Context) {
    private val preferences = context.getSharedPreferences("hidden_apps", Context.MODE_PRIVATE)

    fun load(): HiddenApps = HiddenApps.fromStored(preferences.getString(HIDDEN, null))

    // Written in the background; the framework flushes pending writes before the activity stops.
    fun save(hidden: HiddenApps) = preferences.edit { putString(HIDDEN, hidden.stored()) }

    private companion object {
        const val HIDDEN = "hidden"
    }
}
