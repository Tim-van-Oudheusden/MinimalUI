package dev.buildwithtim.minimalui.e2e

import java.io.File

/**
 * The device under test, driven by keys as a keyboard phone is, and read through uiautomator and the activity stack.
 */
object Device {
    const val MINIMALUI = "dev.buildwithtim.minimalui"

    // How long a screen or an app gets to appear: a cold launcher start and its first app load on a CI emulator take
    // a few seconds.
    private const val WAIT_MILLIS = 30_000L
    private const val POLL_MILLIS = 500L
    private const val DUMP = "/data/local/tmp/minimalui-e2e.xml"

    // `dumpsys activity activities` names the resumed activity as topResumedActivity (Android 10+) or mResumedActivity.
    private val RESUMED = Regex("""(?:topResumedActivity|mResumedActivity)[=:]\s*ActivityRecord\{\S+ u\d+ ([^/\s]+)/""")

    /** The package of the activity that opens Android's settings, as MinimalUI's Settings row finds it. */
    val settingsPackage: String by lazy {
        Adb
            .shell("cmd", "package", "resolve-activity", "--brief", "-a", "android.settings.SETTINGS")
            .lines()
            .last { it.isNotBlank() }
            .substringBefore('/')
            .trim()
    }

    /** Installs [apk] over whatever build of MinimalUI is there; a build signed with another key is refused. */
    fun install(apk: File) {
        check(apk.isFile) { "no APK at $apk: run through ./gradlew :e2e:e2eTest, which builds it" }
        Adb.run("install", "-r", apk.path)
    }

    /** MinimalUI as on a fresh install, made the home app and brought up with Home. Clears its storage. */
    fun freshHome() {
        Adb.shell("pm", "clear", MINIMALUI)
        Adb.shell("cmd", "package", "set-home-activity", "$MINIMALUI/.MainActivity")
        press("HOME")
    }

    /** Presses the key named `KEYCODE_<key>`, e.g. `HOME`, `ENTER`, `MENU`, `DPAD_DOWN`. */
    fun press(key: String) {
        Adb.shell("input", "keyevent", "KEYCODE_$key")
    }

    /** Types [text] as key presses, letters and digits only (adb's `input text` reads spaces specially). */
    fun type(text: String) {
        require(text.all(Char::isLetterOrDigit)) { "type letters and digits only: $text" }
        Adb.shell("input", "text", text)
    }

    /** Kills MinimalUI's process as Android does under memory pressure, keeping its storage. */
    fun killMinimalUi() {
        Adb.shell("am", "force-stop", MINIMALUI)
    }

    /** The package of the activity in front, or null while none is resumed. */
    fun foregroundPackage(): String? =
        RESUMED
            .find(Adb.shell("dumpsys", "activity", "activities"))
            ?.groupValues
            ?.get(1)

    /** Waits until the app in front is [packageName]; fails naming the one that is. */
    fun waitForForeground(packageName: String) {
        var last: String? = null
        waitUntil({ "$packageName never came to the front; in front: $last" }) {
            last = foregroundPackage()
            last == packageName
        }
    }

    /** Waits until MinimalUI shows a screen that meets [condition] and returns it; fails with the last one seen. */
    fun waitForLauncher(
        what: String,
        condition: (Screen) -> Boolean,
    ): Screen {
        var last: Screen? = null
        waitUntil({ "MinimalUI never showed $what; last screen: $last\n${last?.xml}" }) {
            last = dump()
            last?.let { it.packageName == MINIMALUI && condition(it) } == true
        }
        return checkNotNull(last)
    }

    // The screen as uiautomator sees it, or null when it could not dump it, as while the UI is still settling.
    private fun dump(): Screen? {
        val dumped = Adb.tryShell("uiautomator", "dump", DUMP) ?: return null
        if ("dumped to" !in dumped) return null
        return Screen.parse(Adb.run("exec-out", "cat", DUMP))
    }

    // Polls [done] until it holds; fails the test with [failure] once the wait is over.
    private fun waitUntil(
        failure: () -> String,
        done: () -> Boolean,
    ) {
        val deadline = System.currentTimeMillis() + WAIT_MILLIS
        while (!done()) {
            if (System.currentTimeMillis() > deadline) throw AssertionError("after $WAIT_MILLIS ms: ${failure()}")
            Thread.sleep(POLL_MILLIS)
        }
    }
}
