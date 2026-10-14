package dev.buildwithtim.minimalui

import android.app.Activity
import android.app.ActivityOptions
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Intent
import android.content.pm.LauncherApps
import android.graphics.Rect
import android.os.Bundle
import android.os.UserHandle
import android.os.UserManager
import dev.buildwithtim.minimalui.core.LauncherApp
import dev.buildwithtim.minimalui.core.isWorkApp

/**
 * Starts apps and Android's screens for them from [activity], without crashing the launcher when one cannot start:
 * an app gone since the list was loaded makes it [refresh].
 */
internal class AppStarter(
    private val activity: Activity,
    private val launcherApps: LauncherApps,
    private val userManager: UserManager,
    private val refresh: () -> Unit,
) {
    /** Whether [app] started, in its own profile (#78). */
    fun launch(app: LauncherApp): Boolean =
        if (app.isWorkApp) {
            startInProfile(app, LauncherApps::startMainActivity)
        } else {
            start(app.launchIntent())
        }

    /** Android's App info screen for [app], in its own profile. */
    fun showInfo(app: LauncherApp) {
        if (app.isWorkApp) {
            startInProfile(app, LauncherApps::startAppDetailsActivity)
        } else {
            start(app.appInfoIntent())
        }
    }

    /** Whether [intent] started. */
    fun start(intent: Intent): Boolean = starting { activity.startActivity(intent, noAnimation()) }

    /**
     * Whether [app], of another profile (#78), started there through [LauncherApps] by [how]: its main activity, or its
     * App info. While its profile's apps are turned off, Android offers to turn them back on. A profile removed while
     * the list was on screen starts nothing and drops its apps.
     */
    private fun startInProfile(
        app: LauncherApp,
        how: LauncherApps.(ComponentName, UserHandle, Rect?, Bundle?) -> Unit,
    ): Boolean {
        val user = app.profile?.let(userManager::getUserForSerialNumber)
        if (user == null) {
            refresh()
            return false
        }
        return starting {
            launcherApps.how(
                ComponentName(app.packageName, app.activityName),
                user,
                null,
                noAnimation(),
            )
        }
    }

    // No transition, as Cannoli: nothing animates between a key press and the app (BRANDING.md).
    private fun noAnimation(): Bundle = ActivityOptions.makeCustomAnimation(activity, 0, 0).toBundle()

    /** Whether [start] started what it starts, without crashing the launcher when it cannot. */
    private fun starting(start: () -> Unit): Boolean =
        try {
            start()
            true
        } catch (_: ActivityNotFoundException) {
            // Uninstalled or disabled while the list was on screen: drop it rather than crash the launcher.
            refresh()
            false
        } catch (_: SecurityException) {
            // Listed but not startable by us (not exported, or guarded by a permission we lack): ignore the press
            // rather than crash the launcher; a refresh would list it again.
            false
        }
}
