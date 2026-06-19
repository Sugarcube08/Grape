package com.grape.mobile.repository

import android.content.Context
import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import androidx.work.*
import com.grape.mobile.database.DatabaseHelper
import com.grape.mobile.ffi.GrapeRustBridge
import com.grape.mobile.BuildConfig
import com.grape.mobile.ble.HistoricalSyncWorker
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import org.json.JSONObject
import timber.log.Timber

data class HistoricalSyncProgress(
    val sessionId: String,
    val status: String,
    val bytesDownloaded: Int,
    val packetsDownloaded: Int,
    val oldestPage: Long?,
    val newestPage: Long?,
    val currentPage: Long?
)

data class DeviceUiState(
    val connectionState: String = "DISCONNECTED",
    val heartRate: Int = 0,
    val batteryPercent: Int = 0,
    val packetsReceived: Int = 0,
    val framesParsed: Int = 0,
    val parserErrorCount: Int = 0
)

data class UserProfile(
    val displayName: String,
    val createdAt: Long
)

data class DbDiagnostics(
    val path: String,
    val size: String,
    val packets: Long,
    val sleepSessions: Long,
    val recoveryMetrics: Long
)

data class ConnectedDevice(
    val name: String,
    val status: String,
    val battery: Int,
    val lastSync: String
)

class DeviceRepository(private val databaseHelper: DatabaseHelper) {
    private val _uiState = MutableStateFlow(DeviceUiState())
    val uiState: StateFlow<DeviceUiState> = _uiState.asStateFlow()

    var parserErrorCount = 0
        private set

    init {
        // Delayed initialization: refreshState() is called from the UI (DashboardScreen) to prevent startup crash if FFI load fails.
    }

    private val dbPath: String
        get() = databaseHelper.getDatabasePath()

    fun updateConnectionState(status: String) {
        try {
            val json = GrapeRustBridge.insertPacket(dbPath, "CONN:$status", "puffin")
            parseAndApplyState(json)
        } catch (t: Throwable) {
            Timber.e(t, "Error calling Rust updateConnectionState")
        }
    }

    fun recordHeartRate(bpm: Int) {
        try {
            val json = GrapeRustBridge.insertPacket(dbPath, "HR:$bpm", "puffin")
            parseAndApplyState(json)
        } catch (t: Throwable) {
            Timber.e(t, "Error calling Rust recordHeartRate")
        }
    }

    fun recordBattery(percent: Int) {
        try {
            val json = GrapeRustBridge.insertPacket(dbPath, "BAT:$percent", "puffin")
            parseAndApplyState(json)
        } catch (t: Throwable) {
            Timber.e(t, "Error calling Rust recordBattery")
        }
    }

    fun insertWhoopPacket(frameHex: String, deviceType: String) {
        try {
            val json = GrapeRustBridge.insertPacket(dbPath, frameHex, deviceType)
            if (json.contains("\"error\":")) {
                parserErrorCount++
            }
            parseAndApplyState(json)
        } catch (t: Throwable) {
            parserErrorCount++
            Timber.e(t, "Error calling Rust insertWhoopPacket")
        }
    }

    fun refreshState() {
        try {
            val json = GrapeRustBridge.getDeviceState(dbPath)
            parseAndApplyState(json)
        } catch (t: Throwable) {
            Timber.e(t, "Failed to refresh device state from Rust")
        }
    }

    private fun parseAndApplyState(jsonString: String) {
        try {
            val json = JSONObject(jsonString)
            val state = DeviceUiState(
                connectionState = json.optString("connection_state", "DISCONNECTED"),
                heartRate = json.optInt("heart_rate", 0),
                batteryPercent = json.optInt("battery", 0),
                packetsReceived = json.optInt("packets_received", 0),
                framesParsed = json.optInt("frames_parsed", 0),
                parserErrorCount = parserErrorCount
            )
            _uiState.value = state
            Timber.d("Device uiState updated: $state")
        } catch (e: Exception) {
            Timber.e(e, "Error parsing Rust state JSON: $jsonString")
        }
    }

    fun beginHistoricalSync(context: Context, sessionId: String) {
        try {
            val workRequest = OneTimeWorkRequestBuilder<HistoricalSyncWorker>()
                .setInputData(workDataOf("session_id" to sessionId))
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                "historical_sync",
                ExistingWorkPolicy.REPLACE,
                workRequest
            )
        } catch (t: Throwable) {
            Timber.e(t, "Error calling WorkManager.enqueueUniqueWork")
        }
    }

    fun abortHistoricalSync(context: Context) {
        try {
            WorkManager.getInstance(context).cancelUniqueWork("historical_sync")
        } catch (t: Throwable) {
            Timber.e(t, "Error calling WorkManager.cancelUniqueWork")
        }
    }

    fun resumeHistoricalSync(context: Context, sessionId: String) {
        beginHistoricalSync(context, sessionId)
    }

    fun queryHistoricalProgress(sessionId: String): Flow<HistoricalSyncProgress?> = flow {
        while (true) {
            emit(getHistoricalProgressFromDb(sessionId))
            delay(500)
        }
    }

    private fun getHistoricalProgressFromDb(sessionId: String): HistoricalSyncProgress? {
        var db: SQLiteDatabase? = null
        try {
            db = SQLiteDatabase.openOrCreateDatabase(dbPath, null)
            var status = "Idle"
            var bytesDownloaded = 0
            var packetsDownloaded = 0
            db.rawQuery("SELECT status, bytes_downloaded, packets_downloaded FROM sync_sessions WHERE session_id = ?", arrayOf(sessionId)).use { cursor ->
                if (cursor.moveToFirst()) {
                    status = cursor.getString(0)
                    bytesDownloaded = cursor.getInt(1)
                    packetsDownloaded = cursor.getInt(2)
                } else {
                    return null
                }
            }

            var oldestPage: Long? = null
            var newestPage: Long? = null
            var currentPage: Long? = null
            db.rawQuery("SELECT checkpoint_key, checkpoint_value FROM sync_progress WHERE session_id = ?", arrayOf(sessionId)).use { cursor ->
                while (cursor.moveToNext()) {
                    val key = cursor.getString(0)
                    val value = cursor.getString(1)
                    when (key) {
                        "oldest_page" -> oldestPage = value.toLongOrNull()
                        "newest_page" -> newestPage = value.toLongOrNull()
                        "current_page" -> currentPage = value.toLongOrNull()
                    }
                }
            }

            return HistoricalSyncProgress(
                sessionId, status, bytesDownloaded, packetsDownloaded, oldestPage, newestPage, currentPage
            )
        } catch (e: Exception) {
            Timber.e(e, "Error querying progress from DB")
            return null
        } finally {
            db?.close()
        }
    }

    fun insertMockSleepSession(
        startTimeUnixMs: Long,
        endTimeUnixMs: Long,
        remMinutes: Double,
        deepMinutes: Double,
        coreMinutes: Double,
        awakeMinutes: Double
    ) {
        if (!BuildConfig.DEBUG) {
            Timber.w("Rejecting mock sleep session insertion in release mode")
            return
        }
        var db: SQLiteDatabase? = null
        try {
            db = SQLiteDatabase.openOrCreateDatabase(dbPath, null)
            val sleepId = "mock-sleep-" + System.currentTimeMillis()
            val cv = ContentValues().apply {
                put("sleep_id", sleepId)
                put("source", "Health Connect")
                put("platform", "health_connect")
                put("platform_record_id", "hc-" + sleepId)
                put("start_time_unix_ms", startTimeUnixMs)
                put("end_time_unix_ms", endTimeUnixMs)
                put("duration_ms", endTimeUnixMs - startTimeUnixMs)
                put("timezone", "UTC")
                put("confidence", 0.95)
                put("stage_summary_json", """
                    {
                        "minutes_by_stage": {
                            "rem": $remMinutes,
                            "deep": $deepMinutes,
                            "core": $coreMinutes,
                            "awake": $awakeMinutes
                        }
                    }
                """.trimIndent())
                put("provenance_json", "{}")
            }
            db.insert("external_sleep_sessions", null, cv)
            Timber.d("Inserted mock sleep session: $sleepId")
        } catch (e: Exception) {
            Timber.e(e, "Error inserting mock sleep session")
        } finally {
            db?.close()
        }
    }

    fun insertMockRecoveryMetric(
        startTimeUnixMs: Long,
        endTimeUnixMs: Long,
        restingHr: Double,
        hrv: Double,
        tempDelta: Double
    ) {
        if (!BuildConfig.DEBUG) {
            Timber.w("Rejecting mock recovery metric insertion in release mode")
            return
        }
        var db: SQLiteDatabase? = null
        try {
            db = SQLiteDatabase.openOrCreateDatabase(dbPath, null)
            val metricId = "mock-metric-" + System.currentTimeMillis()
            val cv = ContentValues().apply {
                put("daily_metric_id", metricId)
                put("date_key", java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).apply {
                    timeZone = java.util.TimeZone.getTimeZone("UTC")
                }.format(java.util.Date(startTimeUnixMs)))
                put("timezone", "UTC")
                put("start_time_unix_ms", startTimeUnixMs)
                put("end_time_unix_ms", endTimeUnixMs)
                put("resting_hr_bpm", restingHr)
                put("hrv_rmssd_ms", hrv)
                put("respiratory_rate_rpm", 15.5)
                put("skin_temperature_delta_c", tempDelta)
                put("source_kind", "imported")
                put("confidence", 0.95)
                put("provenance_json", "{}")
            }
            db.insert("daily_recovery_metrics", null, cv)
            Timber.d("Inserted mock recovery metric: $metricId")
        } catch (e: Exception) {
            Timber.e(e, "Error inserting mock recovery metric")
        } finally {
            db?.close()
        }
    }

    fun insertMockStressMetric(
        stressScore: Double,
        state: String,
        hrvContribution: Double,
        tempContribution: Double,
        timestamp: Long
    ) {
        if (!BuildConfig.DEBUG) {
            Timber.w("Rejecting mock stress metric insertion in release mode")
            return
        }
        var db: SQLiteDatabase? = null
        try {
            db = SQLiteDatabase.openOrCreateDatabase(dbPath, null)
            val cv = ContentValues().apply {
                put("stress_score", stressScore)
                put("state", state)
                put("hrv_contribution", hrvContribution)
                put("temp_contribution", tempContribution)
                put("timestamp", timestamp)
            }
            db.insert("daily_stress_metrics", null, cv)
            Timber.d("Inserted mock stress metric: $stressScore ($state)")
        } catch (e: Exception) {
            Timber.e(e, "Error inserting mock stress metric")
        } finally {
            db?.close()
        }
    }

    fun getUserProfile(context: Context): UserProfile {
        var db: SQLiteDatabase? = null
        try {
            db = SQLiteDatabase.openOrCreateDatabase(dbPath, null)
            db.rawQuery("SELECT display_name, created_at FROM user_profile LIMIT 1", null).use { cursor ->
                if (cursor.moveToFirst()) {
                    val displayName = cursor.getString(0)
                    val createdAt = cursor.getLong(1)
                    return UserProfile(displayName, createdAt)
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Error querying user profile")
        } finally {
            db?.close()
        }

        // Priority 2: Device owner name / Bluetooth name
        val deviceName = try {
            android.provider.Settings.Global.getString(context.contentResolver, android.provider.Settings.Global.DEVICE_NAME)
                ?: (context.getSystemService(Context.BLUETOOTH_SERVICE) as? android.bluetooth.BluetoothManager)?.adapter?.name
        } catch (e: Exception) {
            null
        }

        val defaultName = deviceName ?: "Athlete"
        val defaultCreatedAt = 1780272000000L // Jun 2026
        return UserProfile(defaultName, defaultCreatedAt)
    }

    fun getRecoveryStreak(): Int {
        var db: SQLiteDatabase? = null
        try {
            db = SQLiteDatabase.openOrCreateDatabase(dbPath, null)
            db.rawQuery("SELECT resting_hr_bpm, hrv_rmssd_ms FROM daily_recovery_metrics ORDER BY start_time_unix_ms DESC", null).use { cursor ->
                var streak = 0
                while (cursor.moveToNext()) {
                    val restingHr = cursor.getDouble(0)
                    val hrv = cursor.getDouble(1)
                    val score = (70.0 + (hrv - 65.0) * 0.4 + (60.0 - restingHr) * 2.0).coerceIn(0.0, 100.0)
                    if (score >= 67.0) {
                        streak++
                    } else {
                        break
                    }
                }
                return streak
            }
        } catch (e: Exception) {
            Timber.e(e, "Error calculating recovery streak")
        } finally {
            db?.close()
        }
        return 0
    }

    fun getDbDiagnostics(context: Context): DbDiagnostics {
        val file = java.io.File(dbPath)
        val sizeStr = if (file.exists()) {
            android.text.format.Formatter.formatFileSize(context, file.length())
        } else {
            "0 B"
        }

        var packets = 0L
        var sleepSessions = 0L
        var recoveryMetrics = 0L

        var db: SQLiteDatabase? = null
        try {
            db = SQLiteDatabase.openOrCreateDatabase(dbPath, null)
            db.rawQuery("SELECT COUNT(*) FROM historical_packets", null).use { cursor ->
                if (cursor.moveToFirst()) packets = cursor.getLong(0)
            }
            db.rawQuery("SELECT COUNT(*) FROM external_sleep_sessions", null).use { cursor ->
                if (cursor.moveToFirst()) sleepSessions = cursor.getLong(0)
            }
            db.rawQuery("SELECT COUNT(*) FROM daily_recovery_metrics", null).use { cursor ->
                if (cursor.moveToFirst()) recoveryMetrics = cursor.getLong(0)
            }
        } catch (e: Exception) {
            Timber.e(e, "Error querying DB diagnostics")
        } finally {
            db?.close()
        }

        return DbDiagnostics(
            path = file.absolutePath,
            size = sizeStr,
            packets = packets,
            sleepSessions = sleepSessions,
            recoveryMetrics = recoveryMetrics
        )
    }

    fun connectedDevice(): ConnectedDevice? {
        var db: SQLiteDatabase? = null
        try {
            db = SQLiteDatabase.openOrCreateDatabase(dbPath, null)
            db.rawQuery("SELECT mac, name FROM companion_devices LIMIT 1", null).use { compCursor ->
                if (compCursor.moveToFirst()) {
                    val mac = compCursor.getString(0)
                    val compName = compCursor.getString(1) ?: "WHOOP Strap"

                    var battery = 0
                    var lastSeen = 0L
                    db.rawQuery("SELECT battery_level, last_seen FROM device_info WHERE mac = ?", arrayOf(mac)).use { infoCursor ->
                        if (infoCursor.moveToFirst()) {
                            battery = infoCursor.getInt(0)
                            lastSeen = infoCursor.getLong(1)
                        }
                    }

                    val connectionState = uiState.value.connectionState
                    val batteryState = if (battery > 0) battery else uiState.value.batteryPercent

                    val syncTimeText = if (lastSeen > 0) {
                        val diffMs = System.currentTimeMillis() - lastSeen
                        val diffMin = diffMs / 1000 / 60
                        if (diffMin < 1) "Just now" else "${diffMin}m ago"
                    } else {
                        "Never"
                    }

                    return ConnectedDevice(
                        name = compName,
                        status = connectionState,
                        battery = batteryState,
                        lastSync = syncTimeText
                    )
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Error querying connected companion device")
        } finally {
            db?.close()
        }
        return null
    }

    fun saveUserProfile(displayName: String) {
        var db: SQLiteDatabase? = null
        try {
            db = SQLiteDatabase.openOrCreateDatabase(dbPath, null)
            val cv = ContentValues().apply {
                put("id", 1)
                put("display_name", displayName)
                
                var exists = false
                db.rawQuery("SELECT created_at FROM user_profile WHERE id = 1", null).use { cursor ->
                    if (cursor.moveToFirst()) {
                        exists = true
                    }
                }
                if (!exists) {
                    put("created_at", System.currentTimeMillis())
                }
            }
            db.insertWithOnConflict("user_profile", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
            Timber.d("Saved user profile displayName: $displayName")
        } catch (e: Exception) {
            Timber.e(e, "Error saving user profile")
        } finally {
            db?.close()
        }
    }
}
