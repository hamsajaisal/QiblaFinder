package com.qiblaapp

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.speech.tts.TextToSpeech
import androidx.core.app.NotificationCompat
import java.util.Locale

class PrayerAlarmReceiver : BroadcastReceiver() {

    companion object {
        const val CHANNEL_ID = "prayer_notifications"
        const val EXTRA_PRAYER_NAME = "prayer_name"
        const val EXTRA_PRAYER_NAME_ML = "prayer_name_ml"
        const val EXTRA_IS_JAMAAT = "is_jamaat"
        const val EXTRA_NOTIFICATION_ID = "notification_id"
    }

    private var tts: TextToSpeech? = null

    override fun onReceive(context: Context, intent: Intent) {
        val prayerName = intent.getStringExtra(EXTRA_PRAYER_NAME) ?: "Prayer"
        val prayerNameMl = intent.getStringExtra(EXTRA_PRAYER_NAME_ML) ?: ""
        val isJamaat = intent.getBooleanExtra(EXTRA_IS_JAMAAT, false)
        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, 1)

        val prefs = context.getSharedPreferences("QiblaPrefs", Context.MODE_PRIVATE)

        // Check if prayer notifications are enabled
        val prayerEnabled = prefs.getBoolean("prayer_notifications", false)
        if (!prayerEnabled) return

        // Check jamaat reminder setting
        if (isJamaat) {
            val jamaatEnabled = prefs.getBoolean("jamaat_reminder", false)
            if (!jamaatEnabled) return
        }

        // Acquire wake lock to keep CPU awake during announcement
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "QiblaApp::PrayerWakeLock"
        )
        wakeLock.acquire(30000) // Hold for max 30 seconds

        // Check silent mode
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val isSilent = audioManager.ringerMode == AudioManager.RINGER_MODE_SILENT

        // Always show notification
        showNotification(context, prayerName, prayerNameMl, isJamaat, notificationId, prefs)

        // Only speak if not in silent mode
        if (!isSilent) {
            speakAnnouncement(context, prayerName, prayerNameMl, isJamaat, prefs) {
                // Release wake lock after speaking
                if (wakeLock.isHeld) wakeLock.release()
            }
        } else {
            if (wakeLock.isHeld) wakeLock.release()
        }
    }

    private fun showNotification(
        context: Context,
        prayerName: String,
        prayerNameMl: String,
        isJamaat: Boolean,
        notificationId: Int,
        prefs: SharedPreferences
    ) {
        val lang = prefs.getString("language", "english") ?: "english"
        val isMalayalam = lang == "malayalam"
        val isArabic = lang == "arabic"
        createNotificationChannel(context)

        val tapIntent = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val prayerNameAr = when (prayerName) {
            "Fajr" -> "الفجر"
            "Dhuhr" -> "الظهر"
            "Asr" -> "العصر"
            "Maghrib" -> "المغرب"
            "Isha" -> "العشاء"
            else -> prayerName
        }

        val title: String
        val message: String

        when {
            isArabic -> {
                if (isJamaat) {
                    title = "صلاة الجماعة لـ $prayerNameAr"
                    message = "حان الآن موعد صلاة الجماعة لـ $prayerNameAr"
                } else {
                    title = "صلاة $prayerNameAr"
                    message = "حان الآن موعد صلاة $prayerNameAr"
                }
            }
            isMalayalam -> {
                title = if (isJamaat) "$prayerNameMl ജമാഅത്ത്" else "$prayerNameMl നമസ്കാരം"
                message = if (isJamaat) "$prayerNameMl ജമാഅത്ത് തുടങ്ങാൻ സമയമായി" else "$prayerNameMl നമസ്കാരത്തിന് സമയമായി"
            }
            else -> {
                title = if (isJamaat) "$prayerName Congregation" else "$prayerName Prayer"
                message = if (isJamaat) "$prayerName congregation is about to begin" else "It is time for $prayerName prayer"
            }
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(message)
            .setContentIntent(tapIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .build()

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(notificationId, notification)
    }

    private fun speakAnnouncement(
        context: Context,
        prayerName: String,
        prayerNameMl: String,
        isJamaat: Boolean,
        prefs: SharedPreferences,
        onComplete: () -> Unit
    ) {
        val lang = prefs.getString("language", "english") ?: "english"
        val isMalayalam = lang == "malayalam"
        val isArabic = lang == "arabic"

        val prayerNameAr = when (prayerName) {
            "Fajr" -> "الفجر"
            "Dhuhr" -> "الظهر"
            "Asr" -> "العصر"
            "Maghrib" -> "المغرب"
            "Isha" -> "العشاء"
            else -> prayerName
        }

        val message = when {
            isArabic -> {
                if (isJamaat) "حان الآن موعد صلاة الجماعة لـ صلاة $prayerNameAr"
                else "حان الآن موعد صلاة $prayerNameAr"
            }
            isMalayalam -> {
                if (isJamaat) "$prayerNameMl ജമാഅത്ത് തുടങ്ങാൻ സമയമായി"
                else "$prayerNameMl നമസ്കാരത്തിന് സമയമായി"
            }
            else -> {
                if (isJamaat) "$prayerName congregation is about to begin"
                else "It is time for $prayerName prayer"
            }
        }

        val locale = when (lang) {
            "malayalam" -> Locale("ml", "IN")
            "arabic" -> Locale("ar")
            else -> Locale.ENGLISH
        }

        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = locale
                tts?.speak(message, TextToSpeech.QUEUE_FLUSH, null, "prayer_$prayerName")
                // Shutdown TTS after speaking then release wake lock
                Handler(Looper.getMainLooper()).postDelayed({
                    tts?.shutdown()
                    tts = null
                    onComplete()
                }, 10000)
            } else {
                tts?.shutdown()
                tts = null
                onComplete()
            }
        }
    }

    private fun createNotificationChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Prayer Time Alerts",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Notifications for each daily prayer time"
            enableVibration(false)
            setSound(null, null)
        }
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(channel)
    }
}