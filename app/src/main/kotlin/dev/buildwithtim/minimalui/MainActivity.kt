package dev.buildwithtim.minimalui

import android.app.ActivityOptions
import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import dev.buildwithtim.minimalui.core.AppFacts
import dev.buildwithtim.minimalui.core.LatestLoader
import dev.buildwithtim.minimalui.core.LauncherApp
import dev.buildwithtim.minimalui.core.ListContent
import dev.buildwithtim.minimalui.core.MenuAction
import dev.buildwithtim.minimalui.core.MenuItem
import dev.buildwithtim.minimalui.core.RecentApps
import dev.buildwithtim.minimalui.core.StatusPill
import dev.buildwithtim.minimalui.core.contextMenu
import dev.buildwithtim.minimalui.core.listContent
import dev.buildwithtim.minimalui.core.minusLastTyped
import dev.buildwithtim.minimalui.core.plusTyped
import dev.buildwithtim.minimalui.core.sortedForDisplay
import dev.buildwithtim.minimalui.core.withSystemSettings
import dev.buildwithtim.minimalui.ui.AppList
import dev.buildwithtim.minimalui.ui.ContextMenu
import dev.buildwithtim.minimalui.ui.MinimalTheme
import dev.buildwithtim.minimalui.ui.Returned
import dev.buildwithtim.minimalui.ui.rememberAppListState
import java.util.concurrent.Executors

class MainActivity : ComponentActivity() {

    // Null until the first load arrives, so loading (a blank screen) is told apart from having no apps.
    private var apps by mutableStateOf<List<LauncherApp>?>(null)
    private var homePresses by mutableIntStateOf(0)
    private var query by mutableStateOf("")
    private var returned by mutableStateOf<Returned?>(null)
    private var status by mutableStateOf<StatusPill?>(null)

    // The context menu on screen in place of the list (#67), or null while the list shows.
    private var menu by mutableStateOf<AppMenu?>(null)

    // Lazy: the system services exist only once the activity is attached.
    private val statusWatcher by lazy { StatusWatcher(this) { status = it } }

    // Set once an app opens from here and cleared by Home, so the next resume is a return by Back, Recents or the app
    // exiting, which selects that app again.
    private var awayInApp = false

    private val recentStore by lazy { RecentAppsStore(this) }
    private var recents by mutableStateOf(RecentApps())

    /**
     * The apps last opened from here, newest first, for the root screen (#70). Derived from the installed list, so an
     * uninstalled app leaves it as soon as the package-change receiver reloads the apps.
     */
    internal val recentApps: List<LauncherApp> get() = recents.shownFrom(apps.orEmpty())

    // One worker thread: loads never run side by side on a slow device, and superseded ones are skipped.
    private val worker = Executors.newSingleThreadExecutor()

    // Lazy: the package manager and main executor exist only once the activity is attached.
    private val loader by lazy {
        LatestLoader(
            background = worker,
            main = mainExecutor,
            load = ::installedApps,
            publish = { apps = it },
        )
    }

    private val packageChanges = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = loader.refresh()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        hideStatusBar()
        recents = recentStore.load()
        // Registered for the activity's whole life, not only while resumed, so the list is current before a return.
        registerReceiver(
            packageChanges,
            IntentFilter().apply {
                addAction(Intent.ACTION_PACKAGE_ADDED)
                addAction(Intent.ACTION_PACKAGE_REMOVED)
                addAction(Intent.ACTION_PACKAGE_CHANGED)
                addDataScheme("package")
            },
        )
        // The home screen has nothing to go back to: finishing it would surface whichever launcher ran before.
        onBackPressedDispatcher.addCallback(this) {}
        setContent {
            // Outside the list, so the list comes back from the menu with the same app selected and the same screen.
            val listState = rememberAppListState()
            MinimalTheme {
                val shownMenu = menu
                if (shownMenu == null) {
                    AppList(
                        content = listContent(apps, query),
                        query = query,
                        homePresses = homePresses,
                        onOpen = ::open,
                        onMenu = ::openMenu,
                        state = listState,
                        returned = returned,
                        status = status,
                    )
                } else {
                    ContextMenu(
                        title = shownMenu.app.label,
                        items = shownMenu.items,
                        onChoose = { choose(shownMenu.app, it) },
                        onClose = { menu = null },
                        status = status,
                    )
                }
            }
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
        awayInApp = false
        menu = null
        homePresses++
        query = ""
    }

    // Typing filters the list. Rows only handle arrows, Enter and Space, so typed keys fall through to here, and still
    // arrive when the filter leaves no row to hold focus. Other keys, and Ctrl or Meta shortcuts, keep their usual job,
    // and so does every key while the context menu is open: it has nothing to filter.
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        val newQuery = when {
            menu != null || event.isCtrlPressed || event.isMetaPressed -> null
            keyCode == KeyEvent.KEYCODE_DEL -> query.minusLastTyped()
            else -> query.plusTyped(event.unicodeChar)
        }
        if (newQuery == null) return super.onKeyDown(keyCode, event)
        query = newQuery
        return true
    }

    override fun onResume() {
        super.onResume()
        // Reload on every return too, off the main thread; the current list stays up until the new one is ready.
        loader.refresh()
        // Home arrives through onNewIntent, always before this, so whatever is left here is a return from the app.
        if (awayInApp) {
            awayInApp = false
            val shown = (listContent(apps, query) as? ListContent.Apps)?.apps.orEmpty()
            returned = Returned(recents.returnRow(shown), (returned?.count ?: 0) + 1)
        }
    }

    override fun onDestroy() {
        unregisterReceiver(packageChanges)
        worker.shutdown()
        super.onDestroy()
    }

    private fun open(app: LauncherApp) {
        if (start(app.launchIntent())) {
            saveRecents(recents.opened(app, apps.orEmpty()))
            awayInApp = true
        }
    }

    /** Whether [intent] started. */
    private fun start(intent: Intent): Boolean =
        try {
            // No transition, as Cannoli: nothing animates between a key press and the app (BRANDING.md).
            startActivity(intent, ActivityOptions.makeCustomAnimation(this, 0, 0).toBundle())
            true
        } catch (_: ActivityNotFoundException) {
            // Uninstalled or disabled while the list was on screen: drop it rather than crash the launcher.
            loader.refresh()
            false
        } catch (_: SecurityException) {
            // Listed but not startable by us (not exported, or guarded by a permission we lack): ignore the press
            // rather than crash the launcher; a refresh would list it again.
            false
        }

    // The facts are read once, as the menu opens; every action closes it again.
    private fun openMenu(app: LauncherApp) {
        val recent = recentApps
        val facts = AppFacts(
            isSystem = packageManager.isSystemApp(app.packageName),
            isSettingsEntry = app.packageName == packageManager.systemSettings()?.packageName,
            isRecent = app in recent,
            hasRecents = recent.isNotEmpty(),
        )
        menu = AppMenu(app, contextMenu(facts))
    }

    // The list comes back on the same app: its selection was kept while the menu was up.
    private fun choose(app: LauncherApp, action: MenuAction) {
        menu = null
        when (action) {
            MenuAction.AppInfo -> start(app.appInfoIntent())
            MenuAction.Uninstall -> start(app.uninstallIntent())
            MenuAction.RemoveFromRecents -> saveRecents(recents.without(app))
            MenuAction.ClearRecents -> saveRecents(recents.cleared())
        }
    }

    private fun saveRecents(changed: RecentApps) {
        recents = changed
        recentStore.save(changed)
    }

    // Runs on the worker thread: the package queries and every label load stay off the main thread.
    private fun installedApps(): List<LauncherApp> {
        val installed = packageManager.launchableApps()
        val settings = packageManager.systemSettings()
        return (settings?.let(installed::withSystemSettings) ?: installed).sortedForDisplay()
    }
}

/** The context menu of [app], with its [items] as they were when it opened. */
private class AppMenu(val app: LauncherApp, val items: List<MenuItem>)
