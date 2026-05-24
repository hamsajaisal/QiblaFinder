package com.qiblaapp

import android.Manifest
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import android.speech.tts.TextToSpeech
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.google.android.gms.location.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.draw.rotate
import android.widget.Toast

class MainActivity : ComponentActivity(), SensorEventListener {

    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null
    private var magnetometer: Sensor? = null

    private val gravity = FloatArray(3)
    private val geomagnetic = FloatArray(3)
    private val alpha = 0.15f

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var userLatState = mutableStateOf(0.0)
    private var userLonState = mutableStateOf(0.0)

    private var userLat: Double
        get() = userLatState.value
        set(value) { userLatState.value = value }

    private var userLon: Double
        get() = userLonState.value
        set(value) { userLonState.value = value }
    private var locationFound = false

    private val KAABA_LAT = 21.4225
    private val KAABA_LON = 39.8262
    private var qiblaDirection = 0f
    private var currentHeading = 0f
    private var wasAligned = false

    private var tts: TextToSpeech? = null
    private var ttsReady = false

    private var vibrator: Vibrator? = null
    private lateinit var prefs: SharedPreferences

    private var headingState = mutableStateOf(0f)
    private var qiblaState = mutableStateOf(0f)
    private var isAlignedState = mutableStateOf(false)
    private var statusMessage = mutableStateOf("Starting up...")
    private var vibrationEnabled = mutableStateOf(false)
    private var spokenEnabled = mutableStateOf(false)
    private var calibrationDone = mutableStateOf(false)
    private var accuracyLabel = mutableStateOf("Unknown")
    private var currentScreen = mutableStateOf("qibla")
    private var prayerTimesDisplay = mutableStateOf<List<Pair<String, String>>>(emptyList())
    private var prayerNotificationsEnabled = mutableStateOf(false)
    private var jamaatReminderEnabled = mutableStateOf(false)
    private var selectedLanguage = mutableStateOf("english")
    private var selectedCalculationMethod = mutableStateOf("MOON_SIGHTING_COMMITTEE")
    private var asrHanafi = mutableStateOf(false)
    private var manualAdjustment = mutableStateOf(0)
    private var selectedTheme = mutableStateOf("system")

    // Update dialog state
    private var showUpdateDialog = mutableStateOf(false)
    private var updateVersion = mutableStateOf("")
    private var updateUrl = mutableStateOf("")
    private var updateNotes = mutableStateOf("")
    private var isDownloading = mutableStateOf(false)
    private var showUpToDateDialog = mutableStateOf(false)
    private var showUpdateErrorDialog = mutableStateOf(false)

    private val exactAlarmLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (prayerNotificationsEnabled.value && locationFound) {
            PrayerScheduler.scheduleAllPrayers(this, userLat, userLon)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        prefs = getSharedPreferences("QiblaPrefs", Context.MODE_PRIVATE)
        loadPreferences()

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        magnetometer = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)

        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vm = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vm.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }

        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                ttsReady = true
            }
        }

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        requestPermissions()

        // Check for updates silently on app open
        checkForUpdates()

        setContent {
            val themeMode = selectedTheme.value
            val isDarkTheme = when (themeMode) {
                "dark" -> true
                "light" -> false
                else -> androidx.compose.foundation.isSystemInDarkTheme()
            }
            com.qiblaapp.ui.theme.QiblaAppTheme(darkTheme = isDarkTheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    // Update dialog
                if (showUpdateDialog.value) {
                    UpdateDialog(
                        version = updateVersion.value,
                        releaseNotes = updateNotes.value,
                        isDownloading = isDownloading.value,
                        onDownload = {
                            isDownloading.value = true
                            UpdateChecker.downloadAndInstall(
                                this,
                                updateUrl.value,
                                updateVersion.value
                            )
                            showUpdateDialog.value = false
                            isDownloading.value = false
                        },
                        onCancel = {
                            showUpdateDialog.value = false
                        }
                    )
                }

                if (showUpToDateDialog.value) {
                    AlertDialog(
                        onDismissRequest = { showUpToDateDialog.value = false },
                        title = { Text("No Update Available", modifier = Modifier.semantics { heading() }) },
                        text = { Text("Your application is up to date.") },
                        confirmButton = {
                            Button(onClick = { showUpToDateDialog.value = false }) {
                                Text("OK")
                            }
                        }
                    )
                }

                if (showUpdateErrorDialog.value) {
                    AlertDialog(
                        onDismissRequest = { showUpdateErrorDialog.value = false },
                        title = { Text("Connection Error", modifier = Modifier.semantics { heading() }) },
                        text = { Text("Could not check for updates. Please check your internet connection and try again.") },
                        confirmButton = {
                            Button(onClick = { showUpdateErrorDialog.value = false }) {
                                Text("OK")
                            }
                        }
                    )
                }

                BackHandler(enabled = currentScreen.value == "settings") {
                    currentScreen.value = "qibla"
                }

                when (currentScreen.value) {
                    "qibla" -> QiblaScreen(
                        headingState = headingState,
                        qiblaState = qiblaState,
                        isAlignedState = isAlignedState,
                        statusMessage = statusMessage,
                        calibrationDone = calibrationDone,
                        accuracyLabel = accuracyLabel,
                        prayerTimes = prayerTimesDisplay,
                        userLat = userLat,
                        userLon = userLon,
                        onNavigateToSettings = {
                            currentScreen.value = "settings"
                        },
                        onRecalibrate = {
                            calibrationDone.value = false
                            accuracyLabel.value = "Unknown"
                            statusMessage.value = "Move phone in figure-8 to calibrate"
                            speak("Recalibrating. Move your phone in a figure-8 motion.")
                        }
                    )
                    "settings" -> SettingsScreen(
                        vibrationEnabled = vibrationEnabled,
                        spokenEnabled = spokenEnabled,
                        prayerNotificationsEnabled = prayerNotificationsEnabled,
                        jamaatReminderEnabled = jamaatReminderEnabled,
                        selectedLanguage = selectedLanguage,
                        selectedCalculationMethod = selectedCalculationMethod,
                        asrHanafi = asrHanafi,
                        manualAdjustment = manualAdjustment,
                        selectedTheme = selectedTheme,
                        onVibrationToggle = { enabled ->
                            vibrationEnabled.value = enabled
                            if (!enabled) stopVibration()
                            prefs.edit().putBoolean("vibration", enabled).apply()
                        },
                        onSpokenToggle = { enabled ->
                            spokenEnabled.value = enabled
                            prefs.edit().putBoolean("spoken", enabled).apply()
                        },
                        onPrayerNotificationsToggle = { enabled ->
                            prayerNotificationsEnabled.value = enabled
                            prefs.edit().putBoolean("prayer_notifications", enabled).apply()
                            if (enabled) {
                                checkExactAlarmPermission()
                                if (locationFound) {
                                    PrayerScheduler.scheduleAllPrayers(this, userLat, userLon)
                                }
                                checkBatteryOptimization()
                            } else {
                                PrayerScheduler.cancelAllAlarms(this)
                            }
                        },
                        onJamaatReminderToggle = { enabled ->
                            jamaatReminderEnabled.value = enabled
                            prefs.edit().putBoolean("jamaat_reminder", enabled).apply()
                        },
                        onLanguageChange = { lang ->
                            selectedLanguage.value = lang
                            prefs.edit().putString("language", lang).apply()
                            refreshPrayerTimes()
                        },
                        onCalculationMethodChange = { method ->
                            selectedCalculationMethod.value = method
                            prefs.edit().putString("calculation_method", method).apply()
                            refreshPrayerTimes()
                        },
                        onAsrHanafiToggle = { enabled ->
                            asrHanafi.value = enabled
                            prefs.edit().putBoolean("asr_hanafi", enabled).apply()
                            refreshPrayerTimes()
                        },
                        onManualAdjustmentChange = { adj ->
                            manualAdjustment.value = adj
                            prefs.edit().putInt("manual_adjustment", adj).apply()
                            refreshPrayerTimes()
                        },
                        onThemeChange = { theme ->
                            selectedTheme.value = theme
                            prefs.edit().putString("theme", theme).apply()
                        },
                        onCheckForUpdates = {
                            CoroutineScope(Dispatchers.Main).launch {
                                Toast.makeText(this@MainActivity, "Checking for updates...", Toast.LENGTH_SHORT).show()
                                val info = UpdateChecker.checkForUpdate(this@MainActivity)
                                if (info != null) {
                                    if (info.isUpdateAvailable) {
                                        updateVersion.value = info.latestVersion
                                        updateNotes.value = info.releaseNotes
                                        updateUrl.value = info.downloadUrl
                                        showUpdateDialog.value = true
                                    } else {
                                        showUpToDateDialog.value = true
                                    }
                                } else {
                                    showUpdateErrorDialog.value = true
                                }
                            }
                        },
                        onNavigateBack = {
                            currentScreen.value = "qibla"
                        }
                    )
                }
                }
            }
        }
    }

    private fun checkForUpdates() {
        // Only check once per day
        val lastCheck = prefs.getLong("last_update_check", 0)
        val oneDayMillis = 24 * 60 * 60 * 1000L
        if (System.currentTimeMillis() - lastCheck < oneDayMillis) return

        CoroutineScope(Dispatchers.Main).launch {
            val updateInfo = UpdateChecker.checkForUpdate(this@MainActivity)
            if (updateInfo != null && updateInfo.isUpdateAvailable) {
                updateVersion.value = updateInfo.latestVersion
                updateUrl.value = updateInfo.downloadUrl
                updateNotes.value = updateInfo.releaseNotes
                showUpdateDialog.value = true
            }
            // Save last check time
            prefs.edit().putLong("last_update_check", System.currentTimeMillis()).apply()
        }
    }

    private fun checkExactAlarmPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
            if (!alarmManager.canScheduleExactAlarms()) {
                AlertDialog.Builder(this)
                    .setTitle("Alarm Permission Required")
                    .setMessage(
                        "To announce prayer times at the exact correct moment, " +
                                "this app needs permission to set exact alarms. " +
                                "Please enable it on the next screen."
                    )
                    .setPositiveButton("Allow") { _, _ ->
                        val intent = Intent(
                            Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                            Uri.parse("package:$packageName")
                        )
                        exactAlarmLauncher.launch(intent)
                    }
                    .setNegativeButton("Skip") { dialog, _ ->
                        dialog.dismiss()
                    }
                    .show()
            }
        }
    }

    private fun checkBatteryOptimization() {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        if (!powerManager.isIgnoringBatteryOptimizations(packageName)) {
            AlertDialog.Builder(this)
                .setTitle("Background Permission")
                .setMessage(
                    "For reliable prayer time announcements, please allow this app " +
                            "to run in background. This uses very little battery as the app " +
                            "only wakes up at prayer times, 5 times a day."
                )
                .setPositiveButton("Allow") { _, _ ->
                    val intent = Intent(
                        Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                        Uri.parse("package:$packageName")
                    )
                    startActivity(intent)
                }
                .setNegativeButton("Skip") { dialog, _ ->
                    dialog.dismiss()
                }
                .show()
        }
    }

    private fun loadPreferences() {
        vibrationEnabled.value = prefs.getBoolean("vibration", false)
        spokenEnabled.value = prefs.getBoolean("spoken", false)
        prayerNotificationsEnabled.value = prefs.getBoolean("prayer_notifications", false)
        jamaatReminderEnabled.value = prefs.getBoolean("jamaat_reminder", false)
        selectedLanguage.value = prefs.getString("language", "english") ?: "english"
        selectedCalculationMethod.value = prefs.getString("calculation_method", "MOON_SIGHTING_COMMITTEE") ?: "MOON_SIGHTING_COMMITTEE"
        asrHanafi.value = prefs.getBoolean("asr_hanafi", false)
        manualAdjustment.value = prefs.getInt("manual_adjustment", 0)
        selectedTheme.value = prefs.getString("theme", "system") ?: "system"
    }

    private fun refreshPrayerTimes() {
        if (locationFound) {
            prayerTimesDisplay.value =
                PrayerScheduler.getPrayerTimesForDisplay(this, userLat, userLon)
            if (prayerNotificationsEnabled.value) {
                PrayerScheduler.scheduleAllPrayers(this, userLat, userLon)
            }
        }
    }

    private fun requestPermissions() {
        val launcher = registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->
            if (permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true) {
                getLocation()
            } else {
                statusMessage.value = "Location permission needed for Qibla direction"
            }
        }

        val permList = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permList.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        launcher.launch(permList.toTypedArray())
    }

    private fun getLocation() {
        if (ContextCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED) return

        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
            if (location != null && location.accuracy < 100f) {
                updateLocation(location)
            } else {
                val request = CurrentLocationRequest.Builder()
                    .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
                    .setMaxUpdateAgeMillis(0)
                    .build()
                fusedLocationClient.getCurrentLocation(request, null)
                    .addOnSuccessListener { fresh ->
                        if (fresh != null) {
                            updateLocation(fresh)
                        } else {
                            statusMessage.value =
                                "Could not get location. Please go outside or near a window."
                        }
                    }
            }
        }
    }

    private fun updateLocation(location: Location) {
        userLat = location.latitude
        userLon = location.longitude
        locationFound = true

        prefs.edit()
            .putFloat("last_lat", userLat.toFloat())
            .putFloat("last_lon", userLon.toFloat())
            .apply()

        qiblaDirection = calculateQibla(userLat, userLon)
        qiblaState.value = qiblaDirection
        prayerTimesDisplay.value =
            PrayerScheduler.getPrayerTimesForDisplay(this, userLat, userLon)

        if (prayerNotificationsEnabled.value) {
            PrayerScheduler.scheduleAllPrayers(this, userLat, userLon)
        }

        statusMessage.value =
            "Location found. Qibla is at ${qiblaDirection.toInt()}°. " +
                    "Move phone in figure-8 to calibrate."

        speak(
            "Location found. Qibla direction is ${qiblaDirection.toInt()} degrees. " +
                    "Now move your phone in a figure-8 motion to calibrate."
        )
    }

    private fun calculateQibla(lat: Double, lon: Double): Float {
        val lat1 = Math.toRadians(lat)
        val lat2 = Math.toRadians(KAABA_LAT)
        val deltaLon = Math.toRadians(KAABA_LON - lon)
        val x = sin(deltaLon) * cos(lat2)
        val y = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(deltaLon)
        val bearing = Math.toDegrees(atan2(x, y))
        return ((bearing + 360) % 360).toFloat()
    }

    override fun onResume() {
        super.onResume()
        accelerometer?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }
        magnetometer?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }
        loadPreferences()
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
        stopVibration()
    }

    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> {
                gravity[0] = alpha * event.values[0] + (1 - alpha) * gravity[0]
                gravity[1] = alpha * event.values[1] + (1 - alpha) * gravity[1]
                gravity[2] = alpha * event.values[2] + (1 - alpha) * gravity[2]
            }
            Sensor.TYPE_MAGNETIC_FIELD -> {
                geomagnetic[0] = alpha * event.values[0] + (1 - alpha) * geomagnetic[0]
                geomagnetic[1] = alpha * event.values[1] + (1 - alpha) * geomagnetic[1]
                geomagnetic[2] = alpha * event.values[2] + (1 - alpha) * geomagnetic[2]
            }
        }

        val rotationMatrix = FloatArray(9)
        val inclinationMatrix = FloatArray(9)
        val success = SensorManager.getRotationMatrix(
            rotationMatrix, inclinationMatrix, gravity, geomagnetic
        )

        if (success) {
            val orientation = FloatArray(3)
            SensorManager.getOrientation(rotationMatrix, orientation)
            val azimuth = Math.toDegrees(orientation[0].toDouble()).toFloat()
            currentHeading = (azimuth + 360) % 360
            headingState.value = currentHeading

            if (locationFound && calibrationDone.value) {
                val aligned = isAligned(currentHeading, qiblaDirection)
                isAlignedState.value = aligned
                handleFeedback(aligned)
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {
        if (sensor.type == Sensor.TYPE_MAGNETIC_FIELD) {
            val label = when (accuracy) {
                SensorManager.SENSOR_STATUS_ACCURACY_HIGH -> {
                    if (!calibrationDone.value) {
                        calibrationDone.value = true
                        statusMessage.value =
                            "Calibration complete. Qibla is at " +
                                    "${qiblaDirection.toInt()}°. Slowly rotate phone."
                        speak("Calibration complete. Slowly rotate your phone to find Qibla.")
                    }
                    "High"
                }
                SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM -> {
                    if (!calibrationDone.value) {
                        statusMessage.value =
                            "Calibration in progress. Keep moving phone in figure-8."
                    }
                    "Medium"
                }
                SensorManager.SENSOR_STATUS_ACCURACY_LOW -> "Low"
                else -> "Unreliable"
            }
            accuracyLabel.value = label
        }
    }

    private fun isAligned(heading: Float, qibla: Float): Boolean {
        var diff = abs(heading - qibla)
        if (diff > 180f) diff = 360f - diff
        val threshold = if (isAlignedState.value) 7f else 5f
        return diff <= threshold
    }

    private fun handleFeedback(isAligned: Boolean) {
        if (isAligned && !wasAligned) {
            if (vibrationEnabled.value) startVibration()
            if (spokenEnabled.value) speak("This is Qibla")
        } else if (!isAligned && wasAligned) {
            if (vibrationEnabled.value) stopVibration()
        }
        wasAligned = isAligned
    }

    private fun startVibration() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val effect = VibrationEffect.createWaveform(
                longArrayOf(0, 400, 100), intArrayOf(0, 255, 0), 0
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val attrs = android.os.VibrationAttributes.Builder()
                    .setUsage(android.os.VibrationAttributes.USAGE_ALARM)
                    .build()
                vibrator?.vibrate(effect, attrs)
            } else {
                vibrator?.vibrate(effect)
            }
        } else {
            @Suppress("DEPRECATION")
            vibrator?.vibrate(longArrayOf(0, 400, 100), 0)
        }
    }

    private fun stopVibration() {
        vibrator?.cancel()
    }

    fun speak(message: String) {
        if (ttsReady) {
            tts?.speak(
                message,
                TextToSpeech.QUEUE_FLUSH,
                null,
                message.hashCode().toString()
            )
        }
    }

    override fun onDestroy() {
        tts?.shutdown()
        stopVibration()
        super.onDestroy()
    }
}

// ============ UPDATE DIALOG ============

@Composable
fun UpdateDialog(
    version: String,
    releaseNotes: String,
    isDownloading: Boolean,
    onDownload: () -> Unit,
    onCancel: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { onCancel() },
        title = {
            Text(
                text = "Update Available",
                fontSize = 22.sp,
                modifier = Modifier.semantics { heading() }
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Version $version is available.",
                    fontSize = 16.sp,
                    modifier = Modifier.semantics {
                        contentDescription = "Version $version is available."
                    }
                )
                Text(
                    text = releaseNotes,
                    fontSize = 14.sp
                )
                if (isDownloading) {
                    Text(
                        text = "Downloading update. Please wait.",
                        fontSize = 16.sp,
                        modifier = Modifier.semantics {
                            liveRegion = LiveRegionMode.Assertive
                            contentDescription = "Downloading update. Please wait."
                        }
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDownload,
                enabled = !isDownloading,
                modifier = Modifier.semantics {
                    contentDescription =
                        "Download and install version $version. Double tap to download."
                }
            ) {
                Text("Download Update", fontSize = 16.sp)
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onCancel,
                modifier = Modifier.semantics {
                    contentDescription =
                        "Cancel. Stay on current version. Double tap to cancel."
                }
            ) {
                Text("Cancel", fontSize = 16.sp)
            }
        }
    )
}

// ============ QIBLA SCREEN ============

@Composable
fun QiblaScreen(
    headingState: MutableState<Float>,
    qiblaState: MutableState<Float>,
    isAlignedState: MutableState<Boolean>,
    statusMessage: MutableState<String>,
    calibrationDone: MutableState<Boolean>,
    accuracyLabel: MutableState<String>,
    prayerTimes: MutableState<List<Pair<String, String>>>,
    userLat: Double,
    userLon: Double,
    onNavigateToSettings: () -> Unit,
    onRecalibrate: () -> Unit
) {
    val heading by headingState
    val qibla by qiblaState
    val isAligned by isAlignedState
    val status by statusMessage
    val accuracy by accuracyLabel
    val calibrated by calibrationDone
    val prayers by prayerTimes

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Qibla Finder",
            fontSize = 32.sp,
            modifier = Modifier.semantics { heading() }
        )

        Text(
            text = status,
            fontSize = 18.sp,
            modifier = Modifier.semantics {
                liveRegion = LiveRegionMode.Polite
                contentDescription = status
            }
        )

        if (calibrated) {
            Text(
                text = if (isAligned) "YOU ARE FACING QIBLA"
                else "Searching for Qibla...",
                fontSize = 24.sp,
                modifier = Modifier.semantics {
                    liveRegion = LiveRegionMode.Polite
                    contentDescription = if (isAligned)
                        "You are now facing Qibla direction"
                    else
                        "Still searching. Slowly rotate your phone."
                }
            )

            Text(
                text = "Your direction: ${heading.toInt()}°",
                fontSize = 16.sp,
                modifier = Modifier.semantics {
                    contentDescription =
                        "Your phone is pointing ${heading.toInt()} degrees"
                }
            )

            Text(
                text = "Qibla direction: ${qibla.toInt()}°",
                fontSize = 16.sp,
                modifier = Modifier.semantics {
                    contentDescription =
                        "Qibla is at ${qibla.toInt()} degrees from North"
                }
            )
        }

        Text(
            text = "Compass accuracy: $accuracy",
            fontSize = 16.sp,
            modifier = Modifier.semantics {
                contentDescription = "Compass accuracy is $accuracy"
            }
        )

        if (prayers.isNotEmpty()) {
            Divider()

            val context = LocalContext.current
            var currentTime by remember { mutableStateOf(System.currentTimeMillis()) }
            LaunchedEffect(Unit) {
                while (true) {
                    kotlinx.coroutines.delay(60000 - (System.currentTimeMillis() % 60000))
                    currentTime = System.currentTimeMillis()
                }
            }

            val countdownText = remember(currentTime, prayers, userLat, userLon) {
                PrayerScheduler.getCountdownText(context, userLat, userLon, currentTime)
            }

            Text(
                text = countdownText,
                fontSize = 18.sp,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics {
                        liveRegion = LiveRegionMode.Polite
                        contentDescription = countdownText
                    }
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Today's Prayer Times",
                fontSize = 20.sp,
                modifier = Modifier.semantics { heading() }
            )
            prayers.forEach { (name, time) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics(mergeDescendants = true) {
                            contentDescription = "$name prayer at $time"
                        },
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = name, fontSize = 16.sp)
                    Text(text = time, fontSize = 16.sp)
                }
            }
        }

        Divider()

        Button(
            onClick = onRecalibrate,
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .semantics {
                    contentDescription =
                        "Recalibrate compass. Double tap to start."
                }
        ) {
            Text("Recalibrate Compass", fontSize = 18.sp)
        }

        Button(
            onClick = onNavigateToSettings,
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .semantics {
                    contentDescription = "Open Settings. Double tap to open."
                }
        ) {
            Text("Settings", fontSize = 18.sp)
        }
    }
}

// ============ SETTINGS SCREEN ============

@Composable
fun SettingsScreen(
    vibrationEnabled: MutableState<Boolean>,
    spokenEnabled: MutableState<Boolean>,
    prayerNotificationsEnabled: MutableState<Boolean>,
    jamaatReminderEnabled: MutableState<Boolean>,
    selectedLanguage: MutableState<String>,
    selectedCalculationMethod: MutableState<String>,
    asrHanafi: MutableState<Boolean>,
    manualAdjustment: MutableState<Int>,
    selectedTheme: MutableState<String>,
    onVibrationToggle: (Boolean) -> Unit,
    onSpokenToggle: (Boolean) -> Unit,
    onPrayerNotificationsToggle: (Boolean) -> Unit,
    onJamaatReminderToggle: (Boolean) -> Unit,
    onLanguageChange: (String) -> Unit,
    onCalculationMethodChange: (String) -> Unit,
    onAsrHanafiToggle: (Boolean) -> Unit,
    onManualAdjustmentChange: (Int) -> Unit,
    onThemeChange: (String) -> Unit,
    onCheckForUpdates: () -> Unit,
    onNavigateBack: () -> Unit
) {
    val formatAdjustment = { adj: Int ->
        when {
            adj < 0 -> "${abs(adj)} minutes early"
            adj > 0 -> "$adj minutes late"
            else -> "No adjustment"
        }
    }

    val languageOptions = listOf(
        Pair("English", "english"),
        Pair("Malayalam", "malayalam"),
        Pair("العربية (Arabic)", "arabic")
    )
    val currentLanguageDisplay = when (selectedLanguage.value) {
        "english" -> "English"
        "malayalam" -> "Malayalam"
        "arabic" -> "العربية (Arabic)"
        else -> selectedLanguage.value
    }

    val methodOptions = listOf(
        Pair("Moon Sighting Committee (Default)", "MOON_SIGHTING_COMMITTEE"),
        Pair("Muslim World League", "MUSLIM_WORLD_LEAGUE"),
        Pair("Egyptian General Authority", "EGYPTIAN"),
        Pair("University of Islamic Sciences Karachi", "KARACHI"),
        Pair("Umm Al-Qura University Makkah", "UMM_AL_QURA")
    )
    val currentMethodDisplay = when (selectedCalculationMethod.value) {
        "MOON_SIGHTING_COMMITTEE" -> "Moon Sighting Committee (Default)"
        "MUSLIM_WORLD_LEAGUE" -> "Muslim World League"
        "EGYPTIAN" -> "Egyptian General Authority"
        "KARACHI" -> "University of Islamic Sciences Karachi"
        "UMM_AL_QURA" -> "Umm Al-Qura University Makkah"
        else -> selectedCalculationMethod.value
    }

    val themeOptions = listOf(
        Pair("System Default", "system"),
        Pair("Light Mode", "light"),
        Pair("Dark Mode", "dark")
    )
    val currentThemeDisplay = when (selectedTheme.value) {
        "system" -> "System Default"
        "light" -> "Light Mode"
        "dark" -> "Dark Mode"
        else -> selectedTheme.value
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Settings",
            fontSize = 32.sp,
            modifier = Modifier.semantics { heading() }
        )

        Text(
            text = "Qibla Feedback",
            fontSize = 22.sp,
            modifier = Modifier.semantics { heading() }
        )

        AccessibleSwitch(
            label = "Vibration Feedback",
            description = "Vibrate when facing Qibla direction",
            checked = vibrationEnabled.value,
            onCheckedChange = onVibrationToggle
        )

        AccessibleSwitch(
            label = "Spoken Feedback",
            description = "Say This is Qibla when aligned",
            checked = spokenEnabled.value,
            onCheckedChange = onSpokenToggle
        )

        Divider()

        Text(
            text = "Prayer Time Notifications",
            fontSize = 22.sp,
            modifier = Modifier.semantics { heading() }
        )

        AccessibleSwitch(
            label = "Prayer Notifications",
            description = "Announce each prayer time",
            checked = prayerNotificationsEnabled.value,
            onCheckedChange = onPrayerNotificationsToggle
        )

        AccessibleSwitch(
            label = "Jamaat Reminder",
            description = "Second reminder before congregation",
            checked = jamaatReminderEnabled.value,
            onCheckedChange = onJamaatReminderToggle
        )

        Divider()

        AccessibleDropdown(
            label = "Announcement Language",
            selectedValue = currentLanguageDisplay,
            options = languageOptions,
            onValueChange = onLanguageChange
        )

        Divider()

        AccessibleDropdown(
            label = "Prayer Calculation Method",
            selectedValue = currentMethodDisplay,
            options = methodOptions,
            onValueChange = onCalculationMethodChange
        )

        Divider()

        Text(
            text = "Asr Prayer School (Madhab)",
            fontSize = 22.sp,
            modifier = Modifier.semantics { heading() }
        )

        AccessibleSwitch(
            label = "Hanafi School for Asr",
            description = "Enable for Hanafi school, disable for Shafi (standard) school",
            checked = asrHanafi.value,
            onCheckedChange = onAsrHanafiToggle
        )

        Divider()

        Text(
            text = "Manual Time Adjustment",
            fontSize = 22.sp,
            modifier = Modifier.semantics { heading() }
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val adj = manualAdjustment.value
            val formattedAdj = formatAdjustment(adj)

            Button(
                onClick = { if (adj > -30) onManualAdjustmentChange(adj - 1) },
                enabled = adj > -30,
                modifier = Modifier
                    .height(56.dp)
                    .width(110.dp)
                    .clearAndSetSemantics {
                        contentDescription = "Go back 1 minute. Current adjustment is $formattedAdj."
                    }
            ) {
                Text("-1 Min", fontSize = 16.sp)
            }

            Text(
                text = formattedAdj,
                fontSize = 16.sp,
                modifier = Modifier.clearAndSetSemantics {
                    contentDescription = "Current manual adjustment is $formattedAdj."
                }
            )

            Button(
                onClick = { if (adj < 30) onManualAdjustmentChange(adj + 1) },
                enabled = adj < 30,
                modifier = Modifier
                    .height(56.dp)
                    .width(110.dp)
                    .clearAndSetSemantics {
                        contentDescription = "Go forward 1 minute. Current adjustment is $formattedAdj."
                    }
            ) {
                Text("+1 Min", fontSize = 16.sp)
            }
        }

        Divider()

        AccessibleDropdown(
            label = "App Theme",
            selectedValue = currentThemeDisplay,
            options = themeOptions,
            onValueChange = onThemeChange
        )

        Divider()

        Button(
            onClick = onCheckForUpdates,
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .semantics {
                    contentDescription =
                        "Check for Updates. Double tap to check."
                }
        ) {
            Text("Check for Updates", fontSize = 18.sp)
        }

        Spacer(modifier = Modifier.height(8.dp))

        val context = LocalContext.current

        OutlinedButton(
            onClick = {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/hamsajaisal/QiblaFinder"))
                context.startActivity(intent)
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .semantics {
                    contentDescription =
                        "View Source Code on GitHub. Opens in browser. Double tap to open."
                }
        ) {
            Text("View Source Code (GitHub)", fontSize = 18.sp)
        }

        Spacer(modifier = Modifier.height(8.dp))

        Button(
            onClick = onNavigateBack,
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .semantics {
                    contentDescription =
                        "Back to Qibla screen. Double tap to go back."
                }
        ) {
            Text("Back to Qibla", fontSize = 18.sp)
        }
    }
}

// ============ ACCESSIBLE SWITCH ============

@Composable
fun AccessibleSwitch(
    label: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .clearAndSetSemantics {
                contentDescription = "$label. $description."
                stateDescription = if (checked) "on" else "off"
                role = Role.Switch
            },
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = label, fontSize = 18.sp)
            Text(text = description, fontSize = 13.sp)
        }
        Switch(
            checked = checked,
            onCheckedChange = null
        )
    }
}

// ============ ACCESSIBLE RADIO BUTTON ============

@Composable
fun AccessibleRadioButton(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .clearAndSetSemantics {
                contentDescription = label
                stateDescription = if (selected) "selected" else "not selected"
                role = Role.RadioButton
            },
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = selected,
            onClick = null
        )
        Text(
            text = label,
            fontSize = 18.sp
        )
    }
}

// ============ ACCESSIBLE DROPDOWN ============

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccessibleDropdown(
    label: String,
    selectedValue: String,
    options: List<Pair<String, String>>, // displayLabel to valueCode
    onValueChange: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val rotation by animateFloatAsState(if (expanded) 180f else 0f)

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor()
                .clickable { expanded = !expanded }
                .padding(vertical = 12.dp)
                .clearAndSetSemantics {
                    contentDescription = "$label. Current selection is $selectedValue. Double tap to change."
                    role = Role.Button
                },
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = label, fontSize = 18.sp, fontWeight = FontWeight.Medium)
                Text(text = selectedValue, fontSize = 14.sp, color = MaterialTheme.colorScheme.primary)
            }
            Text(
                text = "▼",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.rotate(rotation)
            )
        }

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            options.forEach { (display, valueCode) ->
                DropdownMenuItem(
                    text = { Text(text = display, fontSize = 16.sp) },
                    onClick = {
                        onValueChange(valueCode)
                        expanded = false
                    },
                    modifier = Modifier.clearAndSetSemantics {
                        contentDescription = "$display. ${if (selectedValue == display) "Selected" else "Not selected"}."
                        role = Role.Button
                    }
                )
            }
        }
    }
}