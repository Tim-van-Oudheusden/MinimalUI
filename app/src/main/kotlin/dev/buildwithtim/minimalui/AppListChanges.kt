package dev.buildwithtim.minimalui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.LauncherApps
import android.os.UserHandle

/**
 * Watches for changes to the installed apps of every profile, and to the profiles themselves, so [refresh] reloads
 * the list.
 */
internal class AppListChanges(
    private val context: Context,
    private val launcherApps: LauncherApps,
    private val refresh: () -> Unit,
) {
    // LauncherApps queues each report on the main looper and still delivers one queued before unregisterCallback, so a
    // report can arrive after [unregister], when its refresh would run against a destroyed activity (#187).
    private var listening = false

    private fun changed() {
        if (listening) refresh()
    }

    private val listChanges =
        object : BroadcastReceiver() {
            override fun onReceive(
                context: Context,
                intent: Intent,
            ) = changed()
        }

    // The other profiles' package changes (#78), which the package broadcasts only report for the launcher's own user.
    private val profilePackageChanges =
        object : LauncherApps.Callback() {
            override fun onPackageAdded(
                packageName: String,
                user: UserHandle,
            ) = changed()

            override fun onPackageChanged(
                packageName: String,
                user: UserHandle,
            ) = changed()

            override fun onPackageRemoved(
                packageName: String,
                user: UserHandle,
            ) = changed()

            override fun onPackagesAvailable(
                packageNames: Array<out String>,
                user: UserHandle,
                replacing: Boolean,
            ) = changed()

            override fun onPackagesUnavailable(
                packageNames: Array<out String>,
                user: UserHandle,
                replacing: Boolean,
            ) = changed()
        }

    fun register() {
        listening = true
        context.registerReceiver(
            listChanges,
            IntentFilter().apply {
                addAction(Intent.ACTION_PACKAGE_ADDED)
                addAction(Intent.ACTION_PACKAGE_REMOVED)
                addAction(Intent.ACTION_PACKAGE_CHANGED)
                addDataScheme("package")
            },
        )
        // A work profile added or removed, or its apps turned off or on again (#78); these carry no package.
        context.registerReceiver(
            listChanges,
            IntentFilter().apply {
                addAction(Intent.ACTION_MANAGED_PROFILE_ADDED)
                addAction(Intent.ACTION_MANAGED_PROFILE_REMOVED)
                addAction(Intent.ACTION_MANAGED_PROFILE_AVAILABLE)
                addAction(Intent.ACTION_MANAGED_PROFILE_UNAVAILABLE)
                addAction(Intent.ACTION_MANAGED_PROFILE_UNLOCKED)
            },
        )
        launcherApps.registerCallback(profilePackageChanges)
    }

    fun unregister() {
        listening = false
        context.unregisterReceiver(listChanges)
        launcherApps.unregisterCallback(profilePackageChanges)
    }
}
