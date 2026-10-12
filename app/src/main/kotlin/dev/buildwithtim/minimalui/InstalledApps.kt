package dev.buildwithtim.minimalui

import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.os.Build
import android.provider.Settings
import dev.buildwithtim.minimalui.core.LauncherApp

private fun launcherIntent(): Intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)

/** Every activity that declares itself as a launcher entry point; visibility comes from the manifest's `<queries>`. */
fun PackageManager.launchableApps(): List<LauncherApp> =
    queryLauncherActivities().map { info ->
        LauncherApp(
            label = info.loadLabel(this).toString(),
            packageName = info.activityInfo.packageName,
            activityName = info.activityInfo.name,
        )
    }

/** The activity that opens the system settings, or null on a device where nothing handles it. */
fun PackageManager.systemSettings(): LauncherApp? =
    Intent(Settings.ACTION_SETTINGS).resolveActivityInfo(this, PackageManager.MATCH_DEFAULT_ONLY)?.let { info ->
        LauncherApp(label = info.loadLabel(this).toString(), packageName = info.packageName, activityName = info.name)
    }

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
