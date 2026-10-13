package dev.buildwithtim.minimalui

import android.content.Context
import androidx.core.content.edit
import dev.buildwithtim.minimalui.core.AppCollections
import dev.buildwithtim.minimalui.core.Favourites

/** The favourites and collections (#68) in app-private storage, so they survive the launcher's process being killed. */
class FavouritesStore(context: Context) {
    private val preferences = context.getSharedPreferences("favourites", Context.MODE_PRIVATE)

    fun loadFavourites(): Favourites = Favourites.fromStored(preferences.getString(FAVOURITES, null))

    fun loadCollections(): AppCollections = AppCollections.fromStored(preferences.getString(COLLECTIONS, null))

    // Written in the background; the framework flushes pending writes before the activity stops.
    fun save(favourites: Favourites) = preferences.edit { putString(FAVOURITES, favourites.stored()) }

    fun save(collections: AppCollections) = preferences.edit { putString(COLLECTIONS, collections.stored()) }

    private companion object {
        const val FAVOURITES = "favourites"
        const val COLLECTIONS = "collections"
    }
}
