package dev.buildwithtim.minimalui

import android.content.Context
import androidx.core.content.edit
import dev.buildwithtim.minimalui.core.Settings

/** MinimalUI's settings (#73) in app-private storage, so they survive the launcher's process being killed. */
class SettingsStore(
    context: Context,
) {
    private val preferences = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    fun load(): Settings = Settings.fromStored { preferences.getString(it, null) }

    // Written in the background; the framework flushes pending writes before the activity stops.
    fun save(settings: Settings) =
        preferences.edit { settings.stored().forEach { (key, value) -> putString(key, value) } }
}
