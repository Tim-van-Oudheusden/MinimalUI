package dev.buildwithtim.minimalui

import android.app.Activity
import android.app.ActivityOptions
import android.app.Application
import android.content.ComponentName
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.ResolveInfo
import android.os.Bundle
import android.os.Looper
import android.view.KeyEvent
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.shadows.ShadowActivity

@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
class MainActivityTest {
    @get:Rule
    val rule = createEmptyComposeRule()

    private val application: Application = ApplicationProvider.getApplicationContext()
    private val launcherQuery = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    private var controller: ActivityController<MainActivity>? = null

    @After
    fun closeActivity() {
        controller?.pause()?.stop()?.destroy()
    }

    private fun install(label: String, packageName: String) {
        val info = ResolveInfo().apply {
            nonLocalizedLabel = label
            activityInfo = ActivityInfo().apply {
                this.packageName = packageName
                name = "$packageName.Main"
                exported = true
                applicationInfo = ApplicationInfo().apply { this.packageName = packageName }
            }
        }
        shadowOf(application.packageManager).addResolveInfoForIntent(launcherQuery, info)
        shadowOf(application.packageManager).addActivityIfNotPresent(ComponentName(packageName, "$packageName.Main"))
    }

    private fun uninstall(packageName: String) {
        shadowOf(application.packageManager).removeResolveInfosForIntent(launcherQuery, packageName)
        shadowOf(application.packageManager).removeActivity(ComponentName(packageName, "$packageName.Main"))
    }

    private fun launch(): ActivityController<MainActivity> =
        Robolectric.buildActivity(MainActivity::class.java).setup().also { controller = it }

    private fun assertStillHome(activity: ActivityController<MainActivity>) =
        assertTrue(!activity.get().isFinishing && !activity.get().isDestroyed)

    // MinimalUI lists itself too, so test apps are named to sort before it and own the first row.

    /** The app list loads off the main thread; wait until [label] is listed, or no longer listed. */
    private fun awaitListed(label: String, listed: Boolean = true) =
        rule.waitUntil(timeoutMillis = 5_000) {
            rule.onAllNodesWithText(label).fetchSemanticsNodes().isNotEmpty() == listed
        }

    private fun press(key: Key) = rule.onRoot().performKeyInput { pressKey(key) }

    /** Types through the activity, the path a key takes when no row consumes it. */
    private fun type(activity: ActivityController<MainActivity>, keyCode: Int) {
        activity.get().dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
        activity.get().dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))
    }

    /** The user leaves for the opened app, [meanwhile] happens, and they come back by a route other than Home. */
    private fun leaveAndComeBack(activity: ActivityController<MainActivity>, meanwhile: () -> Unit = {}) {
        activity.pause().stop()
        meanwhile()
        activity.restart().resume()
        rule.waitForIdle()
    }

    /** Compose has nothing on screen to wait on while the launcher is stopped, so this runs the main thread itself. */
    private fun awaitWhileAway(condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 5_000
        while (!condition()) {
            check(System.currentTimeMillis() < deadline) { "Timed out while away from the launcher" }
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(10)
        }
    }

    private fun Bundle?.contents(): Map<String, Any?>? = this?.let { b -> b.keySet().associateWith { b.get(it) } }

    @Test
    fun `Enter opens the focused app in its own task`() {
        install("Alarm", "com.example.alarm")
        launch()
        awaitListed("Alarm")

        press(Key.Enter)

        val started = shadowOf(application).nextStartedActivity
        assertEquals(ComponentName("com.example.alarm", "com.example.alarm.Main"), started.component)
        assertEquals(Intent.ACTION_MAIN, started.action)
        val flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
        assertEquals(flags, started.flags and flags)
    }

    @Test
    fun `an app opens with no transition animation`() {
        install("Alarm", "com.example.alarm")
        val activity = launch()
        awaitListed("Alarm")

        press(Key.Enter)

        val options = shadowOf(activity.get()).nextStartedActivityForResult.options
        val none = ActivityOptions.makeCustomAnimation(activity.get(), 0, 0).toBundle()
        assertEquals(none.contents(), options.contents())
    }

    @Test
    fun `coming back from an app selects it, even when it was opened by touch`() {
        install("Alarm", "com.example.alarm")
        install("Zebra", "com.example.zebra")
        val activity = launch()
        awaitListed("Zebra")

        rule.onNodeWithText("Zebra").performClick()
        leaveAndComeBack(activity)

        rule.onNodeWithText("Zebra").assertIsFocused()
    }

    @Test
    fun `coming back selects the top when the app opened last was uninstalled meanwhile`() {
        install("Alarm", "com.example.alarm")
        install("Bell", "com.example.bell")
        val activity = launch()
        awaitListed("Bell")
        press(Key.DirectionDown)
        press(Key.Enter)

        leaveAndComeBack(activity) {
            uninstall("com.example.bell")
            application.sendBroadcast(
                Intent(Intent.ACTION_PACKAGE_REMOVED, android.net.Uri.fromParts("package", "com.example.bell", null)),
            )
            // Nothing is on screen while the launcher is stopped; the reload shows in the recent apps instead.
            awaitWhileAway { activity.get().recentApps.isEmpty() }
        }

        rule.onNodeWithText("Alarm").assertIsFocused()
    }

    @Test
    fun `coming back keeps the filter and selects the app opened from it`() {
        install("Alarm", "com.example.alarm")
        install("Zebra", "com.example.zebra")
        install("Zoo", "com.example.zoo")
        val activity = launch()
        awaitListed("Zoo")
        type(activity, KeyEvent.KEYCODE_Z)
        awaitListed("Alarm", listed = false)
        press(Key.DirectionDown)
        press(Key.Enter)

        leaveAndComeBack(activity)

        rule.onNodeWithText("z_").assertExists()
        rule.onNodeWithText("Zoo").assertIsFocused()
    }

    @Test
    fun `Home from an app puts focus on the first row, not on the app`() {
        install("Alarm", "com.example.alarm")
        install("Bell", "com.example.bell")
        val activity = launch()
        awaitListed("Bell")
        press(Key.DirectionDown)
        press(Key.Enter)

        activity.pause().stop().restart()
        activity.newIntent(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME))
        activity.resume()
        rule.waitForIdle()

        rule.onNodeWithText("Alarm").assertIsFocused()
    }

    @Test
    fun `opening an app uninstalled while listed drops it instead of crashing`() {
        shadowOf(application).checkActivities(true)
        install("Alarm", "com.example.alarm")
        install("Zebra", "com.example.zebra")
        val activity = launch()
        awaitListed("Alarm")

        uninstall("com.example.alarm")
        press(Key.Enter)

        awaitListed("Alarm", listed = false)
        assertStillHome(activity)
        rule.onNodeWithText("Zebra").assertExists()
    }

    @Test
    @Config(shadows = [StartActivityDenied::class])
    fun `opening an app we may not start is ignored`() {
        install("Alarm", "com.example.alarm")
        val activity = launch()
        awaitListed("Alarm")

        press(Key.Enter)
        rule.waitForIdle()

        assertStillHome(activity)
        rule.onNodeWithText("Alarm").assertIsFocused()
        assertNull(shadowOf(application).nextStartedActivity)
    }

    @Test
    fun `Back does not leave the home screen`() {
        install("Alarm", "com.example.alarm")
        val activity = launch()
        awaitListed("Alarm")

        activity.get().onBackPressedDispatcher.onBackPressed()

        assertFalse(activity.get().isFinishing)
    }

    @Test
    fun `Home clears the filter and puts focus back on the first row`() {
        install("Alarm", "com.example.alarm")
        install("Zebra", "com.example.zebra")
        val activity = launch()
        awaitListed("Zebra")

        type(activity, KeyEvent.KEYCODE_Z)
        rule.onNodeWithText("z_").assertExists()
        awaitListed("Alarm", listed = false)

        activity.newIntent(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME))

        awaitListed("Alarm")
        rule.onNodeWithText("z_").assertDoesNotExist()
        rule.onNodeWithText("Alarm").assertIsFocused()
    }

    @Test
    fun `a filter that matches nothing says so, and Backspace edits the query from there`() {
        install("Alarm", "com.example.alarm")
        val activity = launch()
        awaitListed("Alarm")

        type(activity, KeyEvent.KEYCODE_A)
        type(activity, KeyEvent.KEYCODE_Q)
        rule.onNodeWithText("aq_").assertExists()
        awaitListed("No match")

        type(activity, KeyEvent.KEYCODE_DEL)

        rule.onNodeWithText("a_").assertExists()
        awaitListed("Alarm")
        rule.onNodeWithText("No match").assertDoesNotExist()
    }

    @Test
    fun `Home from a filter that matches nothing clears it`() {
        install("Alarm", "com.example.alarm")
        val activity = launch()
        awaitListed("Alarm")
        type(activity, KeyEvent.KEYCODE_Q)
        awaitListed("No match")

        activity.newIntent(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME))

        awaitListed("Alarm")
        rule.onNodeWithText("q_").assertDoesNotExist()
        rule.onNodeWithText("No match").assertDoesNotExist()
        rule.onNodeWithText("Alarm").assertIsFocused()
    }

    @Test
    fun `an opened app is recent, and stays recent when the launcher starts again`() {
        install("Alarm", "com.example.alarm")
        install("Zebra", "com.example.zebra")
        launch()
        awaitListed("Alarm")

        press(Key.DirectionDown)
        press(Key.DirectionDown)
        press(Key.Enter)
        press(Key.DirectionUp)
        press(Key.DirectionUp)
        press(Key.Enter)
        assertEquals(listOf("Alarm", "Zebra"), controller!!.get().recentApps.map { it.label })

        // A new instance reads the list back from storage, as after the process was killed.
        closeActivity()
        val restarted = launch()
        awaitListed("Alarm")
        assertEquals(listOf("Alarm", "Zebra"), restarted.get().recentApps.map { it.label })
    }

    @Test
    fun `an app uninstalled after it was opened is no longer recent`() {
        install("Alarm", "com.example.alarm")
        val activity = launch()
        awaitListed("Alarm")
        press(Key.Enter)

        uninstall("com.example.alarm")
        application.sendBroadcast(
            Intent(Intent.ACTION_PACKAGE_REMOVED, android.net.Uri.fromParts("package", "com.example.alarm", null)),
        )

        awaitListed("Alarm", listed = false)
        assertEquals(emptyList<String>(), activity.get().recentApps.map { it.label })
    }

    @Test
    @Config(shadows = [StartActivityDenied::class])
    fun `an app that could not be opened does not become recent`() {
        install("Alarm", "com.example.alarm")
        val activity = launch()
        awaitListed("Alarm")

        press(Key.Enter)
        rule.waitForIdle()

        assertEquals(emptyList<String>(), activity.get().recentApps)
    }

    @Test
    fun `hardware arrow keys and the D-pad wrap and page through the list`() {
        install("Alarm", "com.example.alarm")
        install("Zebra", "com.example.zebra")
        val activity = launch()
        awaitListed("Zebra")

        type(activity, KeyEvent.KEYCODE_DPAD_UP)
        rule.onNodeWithText("Zebra").assertIsFocused()

        type(activity, KeyEvent.KEYCODE_DPAD_LEFT)
        rule.onNodeWithText("Alarm").assertIsFocused()

        type(activity, KeyEvent.KEYCODE_DPAD_RIGHT)
        rule.onNodeWithText("Zebra").assertIsFocused()
    }

    @Test
    fun `a newly installed app is listed without leaving the home screen`() {
        install("Alarm", "com.example.alarm")
        launch()
        awaitListed("Alarm")

        install("Zebra", "com.example.zebra")
        application.sendBroadcast(
            Intent(Intent.ACTION_PACKAGE_ADDED, android.net.Uri.fromParts("package", "com.example.zebra", null)),
        )

        awaitListed("Zebra")
    }
}

/** Every activity start fails the way an unexported or permission-guarded activity does. */
@Implements(Activity::class)
class StartActivityDenied : ShadowActivity() {
    @Implementation
    protected fun startActivity(intent: Intent, options: Bundle?): Unit = throw SecurityException("Permission Denial")
}
