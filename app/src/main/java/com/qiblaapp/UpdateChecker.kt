package com.qiblaapp

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Environment
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.io.File

object UpdateChecker {

    private const val GITHUB_API_URL =
        "https://api.github.com/repos/hamsajaisal/QiblaFinder/releases/latest"
    private const val CURRENT_VERSION = "2.5"

    data class UpdateInfo(
        val latestVersion: String,
        val downloadUrl: String,
        val releaseNotes: String,
        val isUpdateAvailable: Boolean
    )

    // Check GitHub for latest version
    // Returns null if no internet or no update available
    suspend fun checkForUpdate(): UpdateInfo? {
        return withContext(Dispatchers.IO) {
            try {
                val url = URL(GITHUB_API_URL)
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.setRequestProperty("Accept", "application/json")
                connection.connectTimeout = 5000
                connection.readTimeout = 5000

                if (connection.responseCode != 200) return@withContext null

                val response = BufferedReader(
                    InputStreamReader(connection.inputStream)
                ).use { it.readText() }

                val json = JSONObject(response)
                val latestVersion = json.getString("tag_name")
                    .removePrefix("v") // Remove "v" prefix from "v2.1"
                val releaseNotes = json.getString("body")

                // Get APK download URL from assets
                val assets = json.getJSONArray("assets")
                var downloadUrl = ""
                for (i in 0 until assets.length()) {
                    val asset = assets.getJSONObject(i)
                    val name = asset.getString("name")
                    if (name.endsWith(".apk")) {
                        downloadUrl = asset.getString("browser_download_url")
                        break
                    }
                }

                if (downloadUrl.isEmpty()) return@withContext null

                val isUpdateAvailable = isNewerVersion(latestVersion, CURRENT_VERSION)

                UpdateInfo(
                    latestVersion = latestVersion,
                    downloadUrl = downloadUrl,
                    releaseNotes = releaseNotes,
                    isUpdateAvailable = isUpdateAvailable
                )

            } catch (e: Exception) {
                // No internet or any error — silently return null
                null
            }
        }
    }

    // Compare version strings like "2.1" and "2.2"
    private fun isNewerVersion(latest: String, current: String): Boolean {
        return try {
            val latestParts = latest.split(".").map { it.toInt() }
            val currentParts = current.split(".").map { it.toInt() }
            for (i in 0 until maxOf(latestParts.size, currentParts.size)) {
                val l = latestParts.getOrElse(i) { 0 }
                val c = currentParts.getOrElse(i) { 0 }
                if (l > c) return true
                if (l < c) return false
            }
            false
        } catch (e: Exception) {
            false
        }
    }

    // Download the APK and install it
    fun downloadAndInstall(context: Context, downloadUrl: String, version: String) {
        val appContext = context.applicationContext
        val fileName = "QiblaFinder-v$version.apk"

        // Delete old downloaded file if exists
        val oldFile = File(
            appContext.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS),
            fileName
        )
        if (oldFile.exists()) oldFile.delete()

        val request = DownloadManager.Request(Uri.parse(downloadUrl)).apply {
            setTitle("Qibla Finder Update")
            setDescription("Downloading version $version")
            setNotificationVisibility(
                DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED
            )
            setDestinationInExternalFilesDir(
                appContext,
                Environment.DIRECTORY_DOWNLOADS,
                fileName
            )
        }

        val downloadManager = appContext.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val downloadId = downloadManager.enqueue(request)

        // Listen for download completion
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
                if (id == downloadId) {
                    appContext.unregisterReceiver(this)
                    // Install the downloaded APK
                    installApk(appContext, fileName)
                }
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            appContext.registerReceiver(
                receiver,
                IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
                Context.RECEIVER_EXPORTED
            )
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            appContext.registerReceiver(
                receiver,
                IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
            )
        }
    }

    private fun installApk(context: Context, fileName: String) {
        val file = File(
            context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS),
            fileName
        )
        if (!file.exists()) return

        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
        }
        context.startActivity(intent)
    }
}