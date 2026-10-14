package dev.buildwithtim.minimalui

import android.content.Context
import androidx.core.content.edit

/**
 * One value persisted under one key in its own SharedPreferences file, in app-private storage, so
 * it survives the launcher's process being killed. Aliases, groups, hidden apps, recent apps and
 * both halves of favourites/collections are all this same shape with a different file name, key
 * and value type.
 */
class SingleKeyPreferenceStore<T>(
    context: Context,
    prefsFileName: String,
    private val key: String,
    private val fromStored: (String?) -> T,
    private val stored: (T) -> String,
) {
    private val preferences = context.getSharedPreferences(prefsFileName, Context.MODE_PRIVATE)

    fun load(): T = fromStored(preferences.getString(key, null))

    // Written in the background; the framework flushes pending writes before the activity stops.
    fun save(value: T) = preferences.edit { putString(key, stored(value)) }
}
