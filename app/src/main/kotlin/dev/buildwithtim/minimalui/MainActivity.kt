package dev.buildwithtim.minimalui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.buildwithtim.minimalui.core.LauncherApp
import dev.buildwithtim.minimalui.core.sortedForDisplay
import dev.buildwithtim.minimalui.core.withSystemSettings
import dev.buildwithtim.minimalui.ui.AppList
import dev.buildwithtim.minimalui.ui.MinimalTheme

class MainActivity : ComponentActivity() {

    private var apps by mutableStateOf(emptyList<LauncherApp>())
    private var homePresses by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // The home screen has nothing to go back to: finishing it would surface whichever launcher ran before.
        onBackPressedDispatcher.addCallback(this) {}
        setContent {
            MinimalTheme {
                AppList(apps = apps, homePresses = homePresses, onOpen = ::open)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // singleTask: the Home key (or a launcher icon) reaches the running instance here, never a second one.
        homePresses++
    }

    override fun onResume() {
        super.onResume()
        // Reload on every return so apps installed or removed meanwhile show up.
        reload()
    }

    private fun open(app: LauncherApp) {
        try {
            startActivity(app.launchIntent())
        } catch (_: ActivityNotFoundException) {
            // Uninstalled or disabled while the list was on screen: drop it rather than crash the launcher.
            reload()
        }
    }

    private fun reload() {
        val installed = packageManager.launchableApps()
        val settings = packageManager.systemSettings()
        apps = (settings?.let(installed::withSystemSettings) ?: installed).sortedForDisplay()
    }
}
