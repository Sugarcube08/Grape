package com.grape.mobile.provider

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import com.grape.mobile.database.DatabaseHelper
import android.content.Context

object DebugDataProvider {
    private const val TAG = "DebugDataProvider"

    fun insertMockSleepSession(
        context: Context,
        startTimeUnixMs: Long,
        endTimeUnixMs: Long,
        remMinutes: Double,
        deepMinutes: Double,
        coreMinutes: Double,
        awakeMinutes: Double
    ) {
        val dbHelper = DatabaseHelper(context)
        val dbPath = dbHelper.getDatabasePath()
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
            Log.d(TAG, "Inserted mock sleep session: $sleepId")
        } catch (e: Exception) {
            Log.e(TAG, "Error inserting mock sleep session", e)
        } finally {
            db?.close()
        }
    }

    fun insertMockRecoveryMetric(
        context: Context,
        startTimeUnixMs: Long,
        endTimeUnixMs: Long,
        restingHr: Double,
        hrv: Double,
        tempDelta: Double
    ) {
        val dbHelper = DatabaseHelper(context)
        val dbPath = dbHelper.getDatabasePath()
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
            Log.d(TAG, "Inserted mock recovery metric: $metricId")
        } catch (e: Exception) {
            Log.e(TAG, "Error inserting mock recovery metric", e)
        } finally {
            db?.close()
        }
    }
}