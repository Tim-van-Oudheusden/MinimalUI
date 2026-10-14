package dev.buildwithtim.minimalui

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.net.Uri
import android.os.Build
import android.os.Process
import android.os.UserHandle
import android.os.UserManager
import android.provider.Settings
import dev.buildwithtim.minimalui.core.LauncherApp
import dev.buildwithtim.minimalui.core.appCategory

private fun launcherIntent(): Intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)

/**
 * Every activity that declares itself as a launcher entry point, with the category its app declared (#69); visibility
 * comes from the manifest's `<queries>`.
 */
fun PackageManager.launchableApps(): List<LauncherApp> =
    queryLauncherActivities().map { info ->
        LauncherApp(
            label = info.loadLabel(this).toString(),
            packageName = info.activityInfo.packageName,
            activityName = info.activityInfo.name,
            category = info.activityInfo.applicationInfo.groupCategory(),
        )
    }

/** The activity that opens the system settings, or null on a device where nothing handles it. */
fun PackageManager.systemSettings(): LauncherApp? =
    Intent(Settings.ACTION_SETTINGS).resolveActivityInfo(this, PackageManager.MATCH_DEFAULT_ONLY)?.let { info ->
        LauncherApp(
            label = info.loadLabel(this).toString(),
            packageName = info.packageName,
            activityName = info.name,
            category = info.applicationInfo.groupCategory(),
        )
    }

/**
 * The profiles of the launcher's user other than its own, such as a work profile (#78), as [UserManager] lists them;
 * [LauncherApps] lists their apps.
 */
fun UserManager.otherProfiles(): List<UserHandle> = userProfiles.filter { it != Process.myUserHandle() }

/**
 * Every launcher activity of the [users]' profiles other than the launcher's own (#78), each marked with its profile's
 * serial number, the id the launcher stores it by. They take no category: a work app's only group is Work.
 */
fun LauncherApps.otherProfileApps(users: UserManager): List<LauncherApp> =
    users.otherProfiles().flatMap { user ->
        val serial = users.getSerialNumberForUser(user)
        getActivityList(null, user).map { info ->
            LauncherApp(
                label = info.label.toString(),
                packageName = info.componentName.packageName,
                activityName = info.componentName.className,
                profile = serial,
            )
        }
    }

/** The serial numbers of the other profiles whose apps are turned off (work mode paused, #78). */
fun UserManager.pausedProfiles(): Set<Long> =
    otherProfiles().filter(::isQuietModeEnabled).mapTo(HashSet(), ::getSerialNumberForUser)

/** The automatic group (#69) of the app [this] describes, from the category its developer declared. */
private fun ApplicationInfo.groupCategory() = appCategory(category, Build.VERSION.SDK_INT)

/** Whether [packageName] came with the system image, so it cannot be uninstalled; false if it is not installed. */
fun PackageManager.isSystemApp(packageName: String): Boolean {
    val info =
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                getApplicationInfo(packageName, PackageManager.ApplicationInfoFlags.of(0L))
            } else {
                // The only overload before API 33; deprecated from 33 on, where the branch above is taken.
                @Suppress("DEPRECATION")
                getApplicationInfo(packageName, 0)
            }
        } catch (_: PackageManager.NameNotFoundException) {
            return false
        }
    return info.flags and ApplicationInfo.FLAG_SYSTEM != 0
}

/** Android's App info screen for [this] app, in a task of its own as a home screen opens it. */
fun LauncherApp.appInfoIntent(): Intent =
    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

/** Android's prompt to uninstall [this] app; the user confirms there. */
fun LauncherApp.uninstallIntent(): Intent =
    Intent(Intent.ACTION_DELETE, packageUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

private fun LauncherApp.packageUri(): Uri = Uri.fromParts("package", packageName, null)

/** Starts [this] app the way a home screen does: its existing task if it has one, a new one otherwise. */
fun LauncherApp.launchIntent(): Intent =
    launcherIntent()
        .setClassName(packageName, activityName)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)

private fun PackageManager.queryLauncherActivities(): List<ResolveInfo> =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        queryIntentActivities(launcherIntent(), PackageManager.ResolveInfoFlags.of(0L))
    } else {
        // The only overload before API 33; deprecated from 33 on, where the branch above is taken.
        @Suppress("DEPRECATION")
        queryIntentActivities(launcherIntent(), 0)
    }
