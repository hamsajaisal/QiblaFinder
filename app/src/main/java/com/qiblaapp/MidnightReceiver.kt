package com.qiblaapp

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class MidnightReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val prefs = context.getSharedPreferences("QiblaPrefs", Context.MODE_PRIVATE)
        val prayerEnabled = prefs.getBoolean("prayer_notifications", false)
        if (!prayerEnabled) return

        val lat = prefs.getFloat("last_lat", 0f).toDouble()
        val lon = prefs.getFloat("last_lon", 0f).toDouble()
        if (lat == 0.0 && lon == 0.0) return

        // Reschedule for new day and set next midnight trigger
        PrayerScheduler.scheduleAllPrayers(context, lat, lon)
    }
}