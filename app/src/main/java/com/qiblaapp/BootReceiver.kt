package com.qiblaapp

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == "android.intent.action.LOCKED_BOOT_COMPLETED"
        ) {
            val prefs = context.getSharedPreferences("QiblaPrefs", Context.MODE_PRIVATE)
            val prayerEnabled = prefs.getBoolean("prayer_notifications", false)
            if (!prayerEnabled) return

            val lat = prefs.getFloat("last_lat", 0f).toDouble()
            val lon = prefs.getFloat("last_lon", 0f).toDouble()
            if (lat == 0.0 && lon == 0.0) return

            // Reschedule all prayer alarms
            PrayerScheduler.scheduleAllPrayers(context, lat, lon)
        }
    }
}