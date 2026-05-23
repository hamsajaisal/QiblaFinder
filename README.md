# 🕋 Qibla Finder

[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](https://www.gnu.org/licenses/gpl-3.0)
[![Platform](https://img.shields.io/badge/Platform-Android-green.svg)](https://developer.android.com)
[![Language](https://img.shields.io/badge/Language-Kotlin-orange.svg)](https://kotlinlang.org)
[![UI Framework](https://img.shields.io/badge/UI-Jetpack%20Compose-blueviolet.svg)](https://developer.android.com/jetpack/compose)
[![Version](https://img.shields.io/badge/Version-2.5-brightgreen.svg)](#)

A beautiful, battery-efficient, and fully accessible Android application designed to help users find the precise direction of the Qibla (Kaaba) and stay informed of daily prayer times. Built using modern Android architecture, Jetpack Compose, Kotlin, and high-performance sensor integrations.

---

## 🌟 Key Features

- **🧭 High-Precision Qibla Compass:** Leverages your device's accelerometer and geomagnetic sensors to calculate the exact direction of the Kaaba relative to your location.
- **🕌 Complete Prayer Times Calculator:** Displays accurate daily times for **Fajr, Dhuhr, Asr, Maghrib, Isha, and Midnight** based on astronomical calculations.
- **⚙️ Advanced Calculation Settings:**
  - Multiple calculation methods (Moon Sighting Committee, Muslim World League, Egyptian Authority, Karachi, Umm Al-Qura).
  - Juristic school selection for Asr prayer (Shafi/Standard vs. Hanafi).
  - Manual adjustment offsets (+/- 30 minutes) for custom fine-tuning.
- **⏳ bilingual Countdown Timer:** Dynamic countdown timer showing the time remaining for the next prayer, supported in both **English** and **Malayalam**.
- **📢 Automated Audio Announcements:** Optional spoken announcements of prayer times (Adhan & custom alerts), including complete support for Arabic announcements.
- **🔋 Battery & Heat Optimized:** Features advanced sensor management that registers listeners only when the app is active and foregrounded, eliminating background battery drain and device heating.
- **🎨 Modern Dark & Light Themes:** Premium UI style supports Light Mode, Dark Mode, and automatic system default integration.
- **♿ Fully Accessible (TalkBack):** Designed with full accessibility labels and semantic merge zones, thoroughly tested with Android TalkBack screen reader for visually impaired users.
- **🔄 Local In-App Updates:** Automatically checks for new versions/releases and prompts for a seamless in-app download and installation experience.

---

## 🛠️ Technology Stack

- **Language:** 100% Kotlin
- **UI Toolkit:** Jetpack Compose (Material 3 Components)
- **Asynchronous Flow:** Kotlin Coroutines & Flows
- **Sensors:** Android SensorManager (Accelerometer + Magnetic Field)
- **Location:** Google Play Services Fused Location Provider Client
- **Background Tasks:** Android AlarmManager (for exact prayer notifications)
- **Text-to-Speech:** Android TTS API

---

## 🚀 Getting Started (Developers)

To explore, modify, or build the app yourself, follow these quick steps:

### Prerequisites
- Android Studio Koala (or newer) installed on your system.
- Android SDK 34 or higher.

### Building & Running
1. **Clone the Repository:**
   ```bash
   git clone https://github.com/hamsajaisal/QiblaFinder.git
   ```
2. **Open the Project:**
   - Launch Android Studio.
   - Click `File -> Open` and select the cloned folder `QiblaApp`.
3. **Run the App:**
   - Connect an Android device with USB debugging enabled, or launch an Emulator.
   - Click the green **Run** button (`Shift + F10`) in Android Studio!

---

## 📂 Project Structure

```
QiblaApp/
├── app/                  # Main Android application module
│   ├── src/
│   │   └── main/
│   │       ├── java/     # Kotlin source code files (MainActivity, Receivers, UI)
│   │       └── res/      # Layout values, themes, launcher icons, drawables
│   └── build.gradle.kts  # App level gradle configuration
├── gradle/               # Gradle wrapper and configurations
├── build.gradle.kts      # Project level gradle configuration
└── settings.gradle.kts   # Project modules & dependency repositories list
```

---

## 📄 License

This program is free software: you can redistribute it and/or modify it under the terms of the **GNU General Public License v3.0** as published by the Free Software Foundation. See the [LICENSE](./LICENSE) file for the complete terms and conditions.

---

## 🤝 Contributing

We welcome contributions from the community! Feel free to:
- Open issues for bug reports or feature requests.
- Submit pull requests (PRs) with optimizations, bug fixes, or new features.

---

*Developed with 💖 for the open-source community.*
