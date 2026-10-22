package dev.buildwithtim.minimalui

import android.content.Context
import android.content.pm.ApplicationInfo
import androidx.test.core.app.ApplicationProvider
import dev.buildwithtim.minimalui.core.Aliases
import dev.buildwithtim.minimalui.core.AppCollections
import dev.buildwithtim.minimalui.core.Choice
import dev.buildwithtim.minimalui.core.Favourites
import dev.buildwithtim.minimalui.core.HiddenApps
import dev.buildwithtim.minimalui.core.LauncherSetup
import dev.buildwithtim.minimalui.core.RecentApps
import dev.buildwithtim.minimalui.core.Setting
import dev.buildwithtim.minimalui.core.Settings
import dev.buildwithtim.minimalui.core.UserGroups
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.xmlpull.v1.XmlPullParser

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

    @Test
    fun `an imported setup replaces every store, and is what the launcher reads when it starts again`() {
        stored("favourites", "favourites", BELL)
        stored("aliases", "aliases", "$BELL\tDing")
        val stores = LauncherStores(context)
        val imported =
            LauncherSetup(
                settings = Settings().cycled(Setting.Clock, 1),
                recents = RecentApps(listOf(WORK_MAIL, ALARM)),
                favourites = Favourites(setOf(ALARM)),
                collections = AppCollections(mapOf("Morning" to listOf(ALARM, BELL))),
                hidden = HiddenApps(setOf(BELL)),
                aliases = Aliases(mapOf(ALARM to "Wake up")),
                userGroups = UserGroups(mapOf("Work" to setOf(WORK_MAIL))),
            )

        stores.replaceWith(imported)

        assertEquals(imported, stores.setup)
        assertEquals(imported, LauncherStores(context).setup)
    }

    @Test
    fun `leaves every store out of Android's backup and its transfer to a new phone`() {
        // docs/usage.md promises the setup leaves the phone only in a file the user exports (#208). Up to Android 11
        // allowBackup="false" turns off both backup and transfer; from Android 12 it no longer covers device-to-device
        // transfer, which only the exclusions under <device-transfer> in the data extraction rules keep out.
        val app = context.applicationInfo
        assertEquals(0, app.flags and ApplicationInfo.FLAG_ALLOW_BACKUP)
        assertEquals(R.xml.data_extraction_rules, manifestApplicationAttribute("dataExtractionRules"))

        val rules = extractionRules()
        for (section in listOf("cloud-backup", "device-transfer")) {
            assertEquals(section, APP_DATA_DOMAINS.map { "exclude $it ." }.toSet(), rules[section]?.toSet())
        }
    }

    /** A resource reference on the packaged manifest's <application>, which ApplicationInfo keeps hidden. */
    private fun manifestApplicationAttribute(name: String): Int {
        val parser = context.assets.openXmlResourceParser("AndroidManifest.xml")
        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            if (parser.eventType == XmlPullParser.START_TAG && parser.name == "application") {
                return parser.getAttributeResourceValue(ANDROID, name, 0)
            }
        }
        error("no <application> in the manifest")
    }

    /** Each section of the data extraction rules (depth 2), with its rules (depth 3) as "tag domain path" lines. */
    private fun extractionRules(): Map<String, List<String>> {
        val sections = mutableMapOf<String, MutableList<String>>()
        val parser = context.resources.getXml(R.xml.data_extraction_rules)
        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            if (parser.eventType != XmlPullParser.START_TAG) continue
            if (parser.depth == 2) sections[parser.name] = mutableListOf()
            if (parser.depth == 3) {
                val domain = parser.getAttributeValue(null, "domain")
                sections.values.last() += "${parser.name} $domain ${parser.getAttributeValue(null, "path")}"
            }
        }
        return sections
    }

    private companion object {
        const val ANDROID = "http://schemas.android.com/apk/res/android"

        // The domains of the app's own credential-encrypted storage, where every store's preferences file lives.
        val APP_DATA_DOMAINS = listOf("root", "file", "database", "sharedpref", "external")

        const val ALARM = "com.example.alarm/com.example.alarm.MainActivity"
        const val BELL = "com.example.bell/com.example.bell.MainActivity"

        // An app of a work profile, its profile's serial number before the component (#78).
        const val WORK_MAIL = "10:com.example.mail/com.example.mail.MainActivity"
    }
}
