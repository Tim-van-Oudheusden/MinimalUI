package dev.buildwithtim.minimalui.e2e

import android.content.ComponentName
import android.content.Intent
import android.view.KeyEvent
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The installed debug app on an emulator, driven by key presses from outside its process: what a user with a hardware
 * keyboard does on the home screen, through to the app it opens and back by the system's Home key.
 */
class HomeScreenTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val device = UiDevice.getInstance(instrumentation)

    // Self-instrumenting: this is the test APK's own context; the app under test is the other package, [APP].
    private val context = instrumentation.context

    // The package of the launcher entry named [OPENED], whichever package provides it on the image.
    private val opened: String =
        context.packageManager
            .queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
            .single { it.loadLabel(context.packageManager).toString() == OPENED }
            .activityInfo.packageName

    // The line above the list that shows what was typed, followed by its cursor.
    private val queryLine = By.textStartsWith(QUERY)

    @Before
    fun openMinimalUi() {
        device.wakeUp()
        device.executeShellCommand("wm dismiss-keyguard")
        val launch =
            checkNotNull(context.packageManager.getLaunchIntentForPackage(APP)) { "$APP has no launcher activity" }
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        context.startActivity(launch)
        assertTrue("MinimalUI did not come to the front", device.wait(Until.hasObject(By.pkg(APP).depth(0)), TIMEOUT))
    }

    @Test
    fun typingAnAppsNameAndPressingEnterOpensIt() {
        openByTyping()
    }

    @Test
    fun homeFromAnOpenedAppComesBackToMinimalUiWithTheFilterCleared() {
        val home = ComponentName(APP, "$APP.MainActivity").flattenToShortString()
        device.executeShellCommand("cmd package set-home-activity $home")
        openByTyping()

        device.pressHome()

        assertTrue("Home did not bring MinimalUI back", device.wait(Until.hasObject(By.pkg(APP).depth(0)), TIMEOUT))
        assertTrue("the filter line still shows the query", device.wait(Until.gone(queryLine), TIMEOUT))
    }

    // Types [QUERY] a key at a time, as on a hardware keyboard, then opens the top row, [OPENED].
    private fun openByTyping() {
        for (letter in QUERY) device.pressKeyCode(KeyEvent.KEYCODE_A + (letter - 'a'))
        assertTrue("the filter line does not show the query", device.wait(Until.hasObject(queryLine), TIMEOUT))

        device.pressEnter()

        assertTrue("Enter did not open $opened", device.wait(Until.hasObject(By.pkg(opened).depth(0)), TIMEOUT))
    }

    private companion object {
        // The debug build's application ID: app/build.gradle.kts gives debug builds no suffix.
        const val APP = "dev.buildwithtim.minimalui"

        // AOSP's Clock, on the ATD image the managed device runs: no other launcher entry has a word starting with
        // the query, so it is the only row left.
        const val OPENED = "Clock"
        const val QUERY = "clock"
        const val TIMEOUT = 15_000L
    }
}
