package com.qiblaapp

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.batoulapps.adhan.CalculationMethod
import com.batoulapps.adhan.Coordinates
import com.batoulapps.adhan.Madhab
import com.batoulapps.adhan.PrayerTimes
import com.batoulapps.adhan.data.DateComponents
import java.util.Calendar
import java.util.Date
import kotlin.math.abs

object PrayerScheduler {

    data class Prayer(
        val name: String,
        val nameMl: String,
        val requestCode: Int,
        val jamaatRequestCode: Int,
        val jamaatDelayMinutes: Int
    )

    val prayers = listOf(
        Prayer("Fajr", "സുബഹി", 1001, 1011, 10),
        Prayer("Dhuhr", "ളുഹർ", 1002, 1012, 10),
        Prayer("Asr", "അസർ", 1003, 1013, 10),
        Prayer("Maghrib", "മഗ്രിബ്", 1004, 1014, 2),
        Prayer("Isha", "ഇശാ", 1005, 1015, 10)
    )

    fun getLocalComponents(date: Date = Date()): DateComponents {
        val cal = Calendar.getInstance().apply {
            time = date
        }
        return DateComponents(
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH) + 1,
            cal.get(Calendar.DAY_OF_MONTH)
        )
    }

    private fun getPrayerTimes(context: Context, lat: Double, lon: Double, date: DateComponents): PrayerTimes {
        val coordinates = Coordinates(lat, lon)
        val prefs = context.getSharedPreferences("QiblaPrefs", Context.MODE_PRIVATE)
        val methodStr = prefs.getString("calculation_method", "MOON_SIGHTING_COMMITTEE") ?: "MOON_SIGHTING_COMMITTEE"
        val isHanafi = prefs.getBoolean("asr_hanafi", false)

        val method = try {
            CalculationMethod.valueOf(methodStr)
        } catch (e: Exception) {
            CalculationMethod.MOON_SIGHTING_COMMITTEE
        }

        val params = method.parameters.also {
            it.madhab = if (isHanafi) Madhab.HANAFI else Madhab.SHAFI
        }
        return PrayerTimes(coordinates, date, params)
    }

    fun calculatePrayerTimes(context: Context, lat: Double, lon: Double, date: DateComponents = getLocalComponents(Date())): List<Long> {
        val times = getPrayerTimes(context, lat, lon, date)
        val prefs = context.getSharedPreferences("QiblaPrefs", Context.MODE_PRIVATE)
        val adjustment = prefs.getInt("manual_adjustment", 0)
        val adjMs = adjustment * 60 * 1000L

        return listOf(
            times.fajr.time + adjMs,
            times.dhuhr.time + adjMs,
            times.asr.time + adjMs,
            times.maghrib.time + adjMs,
            times.isha.time + adjMs
        )
    }

    fun getPrayerTimesForDisplay(context: Context, lat: Double, lon: Double): List<Pair<String, String>> {
        val times = calculatePrayerTimes(context, lat, lon)
        return prayers.mapIndexed { index, prayer ->
            val cal = Calendar.getInstance().apply {
                timeInMillis = times[index]
            }
            val hour = cal.get(Calendar.HOUR_OF_DAY)
            val minute = cal.get(Calendar.MINUTE)
            val amPm = if (hour < 12) "AM" else "PM"
            val displayHour = if (hour % 12 == 0) 12 else hour % 12
            val timeStr = "%d:%02d %s".format(displayHour, minute, amPm)
            Pair(prayer.name, timeStr)
        }
    }

    fun scheduleAllPrayers(context: Context, lat: Double, lon: Double) {
        val times = calculatePrayerTimes(context, lat, lon)
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        prayers.forEachIndexed { index, prayer ->
            val prayerTime = times[index]
            if (prayerTime > System.currentTimeMillis()) {
                scheduleSingleAlarm(
                    context, alarmManager,
                    prayerTime, prayer.name, prayer.nameMl,
                    false, prayer.requestCode
                )
                val jamaatTime = prayerTime + (prayer.jamaatDelayMinutes * 60 * 1000L)
                scheduleSingleAlarm(
                    context, alarmManager,
                    jamaatTime, prayer.name, prayer.nameMl,
                    true, prayer.jamaatRequestCode
                )
            }
        }

        // Schedule midnight rescheduling
        scheduleMidnightReschedule(context)
    }

    private fun scheduleMidnightReschedule(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val midnight = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 1)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val intent = Intent(context, MidnightReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context, 9999, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        try {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                midnight.timeInMillis,
                pendingIntent
            )
        } catch (e: SecurityException) {
            alarmManager.set(
                AlarmManager.RTC_WAKEUP,
                midnight.timeInMillis,
                pendingIntent
            )
        }
    }

    private fun scheduleSingleAlarm(
        context: Context,
        alarmManager: AlarmManager,
        timeMillis: Long,
        prayerName: String,
        prayerNameMl: String,
        isJamaat: Boolean,
        requestCode: Int
    ) {
        val intent = Intent(context, PrayerAlarmReceiver::class.java).apply {
            putExtra(PrayerAlarmReceiver.EXTRA_PRAYER_NAME, prayerName)
            putExtra(PrayerAlarmReceiver.EXTRA_PRAYER_NAME_ML, prayerNameMl)
            putExtra(PrayerAlarmReceiver.EXTRA_IS_JAMAAT, isJamaat)
            putExtra(PrayerAlarmReceiver.EXTRA_NOTIFICATION_ID, requestCode)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        try {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP, timeMillis, pendingIntent
            )
        } catch (e: SecurityException) {
            alarmManager.set(AlarmManager.RTC_WAKEUP, timeMillis, pendingIntent)
        }
    }

    fun cancelAllAlarms(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        prayers.forEach { prayer ->
            listOf(prayer.requestCode, prayer.jamaatRequestCode).forEach { code ->
                val intent = Intent(context, PrayerAlarmReceiver::class.java)
                val pi = PendingIntent.getBroadcast(
                    context, code, intent,
                    PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
                )
                pi?.let { alarmManager.cancel(it) }
            }
        }
        val midnightIntent = Intent(context, MidnightReceiver::class.java)
        val midnightPi = PendingIntent.getBroadcast(
            context, 9999, midnightIntent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        midnightPi?.let { alarmManager.cancel(it) }
    }

    fun getCountdownText(context: Context, lat: Double, lon: Double, nowMs: Long): String {
        val prefs = context.getSharedPreferences("QiblaPrefs", Context.MODE_PRIVATE)
        val lang = prefs.getString("language", "english") ?: "english"

        // Today's times (calculated using local components of current time)
        val timesToday = calculatePrayerTimes(context, lat, lon, getLocalComponents(Date(nowMs)))
        
        var nextPrayerIndex = -1
        var nextPrayerTime = 0L
        var isTomorrow = false

        for (i in 0 until 5) {
            if (timesToday[i] > nowMs) {
                nextPrayerIndex = i
                nextPrayerTime = timesToday[i]
                break
            }
        }

        if (nextPrayerIndex == -1) {
            // After Isha, next is Fajr tomorrow
            nextPrayerIndex = 0
            isTomorrow = true
            
            // Calculate tomorrow's local date
            val cal = Calendar.getInstance().apply {
                timeInMillis = nowMs
                add(Calendar.DAY_OF_MONTH, 1)
            }
            val tomorrowDate = getLocalComponents(cal.time)
            val timesTomorrow = calculatePrayerTimes(context, lat, lon, tomorrowDate)
            nextPrayerTime = timesTomorrow[0]
        }

        val prayer = prayers[nextPrayerIndex]
        val diffMs = nextPrayerTime - nowMs
        val diffMinutes = (diffMs / (1000 * 60)).toInt()
        val hours = diffMinutes / 60
        val minutes = diffMinutes % 60

        // Format duration based on language
        val durationStr = when (lang) {
            "malayalam" -> {
                when {
                    hours > 0 && minutes > 0 -> "$hours മണിക്കൂർ $minutes മിനിറ്റ്"
                    hours > 0 -> "$hours മണിക്കൂർ"
                    else -> "$minutes മിനിറ്റ്"
                }
            }
            "arabic" -> {
                when {
                    hours > 0 && minutes > 0 -> "$hours ساعة و $minutes دقيقة"
                    hours > 0 -> "$hours ساعة"
                    else -> "$minutes دقيقة"
                }
            }
            else -> {
                val hrStr = if (hours == 1) "1 hour" else "$hours hours"
                val minStr = if (minutes == 1) "1 minute" else "$minutes minutes"
                when {
                    hours > 0 && minutes > 0 -> "$hrStr and $minStr"
                    hours > 0 -> hrStr
                    else -> minStr
                }
            }
        }

        // Format full sentence
        return when (lang) {
            "malayalam" -> {
                if (isTomorrow) {
                    "നാളത്തെ സുബഹി നമസ്കാരത്തിന് $durationStr ബാക്കി"
                } else {
                    "അടുത്ത നമസ്കാരം ${prayer.nameMl}. സമയം അവശേഷിക്കുന്നത് $durationStr"
                }
            }
            "arabic" -> {
                val arName = when (prayer.name) {
                    "Fajr" -> "الفجر"
                    "Dhuhr" -> "الظهر"
                    "Asr" -> "العصر"
                    "Maghrib" -> "المغرب"
                    "Isha" -> "العشاء"
                    else -> prayer.name
                }
                if (isTomorrow) {
                    "الصلاة القادمة هي صلاة الفجر غداً بعد $durationStr"
                } else {
                    "الصلاة القادمة هي صلاة $arName بعد $durationStr"
                }
            }
            else -> {
                if (isTomorrow) {
                    "Next prayer is Fajr tomorrow in $durationStr"
                } else {
                    "Next prayer is ${prayer.name} in $durationStr"
                }
            }
        }
    }
}