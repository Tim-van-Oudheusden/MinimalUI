package dev.buildwithtim.minimalui

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.buildwithtim.minimalui.core.Aliases
import dev.buildwithtim.minimalui.core.AppCollections
import dev.buildwithtim.minimalui.core.Choice
import dev.buildwithtim.minimalui.core.Favourites
import dev.buildwithtim.minimalui.core.HiddenApps
import dev.buildwithtim.minimalui.core.RecentApps
import dev.buildwithtim.minimalui.core.Setting
import dev.buildwithtim.minimalui.core.UserGroups
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The preferences file and key each of the user's choices lives under, as released versions wrote them (v0.1.1): an
 * update that reads another file or key starts every user over with nothing, and a test that saves and loads through
 * the same store can't see it. These names are spelled out here once, on purpose, apart from the code.
 */
@RunWith(RobolectricTestRunner::class)
class LauncherStoresTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun stored(
        file: String,
        key: String,
        value: String,
    ) = context
        .getSharedPreferences(file, Context.MODE_PRIVATE)
        .edit()
        .putString(key, value)
        .commit()

    private fun storedIn(
        file: String,
        key: String,
    ): String? = context.getSharedPreferences(file, Context.MODE_PRIVATE).getString(key, null)

    @Test
    fun `reads what a released version stored, from each file and key`() {
        stored("recent_apps", "components", "$BELL\n$WORK_MAIL\n$ALARM")
        stored("favourites", "favourites", "$ALARM\n$BELL")
        stored("favourites", "collections", "Morning\t$ALARM\t$BELL")
        stored("hidden_apps", "hidden", BELL)
        stored("aliases", "aliases", "$ALARM\tWake up")
        stored("groups", "groups", "Work\t$WORK_MAIL")
        stored("settings", "recently_used", "Off")

        val stores = LauncherStores(context)

        assertEquals(RecentApps(listOf(BELL, WORK_MAIL, ALARM)), stores.recents)
        assertEquals(Favourites(setOf(ALARM, BELL)), stores.favourites)
        assertEquals(AppCollections(mapOf("Morning" to listOf(ALARM, BELL))), stores.collections)
        assertEquals(HiddenApps(setOf(BELL)), stores.hidden)
        assertEquals(Aliases(mapOf(ALARM to "Wake up")), stores.aliases)
        assertEquals(UserGroups(mapOf("Work" to setOf(WORK_MAIL))), stores.userGroups)
        assertEquals(Choice.Off, stores.settings[Setting.RecentlyUsed])
    }

    @Test
    fun `writes each change back to the file and key a released version reads`() {
        val stores = LauncherStores(context)

        stores.save(RecentApps(listOf(WORK_MAIL, ALARM)))
        stores.save(Favourites(setOf(BELL)))
        stores.save(AppCollections(mapOf("Evening" to listOf(BELL))))
        stores.save(HiddenApps(setOf(ALARM)))
        stores.save(Aliases(mapOf(BELL to "Ding")))
        stores.save(UserGroups(mapOf("Work" to setOf(WORK_MAIL))))
        stores.save(stores.settings.cycled(Setting.Clock, 1))

        assertEquals("$WORK_MAIL\n$ALARM", storedIn("recent_apps", "components"))
        assertEquals(BELL, storedIn("favourites", "favourites"))
        assertEquals("Evening\t$BELL", storedIn("favourites", "collections"))
        assertEquals(ALARM, storedIn("hidden_apps", "hidden"))
        assertEquals("$BELL\tDing", storedIn("aliases", "aliases"))
        assertEquals("Work\t$WORK_MAIL", storedIn("groups", "groups"))
        assertEquals("Off", storedIn("settings", "status_clock"))
    }

    @Test
    fun `favourites and collections share one file without overwriting each other`() {
        stored("favourites", "favourites", ALARM)
        stored("favourites", "collections", "Morning\t$ALARM")
        val stores = LauncherStores(context)

        stores.save(Favourites(setOf(ALARM, BELL)))
        stores.save(AppCollections(mapOf("Morning" to listOf(ALARM, BELL))))

        assertEquals("$ALARM\n$BELL", storedIn("favourites", "favourites"))
        assertEquals("Morning\t$ALARM\t$BELL", storedIn("favourites", "collections"))
        val reloaded = LauncherStores(context)
        assertEquals(Favourites(setOf(ALARM, BELL)), reloaded.favourites)
        assertEquals(AppCollections(mapOf("Morning" to listOf(ALARM, BELL))), reloaded.collections)
    }

    private companion object {
        const val ALARM = "com.example.alarm/com.example.alarm.MainActivity"
        const val BELL = "com.example.bell/com.example.bell.MainActivity"

        // An app of a work profile, its profile's serial number before the component (#78).
        const val WORK_MAIL = "10:com.example.mail/com.example.mail.MainActivity"
    }
}
