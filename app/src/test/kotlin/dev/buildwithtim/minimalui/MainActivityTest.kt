package dev.buildwithtim.minimalui

import android.app.Activity
import android.app.ActivityOptions
import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.LauncherActivityInfo
import android.content.pm.LauncherApps
import android.content.pm.LauncherUserInfo
import android.content.pm.PackageInfo
import android.graphics.Paint
import android.graphics.Rect
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.BatteryManager
import android.os.Bundle
import android.os.Looper
import android.os.UserHandle
import android.os.UserManager
import android.provider.Settings
import android.view.KeyEvent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.em
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.test.core.app.ApplicationProvider
import dev.buildwithtim.minimalui.core.Aliases
import dev.buildwithtim.minimalui.core.Favourites
import dev.buildwithtim.minimalui.core.LauncherSetup
import dev.buildwithtim.minimalui.core.Setting
import dev.buildwithtim.minimalui.core.SetupImport
import dev.buildwithtim.minimalui.ui.DimText
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
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
import org.robolectric.shadows.ShadowLauncherApps
import org.robolectric.shadows.ShadowNetwork
import org.robolectric.shadows.ShadowNetworkCapabilities
import org.robolectric.shadows.ShadowPaint
import org.robolectric.shadows.ShadowToast
import org.robolectric.shadows.ShadowUserManager
import org.robolectric.util.ReflectionHelpers
import org.robolectric.util.ReflectionHelpers.ClassParameter
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
class MainActivityTest {
    @get:Rule
    val rule = createEmptyComposeRule()

    private val application: Application = ApplicationProvider.getApplicationContext()
    private var controller: ActivityController<MainActivity>? = null

    @After
    fun closeActivity() {
        controller?.pause()?.stop()?.destroy()
    }

    private fun install(
        label: String,
        packageName: String,
        category: Int = ApplicationInfo.CATEGORY_UNDEFINED,
    ) {
        val activity =
            ActivityInfo().apply {
                nonLocalizedLabel = label
                this.packageName = packageName
                name = "$packageName.Main"
                applicationInfo =
                    ApplicationInfo().apply {
                        this.packageName = packageName
                        this.category = category
                    }
            }
        shadowOf(application.packageManager).installActivity(activity, Intent.ACTION_MAIN, Intent.CATEGORY_LAUNCHER)
    }

    private fun uninstall(packageName: String) {
        shadowOf(application.packageManager).removeActivity(ComponentName(packageName, "$packageName.Main"))
    }

    private fun launch(): ActivityController<MainActivity> =
        Robolectric.buildActivity(MainActivity::class.java).setup().also { controller = it }

    private fun assertStillHome(activity: ActivityController<MainActivity>) =
        assertTrue(!activity.get().isFinishing && !activity.get().isDestroyed)

    // MinimalUI lists itself too, so test apps are named to sort before it and own the first row.

    /** The app list loads off the main thread; wait until [label] is listed, or no longer listed. */
    private fun awaitListed(
        label: String,
        listed: Boolean = true,
    ) = rule.waitUntil(timeoutMillis = 5_000) {
        rule.onAllNodesWithText(label).fetchSemanticsNodes().isNotEmpty() == listed
    }

    private fun press(key: Key) = rule.onRoot().performKeyInput { pressKey(key) }

    /** A row of the open list; the hint row's pills take taps too, but are not rows. */
    private val listRow = hasClickAction() and hasAnyAncestor(hasScrollAction())

    /** Types through the activity, the path a key takes when no row consumes it. */
    private fun type(
        activity: ActivityController<MainActivity>,
        keyCode: Int,
    ) {
        activity.get().dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
        activity.get().dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))
    }

    /** The user leaves for the opened app, [meanwhile] happens, and they come back by a route other than Home. */
    private fun leaveAndComeBack(
        activity: ActivityController<MainActivity>,
        meanwhile: () -> Unit = {},
    ) {
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

    // Bundle has no equals; its string form lists every entry. Neither bundle here was parcelled, so both list theirs.
    private fun Bundle?.contents(): String? = this?.toString()

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

        // Zebra is now listed twice: first in the recently used block, whose row (the top one) is selected.
        rule.onAllNodesWithText("Zebra")[0].assertIsFocused()
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
        // Without the recently used block, whose first row would be the app itself.
        recentlyUsedOff()
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

        // Zebra is the last app row (after MinimalUI and its settings row): Up wraps to the Groups row below it, and Up
        // again skips the blank row to Zebra. Opened, it heads the recently used block above a blank row: Down reaches
        // the Groups row, Down again wraps to Zebra there, and Down once more skips the blank row to Alarm.
        press(Key.DirectionUp)
        press(Key.DirectionUp)
        press(Key.Enter)
        press(Key.DirectionDown)
        press(Key.DirectionDown)
        press(Key.DirectionDown)
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

        // The last row is the Groups row (#69); Up again skips the blank row above it.
        type(activity, KeyEvent.KEYCODE_DPAD_UP)
        rule.onNodeWithText("Groups").assertIsFocused()
        type(activity, KeyEvent.KEYCODE_DPAD_UP)
        rule.onNodeWithText("Zebra").assertIsFocused()

        type(activity, KeyEvent.KEYCODE_DPAD_LEFT)
        rule.onNodeWithText("Alarm").assertIsFocused()

        type(activity, KeyEvent.KEYCODE_DPAD_RIGHT)
        rule.onNodeWithText("Groups").assertIsFocused()
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
    private fun installSystem(
        label: String,
        packageName: String,
    ) {
        shadowOf(application.packageManager).installPackage(
            PackageInfo().apply {
                this.packageName = packageName
                applicationInfo =
                    ApplicationInfo().apply {
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
        installSystemSettings()
        val activity = launch()
        awaitListed("Settings")
        type(activity, KeyEvent.KEYCODE_S)
        awaitListed("MinimalUI", listed = false)
        rule.onNodeWithText("Settings").assertIsFocused()
        openMenu(activity)

        press(Key.DirectionDown)

        rule.onNodeWithText("Uninstall").assertIsNotEnabled()
    }

    // One row taller than the default screen, for the hint row (#74): the menu's recents rows still fit on it.
    @Test
    @Config(qualifiers = "h514dp")
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

    private fun texts(): List<String> =
        rule
            .onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.Text), true)
            .fetchSemanticsNodes()
            .flatMap { node -> node.config[SemanticsProperties.Text].map { it.text } }

    private val connectivity get() = shadowOf(application.getSystemService(ConnectivityManager::class.java))

    @Test
    fun `the status pill shows the battery charge, and follows it`() {
        install("Alarm", "com.example.alarm")
        application.broadcastBattery(42)
        launch()
        awaitListed("Alarm")
        rule.onNodeWithText("42%").assertExists()

        application.broadcastBattery(41, BatteryManager.BATTERY_STATUS_CHARGING)

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

        rule.waitUntil(timeoutMillis = 5_000) { texts().any { it.matches(Regex("\\d{1,2}:\\d\\d[ \u202F](AM|PM)")) } }
    }

    @Test
    @Config(qualifiers = "ko-rKR")
    fun `the clock writes the time as the device's language does`() {
        install("Alarm", "com.example.alarm")
        Settings.System.putString(application.contentResolver, Settings.System.TIME_12_24, "12")
        launch()
        awaitListed("Alarm")
        // Korean puts the marker first: 오후 3:05, never 3:05 오후 (#129).
        assertTrue(texts().toString(), texts().any { it.matches(Regex("(오전|오후) \\d{1,2}:\\d\\d")) })
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

        fun receivers() =
            shadowOf(application).registeredReceivers.filter { r -> watched.all(r.intentFilter::hasAction) }
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

    /** Android's Settings app, which the list always keeps, as a device that has one resolves it. */
    private fun installSystemSettings() {
        val settings =
            ActivityInfo().apply {
                nonLocalizedLabel = "Settings"
                packageName = "com.example.settings"
                name = "com.example.settings.Main"
                applicationInfo = ApplicationInfo().apply { packageName = "com.example.settings" }
            }
        shadowOf(application.packageManager).installActivity(settings, Settings.ACTION_SETTINGS)
    }

    /** The labels of the list's rows, top to bottom, as filtered by the [query] typed into [activity]. */
    private fun rowsMatching(
        activity: ActivityController<MainActivity>,
        query: String,
    ): List<String> {
        query.forEach { type(activity, KeyEvent.keyCodeFromString("KEYCODE_${it.uppercaseChar()}")) }
        rule.waitForIdle()
        return rule
            .onAllNodes(listRow)
            .fetchSemanticsNodes()
            .map { node -> node.config[SemanticsProperties.Text].joinToString { it.text } }
    }

    /**
     * Opens MinimalUI's settings from its row: the last app row while no test app sorts after it and Settings is
     * absent. Up wraps to the Groups row below it (#69), and Up again skips the blank row above that.
     */
    private fun openSettings() {
        awaitListed("MinimalUI settings")
        press(Key.DirectionUp)
        press(Key.DirectionUp)
        rule.onNodeWithText("MinimalUI settings").assertIsFocused()
        press(Key.Enter)
        awaitListed("Clock")
    }

    private fun assertValue(
        setting: String,
        value: String,
    ) = rule.onNodeWithText(setting).assert(hasText(value))

    @Test
    fun `the MinimalUI settings row sits right after Android's Settings, and typing finds it`() {
        installSystemSettings()
        install("Alarm", "com.example.alarm")
        val activity = launch()
        awaitListed("MinimalUI settings")

        assertEquals(listOf("Settings", "MinimalUI settings"), rowsMatching(activity, "se"))
        type(activity, KeyEvent.KEYCODE_DEL)
        type(activity, KeyEvent.KEYCODE_DEL)
        assertEquals(listOf("MinimalUI", "MinimalUI settings"), rowsMatching(activity, "min"))
    }

    @Test
    fun `the settings row opens the settings list, and Esc brings the list back on that row`() {
        install("Alarm", "com.example.alarm")
        val activity = launch()

        openSettings()
        rule.onNodeWithText("Alarm").assertDoesNotExist()
        rule.onNodeWithText("Clock").assertIsFocused()
        assertValue("Clock", "On")
        assertValue("Battery percentage", "On")
        assertNull(shadowOf(application).nextStartedActivity)

        type(activity, KeyEvent.KEYCODE_ESCAPE)

        awaitListed("Alarm")
        rule.onNodeWithText("Clock").assertDoesNotExist()
        rule.onNodeWithText("MinimalUI settings").assertIsFocused()
        assertEquals(emptyList<String>(), activity.get().recentApps)
    }

    @Test
    fun `Left and Right cycle the selected setting, and Enter toggles it`() {
        launch()
        openSettings()

        press(Key.DirectionRight)
        assertValue("Clock", "Off")
        press(Key.DirectionRight)
        assertValue("Clock", "On")
        press(Key.DirectionLeft)
        assertValue("Clock", "Off")
        press(Key.Enter)
        assertValue("Clock", "On")

        press(Key.DirectionDown)
        press(Key.Enter)
        assertValue("Battery percentage", "Off")
        assertValue("Clock", "On")
    }

    @Test
    fun `a changed setting is kept when the launcher starts again`() {
        launch()
        openSettings()
        press(Key.DirectionDown)
        press(Key.DirectionRight)

        // A new instance reads the settings back from storage, as after the process was killed.
        closeActivity()
        launch()
        openSettings()

        assertValue("Battery percentage", "Off")
        assertValue("Clock", "On")
    }

    @Test
    fun `the status pill follows the settings at once`() {
        application.broadcastBattery(42)
        Settings.System.putString(application.contentResolver, Settings.System.TIME_12_24, "24")
        launch()
        openSettings()
        val clock = Regex("\\d\\d:\\d\\d")
        assertTrue(texts().any { it.matches(clock) })
        rule.onNodeWithText("42%").assertExists()

        press(Key.Enter)
        rule.waitForIdle()
        assertFalse(texts().toString(), texts().any { it.matches(clock) })

        press(Key.DirectionDown)
        press(Key.Enter)
        rule.onNodeWithText("42%").assertDoesNotExist()

        type(controller!!, KeyEvent.KEYCODE_ESCAPE)
        awaitListed("MinimalUI")
        rule.onNodeWithText("42%").assertDoesNotExist()
        assertFalse(texts().any { it.matches(clock) })
    }

    @Test
    fun `About, the last row, shows the version and build commit, and Esc goes back one list at a time`() {
        val activity = launch()
        openSettings()

        press(Key.DirectionUp)
        rule.onNodeWithText("About").assertIsFocused()
        press(Key.Enter)

        awaitListed("Version ${BuildConfig.VERSION_NAME}")
        rule.onNodeWithText("Commit ${BuildConfig.BUILD_COMMIT}").assertExists()
        assertTrue(BuildConfig.BUILD_COMMIT.isNotBlank())
        rule.onNodeWithText("Clock").assertDoesNotExist()

        type(activity, KeyEvent.KEYCODE_ESCAPE)
        awaitListed("Clock")
        rule.onNodeWithText("About").assertIsFocused()

        activity.get().onBackPressedDispatcher.onBackPressed()
        awaitListed("MinimalUI")
        rule.onNodeWithText("MinimalUI settings").assertIsFocused()
    }

    @Test
    fun `typing in the settings list does not filter the app list`() {
        val activity = launch()
        openSettings()

        type(activity, KeyEvent.KEYCODE_Z)
        type(activity, KeyEvent.KEYCODE_ESCAPE)

        awaitListed("MinimalUI")
        rule.onNodeWithText("z_").assertDoesNotExist()
    }

    @Test
    fun `Home closes the settings and returns to the top of the list`() {
        install("Alarm", "com.example.alarm")
        val activity = launch()
        openSettings()
        press(Key.DirectionUp)
        press(Key.Enter)
        awaitListed("Version ${BuildConfig.VERSION_NAME}")

        activity.newIntent(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME))

        awaitListed("Alarm")
        rule.onNodeWithText("Clock").assertDoesNotExist()
        rule.onNodeWithText("Alarm").assertIsFocused()
    }

    @Test
    fun `Uninstall is disabled for the MinimalUI settings row`() {
        val activity = launch()
        awaitListed("MinimalUI settings")
        // Up wraps to the Groups row, and Up again skips its blank row to the last app row.
        press(Key.DirectionUp)
        press(Key.DirectionUp)
        openMenu(activity)

        press(Key.DirectionDown)

        rule.onNodeWithText("Uninstall").assertIsNotEnabled()
    }

    /** MinimalUI's Recently used setting at Off, as stored before the launcher starts. */
    private fun recentlyUsedOff() {
        application
            .getSharedPreferences("settings", Context.MODE_PRIVATE)
            .edit()
            .putString("recently_used", "Off")
            .commit()
    }

    /** Opens each of [labels] in turn from its row in the full list (the last row with it), and comes back by Back. */
    private fun openInTurn(
        activity: ActivityController<MainActivity>,
        vararg labels: String,
    ) = labels.forEach {
        rule.onAllNodesWithText(it).onLast().performClick()
        leaveAndComeBack(activity)
    }

    // One row taller than the default screen, for the hint row (#74): the whole root still fits on it.
    @Test
    @Config(qualifiers = "h514dp")
    fun `the newest recent apps head the list above one blank row, and the full list still holds them`() {
        install("Alarm", "com.example.alarm")
        install("Bell", "com.example.bell")
        val activity = launch()
        awaitListed("Bell")
        assertEquals(listOf("Alarm", "Bell", "MinimalUI", "MinimalUI settings", "Groups"), rowsMatching(activity, ""))

        openInTurn(activity, "Alarm", "Bell")

        val rows = listOf("Bell", "Alarm", "Alarm", "Bell", "MinimalUI", "MinimalUI settings", "Groups")
        assertEquals(rows, rowsMatching(activity, ""))
        val bounds = rule.onAllNodes(listRow).fetchSemanticsNodes().map { it.boundsInRoot }
        // The blank row between the block and the full list is as tall as any other row.
        assertEquals(bounds[1].height, bounds[2].top - bounds[1].bottom, 1f)
        rule.onAllNodesWithText("Bell")[0].assertIsFocused()
    }

    @Test
    fun `Up and Down skip the blank row`() {
        install("Alarm", "com.example.alarm")
        install("Bell", "com.example.bell")
        val activity = launch()
        awaitListed("Bell")
        openInTurn(activity, "Bell")
        rule.onAllNodesWithText("Bell")[0].assertIsFocused()

        press(Key.DirectionDown)
        rule.onNodeWithText("Alarm").assertIsFocused()

        press(Key.DirectionUp)
        rule.onAllNodesWithText("Bell")[0].assertIsFocused()
    }

    @Test
    fun `typing shows only the matching apps, without the block`() {
        install("Alarm", "com.example.alarm")
        install("Bell", "com.example.bell")
        val activity = launch()
        awaitListed("Bell")
        openInTurn(activity, "Bell", "Alarm")

        assertEquals(listOf("Bell"), rowsMatching(activity, "b"))
        assertEquals(listOf("Bell"), rowsMatching(activity, "e"))
        type(activity, KeyEvent.KEYCODE_DEL)
        type(activity, KeyEvent.KEYCODE_DEL)

        assertEquals(listOf("Alarm", "Bell", "Alarm", "Bell"), rowsMatching(activity, "").take(4))
    }

    @Test
    fun `the Recently used setting sizes the block, and Off hides it`() {
        install("Alarm", "com.example.alarm")
        val activity = launch()
        awaitListed("Alarm")
        openInTurn(activity, "Alarm")

        openSettings()
        press(Key.DirectionDown)
        press(Key.DirectionDown)
        rule.onNodeWithText("Recently used").assertIsFocused()
        assertValue("Recently used", "3")
        press(Key.DirectionRight)
        assertValue("Recently used", "5")
        press(Key.DirectionRight)
        assertValue("Recently used", "15")
        press(Key.DirectionRight)
        assertValue("Recently used", "Off")
        type(activity, KeyEvent.KEYCODE_ESCAPE)
        awaitListed("Clock", listed = false)

        assertEquals(listOf("Alarm", "MinimalUI", "MinimalUI settings", "Groups"), rowsMatching(activity, ""))
        rule.onNodeWithText("MinimalUI settings").assertIsFocused()
    }

    @Test
    fun `Home returns to the root's first row, the newest recent app`() {
        install("Alarm", "com.example.alarm")
        install("Bell", "com.example.bell")
        val activity = launch()
        awaitListed("Bell")
        openInTurn(activity, "Alarm")
        press(Key.DirectionDown)
        press(Key.DirectionDown)
        openMenu(activity)

        activity.newIntent(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME))

        awaitListed("App info", listed = false)
        rule.onAllNodesWithText("Alarm")[0].assertIsFocused()
    }

    /** Moves down the open list until the row [label] is selected, then presses Enter on it. */
    private fun chooseRow(label: String) {
        val selected = hasText(label) and isFocused()
        repeat(10) {
            if (rule.onAllNodes(selected).fetchSemanticsNodes().isNotEmpty()) return@repeat
            press(Key.DirectionDown)
        }
        rule.onNode(selected).assertExists()
        press(Key.Enter)
    }

    /** Types [text] (letters and digits) through [activity]. */
    private fun typeText(
        activity: ActivityController<MainActivity>,
        text: String,
    ) = text.forEach { type(activity, KeyEvent.keyCodeFromString("KEYCODE_${it.uppercaseChar()}")) }

    /** From the selected app's menu, by keyboard: Add to collection, then [name], made new when [new]. */
    private fun addToCollection(
        activity: ActivityController<MainActivity>,
        name: String,
        new: Boolean,
    ) {
        openMenu(activity)
        chooseRow("Add to collection")
        awaitListed("App info", listed = false)
        if (new) {
            chooseRow("New collection")
            awaitEditor()
            typeChars(activity, name)
            rule.waitUntil(timeoutMillis = 5_000) { editorText() == name }
            press(Key.Enter)
        } else {
            chooseRow(name)
        }
        awaitListed("App info", listed = false)
    }

    @Test
    fun `the context menu stars an app as a favourite, pinned above the blank row, and takes it off again`() {
        install("Alarm", "com.example.alarm")
        install("Bell", "com.example.bell")
        val activity = launch()
        awaitListed("Bell")
        press(Key.DirectionDown)
        openMenu(activity)

        chooseRow("Add to favourites")

        awaitListed("★ Bell")
        val rows = listOf("★ Bell", "Alarm", "★ Bell", "MinimalUI", "MinimalUI settings", "Groups")
        assertEquals(rows, rowsMatching(activity, ""))
        val bounds = rule.onAllNodes(listRow).fetchSemanticsNodes().map { it.boundsInRoot }
        assertEquals(bounds[0].height, bounds[1].top - bounds[0].bottom, 1f)
        // The selection stays on the row the menu was opened on, now one block row lower.
        rule.onAllNodesWithText("★ Bell")[1].assertIsFocused()

        openMenu(activity)
        chooseRow("Remove from favourites")

        awaitListed("★ Bell", listed = false)
        assertEquals(listOf("Alarm", "Bell", "MinimalUI", "MinimalUI settings", "Groups"), rowsMatching(activity, ""))
    }

    @Test
    fun `favourites are kept when the launcher starts again, and typing finds them by their plain name, first`() {
        install("Alarm", "com.example.alarm")
        install("Bell", "com.example.bell")
        install("Bike", "com.example.bike")
        val activity = launch()
        awaitListed("Bike")
        press(Key.DirectionDown)
        press(Key.DirectionDown)
        rule.onNodeWithText("Bike").assertIsFocused()
        openMenu(activity)
        chooseRow("Add to favourites")
        awaitListed("★ Bike")

        closeActivity()
        val restarted = launch()
        awaitListed("★ Bike")

        assertEquals(listOf("★ Bike", "Bell"), rowsMatching(restarted, "b"))
    }

    @Test
    fun `a new collection opens from the root's Collections row, and Back goes up a list at a time`() {
        install("Alarm", "com.example.alarm")
        install("Bell", "com.example.bell")
        val activity = launch()
        awaitListed("Bell")
        press(Key.DirectionDown)
        rule.onNodeWithText("Bell").assertIsFocused()
        addToCollection(activity, "gamez", new = true)

        awaitListed("Collections")
        val rows = listOf("Collections", "Alarm", "Bell", "MinimalUI", "MinimalUI settings", "Groups")
        assertEquals(rows, rowsMatching(activity, ""))
        rule.onNodeWithText("Bell").assertIsFocused()
        press(Key.DirectionUp)
        addToCollection(activity, "gamez", new = false)
        rule.onNodeWithText("Alarm").assertIsFocused()

        press(Key.DirectionUp)
        rule.onNodeWithText("Collections").assertIsFocused()
        press(Key.Enter)
        awaitListed("gamez")
        rule.onNodeWithText("gamez").assertIsFocused()
        press(Key.Enter)
        awaitListed("Bell")
        assertEquals(
            listOf("Bell", "Alarm"),
            rule.onAllNodes(listRow).fetchSemanticsNodes().map {
                it.config[SemanticsProperties.Text].joinToString()
            },
        )
        rule.onNodeWithText("Bell").assertIsFocused()

        activity.get().onBackPressedDispatcher.onBackPressed()
        awaitListed("Bell", listed = false)
        rule.onNodeWithText("gamez").assertIsFocused()
        type(activity, KeyEvent.KEYCODE_ESCAPE)
        awaitListed("Bell")
        rule.onNodeWithText("Collections").assertIsFocused()
    }

    @Test
    fun `a new collection's name takes Backspace, and Esc leaves without making it`() {
        install("Alarm", "com.example.alarm")
        val activity = launch()
        awaitListed("Alarm")
        openMenu(activity)
        chooseRow("Add to collection")
        chooseRow("New collection")
        awaitEditor()
        typeText(activity, "gamex")
        type(activity, KeyEvent.KEYCODE_DEL)
        typeText(activity, "s")
        rule.waitUntil(timeoutMillis = 5_000) { editorText() == "games" }

        type(activity, KeyEvent.KEYCODE_ESCAPE)
        rule.waitUntil(timeoutMillis = 5_000) { rule.onAllNodes(editable).fetchSemanticsNodes().isEmpty() }
        rule.onNodeWithText("New collection").assertIsFocused()
        chooseRow("New collection")
        awaitEditor()
        assertEquals("", editorText())
        press(Key.Enter)
        rule.onNode(editable).assertExists()
        activity.get().onBackPressedDispatcher.onBackPressed()
        type(activity, KeyEvent.KEYCODE_ESCAPE)
        awaitListed("App info")
        rule.onNodeWithText("Add to collection").assertIsFocused()
        type(activity, KeyEvent.KEYCODE_ESCAPE)

        awaitListed("Alarm")
        rule.onNodeWithText("Collections").assertDoesNotExist()
    }

    // One row taller than the default screen, for the hint row (#74): the whole menu still fits on it.
    @Test
    @Config(qualifiers = "h514dp")
    fun `an app moves up and down in its collection with the selection, and the last one out hides the row`() {
        install("Alarm", "com.example.alarm")
        install("Bell", "com.example.bell")
        val activity = launch()
        awaitListed("Bell")
        press(Key.DirectionDown)
        addToCollection(activity, "games", new = true)
        press(Key.DirectionUp)
        addToCollection(activity, "games", new = false)
        press(Key.DirectionUp)
        press(Key.Enter)
        awaitListed("games")
        press(Key.Enter)
        awaitListed("Alarm")

        fun rows() =
            rule.onAllNodes(listRow).fetchSemanticsNodes().map {
                it.config[SemanticsProperties.Text].joinToString()
            }

        openMenu(activity)
        rule.onNodeWithText("Move up").assertIsNotEnabled()
        chooseRow("Move down")
        awaitListed("App info", listed = false)
        assertEquals(listOf("Alarm", "Bell"), rows())
        rule.onNodeWithText("Bell").assertIsFocused()

        openMenu(activity)
        rule.onNodeWithText("Move down").assertIsNotEnabled()
        chooseRow("Move up")
        awaitListed("App info", listed = false)
        assertEquals(listOf("Bell", "Alarm"), rows())
        rule.onNodeWithText("Bell").assertIsFocused()

        openMenu(activity)
        chooseRow("Remove from collection")
        awaitListed("App info", listed = false)
        assertEquals(listOf("Alarm"), rows())
        openMenu(activity)
        chooseRow("Remove from collection")

        awaitListed("MinimalUI")
        assertEquals(listOf("Alarm", "Bell", "MinimalUI", "MinimalUI settings", "Groups"), rowsMatching(activity, ""))
        rule.onNodeWithText("Collections").assertDoesNotExist()
    }

    @Test
    fun `collections are kept when the launcher starts again, and their apps open and become recent`() {
        install("Alarm", "com.example.alarm")
        install("Bell", "com.example.bell")
        val activity = launch()
        awaitListed("Bell")
        press(Key.DirectionDown)
        addToCollection(activity, "games", new = true)

        closeActivity()
        val restarted = launch()
        awaitListed("Collections")
        press(Key.Enter)
        awaitListed("games")
        press(Key.Enter)
        awaitListed("Bell")
        press(Key.Enter)

        val started = shadowOf(application).nextStartedActivity
        assertEquals(ComponentName("com.example.bell", "com.example.bell.Main"), started.component)
        assertEquals(listOf("Bell"), restarted.get().recentApps.map { it.label })
        leaveAndComeBack(restarted)
        rule.onNodeWithText("Bell").assertIsFocused()
        rule.onNodeWithText("Alarm").assertDoesNotExist()

        restarted.newIntent(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME))
        awaitListed("Alarm")
        rule.onAllNodesWithText("Bell")[0].assertIsFocused()
    }

    /** From the selected app's menu, by keyboard: [action] (Hide or Unhide), back on the list it was opened from. */
    private fun fromMenu(
        activity: ActivityController<MainActivity>,
        action: String,
    ) {
        openMenu(activity)
        chooseRow(action)
        awaitListed("App info", listed = false)
    }

    /** The colour [label]'s text is drawn in. */
    private fun textColor(label: String): Color {
        val layouts = mutableListOf<TextLayoutResult>()
        rule
            .onNodeWithText(label, useUnmergedTree = true)
            .fetchSemanticsNode()
            .config[SemanticsActions.GetTextLayoutResult]
            .action!!
            .invoke(layouts)
        return layouts
            .single()
            .layoutInput.style.color
    }

    // One row taller than the default screen, for the hint row (#74): the list's first page still holds the block,
    // the full list's first rows and the Groups row's page break where they were.
    @Test
    @Config(qualifiers = "h514dp")
    fun `Hide takes the app off the list and off the favourites and recently used blocks`() {
        install("Alarm", "com.example.alarm")
        install("Bell", "com.example.bell")
        install("Cello", "com.example.cello")
        val activity = launch()
        awaitListed("Bell")
        press(Key.DirectionDown)
        openMenu(activity)
        chooseRow("Add to favourites")
        awaitListed("★ Bell")
        openInTurn(activity, "Cello")
        // A full page: the Groups row is on the next one.
        val before = listOf("★ Bell", "Cello", "Alarm", "★ Bell", "Cello", "MinimalUI", "MinimalUI settings")
        assertEquals(before, rowsMatching(activity, ""))
        rule.onAllNodesWithText("Cello")[0].assertIsFocused()

        fromMenu(activity, "Hide")
        awaitListed("Cello", listed = false)
        press(Key.DirectionUp)
        rule.onAllNodesWithText("★ Bell")[0].assertIsFocused()
        fromMenu(activity, "Hide")

        awaitListed("★ Bell", listed = false)
        assertEquals(listOf("Alarm", "MinimalUI", "MinimalUI settings", "Groups"), rowsMatching(activity, ""))
        rule.onNodeWithText("Alarm").assertIsFocused()
    }

    @Test
    fun `Hide inside a collection takes the app off its list, and unhiding brings it back in its place`() {
        install("Alarm", "com.example.alarm")
        install("Bell", "com.example.bell")
        val activity = launch()
        awaitListed("Bell")
        press(Key.DirectionDown)
        addToCollection(activity, "games", new = true)
        press(Key.DirectionUp)
        addToCollection(activity, "games", new = false)
        press(Key.DirectionUp)
        press(Key.Enter)
        awaitListed("games")
        press(Key.Enter)
        awaitListed("Alarm")

        fun rows() =
            rule.onAllNodes(listRow).fetchSemanticsNodes().map {
                it.config[SemanticsProperties.Text].joinToString()
            }
        assertEquals(listOf("Bell", "Alarm"), rows())

        fromMenu(activity, "Hide")
        assertEquals(listOf("Alarm"), rows())
        rule.onNodeWithText("Alarm").assertIsFocused()
        fromMenu(activity, "Hide")

        awaitListed("MinimalUI")
        rule.onNodeWithText("Collections").assertDoesNotExist()
        assertEquals(listOf("MinimalUI", "MinimalUI settings", "Groups"), rowsMatching(activity, ""))
        openHiddenApps()
        rule.onNodeWithText("Alarm").assertIsFocused()
        press(Key.Enter)
        rule.onNodeWithText("Bell").assertIsFocused()
        press(Key.Enter)
        awaitListed("No hidden apps")
        activity.newIntent(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME))
        awaitListed("Collections")
        press(Key.Enter)
        awaitListed("games")
        press(Key.Enter)
        awaitListed("Alarm")
        assertEquals(listOf("Bell", "Alarm"), rows())
    }

    @Test
    fun `Hide is disabled for Android's Settings and for the MinimalUI settings row`() {
        installSystemSettings()
        val activity = launch()
        awaitListed("Settings")
        assertEquals(listOf("Settings", "MinimalUI settings"), rowsMatching(activity, "se"))
        rule.onNodeWithText("Settings").assertIsFocused()

        openMenu(activity)
        chooseRow("Hide")
        rule.onNodeWithText("Hide").assertIsFocused().assertIsNotEnabled()
        type(activity, KeyEvent.KEYCODE_ESCAPE)
        awaitListed("App info", listed = false)
        press(Key.DirectionDown)
        rule.onNodeWithText("MinimalUI settings").assertIsFocused()
        openMenu(activity)
        chooseRow("Hide")
        rule.onNodeWithText("Hide").assertIsFocused().assertIsNotEnabled()
        type(activity, KeyEvent.KEYCODE_ESCAPE)

        awaitListed("App info", listed = false)
        assertEquals(listOf("Settings", "MinimalUI settings"), rowsMatching(activity, ""))
    }

    @Test
    fun `typing still finds a hidden app, after the others and dim, and it opens and unhides from its menu`() {
        install("Bell", "com.example.bell")
        install("Bike", "com.example.bike")
        val activity = launch()
        awaitListed("Bell")
        fromMenu(activity, "Hide")
        awaitListed("Bell", listed = false)

        assertEquals(listOf("Bike", "Bell"), rowsMatching(activity, "b"))
        assertEquals(DimText, textColor("Bell"))
        assertNotEquals(DimText, textColor("Bike"))
        press(Key.DirectionDown)
        rule.onNodeWithText("Bell").assertIsFocused()
        assertEquals(DimText, textColor("Bell"))
        press(Key.Enter)
        val started = shadowOf(application).nextStartedActivity
        assertEquals(ComponentName("com.example.bell", "com.example.bell.Main"), started.component)
        leaveAndComeBack(activity)
        rule.onNodeWithText("Bell").assertIsFocused()

        fromMenu(activity, "Unhide")

        assertEquals(listOf("Bell", "Bike"), rowsMatching(activity, ""))
        assertNotEquals(DimText, textColor("Bike"))
        activity.newIntent(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME))
        awaitListed("b_", listed = false)
        assertEquals(
            listOf("Bell", "Bell", "Bike", "MinimalUI", "MinimalUI settings", "Groups"),
            rowsMatching(activity, ""),
        )
    }

    /** Opens MinimalUI's settings, then its Hidden apps list. */
    private fun openHiddenApps() {
        openSettings()
        chooseRow("Hidden apps")
        awaitListed("Clock", listed = false)
    }

    @Test
    fun `Enter in the Hidden apps list unhides the app, and the selection stays on its row number`() {
        install("Alarm", "com.example.alarm")
        install("Bell", "com.example.bell")
        install("Cello", "com.example.cello")
        val activity = launch()
        repeat(3) {
            awaitListed("MinimalUI")
            fromMenu(activity, "Hide")
        }
        awaitListed("Cello", listed = false)
        openHiddenApps()

        fun rows() =
            rule.onAllNodes(listRow).fetchSemanticsNodes().map {
                it.config[SemanticsProperties.Text].joinToString()
            }
        assertEquals(listOf("Alarm", "Bell", "Cello"), rows())
        rule.onNodeWithText("Alarm").assertIsFocused()

        press(Key.DirectionDown)
        press(Key.Enter)
        awaitListed("Bell", listed = false)
        assertEquals(listOf("Alarm", "Cello"), rows())
        rule.onNodeWithText("Cello").assertIsFocused()
        press(Key.Enter)
        awaitListed("Cello", listed = false)
        rule.onNodeWithText("Alarm").assertIsFocused()
        press(Key.Enter)

        awaitListed("No hidden apps")
        assertEquals(emptyList<String>(), rows())
        type(activity, KeyEvent.KEYCODE_ESCAPE)
        awaitListed("Clock")
        rule.onNodeWithText("Hidden apps").assertIsFocused()
        activity.newIntent(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME))
        awaitListed("Alarm")
        assertEquals(
            listOf("Alarm", "Bell", "Cello", "MinimalUI", "MinimalUI settings", "Groups"),
            rowsMatching(activity, ""),
        )
        rule.onNodeWithText("Alarm").assertIsFocused()
    }

    @Test
    fun `with no hidden apps the Hidden apps list says so, and Back returns to its settings row`() {
        val activity = launch()
        openHiddenApps()

        awaitListed("No hidden apps")
        activity.get().onBackPressedDispatcher.onBackPressed()

        awaitListed("Clock")
        rule.onNodeWithText("Hidden apps").assertIsFocused()
    }

    /** The document the user picks or creates in Android's file picker, as its provider hands it back. */
    private val pickedFile = Uri.parse("content://com.example.documents/document/minimalui-setup.json")

    private val alarmComponent = "com.example.alarm/com.example.alarm.Main"
    private val bellComponent = "com.example.bell/com.example.bell.Main"

    /** Answers the file picker the launcher started with [file], or leaves it without one when [file] is null. */
    private fun pick(
        activity: ActivityController<MainActivity>,
        file: Uri?,
    ): Intent {
        val picker = shadowOf(activity.get()).nextStartedActivityForResult.intent
        val result = if (file == null) Activity.RESULT_CANCELED else Activity.RESULT_OK
        shadowOf(activity.get()).receiveResult(picker, result, file?.let { Intent().setData(it) })
        return picker
    }

    /** The file picked for import holds [text]. */
    private fun pickedFileHolds(text: String) =
        shadowOf(application.contentResolver).registerInputStreamSupplier(pickedFile) {
            ByteArrayInputStream(text.toByteArray())
        }

    /**
     * Reading and writing the file run off the main thread; wait until the launcher reports the outcome. A toast is no
     * change on screen for Compose to wait on, so this runs the main thread itself.
     */
    private fun awaitToast(text: String) =
        rule.waitUntil(timeoutMillis = 5_000) {
            shadowOf(Looper.getMainLooper()).idle()
            ShadowToast.getTextOfLatestToast() == text
        }

    /** [value] stored under [key] in the preferences [file] before the launcher starts. */
    private fun storeBeforeLaunch(
        file: String,
        key: String,
        value: String,
    ) = application
        .getSharedPreferences(file, Context.MODE_PRIVATE)
        .edit()
        .putString(key, value)
        .commit()

    @Test
    fun `Export setup writes the whole setup to a new file the user names in Android's file picker`() {
        recentlyUsedOff()
        storeBeforeLaunch("favourites", "favourites", alarmComponent)
        storeBeforeLaunch("aliases", "aliases", "$bellComponent\tDing")
        install("Alarm", "com.example.alarm")
        install("Bell", "com.example.bell")
        val activity = launch()
        openSettings()
        val exported = ByteArrayOutputStream()
        shadowOf(application.contentResolver).registerOutputStream(pickedFile, exported)

        chooseRow("Export setup")
        val picker = pick(activity, pickedFile)

        assertEquals(Intent.ACTION_CREATE_DOCUMENT, picker.action)
        assertEquals("application/json", picker.type)
        assertEquals("minimalui-setup.json", picker.getStringExtra(Intent.EXTRA_TITLE))
        awaitToast("Setup exported")
        val expected =
            LauncherSetup(
                settings = LauncherSetup().settings.cycled(Setting.RecentlyUsed, -1),
                favourites = Favourites(setOf(alarmComponent)),
                aliases = Aliases(mapOf(bellComponent to "Ding")),
            )
        assertEquals(SetupImport.Read(expected), LauncherSetup.imported(exported.toString(Charsets.UTF_8)))
        rule.onNodeWithText("Export setup").assertIsFocused()
    }

    @Test
    fun `Import setup replaces the setup only once the user confirms, and keeps it after a restart`() {
        install("Alarm", "com.example.alarm")
        install("Bell", "com.example.bell")
        val activity = launch()
        openSettings()
        val imported =
            LauncherSetup(
                settings = LauncherSetup().settings.cycled(Setting.Clock, 1),
                favourites = Favourites(setOf(bellComponent)),
            )
        pickedFileHolds(imported.exported())

        chooseRow("Import setup")
        val picker = pick(activity, pickedFile)

        assertEquals(Intent.ACTION_OPEN_DOCUMENT, picker.action)
        awaitListed("Replace current setup")
        rule.onNodeWithText("Replace current setup").assertIsFocused()
        press(Key.DirectionDown)
        press(Key.Enter)
        awaitListed("Clock")
        rule.onNodeWithText("Import setup").assertIsFocused()
        assertValue("Clock", "On")

        press(Key.Enter)
        pick(activity, pickedFile)
        awaitListed("Replace current setup")
        press(Key.Enter)

        awaitToast("Setup imported")
        awaitListed("Clock")
        assertValue("Clock", "Off")
        closeActivity()
        launch()
        awaitListed("★ Bell")
    }

    @Test
    fun `Import setup refuses a file that is not a MinimalUI setup, and one from a newer MinimalUI`() {
        val activity = launch()
        openSettings()

        pickedFileHolds("Just some notes")
        chooseRow("Import setup")
        pick(activity, pickedFile)
        awaitToast("Not a MinimalUI setup file")

        pickedFileHolds("""{"format": "minimalui-setup", "version": 2}""")
        press(Key.Enter)
        pick(activity, pickedFile)
        awaitToast("Made by a newer MinimalUI: update this one first")

        rule.onNodeWithText("Replace current setup").assertDoesNotExist()
        rule.onNodeWithText("Import setup").assertIsFocused()
    }

    @Test
    fun `Import setup reads a file of up to 1 MiB, refuses a larger one, and leaving its picker changes nothing`() {
        val activity = launch()
        openSettings()
        val setup = """{"format": "minimalui-setup", "version": 1}"""
        val mebibyte = 1 shl 20

        chooseRow("Import setup")
        pick(activity, null)
        rule.waitForIdle()
        assertNull(ShadowToast.getTextOfLatestToast())
        rule.onNodeWithText("Import setup").assertIsFocused()

        pickedFileHolds(setup + " ".repeat(mebibyte + 1 - setup.length))
        press(Key.Enter)
        pick(activity, pickedFile)
        awaitToast("Not a MinimalUI setup file")
        rule.onNodeWithText("Replace current setup").assertDoesNotExist()

        pickedFileHolds(setup + " ".repeat(mebibyte - setup.length))
        press(Key.Enter)
        pick(activity, pickedFile)
        awaitListed("Replace current setup")
    }

    @Test
    fun `a file that cannot be written or read says so, and leaving the picker changes nothing`() {
        val activity = launch()
        openSettings()
        val failing =
            object : OutputStream() {
                override fun write(b: Int) = throw IOException("disk full")
            }
        shadowOf(application.contentResolver).registerOutputStream(pickedFile, failing)
        shadowOf(application.contentResolver).registerInputStreamSupplier(pickedFile) {
            object : InputStream() {
                override fun read(): Int = throw IOException("provider gone")
            }
        }

        chooseRow("Export setup")
        pick(activity, null)
        rule.waitForIdle()
        assertNull(ShadowToast.getTextOfLatestToast())

        press(Key.Enter)
        pick(activity, pickedFile)
        awaitToast("Could not export the setup")

        press(Key.DirectionDown)
        press(Key.Enter)
        pick(activity, pickedFile)
        awaitToast("Could not read the file")
        rule.onNodeWithText("Import setup").assertIsFocused()
    }

    @Test
    fun `hidden apps stay hidden when the launcher starts again, and are listed under Hidden apps`() {
        install("Alarm", "com.example.alarm")
        install("Bell", "com.example.bell")
        val activity = launch()
        awaitListed("Alarm")
        fromMenu(activity, "Hide")
        awaitListed("Alarm", listed = false)

        closeActivity()
        val restarted = launch()
        awaitListed("Bell")

        assertEquals(listOf("Bell", "MinimalUI", "MinimalUI settings", "Groups"), rowsMatching(restarted, ""))
        openHiddenApps()
        awaitListed("Alarm")
        rule.onNodeWithText("Alarm").assertIsFocused()
    }

    /** Types [text] through [activity] as a hardware keyboard would: letters (capitals with Shift), digits, spaces. */
    private fun typeChars(
        activity: ActivityController<MainActivity>,
        text: String,
    ) = text.forEach { char ->
        val code =
            if (char == ' ') {
                KeyEvent.KEYCODE_SPACE
            } else {
                KeyEvent.keyCodeFromString("KEYCODE_${char.uppercaseChar()}")
            }
        val meta = if (char.isUpperCase()) KeyEvent.META_SHIFT_ON or KeyEvent.META_SHIFT_LEFT_ON else 0
        listOf(KeyEvent.ACTION_DOWN, KeyEvent.ACTION_UP).forEach { action ->
            activity.get().dispatchKeyEvent(KeyEvent(0, 0, action, code, 0, meta))
        }
    }

    /** The text editor's field. */
    private val editable = SemanticsMatcher.keyIsDefined(SemanticsProperties.EditableText)

    /** The text in the open text editor. */
    private fun editorText(): String =
        rule
            .onNode(editable)
            .fetchSemanticsNode()
            .config[SemanticsProperties.EditableText]
            .text

    /** Waits until the text editor is open with its field focused, so typed keys reach it. */
    private fun awaitEditor() =
        rule.waitUntil(timeoutMillis = 5_000) {
            rule.onAllNodes(editable and isFocused()).fetchSemanticsNodes().isNotEmpty()
        }

    /** From the selected app's menu, by keyboard: Rename, Backspace over the [current] name, then [name] and Enter. */
    private fun rename(
        activity: ActivityController<MainActivity>,
        current: String,
        name: String,
    ) {
        openMenu(activity)
        chooseRow("Rename")
        awaitEditor()
        assertEquals(current, editorText())
        repeat(current.length) { type(activity, KeyEvent.KEYCODE_DEL) }
        typeChars(activity, name)
        rule.waitUntil(timeoutMillis = 5_000) { editorText() == name }
        type(activity, KeyEvent.KEYCODE_ENTER)
        awaitListed("Rename", listed = false)
    }

    @Test
    fun `Rename moves the app to its new sort place, selected there, and typing its new name finds it`() {
        install("Alarm", "com.example.alarm")
        install("Bell", "com.example.bell")
        val activity = launch()
        awaitListed("Alarm")
        rule.onNodeWithText("Alarm").assertIsFocused()

        rename(activity, current = "Alarm", name = "Zoo b")

        awaitListed("Zoo b")
        assertEquals(listOf("Bell", "MinimalUI", "MinimalUI settings", "Zoo b", "Groups"), rowsMatching(activity, ""))
        rule.onNodeWithText("Zoo b").assertIsFocused()
        assertEquals(listOf("Zoo b"), rowsMatching(activity, "zo"))
    }

    @Test
    fun `Esc or Back in the editor cancels, back on the Rename row, and the name stays`() {
        install("Alarm", "com.example.alarm")
        install("Bell", "com.example.bell")
        val activity = launch()
        awaitListed("Alarm")
        openMenu(activity)
        chooseRow("Rename")
        awaitEditor()
        typeChars(activity, "x")
        rule.waitUntil(timeoutMillis = 5_000) { editorText() == "Alarmx" }

        type(activity, KeyEvent.KEYCODE_ESCAPE)
        awaitListed("App info")
        rule.onNodeWithText("Rename").assertIsFocused()
        press(Key.Enter)
        awaitEditor()
        assertEquals("Alarm", editorText())
        activity.get().onBackPressedDispatcher.onBackPressed()
        awaitListed("App info")
        type(activity, KeyEvent.KEYCODE_ESCAPE)

        awaitListed("Bell")
        assertEquals(listOf("Alarm", "Bell", "MinimalUI", "MinimalUI settings", "Groups"), rowsMatching(activity, ""))
        rule.onNodeWithText("Alarm").assertIsFocused()
    }

    @Test
    fun `typing in the editor does not filter the list below it`() {
        install("Alarm", "com.example.alarm")
        val activity = launch()
        awaitListed("Alarm")
        openMenu(activity)
        chooseRow("Rename")
        awaitEditor()
        typeChars(activity, "q")
        type(activity, KeyEvent.KEYCODE_ESCAPE)
        awaitListed("App info")
        type(activity, KeyEvent.KEYCODE_ESCAPE)

        awaitListed("Alarm")
        rule.onNodeWithText("q_").assertDoesNotExist()
        assertEquals(listOf("Alarm", "MinimalUI", "MinimalUI settings", "Groups"), rowsMatching(activity, ""))
    }

    @Test
    fun `clearing the name gives the app back its system label and place`() {
        install("Alarm", "com.example.alarm")
        install("Bell", "com.example.bell")
        val activity = launch()
        awaitListed("Alarm")
        rename(activity, current = "Alarm", name = "Zoo")
        awaitListed("Zoo")
        rule.onNodeWithText("Zoo").assertIsFocused()

        rename(activity, current = "Zoo", name = "")

        awaitListed("Alarm")
        assertEquals(listOf("Alarm", "Bell", "MinimalUI", "MinimalUI settings", "Groups"), rowsMatching(activity, ""))
        rule.onNodeWithText("Alarm").assertIsFocused()
    }

    @Test
    fun `renaming an app uninstalled while the editor is open closes it and names nothing`() {
        install("Alarm", "com.example.alarm")
        install("Bell", "com.example.bell")
        val activity = launch()
        awaitListed("Bell")
        press(Key.DirectionDown)
        openMenu(activity)
        chooseRow("Rename")
        awaitEditor()
        uninstall("com.example.bell")
        application.sendBroadcast(Intent(Intent.ACTION_PACKAGE_REMOVED, packageUri("com.example.bell")))
        // The broadcast's reload runs off the main thread. Enter must come after it lands, or Bell is still there
        // to name (#183). Nothing on screen changes until then, so this runs the main thread to deliver the broadcast
        // and publish the reload.
        rule.waitUntil(timeoutMillis = 5_000) {
            rule.waitForIdle()
            activity.get().loadedApps.none { it.packageName == "com.example.bell" }
        }
        repeat("Bell".length) { type(activity, KeyEvent.KEYCODE_DEL) }
        typeChars(activity, "Ring")
        rule.waitUntil(timeoutMillis = 5_000) { editorText() == "Ring" }
        type(activity, KeyEvent.KEYCODE_ENTER)

        awaitListed("Rename", listed = false)
        assertStillHome(activity)
        assertEquals(listOf("Alarm", "MinimalUI", "MinimalUI settings", "Groups"), rowsMatching(activity, ""))
        // Back, the app keeps its own name: the one typed for it while it was gone was not kept.
        install("Bell", "com.example.bell")
        application.sendBroadcast(Intent(Intent.ACTION_PACKAGE_ADDED, packageUri("com.example.bell")))
        awaitListed("Bell")
        rule.onNodeWithText("Ring").assertDoesNotExist()
    }

    @Test
    fun `a new name is kept when the launcher starts again, and the recently used block shows it`() {
        install("Alarm", "com.example.alarm")
        install("Bell", "com.example.bell")
        val activity = launch()
        awaitListed("Bell")
        press(Key.DirectionDown)
        rename(activity, current = "Bell", name = "Ring")
        awaitListed("Ring")

        closeActivity()
        val restarted = launch()
        awaitListed("Ring")
        openInTurn(restarted, "Ring")

        assertEquals(
            listOf("Ring", "Alarm", "MinimalUI", "MinimalUI settings", "Ring", "Groups"),
            rowsMatching(restarted, ""),
        )
    }

    @Test
    fun `Rename is disabled for the MinimalUI settings row`() {
        val activity = launch()
        awaitListed("MinimalUI settings")
        press(Key.DirectionUp)
        press(Key.DirectionUp)
        rule.onNodeWithText("MinimalUI settings").assertIsFocused()
        openMenu(activity)

        rule.onNodeWithText("Rename").assertIsNotEnabled()
    }

    @Test
    fun `apps of the same name show their package after it, small and dim, and a unique name shows none`() {
        install("Phone", "com.example.dialer")
        install("Phone", "com.example.phone")
        install("Bell", "com.example.bell")
        val activity = launch()
        awaitListed("Bell")

        assertEquals(
            listOf(
                "Bell",
                "MinimalUI",
                "MinimalUI settings",
                "Phone com.example.dialer",
                "Phone com.example.phone",
                "Groups",
            ),
            rowsMatching(activity, ""),
        )
        val layouts = mutableListOf<TextLayoutResult>()
        rule
            .onNodeWithText("Phone com.example.dialer", useUnmergedTree = true)
            .fetchSemanticsNode()
            .config[SemanticsActions.GetTextLayoutResult]
            .action!!
            .invoke(layouts)
        val text = layouts.single().layoutInput.text
        val suffix = text.spanStyles.single()
        assertEquals("com.example.dialer", text.text.substring(suffix.start, suffix.end))
        assertEquals(DimText, suffix.item.color)
        assertEquals(0.75f.em, suffix.item.fontSize)
        // The filter matches the name only, never the suffix.
        assertEquals(emptyList<String>(), rowsMatching(activity, "com"))
    }

    @Test
    fun `a new collection's name takes spaces`() {
        install("Alarm", "com.example.alarm")
        val activity = launch()
        awaitListed("Alarm")
        addToCollection(activity, "My games", new = true)

        awaitListed("Collections")
        press(Key.DirectionUp)
        rule.onNodeWithText("Collections").assertIsFocused()
        press(Key.Enter)
        awaitListed("My games")
    }

    /** The texts of the open list's rows, top to bottom. */
    private fun shownRows(): List<String> =
        rule.onAllNodes(listRow).fetchSemanticsNodes().map {
            it.config[SemanticsProperties.Text].joinToString()
        }

    @Test
    fun `the Groups row ends the root, Up wraps to it, a game is under Games, and Back restores each row`() {
        install("Alarm", "com.example.alarm")
        install("Chess", "com.example.chess", ApplicationInfo.CATEGORY_GAME)
        val activity = launch()
        awaitListed("Chess")
        assertEquals(listOf("Alarm", "Chess", "MinimalUI", "MinimalUI settings", "Groups"), shownRows())
        rule.onNodeWithText("Alarm").assertIsFocused()

        press(Key.DirectionUp)
        rule.onNodeWithText("Groups").assertIsFocused()
        press(Key.Enter)
        awaitListed("Games")
        assertEquals(listOf("Games", "Other"), shownRows())
        rule.onNodeWithText("Games").assertIsFocused()
        press(Key.Enter)
        awaitListed("Alarm", listed = false)
        assertEquals(listOf("Chess"), shownRows())

        type(activity, KeyEvent.KEYCODE_ESCAPE)
        awaitListed("Other")
        rule.onNodeWithText("Games").assertIsFocused()
        press(Key.DirectionDown)
        press(Key.Enter)
        awaitListed("Games", listed = false)
        assertEquals(listOf("Alarm", "MinimalUI"), shownRows())
        activity.get().onBackPressedDispatcher.onBackPressed()
        awaitListed("Games")
        rule.onNodeWithText("Other").assertIsFocused()
        activity.get().onBackPressedDispatcher.onBackPressed()
        awaitListed("Chess")
        rule.onNodeWithText("Groups").assertIsFocused()
    }

    /** From the first row of the root, by keyboard: Up wraps to the Groups row, and Enter opens the groups. */
    private fun openGroups() {
        press(Key.DirectionUp)
        rule.onNodeWithText("Groups").assertIsFocused()
        press(Key.Enter)
        awaitListed("Other")
    }

    @Test
    fun `a list opened from the root adds Esc to its hints, a menu hints choosing, and the editor saving`() {
        install("Alarm", "com.example.alarm")
        val activity = launch()
        awaitListed("Alarm")
        rule.onNodeWithText("BACK").assertDoesNotExist()

        openGroups()
        for (text in listOf("☰", "MENU", "⎋", "BACK", "⏎", "OPEN")) rule.onNodeWithText(text).assertIsDisplayed()
        press(Key.Enter)
        awaitListed("Alarm")
        rule.onNodeWithText("BACK").assertIsDisplayed()
        rule.onNodeWithText("MENU").assertIsDisplayed()

        openMenu(activity)
        rule.onNodeWithText("SELECT").assertIsDisplayed()
        rule.onNodeWithText("MENU").assertDoesNotExist()

        chooseRow("Rename")
        awaitEditor()
        rule.onNodeWithText("CANCEL").assertIsDisplayed()
        rule.onNodeWithText("SAVE").assertIsDisplayed()
        rule.onNodeWithText("SELECT").assertDoesNotExist()
    }

    @Test
    fun `a tapped hint acts as its key does, so Backspace edits the filter, Enter saves a name, Esc closes the menu`() {
        install("Alarm", "com.example.alarm")
        val activity = launch()
        awaitListed("Alarm")

        type(activity, KeyEvent.KEYCODE_A)
        type(activity, KeyEvent.KEYCODE_Q)
        awaitListed("No match")
        rule.onNodeWithText("DELETE").performClick()
        rule.onNodeWithText("a_").assertExists()
        rule.onNodeWithText("DELETE").performClick()
        awaitListed("Groups")

        openMenu(activity)
        chooseRow("Rename")
        awaitEditor()
        typeText(activity, "s")
        rule.waitUntil(timeoutMillis = 5_000) { editorText() == "Alarms" }
        rule.onNodeWithText("SAVE").performClick()
        awaitListed("Alarms")
        rule.onNodeWithText("Alarms").assertIsFocused()

        openMenu(activity)
        rule.onNodeWithText("BACK").performClick()
        awaitListed("App info", listed = false)
        rule.onNodeWithText("Alarms").assertIsFocused()
    }

    /** From the selected app's menu, by keyboard: Add to group, then [name], made new when [new]. */
    private fun addToGroup(
        activity: ActivityController<MainActivity>,
        name: String,
        new: Boolean,
    ) {
        openMenu(activity)
        chooseRow("Add to group")
        awaitListed("App info", listed = false)
        if (new) {
            chooseRow("New group")
            awaitEditor()
            typeChars(activity, name)
            rule.waitUntil(timeoutMillis = 5_000) { editorText() == name }
            press(Key.Enter)
        } else {
            chooseRow(name)
        }
        awaitListed("App info", listed = false)
    }

    @Test
    fun `a new group is made by keyboard from an app's menu, then renamed and deleted from the group's menu`() {
        install("Alarm", "com.example.alarm")
        install("Bell", "com.example.bell")
        val activity = launch()
        awaitListed("Bell")
        press(Key.DirectionDown)
        addToGroup(activity, "Work", new = true)
        rule.onNodeWithText("Bell").assertIsFocused()

        press(Key.DirectionUp)
        openGroups()
        assertEquals(listOf("Work", "Other"), shownRows())
        rule.onNodeWithText("Work").assertIsFocused()
        press(Key.Enter)
        awaitListed("Other", listed = false)
        assertEquals(listOf("Bell"), shownRows())
        type(activity, KeyEvent.KEYCODE_ESCAPE)
        awaitListed("Other")

        type(activity, KeyEvent.KEYCODE_MENU)
        awaitListed("Rename group")
        chooseRow("Rename group")
        awaitEditor()
        assertEquals("Work", editorText())
        repeat(4) { type(activity, KeyEvent.KEYCODE_DEL) }
        typeChars(activity, "Play")
        rule.waitUntil(timeoutMillis = 5_000) { editorText() == "Play" }
        press(Key.Enter)
        awaitListed("Play")
        assertEquals(listOf("Play", "Other"), shownRows())

        rule.onNodeWithText("Play").assertIsFocused()
        type(activity, KeyEvent.KEYCODE_MENU)
        awaitListed("Delete group")
        chooseRow("Delete group")
        awaitListed("Play", listed = false)
        assertEquals(listOf("Other"), shownRows())
        rule.onNodeWithText("Other").assertIsFocused()
    }

    @Test
    fun `an automatic group's menu shows Rename group and Delete group disabled`() {
        install("Alarm", "com.example.alarm")
        val activity = launch()
        awaitListed("Alarm")
        openGroups()

        type(activity, KeyEvent.KEYCODE_MENU)
        awaitListed("Rename group")
        rule.onNodeWithText("Rename group").assertIsNotEnabled()
        rule.onNodeWithText("Delete group").assertIsNotEnabled()
        press(Key.Enter)
        rule.onNodeWithText("Rename group").assertExists()
        type(activity, KeyEvent.KEYCODE_ESCAPE)
        awaitListed("Rename group", listed = false)
        rule.onNodeWithText("Other").assertIsFocused()
    }

    @Test
    fun `user groups are kept when the launcher starts again, and Remove from group empties one until it goes`() {
        install("Alarm", "com.example.alarm")
        install("Bell", "com.example.bell")
        val activity = launch()
        awaitListed("Bell")
        press(Key.DirectionDown)
        addToGroup(activity, "Work", new = true)
        press(Key.DirectionUp)
        addToGroup(activity, "Work", new = false)

        closeActivity()
        val restarted = launch()
        awaitListed("Alarm")
        openGroups()
        press(Key.Enter)
        awaitListed("Other", listed = false)
        assertEquals(listOf("Alarm", "Bell"), shownRows())

        openMenu(restarted)
        chooseRow("Remove from group")
        awaitListed("App info", listed = false)
        assertEquals(listOf("Bell"), shownRows())
        openMenu(restarted)
        chooseRow("Remove from group")

        awaitListed("Other")
        assertEquals(listOf("Other"), shownRows())
    }

    @Test
    fun `a group whose apps are all hidden is not shown, yet kept to add to`() {
        install("Alarm", "com.example.alarm")
        install("Bell", "com.example.bell")
        val activity = launch()
        awaitListed("Bell")
        press(Key.DirectionDown)
        addToGroup(activity, "Work", new = true)
        fromMenu(activity, "Hide")
        awaitListed("Bell", listed = false)
        press(Key.DirectionUp)
        rule.onNodeWithText("Alarm").assertIsFocused()

        openGroups()
        assertEquals(listOf("Other"), shownRows())
        type(activity, KeyEvent.KEYCODE_ESCAPE)
        awaitListed("Alarm")
        rule.onNodeWithText("Groups").assertIsFocused()
        press(Key.DirectionDown)
        rule.onNodeWithText("Alarm").assertIsFocused()
        openMenu(activity)
        chooseRow("Add to group")
        awaitListed("App info", listed = false)
        assertEquals(listOf("Work", "New group"), shownRows())
    }

    @Test
    fun `Hide inside a group takes the app off its list, and the last one out closes the group`() {
        install("Alarm", "com.example.alarm")
        install("Chess", "com.example.chess", ApplicationInfo.CATEGORY_GAME)
        install("Go", "com.example.go", ApplicationInfo.CATEGORY_GAME)
        val activity = launch()
        awaitListed("Go")
        openGroups()
        assertEquals(listOf("Games", "Other"), shownRows())
        press(Key.Enter)
        awaitListed("Other", listed = false)
        assertEquals(listOf("Chess", "Go"), shownRows())

        fromMenu(activity, "Hide")
        assertEquals(listOf("Go"), shownRows())
        rule.onNodeWithText("Go").assertIsFocused()
        fromMenu(activity, "Hide")

        awaitListed("Other")
        assertEquals(listOf("Other"), shownRows())
        rule.onNodeWithText("Other").assertIsFocused()
        type(activity, KeyEvent.KEYCODE_ESCAPE)
        awaitListed("Alarm")
        assertEquals(listOf("Alarm", "MinimalUI", "MinimalUI settings", "Groups"), shownRows())
    }

    @Test
    fun `an app opened from a group becomes recent, and coming back keeps the group open on it`() {
        install("Alarm", "com.example.alarm")
        install("Chess", "com.example.chess", ApplicationInfo.CATEGORY_GAME)
        val activity = launch()
        awaitListed("Chess")
        openGroups()
        press(Key.Enter)
        awaitListed("Other", listed = false)
        press(Key.Enter)

        val started = shadowOf(application).nextStartedActivity
        assertEquals(ComponentName("com.example.chess", "com.example.chess.Main"), started.component)
        assertEquals(listOf("Chess"), activity.get().recentApps.map { it.label })
        leaveAndComeBack(activity)
        rule.onNodeWithText("Chess").assertIsFocused()
        rule.onNodeWithText("Alarm").assertDoesNotExist()
    }

    private val userManager: UserManager get() = application.getSystemService(UserManager::class.java)
    private val launcherApps: LauncherApps get() = application.getSystemService(LauncherApps::class.java)

    // UserHandle.of is not public API; an app uid of the profile names the same user.
    private val work: UserHandle = UserHandle.getUserHandleForUid(WORK_USER * PER_USER_RANGE)

    /** A managed (work) profile of the user, as `pm create-user --profileOf 0 --managed` makes one. */
    private fun addWorkProfile() {
        shadowOf(userManager).addProfile(0, WORK_USER, "Work", ShadowUserManager.FLAG_MANAGED_PROFILE)
        shadowOf(userManager).setSerialNumberForUser(work, WORK_SERIAL)
    }

    /** [label]'s launcher activity, installed in the work profile. */
    private fun installWork(
        label: String,
        packageName: String,
    ) {
        val info =
            ActivityInfo().apply {
                this.packageName = packageName
                name = "$packageName.Main"
                nonLocalizedLabel = label
                applicationInfo = ApplicationInfo().apply { this.packageName = packageName }
            }
        shadowOf(launcherApps).addActivity(work, launcherActivity(info, work))
    }

    /** The framework's own [LauncherActivityInfo] for [info] in [user]; its constructors are hidden. */
    private fun launcherActivity(
        info: ActivityInfo,
        user: UserHandle,
    ): LauncherActivityInfo {
        val internalClass = Class.forName("android.content.pm.LauncherActivityInfoInternal")
        val internalConstructor =
            internalClass.constructors.first { it.parameterTypes.firstOrNull() == ActivityInfo::class.java }
        val arguments =
            internalConstructor.parameterTypes.map { type ->
                when (type) {
                    ActivityInfo::class.java -> info
                    UserHandle::class.java -> user
                    else -> null
                }
            }
        val internal = internalConstructor.newInstance(*arguments.toTypedArray())
        return LauncherActivityInfo::class.java
            .getDeclaredConstructor(Context::class.java, internalClass)
            .apply { isAccessible = true }
            .newInstance(application, internal)
    }

    @Test
    @Config(shadows = [GlyphsFound::class])
    fun `a work app is listed only under Groups, in Work, not in the root's full list`() {
        install("Alarm", "com.example.alarm")
        addWorkProfile()
        installWork("Mail", "com.example.mail")
        launch()
        awaitListed("Alarm")
        assertEquals(listOf("Alarm", "MinimalUI", "MinimalUI settings", "Groups"), shownRows())

        openGroups()
        assertEquals(listOf("Work", "Other"), shownRows())
        rule.onNodeWithText("Work").assertIsFocused()
        press(Key.Enter)
        awaitListed("Other", listed = false)
        assertEquals(listOf("Mail"), shownRows())
    }

    /** From the root, by keyboard: Groups, then the Work group, which lists [label]. */
    private fun openWorkGroup(label: String) {
        openGroups()
        press(Key.Enter)
        awaitListed(label)
    }

    @Test
    @Config(shadows = [GlyphsFound::class, LauncherAppsRecorder::class])
    fun `a work app opens through LauncherApps in its profile, without animation, and becomes recent`() {
        LauncherAppsRecorder.started.clear()
        install("Alarm", "com.example.alarm")
        addWorkProfile()
        installWork("Mail", "com.example.mail")
        val activity = launch()
        awaitListed("Alarm")
        openWorkGroup("Mail")

        press(Key.Enter)

        val started = LauncherAppsRecorder.started.single()
        assertEquals(LauncherAppsRecorder.MAIN, started.kind)
        assertEquals(ComponentName("com.example.mail", "com.example.mail.Main"), started.component)
        assertEquals(work, started.user)
        assertNotNull(started.options)
        assertNull(shadowOf(application).nextStartedActivity)
        assertEquals(listOf(WORK_SERIAL), activity.get().recentApps.map { it.profile })
    }

    @Test
    @Config(shadows = [GlyphsFound::class, LauncherAppsRecorder::class])
    fun `a work app's menu opens its App info in its profile, and cannot uninstall it`() {
        LauncherAppsRecorder.started.clear()
        install("Alarm", "com.example.alarm")
        addWorkProfile()
        installWork("Mail", "com.example.mail")
        val activity = launch()
        awaitListed("Alarm")
        openWorkGroup("Mail")
        openMenu(activity)

        rule.onNodeWithText("Uninstall").assertIsNotEnabled()
        chooseRow("App info")

        val started = LauncherAppsRecorder.started.single()
        assertEquals(LauncherAppsRecorder.DETAILS, started.kind)
        assertEquals(ComponentName("com.example.mail", "com.example.mail.Main"), started.component)
        assertEquals(work, started.user)
        assertNull(shadowOf(application).nextStartedActivity)
    }

    @Test
    @Config(shadows = [GlyphsFound::class])
    fun `a work copy of a personal app is told apart by Work, and the filter finds both`() {
        install("Mail", "com.example.mail")
        addWorkProfile()
        installWork("Mail", "com.example.mail")
        val activity = launch()
        awaitListed("Mail")

        assertEquals(listOf("Mail", "Mail Work"), rowsMatching(activity, "mail"))
    }

    @Test
    @Config(shadows = [GlyphsFound::class])
    fun `renaming a work app leaves its personal copy's name alone`() {
        install("Mail", "com.example.mail")
        addWorkProfile()
        installWork("Mail", "com.example.mail")
        val activity = launch()
        awaitListed("Mail")
        openWorkGroup("Mail Work")

        rename(activity, "Mail", "Office")
        awaitListed("Office")
        type(activity, KeyEvent.KEYCODE_ESCAPE)
        type(activity, KeyEvent.KEYCODE_ESCAPE)

        awaitListed("Groups")
        assertEquals(listOf("Mail", "MinimalUI", "MinimalUI settings", "Groups"), shownRows())
    }

    private val clone: UserHandle = UserHandle.getUserHandleForUid(CLONE_USER * PER_USER_RANGE)

    /** An app-clone profile of the user (#201), as Android's app cloning makes one: neither managed nor hidden. */
    private fun addCloneProfile() {
        shadowOf(userManager).addProfile(0, CLONE_USER, "Clone", 0)
        shadowOf(userManager).setSerialNumberForUser(clone, CLONE_SERIAL)
    }

    /** [label]'s launcher activity of the [category], cloned into the app-clone profile. */
    private fun installClone(
        label: String,
        packageName: String,
        category: Int,
    ) {
        val info =
            ActivityInfo().apply {
                this.packageName = packageName
                name = "$packageName.Main"
                nonLocalizedLabel = label
                applicationInfo =
                    ApplicationInfo().apply {
                        this.packageName = packageName
                        this.category = category
                    }
            }
        shadowOf(launcherApps).addActivity(clone, launcherActivity(info, clone))
    }

    @Test
    @Config(shadows = [GlyphsFound::class, ProfileTypes::class])
    fun `an app clone is listed as a personal app is, told apart by Clone, also from a work copy`() {
        install("Chat", "com.example.chat", ApplicationInfo.CATEGORY_SOCIAL)
        addWorkProfile()
        installWork("Chat", "com.example.chat")
        addCloneProfile()
        installClone("Chat", "com.example.chat", ApplicationInfo.CATEGORY_SOCIAL)
        val activity = launch()
        awaitListed("Chat")
        assertEquals(listOf("Chat", "Chat Clone", "MinimalUI", "MinimalUI settings", "Groups"), shownRows())

        openGroups()
        assertEquals(listOf("Social", "Work", "Other"), shownRows())
        press(Key.Enter)
        awaitListed("Other", listed = false)
        assertEquals(listOf("Chat", "Chat Clone"), shownRows())
        type(activity, KeyEvent.KEYCODE_ESCAPE)
        type(activity, KeyEvent.KEYCODE_ESCAPE)

        awaitListed("Groups")
        assertEquals(listOf("Chat", "Chat Work", "Chat Clone"), rowsMatching(activity, "chat"))
    }

    @Test
    @Config(shadows = [GlyphsFound::class, ProfileTypes::class])
    fun `an app clone opens through LauncherApps in its profile, and cannot be uninstalled`() {
        LauncherAppsRecorder.started.clear()
        install("Chat", "com.example.chat", ApplicationInfo.CATEGORY_SOCIAL)
        addCloneProfile()
        installClone("Chat", "com.example.chat", ApplicationInfo.CATEGORY_SOCIAL)
        val activity = launch()
        awaitListed("Chat Clone")
        press(Key.DirectionDown)
        rule.onNodeWithText("Chat Clone").assertIsFocused()
        openMenu(activity)
        rule.onNodeWithText("Uninstall").assertIsNotEnabled()
        type(activity, KeyEvent.KEYCODE_ESCAPE)
        awaitListed("Chat Clone")

        press(Key.Enter)

        val started = LauncherAppsRecorder.started.single()
        assertEquals(LauncherAppsRecorder.MAIN, started.kind)
        assertEquals(ComponentName("com.example.chat", "com.example.chat.Main"), started.component)
        assertEquals(clone, started.user)
        assertNull(shadowOf(application).nextStartedActivity)
        assertEquals(listOf(CLONE_SERIAL), activity.get().recentApps.map { it.profile })
    }

    @Test
    @Config(shadows = [GlyphsFound::class])
    fun `a work profile added or removed reloads the list`() {
        install("Alarm", "com.example.alarm")
        launch()
        awaitListed("Alarm")
        openGroups()
        assertEquals(listOf("Other"), shownRows())

        addWorkProfile()
        installWork("Mail", "com.example.mail")
        application.sendBroadcast(Intent(Intent.ACTION_MANAGED_PROFILE_ADDED))
        awaitListed("Work")
        assertEquals(listOf("Work", "Other"), shownRows())

        ReflectionHelpers.callInstanceMethod<Boolean>(
            userManager,
            "removeUser",
            ClassParameter.from(Int::class.javaPrimitiveType, WORK_USER),
        )
        application.sendBroadcast(Intent(Intent.ACTION_MANAGED_PROFILE_REMOVED))
        awaitListed("Work", listed = false)
        assertEquals(listOf("Other"), shownRows())
    }

    @Test
    @Config(shadows = [GlyphsFound::class, LauncherAppsRecorder::class])
    fun `a work app whose profile was removed while listed starts nothing, and the list drops it`() {
        LauncherAppsRecorder.started.clear()
        install("Alarm", "com.example.alarm")
        addWorkProfile()
        installWork("Mail", "com.example.mail")
        val activity = launch()
        awaitListed("Alarm")
        openWorkGroup("Mail")

        // Gone before ACTION_MANAGED_PROFILE_REMOVED reloads the list, so Mail is still on screen.
        ReflectionHelpers.callInstanceMethod<Boolean>(
            userManager,
            "removeUser",
            ClassParameter.from(Int::class.javaPrimitiveType, WORK_USER),
        )
        press(Key.Enter)

        assertTrue(LauncherAppsRecorder.started.isEmpty())
        assertNull(shadowOf(application).nextStartedActivity)
        assertTrue(activity.get().recentApps.isEmpty())
        awaitListed("Mail", listed = false)
    }

    @Test
    @Config(shadows = [GlyphsFound::class])
    fun `a package change in the work profile reloads the list, whichever LauncherApps reports`() {
        install("Alarm", "com.example.alarm")
        addWorkProfile()
        installWork("Mail", "com.example.mail")
        launch()
        awaitListed("Alarm")
        openWorkGroup("Mail")
        val callbacks: List<android.util.Pair<LauncherApps.Callback, *>> =
            ReflectionHelpers.getStaticField(ShadowLauncherApps::class.java, "callbacks")
        val callback = callbacks.single().first
        val reports =
            listOf<(String) -> Unit>(
                { callback.onPackageAdded(it, work) },
                { callback.onPackageChanged(it, work) },
                { callback.onPackageRemoved(it, work) },
                { callback.onPackagesAvailable(arrayOf(it), work, false) },
                { callback.onPackagesUnavailable(arrayOf(it), work, false) },
            )

        reports.forEachIndexed { index, report ->
            val label = "Mail$index"
            installWork(label, "com.example.mail$index")
            rule.runOnUiThread { report("com.example.mail$index") }
            awaitListed(label)
        }
    }

    @Test
    fun `a LauncherApps report queued before the launcher was destroyed does not crash it`() {
        install("Alarm", "com.example.alarm")
        val activity = launch()
        awaitListed("Alarm")
        val callbacks: List<android.util.Pair<LauncherApps.Callback, *>> =
            ReflectionHelpers.getStaticField(ShadowLauncherApps::class.java, "callbacks")
        val callback = callbacks.single().first
        activity.pause().stop().destroy()
        controller = null

        // Delivered after destroy, as LauncherApps delivers a report it queued on the main looper before
        // unregisterCallback (#187).
        rule.runOnUiThread { callback.onPackageAdded("com.example.alarm", android.os.Process.myUserHandle()) }
    }

    @Test
    @Config(shadows = [GlyphsFound::class])
    fun `while work apps are turned off they stay listed, dim, and turning them on again brightens them`() {
        install("Alarm", "com.example.alarm")
        addWorkProfile()
        installWork("Mail", "com.example.mail")
        launch()
        awaitListed("Alarm")
        openWorkGroup("Mail")
        assertNotEquals(DimText, textColor("Mail"))

        userManager.requestQuietModeEnabled(true, work)
        rule.waitUntil(timeoutMillis = 5_000) { textColor("Mail") == DimText }

        userManager.requestQuietModeEnabled(false, work)
        rule.waitUntil(timeoutMillis = 5_000) { textColor("Mail") != DimText }
    }
}

/** Every activity start fails the way an unexported or permission-guarded activity does. */
@Implements(Activity::class)
class StartActivityDenied : ShadowActivity() {
    @Implementation
    protected fun startActivity(
        intent: Intent,
        options: Bundle?,
    ): Unit = throw SecurityException("Permission Denial")
}

/**
 * The font has a glyph for every character, as a device's has for an app's name. LauncherActivityInfo drops the
 * characters it has none for from a label, and Robolectric's own Paint has none at all.
 */
@Implements(Paint::class)
class GlyphsFound : ShadowPaint() {
    @Implementation
    protected fun hasGlyph(string: String): Boolean = true
}

/** Records what the launcher starts through LauncherApps, which Robolectric cannot start. */
@Implements(LauncherApps::class)
open class LauncherAppsRecorder : ShadowLauncherApps() {
    /** One start: its [kind] ([MAIN] or [DETAILS]), the [component] in the [user]'s profile, with its [options]. */
    data class Started(
        val kind: String,
        val component: ComponentName,
        val user: UserHandle,
        val options: Bundle?,
    )

    @Implementation
    protected fun startMainActivity(
        component: ComponentName,
        user: UserHandle,
        bounds: Rect?,
        options: Bundle?,
    ) {
        started += Started(MAIN, component, user, options)
    }

    @Implementation
    override fun startAppDetailsActivity(
        component: ComponentName,
        user: UserHandle,
        bounds: Rect?,
        options: Bundle?,
    ) {
        started += Started(DETAILS, component, user, options)
    }

    companion object {
        const val MAIN = "main"
        const val DETAILS = "details"
        val started = mutableListOf<Started>()
    }
}

/**
 * Gives each profile its type, as LauncherApps does from API 35 on: [CLONE_USER]'s is an app clone's (#201), any
 * other's a work profile's. Robolectric's LauncherApps knows no types. Records starts as [LauncherAppsRecorder] does.
 */
@Implements(LauncherApps::class)
class ProfileTypes : LauncherAppsRecorder() {
    @Implementation
    protected fun getLauncherUserInfo(user: UserHandle): LauncherUserInfo {
        val clone = user == UserHandle.getUserHandleForUid(CLONE_USER * PER_USER_RANGE)
        val type = if (clone) UserManager.USER_TYPE_PROFILE_CLONE else UserManager.USER_TYPE_PROFILE_MANAGED
        // Its builder is hidden API.
        val builder =
            ReflectionHelpers.callConstructor(
                Class.forName("android.content.pm.LauncherUserInfo\$Builder"),
                ClassParameter.from(String::class.java, type),
                ClassParameter.from(
                    Int::class.javaPrimitiveType,
                    if (clone) CLONE_SERIAL.toInt() else WORK_SERIAL.toInt(),
                ),
            )
        return ReflectionHelpers.callInstanceMethod(builder, "build")
    }
}

// The work profile's user id and serial number in the tests, as the emulator's first created profile has.
private const val WORK_USER = 10
private const val WORK_SERIAL = 10L
private const val PER_USER_RANGE = 100_000

// The app-clone profile's (#201), created after the work profile.
private const val CLONE_USER = 11
private const val CLONE_SERIAL = 11L
