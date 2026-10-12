package dev.buildwithtim.minimalui

import android.content.ActivityNotFoundException
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.buildwithtim.minimalui.core.LauncherApp
import dev.buildwithtim.minimalui.core.sortedForDisplay
import dev.buildwithtim.minimalui.ui.AppList
import dev.buildwithtim.minimalui.ui.MinimalTheme

class MainActivity : ComponentActivity() {

    private var apps by mutableStateOf(emptyList<LauncherApp>())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MinimalTheme {
                AppList(apps = apps, onOpen = ::open)
            }
        }
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
        apps = packageManager.launchableApps().sortedForDisplay()
    }
}
