package dev.buildwithtim.minimalui

import android.app.Application
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.ResolveInfo
import android.os.Build
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import dev.buildwithtim.minimalui.core.AppCategory
import dev.buildwithtim.minimalui.core.LauncherApp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

// 28 is minSdk and takes the pre-33 query; 35 takes the ResolveInfoFlags one.
@Config(sdk = [28, 35])
@RunWith(RobolectricTestRunner::class)
class InstalledAppsTest {
    private val application: Application = ApplicationProvider.getApplicationContext()
    private val packageManager = application.packageManager

    private fun resolveInfo(
        label: String,
        packageName: String,
        activityName: String,
        category: Int = ApplicationInfo.CATEGORY_UNDEFINED,
    ) = ResolveInfo().apply {
        nonLocalizedLabel = label
        activityInfo = ActivityInfo().apply {
            // launchableApps() reads the ResolveInfo's label, systemSettings() the ActivityInfo's.
            nonLocalizedLabel = label
            this.packageName = packageName
            name = activityName
            applicationInfo = ApplicationInfo().apply {
                this.packageName = packageName
                this.category = category
            }
        }
    }

    @Test
    fun `lists every launcher activity by label and component, with the category its app declared`() {
        val launcherQuery = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val reader = resolveInfo("Reader", "com.example.reader", "com.example.reader.Main", category = 8)
        shadowOf(packageManager).run {
            addResolveInfoForIntent(launcherQuery, resolveInfo("Notes", "com.example.notes", "com.example.notes.Main"))
            addResolveInfoForIntent(
                launcherQuery,
                resolveInfo("Mail", "com.example.mail", "com.example.mail.Inbox", ApplicationInfo.CATEGORY_PRODUCTIVITY),
            )
            addResolveInfoForIntent(launcherQuery, reader)
        }

        val apps = packageManager.launchableApps()

        assertTrue(LauncherApp("Notes", "com.example.notes", "com.example.notes.Main", AppCategory.Other) in apps)
        assertTrue(LauncherApp("Mail", "com.example.mail", "com.example.mail.Inbox", AppCategory.Productivity) in apps)
        // CATEGORY_ACCESSIBILITY (8) exists from API 31 on; below it the code means nothing known.
        val accessibility = if (Build.VERSION.SDK_INT >= 31) AppCategory.Accessibility else AppCategory.Other
        assertTrue(LauncherApp("Reader", "com.example.reader", "com.example.reader.Main", accessibility) in apps)
    }

    @Test
    fun `finds the activity that opens the system settings`() {
        shadowOf(packageManager).addResolveInfoForIntent(
            Intent(Settings.ACTION_SETTINGS),
            resolveInfo("Settings", "com.android.settings", "com.android.settings.Settings"),
        )

        assertEquals(
            LauncherApp("Settings", "com.android.settings", "com.android.settings.Settings", AppCategory.Other),
            packageManager.systemSettings(),
        )
    }

    @Test
    fun `has no settings entry where nothing opens the system settings`() {
        assertNull(packageManager.systemSettings())
    }
}
