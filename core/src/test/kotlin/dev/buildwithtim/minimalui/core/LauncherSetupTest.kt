package dev.buildwithtim.minimalui.core

import org.junit.Assert.assertEquals
import org.junit.Test

class LauncherSetupTest {
    // A setup with something in every store, and names that need escaping in JSON.
    private val full =
        LauncherSetup(
            settings = Settings().cycled(Setting.Clock, 1).cycled(Setting.RecentlyUsed, 2),
            recents = RecentApps(listOf(BELL, WORK_MAIL, ALARM)),
            favourites = Favourites(setOf(ALARM, WORK_MAIL)),
            collections =
                AppCollections(
                    mapOf(
                        "Morning \"routine\"" to listOf(BELL, ALARM),
                        "Ünïcode ☕" to listOf(ALARM),
                    ),
                ),
            hidden = HiddenApps(setOf(BELL)),
            aliases = Aliases(mapOf(ALARM to "Wake up \\ now", WORK_MAIL to "Mail 📬")),
            userGroups = UserGroups(mapOf("Work" to setOf(WORK_MAIL, BELL))),
        )

    @Test
    fun `an exported setup imports as it was, every store included`() {
        assertEquals(SetupImport.Read(full), LauncherSetup.imported(full.exported()))
    }

    @Test
    fun `an empty setup imports as empty, with the default settings`() {
        assertEquals(SetupImport.Read(LauncherSetup()), LauncherSetup.imported(LauncherSetup().exported()))
    }

    @Test
    fun `a collection keeps its order through an export`() {
        val setup = LauncherSetup(collections = AppCollections(mapOf("Morning" to listOf(BELL, ALARM, WORK_MAIL))))

        val imported = LauncherSetup.imported(setup.exported()) as SetupImport.Read

        assertEquals(
            listOf(BELL, ALARM, WORK_MAIL),
            imported.setup.collections.byName
                .getValue("Morning"),
        )
    }

    @Test
    fun `reads the version 1 file format`() {
        // Spelled out on purpose, apart from the code: a file exported by a released version must keep importing.
        val file =
            """
            {
              "format": "minimalui-setup",
              "version": 1,
              "settings": {"status_clock": "Off", "status_battery_percentage": "On", "recently_used": "Five"},
              "recent": ["$BELL", "$ALARM"],
              "favourites": ["$ALARM"],
              "collections": {"Morning": ["$BELL", "$ALARM"]},
              "groups": {"Work": ["$WORK_MAIL"]},
              "hidden": ["$BELL"],
              "aliases": {"$ALARM": "Wake up"}
            }
            """.trimIndent()

        val expected =
            LauncherSetup(
                settings = Settings().cycled(Setting.Clock, 1).cycled(Setting.RecentlyUsed, 1),
                recents = RecentApps(listOf(BELL, ALARM)),
                favourites = Favourites(setOf(ALARM)),
                collections = AppCollections(mapOf("Morning" to listOf(BELL, ALARM))),
                hidden = HiddenApps(setOf(BELL)),
                aliases = Aliases(mapOf(ALARM to "Wake up")),
                userGroups = UserGroups(mapOf("Work" to setOf(WORK_MAIL))),
            )
        assertEquals(SetupImport.Read(expected), LauncherSetup.imported(file))
    }

    @Test
    fun `a missing part of the setup imports as empty, and unknown members are ignored`() {
        val file = """{"format": "minimalui-setup", "version": 1, "favourites": ["$ALARM"], "theme": "dark"}"""

        assertEquals(
            SetupImport.Read(LauncherSetup(favourites = Favourites(setOf(ALARM)))),
            LauncherSetup.imported(file),
        )
    }

    @Test
    fun `entries the launcher would not store are skipped, as when it reads its own storage`() {
        val file =
            """
            {
              "format": "minimalui-setup",
              "version": 1,
              "settings": {"status_clock": "Sideways", "recently_used": 5},
              "favourites": ["junk", 7, "$ALARM", "com.example.tab/x\ty/z", "com.example.line/x\ny/z"],
              "collections": {"  ": ["$ALARM"], "Empty": [], "Two\tlines\n": ["$BELL"], "Not a list": "$ALARM"},
              "groups": {"work": ["$WORK_MAIL"], "Work": ["$BELL"]},
              "aliases": {"$ALARM": "  ", "$BELL": "Ding\tdong", "junk": "Nothing"},
              "recent": "$ALARM"
            }
            """.trimIndent()

        val expected =
            LauncherSetup(
                favourites = Favourites(setOf(ALARM)),
                collections = AppCollections(mapOf("Two lines" to listOf(BELL))),
                aliases = Aliases(mapOf(BELL to "Ding dong")),
                userGroups = UserGroups(mapOf("work" to setOf(WORK_MAIL, BELL))),
            )
        assertEquals(SetupImport.Read(expected), LauncherSetup.imported(file))
    }

    @Test
    fun `an alias for an app with a line break, or that is not text, is skipped instead of naming another app`() {
        val file =
            """{"format": "minimalui-setup", "version": 1, "aliases": {"$BELL\n$ALARM": "Not Alarm", "$BELL": 5}}"""

        assertEquals(SetupImport.Read(LauncherSetup()), LauncherSetup.imported(file))
    }

    @Test
    fun `a file cut off anywhere is refused, not read in part`() {
        val whole = """{"format": "minimalui-setup", "version": 1, "aliases": {"$ALARM": "\u00e9"}}"""

        for (end in whole.indices) {
            assertEquals(whole.take(end), SetupImport.NotASetup, LauncherSetup.imported(whole.take(end)))
        }
    }

    @Test
    fun `JSON escapes are read, surrogate pairs included`() {
        val file =
            """{"format": "minimalui-setup", "version": 1, "aliases": {"$ALARM": "\u00e9\ud83d\ude00 \"\\\/"}}"""

        assertEquals(
            SetupImport.Read(LauncherSetup(aliases = Aliases(mapOf(ALARM to "é😀 \"\\/")))),
            LauncherSetup.imported(file),
        )
    }

    @Test
    fun `a file that is not a MinimalUI setup is refused`() {
        val notSetups =
            listOf(
                "",
                "not json",
                "[]",
                "{}",
                """{"format": "something-else", "version": 1}""",
                """{"format": "minimalui-setup"}""",
                """{"format": "minimalui-setup", "version": "1"}""",
                """{"format": "minimalui-setup", "version": 0}""",
                """{"format": "minimalui-setup", "version": 1.5}""",
                """{"format": "minimalui-setup", "version": 1} trailing""",
                """{"format": "minimalui-setup", "version": 1""",
                """{"format": "minimalui-setup", "version": 1, "aliases": {"a/b": "\x"}}""",
                """{"format": "minimalui-setup", "version": 1, "aliases": {"a/b": "\u00zz"}}""",
                """{"format": "minimalui-setup", "version": 1, "aliases": {"a/b": "line
                break"}}""",
                """{"format": "minimalui-setup", "version": 01}""",
                """{"format": "minimalui-setup", "version": 1, "x": tru}""",
                """{"format": "minimalui-setup", "version": 1, "aliases": {com.example.a/b": "Name"}}""",
                """{"format": "minimalui-setup", "version": 1, "x": "\u${"\u0661\u0662\u0663\u0664"}"}""",
            )

        for (text in notSetups) assertEquals(text, SetupImport.NotASetup, LauncherSetup.imported(text))
    }

    @Test
    fun `a file nested too deep is refused instead of overflowing the stack`() {
        val deep = """{"format": "minimalui-setup", "version": 1, "x": ${"[".repeat(100_000)}"""

        assertEquals(SetupImport.NotASetup, LauncherSetup.imported(deep))
    }

    @Test
    fun `numbers, booleans and null are read wherever they appear`() {
        val file =
            """{"format":"minimalui-setup","version":1.0e0,"x":[-0.5,2E+3,true,false,null,{}],"favourites":["$ALARM"]}"""

        assertEquals(
            SetupImport.Read(LauncherSetup(favourites = Favourites(setOf(ALARM)))),
            LauncherSetup.imported(file),
        )
    }

    @Test
    fun `a file from a newer MinimalUI is refused rather than read in part`() {
        assertEquals(
            SetupImport.TooNew,
            LauncherSetup.imported("""{"format": "minimalui-setup", "version": 2, "favourites": ["$ALARM"]}"""),
        )
    }

    private companion object {
        const val ALARM = "com.example.alarm/com.example.alarm.MainActivity"
        const val BELL = "com.example.bell/com.example.bell.MainActivity"

        // An app of a work profile, its profile's serial number before the component (#78).
        const val WORK_MAIL = "10:com.example.mail/com.example.mail.MainActivity"
    }
}
