package dev.buildwithtim.minimalui

import android.content.Intent
import android.content.pm.LauncherApps
import android.os.Bundle
import android.os.UserManager
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import dev.buildwithtim.minimalui.core.LatestLoader
import dev.buildwithtim.minimalui.core.LauncherApp
import dev.buildwithtim.minimalui.core.launcherSettings
import dev.buildwithtim.minimalui.core.minusLastTyped
import dev.buildwithtim.minimalui.core.plusTyped
import dev.buildwithtim.minimalui.core.withSystemSettings
import dev.buildwithtim.minimalui.ui.MinimalTheme
import dev.buildwithtim.minimalui.ui.rememberAppListState
import java.util.concurrent.Executors

/**
 * The home screen: wires the launcher's [LauncherState] to the activity's lifecycle, the installed apps and the keys,
 * and shows the list on top of it.
 */
class MainActivity : ComponentActivity() {
    // Made in onCreate, once the stores can read the app's storage.
    private lateinit var state: LauncherState

    /**
     * The apps last opened from here, newest first, for the root screen (#70). Derived from the installed list, so an
     * uninstalled app leaves it as soon as the package-change receiver reloads the apps.
     */
    internal val recentApps: List<LauncherApp> get() = state.recentApps

    /**
     * The installed apps as last loaded, before aliases and sorting; empty until the first load is published. A reload
     * runs off the main thread, so this is how to tell that one has landed (#183).
     */
    internal val loadedApps: List<LauncherApp> get() = state.loaded?.apps.orEmpty()

    // Lazy: the system services exist only once the activity is attached.
    private val statusWatcher by lazy { StatusWatcher(this) { state.status = it } }
    private val userManager by lazy { getSystemService(UserManager::class.java) }
    private val launcherApps by lazy { getSystemService(LauncherApps::class.java) }

    // One worker thread: loads never run side by side on a slow device, and superseded ones are skipped.
    private val worker = Executors.newSingleThreadExecutor()

    // Exported and imported setups (#208) are written and read on their own thread, so a slow file provider never
    // holds up a reload of the list.
    private val fileWorker = Executors.newSingleThreadExecutor()

    // Lazy: the package manager and main executor exist only once the activity is attached.
    private val loader by lazy {
        LatestLoader(
            background = worker,
            main = mainExecutor,
            load = ::installedApps,
            publish = { state.loaded = it },
        )
    }

    private val listChanges by lazy { AppListChanges(this, launcherApps, loader::refresh) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        hideStatusBar()
        state = LauncherState(LauncherStores(this))
        // Registered for the activity's whole life, not only while resumed, so the list is current before a return.
        listChanges.register()
        // The home screen has nothing to go back to: finishing it would surface whichever launcher ran before.
        onBackPressedDispatcher.addCallback(this) {}
        val starter = AppStarter(this, launcherApps, userManager, loader::refresh)
        val actions = AppActions(state, starter, packageManager)
        val edits = ListEdits(state)
        val files = SetupFiles(this, state, fileWorker)
        setContent {
            // Outside the list, so the list comes back from another screen with the same row selected and the same
            // rows on screen.
            val listState = rememberAppListState()
            MinimalTheme { LauncherScreens(state, actions, edits, listState, files) }
        }
    }

    /**
     * The status pill stands in for Android's status bar (#65), so the launcher's window hides it; a swipe from the top
     * edge brings it back for a moment. It is this window's setting only, so the bar shows again in any other app.
     */
    private fun hideStatusBar() {
        enableEdgeToEdge()
        WindowCompat.getInsetsController(window, window.decorView).run {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.statusBars())
        }
    }

    // The pill's sources are watched only while the launcher can be seen.
    override fun onStart() {
        super.onStart()
        statusWatcher.start()
    }

    override fun onStop() {
        statusWatcher.stop()
        super.onStop()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // singleTask: the Home key (or a launcher icon) reaches the running instance here, never a second one.
        state.home()
    }

    // Typing filters the list. Rows only handle arrows, Enter and Space, so typed keys fall through to here, and still
    // arrive when the filter leaves no row to hold focus. Other keys, and Ctrl or Meta shortcuts, keep their usual job,
    // and so does every key while another list (a context menu, the settings) or the text editor is open: the editor
    // takes its own typing.
    override fun onKeyDown(
        keyCode: Int,
        event: KeyEvent,
    ): Boolean {
        val typed = state.query.takeIf { state.screens.isRoot && !event.isCtrlPressed && !event.isMetaPressed }
        val edited =
            when {
                typed == null -> null
                keyCode == KeyEvent.KEYCODE_DEL -> typed.minusLastTyped()
                else -> typed.plusTyped(event.unicodeChar)
            }
        if (edited == null) return super.onKeyDown(keyCode, event)
        state.query = edited
        return true
    }

    override fun onResume() {
        super.onResume()
        // Reload on every return too, off the main thread; the current list stays up until the new one is ready.
        loader.refresh()
        // Home arrives through onNewIntent, always before this, so whatever is left here is a return from the app.
        state.cameBack()
    }

    override fun onDestroy() {
        listChanges.unregister()
        worker.shutdown()
        fileWorker.shutdown()
        super.onDestroy()
    }

    // Runs on the worker thread: the package queries and every label load stay off the main thread. Sorting waits for
    // the aliases, in [LauncherState.apps]. The other profiles' apps (#78) come from LauncherApps, after the Settings
    // check, so a work profile's Settings never stands in for the launcher's own.
    private fun installedApps(): Loaded {
        val installed = packageManager.launchableApps()
        val system = packageManager.systemSettings()
        val entry = launcherSettings(getString(R.string.settings_entry), packageName)
        val own = system?.let(installed::withSystemSettings) ?: installed
        val others = launcherApps.otherProfileApps(userManager)
        return Loaded(own + others + entry, system?.packageName, userManager.pausedProfiles())
    }
}
