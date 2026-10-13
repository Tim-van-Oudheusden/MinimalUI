package dev.buildwithtim.minimalui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Handler
import android.os.Looper
import android.text.format.DateFormat
import dev.buildwithtim.minimalui.core.Battery
import dev.buildwithtim.minimalui.core.StatusPill
import dev.buildwithtim.minimalui.core.battery
import dev.buildwithtim.minimalui.core.clockText
import java.text.DateFormatSymbols
import java.time.LocalTime

/**
 * Feeds the status pill (#65) between [start] and [stop]: the battery from the sticky battery broadcast, Wi-Fi from
 * the default network, and the clock on every minute tick, clock or time-zone change. Nothing is polled, and no
 * dangerous permission is needed. Each change [publish]es a new pill on the main thread.
 */
internal class StatusWatcher(private val context: Context, private val publish: (StatusPill) -> Unit) {
    private var battery: Battery? = null
    private var wifi = false
    private val connectivity = context.getSystemService(ConnectivityManager::class.java)

    private val broadcasts = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == Intent.ACTION_BATTERY_CHANGED) battery = intent.toBattery()
            update()
        }
    }

    private val network = object : ConnectivityManager.NetworkCallback() {
        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
            wifi = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
            update()
        }

        override fun onLost(network: Network) {
            wifi = false
            update()
        }
    }

    fun start() {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_BATTERY_CHANGED)
            addAction(Intent.ACTION_TIME_TICK)
            // Also sent when the user switches between 12- and 24-hour time.
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
        }
        // The battery broadcast is sticky: registering returns the current charge, so the first frame shows it.
        battery = context.registerReceiver(broadcasts, filter)?.toBattery()
        // The callback reports the current default network right away; until it does, assume none.
        wifi = false
        connectivity.registerDefaultNetworkCallback(network, Handler(Looper.getMainLooper()))
        update()
    }

    fun stop() {
        context.unregisterReceiver(broadcasts)
        connectivity.unregisterNetworkCallback(network)
    }

    private fun update() {
        val now = LocalTime.now()
        val (am, pm) = DateFormatSymbols.getInstance().amPmStrings
        val clock = clockText(now.hour, now.minute, DateFormat.is24HourFormat(context), am, pm)
        publish(StatusPill(wifi, battery, clock))
    }

    private fun Intent.toBattery(): Battery? {
        val status = getIntExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_UNKNOWN)
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
        return battery(getIntExtra(BatteryManager.EXTRA_LEVEL, -1), getIntExtra(BatteryManager.EXTRA_SCALE, -1), charging)
    }
}
