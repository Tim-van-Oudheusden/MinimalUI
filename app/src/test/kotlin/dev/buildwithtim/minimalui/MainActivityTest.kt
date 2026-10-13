package dev.buildwithtim.minimalui

import android.app.Activity
import android.app.ActivityOptions
import android.app.Application
import android.content.ComponentName
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.ResolveInfo
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Bundle
import android.os.Looper
import android.provider.Settings
import android.view.KeyEvent
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
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
import org.robolectric.shadows.ShadowNetwork
import org.robolectric.shadows.ShadowNetworkCapabilities

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

    /** An app that came with the system, so it cannot be uninstalled. */
    private fun installSystem(label: String, packageName: String) {
        shadowOf(application.packageManager).installPackage(
            PackageInfo().apply {
                this.packageName = packageName
                applicationInfo = ApplicationInfo().apply {
                    this.packageName = packageName
                    flags = ApplicationInfo.FLAG_SYSTEM
                }
            },
        )
        install(label, packageName)
    }

    /** Enter held until it auto-repeats, the way a hardware keyboard sends a long press, then released. */
    private fun holdEnter(activity: ActivityController<MainActivity>) {
        val down = KeyEvent(0, 0, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER, 0)
        activity.get().dispatchKeyEvent(down)
        activity.get().dispatchKeyEvent(KeyEvent.changeTimeRepeat(down, 500, 1, KeyEvent.FLAG_LONG_PRESS))
        activity.get().dispatchKeyEvent(KeyEvent.changeAction(down, KeyEvent.ACTION_UP))
        rule.waitForIdle()
    }

    /** Opens the context menu on the selected app with the Menu key. */
    private fun openMenu(activity: ActivityController<MainActivity>) {
        type(activity, KeyEvent.KEYCODE_MENU)
        awaitListed("App info")
    }

    private fun packageUri(packageName: String) = android.net.Uri.fromParts("package", packageName, null)

    @Test
    fun `the Menu key replaces the list with the app's menu, and Esc brings the list back on that app`() {
        install("Alarm", "com.example.alarm")
        install("Bell", "com.example.bell")
        val activity = launch()
        awaitListed("Bell")
        press(Key.DirectionDown)

        openMenu(activity)
        rule.onNodeWithText("Alarm").assertDoesNotExist()
        rule.onNodeWithText("Bell").assertExists()
        rule.onNodeWithText("App info").assertIsFocused()

        type(activity, KeyEvent.KEYCODE_ESCAPE)

        awaitListed("Alarm")
        rule.onNodeWithText("App info").assertDoesNotExist()
        rule.onNodeWithText("Bell").assertIsFocused()
    }

    @Test
    fun `a long press of Enter opens the menu and not the app`() {
        install("Alarm", "com.example.alarm")
        val activity = launch()
        awaitListed("Alarm")

        holdEnter(activity)

        rule.onNodeWithText("App info").assertIsFocused()
        assertNull(shadowOf(application).nextStartedActivity)
        assertEquals(emptyList<String>(), activity.get().recentApps)
    }

    @Test
    fun `App info opens Android's details screen for the app, and the list comes back on it`() {
        install("Alarm", "com.example.alarm")
        install("Bell", "com.example.bell")
        val activity = launch()
        awaitListed("Bell")
        press(Key.DirectionDown)
        openMenu(activity)

        press(Key.Enter)

        val started = shadowOf(application).nextStartedActivity
        assertEquals(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, started.action)
        assertEquals(packageUri("com.example.bell"), started.data)
        awaitListed("Alarm")
        rule.onNodeWithText("Bell").assertIsFocused()
    }

    @Test
    fun `Back closes the menu on the same app, and on the list still does nothing`() {
        install("Alarm", "com.example.alarm")
        install("Bell", "com.example.bell")
        val activity = launch()
        awaitListed("Bell")
        press(Key.DirectionDown)
        openMenu(activity)

        activity.get().onBackPressedDispatcher.onBackPressed()
        awaitListed("Alarm")
        rule.onNodeWithText("Bell").assertIsFocused()

        activity.get().onBackPressedDispatcher.onBackPressed()
        rule.waitForIdle()
        assertFalse(activity.get().isFinishing)
        rule.onNodeWithText("Bell").assertIsFocused()
    }

    @Test
    fun `Uninstall asks Android to remove the app`() {
        install("Alarm", "com.example.alarm")
        val activity = launch()
        awaitListed("Alarm")
        openMenu(activity)

        press(Key.DirectionDown)
        press(Key.Enter)

        val started = shadowOf(application).nextStartedActivity
        assertEquals(Intent.ACTION_DELETE, started.action)
        assertEquals(packageUri("com.example.alarm"), started.data)
    }

    @Test
    fun `Uninstall is disabled for a system app`() {
        installSystem("Alarm", "com.example.alarm")
        val activity = launch()
        awaitListed("Alarm")
        openMenu(activity)

        press(Key.DirectionDown)
        rule.onNodeWithText("Uninstall").assertIsFocused().assertIsNotEnabled()
        press(Key.Enter)

        assertNull(shadowOf(application).nextStartedActivity)
        rule.onNodeWithText("Uninstall").assertIsFocused()
    }

    @Test
    fun `Uninstall is disabled for the Settings entry the list always keeps`() {
        val settings = ResolveInfo().apply {
            activityInfo = ActivityInfo().apply {
                nonLocalizedLabel = "Settings"
                packageName = "com.example.settings"
                name = "com.example.settings.Main"
                applicationInfo = ApplicationInfo().apply { packageName = "com.example.settings" }
            }
        }
        shadowOf(application.packageManager).addResolveInfoForIntent(Intent(Settings.ACTION_SETTINGS), settings)
        val activity = launch()
        awaitListed("Settings")
        type(activity, KeyEvent.KEYCODE_S)
        awaitListed("MinimalUI", listed = false)
        rule.onNodeWithText("Settings").assertIsFocused()
        openMenu(activity)

        press(Key.DirectionDown)

        rule.onNodeWithText("Uninstall").assertIsNotEnabled()
    }

    @Test
    fun `Remove from recently used drops only that app, and only a recent app offers it`() {
        install("Alarm", "com.example.alarm")
        install("Bell", "com.example.bell")
        val activity = launch()
        awaitListed("Bell")
        press(Key.Enter)
        press(Key.DirectionDown)
        press(Key.Enter)
        openMenu(activity)

        rule.onNodeWithText("Remove from recently used").performClick()

        assertEquals(listOf("Alarm"), activity.get().recentApps.map { it.label })
        rule.onNodeWithText("Bell").assertIsFocused()
        openMenu(activity)
        rule.onNodeWithText("Remove from recently used").assertDoesNotExist()
        rule.onNodeWithText("Clear recently used").assertExists()
    }

    @Test
    fun `Clear recently used empties the recent apps, for good`() {
        install("Alarm", "com.example.alarm")
        val activity = launch()
        awaitListed("Alarm")
        press(Key.Enter)
        openMenu(activity)

        // The last action, reached by wrapping up from the first.
        press(Key.DirectionUp)
        rule.onNodeWithText("Clear recently used").assertIsFocused()
        press(Key.Enter)

        assertEquals(emptyList<String>(), activity.get().recentApps)
        closeActivity()
        val restarted = launch()
        awaitListed("Alarm")
        assertEquals(emptyList<String>(), restarted.get().recentApps)
        openMenu(restarted)
        rule.onNodeWithText("Clear recently used").assertDoesNotExist()
    }

    @Test
    fun `typing while the menu is open does not filter the list`() {
        install("Alarm", "com.example.alarm")
        val activity = launch()
        awaitListed("Alarm")
        openMenu(activity)

        type(activity, KeyEvent.KEYCODE_Z)
        type(activity, KeyEvent.KEYCODE_ESCAPE)

        awaitListed("Alarm")
        rule.onNodeWithText("z_").assertDoesNotExist()
    }

    @Test
    fun `Home closes the menu and returns to the top of the list`() {
        install("Alarm", "com.example.alarm")
        install("Bell", "com.example.bell")
        val activity = launch()
        awaitListed("Bell")
        press(Key.DirectionDown)
        openMenu(activity)

        activity.newIntent(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME))

        awaitListed("Alarm")
        rule.onNodeWithText("App info").assertDoesNotExist()
        rule.onNodeWithText("Alarm").assertIsFocused()
    }

    private fun batteryAt(level: Int, status: Int = BatteryManager.BATTERY_STATUS_DISCHARGING) =
        application.sendStickyBroadcast(
            Intent(Intent.ACTION_BATTERY_CHANGED)
                .putExtra(BatteryManager.EXTRA_LEVEL, level)
                .putExtra(BatteryManager.EXTRA_SCALE, 100)
                .putExtra(BatteryManager.EXTRA_STATUS, status),
        )

    private fun texts(): List<String> = rule.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.Text), true)
        .fetchSemanticsNodes()
        .flatMap { node -> node.config[SemanticsProperties.Text].map { it.text } }

    private val connectivity get() = shadowOf(application.getSystemService(ConnectivityManager::class.java))

    @Test
    fun `the status pill shows the battery charge, and follows it`() {
        install("Alarm", "com.example.alarm")
        batteryAt(42)
        launch()
        awaitListed("Alarm")
        rule.onNodeWithText("42%").assertExists()

        batteryAt(41, BatteryManager.BATTERY_STATUS_CHARGING)

        awaitListed("41%")
        rule.onNodeWithContentDescription("Charging", useUnmergedTree = true).assertExists()
    }

    @Test
    fun `the clock follows the system 12 or 24-hour setting`() {
        install("Alarm", "com.example.alarm")
        Settings.System.putString(application.contentResolver, Settings.System.TIME_12_24, "24")
        launch()
        awaitListed("Alarm")
        assertTrue(texts().toString(), texts().any { it.matches(Regex("\\d\\d:\\d\\d")) })

        Settings.System.putString(application.contentResolver, Settings.System.TIME_12_24, "12")
        application.sendBroadcast(Intent(Intent.ACTION_TIME_CHANGED))

        rule.waitUntil(timeoutMillis = 5_000) { texts().any { it.matches(Regex("\\d{1,2}:\\d\\d (AM|PM)")) } }
    }

    @Test
    fun `the Wi-Fi glyph shows while the default network is Wi-Fi`() {
        install("Alarm", "com.example.alarm")
        launch()
        awaitListed("Alarm")
        rule.onNodeWithContentDescription("Wi-Fi", useUnmergedTree = true).assertDoesNotExist()
        val network = ShadowNetwork.newInstance(1)
        val wifi = ShadowNetworkCapabilities.newInstance()
        shadowOf(wifi).addTransportType(NetworkCapabilities.TRANSPORT_WIFI)

        connectivity.networkCallbacks.forEach { it.onCapabilitiesChanged(network, wifi) }
        rule.waitForIdle()
        rule.onNodeWithContentDescription("Wi-Fi", useUnmergedTree = true).assertExists()

        connectivity.networkCallbacks.forEach { it.onLost(network) }
        rule.waitForIdle()
        rule.onNodeWithContentDescription("Wi-Fi", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun `the status sources are watched only while the launcher is in front`() {
        val activity = launch()
        val watched = listOf(Intent.ACTION_BATTERY_CHANGED, Intent.ACTION_TIME_TICK)
        fun receivers() = shadowOf(application).registeredReceivers.filter { r -> watched.all(r.intentFilter::hasAction) }
        assertEquals(1, receivers().size)
        assertEquals(1, connectivity.networkCallbacks.size)

        activity.pause().stop()

        assertEquals(0, receivers().size)
        assertEquals(0, connectivity.networkCallbacks.size)
    }

    @Test
    fun `the system status bar is hidden on the launcher`() {
        val activity = launch()

        val controller = WindowCompat.getInsetsController(activity.get().window, activity.get().window.decorView)
        assertEquals(WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE, controller.systemBarsBehavior)
        val insets = ViewCompat.getRootWindowInsets(activity.get().window.decorView)!!
        assertFalse(insets.isVisible(WindowInsetsCompat.Type.statusBars()))
    }
}

/** Every activity start fails the way an unexported or permission-guarded activity does. */
@Implements(Activity::class)
class StartActivityDenied : ShadowActivity() {
    @Implementation
    protected fun startActivity(intent: Intent, options: Bundle?): Unit = throw SecurityException("Permission Denial")
}
