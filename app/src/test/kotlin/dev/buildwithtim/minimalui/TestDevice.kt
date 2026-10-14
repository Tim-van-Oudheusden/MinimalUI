package dev.buildwithtim.minimalui

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.os.BatteryManager
import org.robolectric.shadows.ShadowPackageManager
import org.robolectric.util.ReflectionHelpers
import org.robolectric.util.ReflectionHelpers.ClassParameter

/**
 * Installs [activity] with an intent filter for [action] and [categories], as its manifest would declare it, so
 * package queries and implicit intents resolve to it.
 */
internal fun ShadowPackageManager.installActivity(
    activity: ActivityInfo,
    action: String,
    vararg categories: String,
) {
    // Another app's activity is only reachable from the launcher when it is exported.
    addOrUpdateActivity(ActivityInfo(activity).apply { exported = true })
    val filter =
        IntentFilter(action).apply {
            // startActivity and MATCH_DEFAULT_ONLY resolve only activities in the default category.
            addCategory(Intent.CATEGORY_DEFAULT)
            categories.forEach(::addCategory)
        }
    addIntentFilterForActivity(ComponentName(activity.packageName, activity.name), filter)
}

/**
 * Plays the system's sticky battery broadcast: registering for it returns the latest one. Apps may no longer send
 * sticky broadcasts (`Context.sendStickyBroadcast` is deprecated since API 21) but Robolectric still delivers them, so
 * the call goes through Robolectric's reflection helper in the system's place.
 */
internal fun Context.broadcastBattery(
    level: Int,
    status: Int = BatteryManager.BATTERY_STATUS_DISCHARGING,
) {
    val battery =
        Intent(Intent.ACTION_BATTERY_CHANGED)
            .putExtra(BatteryManager.EXTRA_LEVEL, level)
            .putExtra(BatteryManager.EXTRA_SCALE, 100)
            .putExtra(BatteryManager.EXTRA_STATUS, status)
    ReflectionHelpers.callInstanceMethod<Any?>(
        this,
        "sendStickyBroadcast",
        ClassParameter.from(Intent::class.java, battery),
    )
}
