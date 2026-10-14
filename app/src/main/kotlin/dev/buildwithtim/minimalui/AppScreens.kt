package dev.buildwithtim.minimalui

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import dev.buildwithtim.minimalui.core.LauncherApp

/** Android's App info screen for [this] app, in a task of its own as a home screen opens it. */
fun LauncherApp.appInfoIntent(): Intent =
    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

/** Android's prompt to uninstall [this] app; the user confirms there. */
fun LauncherApp.uninstallIntent(): Intent =
    Intent(Intent.ACTION_DELETE, packageUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

private fun LauncherApp.packageUri(): Uri = Uri.fromParts("package", packageName, null)
