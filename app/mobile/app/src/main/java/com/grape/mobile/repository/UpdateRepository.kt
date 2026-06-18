package com.grape.mobile.repository

import android.content.Context
import android.content.Intent
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import timber.log.Timber
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import com.grape.mobile.BuildConfig

data class VersionMetadata(
    val version: String,
    val build: Int,
    val mandatory: Boolean,
    val notes: List<String>
)

class UpdateRepository(private val context: Context) {

    private val sharedPrefs = context.getSharedPreferences("grape_update_prefs", Context.MODE_PRIVATE)

    suspend fun checkForUpdates(): VersionMetadata? = withContext(Dispatchers.IO) {
        val metadataUrl = "https://raw.githubusercontent.com/Sugarcube08/Grape/main/latest_version.json"
        var connection: HttpURLConnection? = null
        try {
            val url = URL(metadataUrl)
            connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 10000
            connection.readTimeout = 10000
            
            val responseCode = connection.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK) {
                val reader = BufferedReader(InputStreamReader(connection.inputStream))
                val response = StringBuilder()
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    response.append(line)
                }
                reader.close()

                val json = JSONObject(response.toString())
                val version = json.getString("version")
                val build = json.getInt("build")
                val mandatory = json.optBoolean("mandatory", false)
                val notesArray = json.optJSONArray("notes")
                val notes = mutableListOf<String>()
                if (notesArray != null) {
                    for (i in 0 until notesArray.length()) {
                        notes.add(notesArray.getString(i))
                    }
                }
                
                val meta = VersionMetadata(version, build, mandatory, notes)
                Timber.d("Fetched latest version metadata: $meta")
                return@withContext meta
            } else {
                Timber.w("Failed to fetch version metadata. HTTP response code: $responseCode")
            }
        } catch (e: Exception) {
            Timber.e(e, "Error checking for updates")
        } finally {
            connection?.disconnect()
        }
        return@withContext null
    }

    fun compareVersions(latestVersion: String, latestBuild: Int): Boolean {
        val currentBuild = BuildConfig.VERSION_CODE
        Timber.d("Comparing local build $currentBuild to latest build $latestBuild")
        
        if (currentBuild < latestBuild) {
            val dismissedVersion = sharedPrefs.getString("dismissed_version", "")
            if (dismissedVersion == latestVersion) {
                Timber.d("User previously dismissed version $latestVersion; skipping dialog.")
                return false
            }
            return true
        }
        return false
    }

    fun openReleasePage(version: String) {
        try {
            val url = "https://github.com/Sugarcube08/Grape/releases/tag/v$version"
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Timber.e(e, "Error opening release page")
        }
    }

    fun dismissVersion(version: String) {
        sharedPrefs.edit().putString("dismissed_version", version).apply()
        Timber.d("Dismissed update notification for version $version")
    }
}
