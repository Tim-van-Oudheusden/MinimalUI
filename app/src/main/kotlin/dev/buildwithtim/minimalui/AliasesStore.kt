package dev.buildwithtim.minimalui

import android.content.Context
import androidx.core.content.edit
import dev.buildwithtim.minimalui.core.Aliases

/** The names the user gave apps (#72) in app-private storage, so they survive the process being killed and updates. */
class AliasesStore(context: Context) {
    private val preferences = context.getSharedPreferences("aliases", Context.MODE_PRIVATE)

    fun load(): Aliases = Aliases.fromStored(preferences.getString(ALIASES, null))

    // Written in the background; the framework flushes pending writes before the activity stops.
    fun save(aliases: Aliases) = preferences.edit { putString(ALIASES, aliases.stored()) }

    private companion object {
        const val ALIASES = "aliases"
    }
}
