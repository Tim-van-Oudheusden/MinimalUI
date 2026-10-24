package dev.buildwithtim.minimalui

import android.app.Application
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.graphics.Bitmap
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.provider.Settings
import android.view.KeyEvent
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onRoot
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowNetwork
import org.robolectric.shadows.ShadowNetworkCapabilities
import java.io.File

/**
 * Renders the store and README screenshots (#120) from the real [MainActivity], filled with demo apps, under
 * Robolectric's native graphics, so no phone or emulator is needed. Not a test: `./gradlew test` leaves it out, and
 * `./gradlew :app:testDebugUnitTest -PstoreScreenshots` runs only this and writes the PNGs to the `screenshots.dir`
 * system property (`app/build.gradle.kts` points it at the fastlane listing). The status pill shows the time of the
 * run.
 *
 * 360 × 540 dp at xxhdpi is 1080 × 1620 px, the 3:2 screen of the keyboard phones MinimalUI is for.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h540dp-xxhdpi")
class StoreScreenshots {
    @get:Rule
    val rule = createEmptyComposeRule()

    private val application: Application = ApplicationProvider.getApplicationContext()
    private val output = File(checkNotNull(System.getProperty("screenshots.dir")) { "screenshots.dir is not set" })
    private var controller: ActivityController<MainActivity>? = null

    @After
    fun closeActivity() {
        controller?.pause()?.stop()?.destroy()
    }

    private fun install(
        label: String,
        category: Int = ApplicationInfo.CATEGORY_UNDEFINED,
    ) {
        val packageName = "com.example.${label.lowercase()}"
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

    /** Android's Settings app, which the list always keeps. */
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

    /** A phone with its apps installed, on Wi-Fi at 80 % charge, showing MinimalUI's home screen. */
    private fun launch(): ActivityController<MainActivity> {
        install("Calculator")
        install("Calendar", ApplicationInfo.CATEGORY_PRODUCTIVITY)
        install("Camera", ApplicationInfo.CATEGORY_IMAGE)
        install("Chess", ApplicationInfo.CATEGORY_GAME)
        install("Clock")
        install("Contacts", ApplicationInfo.CATEGORY_SOCIAL)
        install("Files", ApplicationInfo.CATEGORY_PRODUCTIVITY)
        install("Gallery", ApplicationInfo.CATEGORY_IMAGE)
        install("Maps", ApplicationInfo.CATEGORY_MAPS)
        install("Messages", ApplicationInfo.CATEGORY_SOCIAL)
        install("Music", ApplicationInfo.CATEGORY_AUDIO)
        install("News", ApplicationInfo.CATEGORY_NEWS)
        install("Notes", ApplicationInfo.CATEGORY_PRODUCTIVITY)
        install("Phone", ApplicationInfo.CATEGORY_SOCIAL)
        install("Podcasts", ApplicationInfo.CATEGORY_AUDIO)
        install("Weather")
        installSystemSettings()
        application.broadcastBattery(80)
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().also { controller = it }
        connectWifi()
        awaitListed("Calculator")
        return activity
    }

    /** Reports Wi-Fi as the default network; the launcher registers a new callback each time it comes back. */
    private fun connectWifi() {
        val wifi = ShadowNetworkCapabilities.newInstance()
        shadowOf(wifi).addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
        shadowOf(application.getSystemService(ConnectivityManager::class.java))
            .networkCallbacks
            .forEach { it.onCapabilitiesChanged(ShadowNetwork.newInstance(1), wifi) }
    }

    /** The app list loads off the main thread; wait until [label] is listed. */
    private fun awaitListed(label: String) =
        rule.waitUntil(timeoutMillis = 5_000) { rule.onAllNodesWithText(label).fetchSemanticsNodes().isNotEmpty() }

    private fun type(
        activity: ActivityController<MainActivity>,
        keyCode: Int,
    ) {
        activity.get().dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
        activity.get().dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))
        rule.waitForIdle()
    }

    /**
     * Presses Down until the row [label] is selected; the selected row is the focused one. The list wraps around, so a
     * row that is not there fails after more presses than the demo phone has rows (#216) instead of cycling forever.
     */
    private fun select(
        activity: ActivityController<MainActivity>,
        label: String,
    ) {
        val selected = hasText(label) and isFocused()
        repeat(MAX_SELECT_PRESSES) {
            if (rule.onAllNodes(selected).fetchSemanticsNodes().isNotEmpty()) return
            type(activity, KeyEvent.KEYCODE_DPAD_DOWN)
        }
        error("No row $label to select within $MAX_SELECT_PRESSES presses of Down")
    }

    /** Makes [label] a favourite through its menu, as a user would. */
    private fun favourite(
        activity: ActivityController<MainActivity>,
        label: String,
    ) {
        select(activity, label)
        type(activity, KeyEvent.KEYCODE_MENU)
        awaitListed("Add to favourites")
        select(activity, "Add to favourites")
        type(activity, KeyEvent.KEYCODE_ENTER)
        awaitListed("★ $label")
    }

    /** Opens [label] with Enter and comes back from it, so it heads the recently used block. */
    private fun openAndComeBack(
        activity: ActivityController<MainActivity>,
        label: String,
    ) {
        select(activity, label)
        type(activity, KeyEvent.KEYCODE_ENTER)
        activity.pause().stop()
        activity.restart().resume()
        connectWifi()
        rule.waitForIdle()
    }

    /** Back to the top of the home screen, as the Home key does. */
    private fun home(activity: ActivityController<MainActivity>) {
        activity.newIntent(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME))
        rule.waitForIdle()
    }

    private fun capture(name: String) {
        rule.waitForIdle()
        val bitmap = rule.onRoot().captureToImage().asAndroidBitmap()
        output.mkdirs()
        output.resolve(name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        println("wrote ${output.resolve(name)}")
    }

    @Test
    fun screenshots() {
        val activity = launch()
        favourite(activity, "Messages")
        favourite(activity, "Phone")
        openAndComeBack(activity, "Camera")
        openAndComeBack(activity, "Music")
        home(activity)
        capture("1_home.png")

        "ca".forEach { type(activity, KeyEvent.keyCodeFromString("KEYCODE_${it.uppercaseChar()}")) }
        awaitListed("ca_")
        capture("2_filter.png")
        type(activity, KeyEvent.KEYCODE_DEL)
        type(activity, KeyEvent.KEYCODE_DEL)

        home(activity)
        select(activity, "Calendar")
        type(activity, KeyEvent.KEYCODE_MENU)
        awaitListed("App info")
        capture("3_app_menu.png")
        type(activity, KeyEvent.KEYCODE_ESCAPE)

        home(activity)
        select(activity, "MinimalUI settings")
        type(activity, KeyEvent.KEYCODE_ENTER)
        awaitListed("Battery percentage")
        capture("4_settings.png")
    }
}

// More than every row of the demo phone's lists: 17 apps, the recent and favourite blocks and their extra rows.
private const val MAX_SELECT_PRESSES = 50
