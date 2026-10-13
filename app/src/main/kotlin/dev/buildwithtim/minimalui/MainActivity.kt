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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.buildwithtim.minimalui.core.LatestLoader
import dev.buildwithtim.minimalui.core.LauncherApp
import dev.buildwithtim.minimalui.core.ListContent
import dev.buildwithtim.minimalui.core.RecentApps
import dev.buildwithtim.minimalui.core.listContent
import dev.buildwithtim.minimalui.core.minusLastTyped
import dev.buildwithtim.minimalui.core.plusTyped
import dev.buildwithtim.minimalui.core.sortedForDisplay
import dev.buildwithtim.minimalui.core.withSystemSettings
import dev.buildwithtim.minimalui.ui.AppList
import dev.buildwithtim.minimalui.ui.MinimalTheme
import dev.buildwithtim.minimalui.ui.Returned
import java.util.concurrent.Executors

class MainActivity : ComponentActivity() {

    // Null until the first load arrives, so loading (a blank screen) is told apart from having no apps.
    private var apps by mutableStateOf<List<LauncherApp>?>(null)
    private var homePresses by mutableIntStateOf(0)
    private var query by mutableStateOf("")
    private var returned by mutableStateOf<Returned?>(null)

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
            MinimalTheme {
                AppList(
                    content = listContent(apps, query),
                    query = query,
                    homePresses = homePresses,
                    onOpen = ::open,
                    returned = returned,
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // singleTask: the Home key (or a launcher icon) reaches the running instance here, never a second one.
        awayInApp = false
        homePresses++
        query = ""
    }

    // Typing filters the list. Rows only handle arrows, Enter and Space, so typed keys fall through to here, and still
    // arrive when the filter leaves no row to hold focus. Other keys, and Ctrl or Meta shortcuts, keep their usual job.
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        val newQuery = when {
            event.isCtrlPressed || event.isMetaPressed -> null
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
        try {
            // No transition, as Cannoli: nothing animates between a key press and the app (BRANDING.md).
            startActivity(app.launchIntent(), ActivityOptions.makeCustomAnimation(this, 0, 0).toBundle())
            recents = recents.opened(app, apps.orEmpty())
            recentStore.save(recents)
            awayInApp = true
        } catch (_: ActivityNotFoundException) {
            // Uninstalled or disabled while the list was on screen: drop it rather than crash the launcher.
            loader.refresh()
        } catch (_: SecurityException) {
            // Listed but not startable by us (not exported, or guarded by a permission we lack): ignore the press
            // rather than crash the launcher; a refresh would list it again.
        }
    }

    // Runs on the worker thread: the package queries and every label load stay off the main thread.
    private fun installedApps(): List<LauncherApp> {
        val installed = packageManager.launchableApps()
        val settings = packageManager.systemSettings()
        return (settings?.let(installed::withSystemSettings) ?: installed).sortedForDisplay()
    }
}
