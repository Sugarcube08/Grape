package com.grape.mobile.database

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import java.io.File
import timber.log.Timber

class DatabaseHelper(private val context: Context) {

    init {
        initializeSchema()
    }

    fun getDatabasePath(): String {
        val databaseFile = File(context.filesDir, "grape.sqlite")
        databaseFile.parentFile?.mkdirs()
        return databaseFile.absolutePath
    }

    private fun initializeSchema() {
        val dbPath = getDatabasePath()
        var db: SQLiteDatabase? = null
        try {
            db = SQLiteDatabase.openOrCreateDatabase(dbPath, null)
            
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS sync_sessions (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    session_id TEXT UNIQUE,
                    status TEXT NOT NULL,
                    bytes_downloaded INTEGER DEFAULT 0,
                    packets_downloaded INTEGER DEFAULT 0,
                    started_at TEXT,
                    ended_at TEXT
                );
            """.trimIndent())

            db.execSQL("""
                CREATE TABLE IF NOT EXISTS sync_progress (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    session_id TEXT,
                    checkpoint_key TEXT,
                    checkpoint_value TEXT,
                    UNIQUE(session_id, checkpoint_key)
                );
            """.trimIndent())

            db.execSQL("""
                CREATE TABLE IF NOT EXISTS historical_packets (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    session_id TEXT,
                    packet_type TEXT,
                    timestamp INTEGER,
                    source TEXT,
                    frame_hex TEXT
                );
            """.trimIndent())

            db.execSQL("""
                CREATE TABLE IF NOT EXISTS device_settings (
                    key TEXT PRIMARY KEY,
                    value TEXT
                );
            """.trimIndent())

            db.execSQL("""
                CREATE TABLE IF NOT EXISTS companion_devices (
                    id INTEGER PRIMARY KEY,
                    mac TEXT UNIQUE,
                    name TEXT,
                    association_id INTEGER,
                    paired_at INTEGER,
                    last_seen INTEGER
                );
            """.trimIndent())

            db.execSQL("""
                CREATE TABLE IF NOT EXISTS device_info (
                    mac TEXT PRIMARY KEY,
                    manufacturer TEXT,
                    serial_number TEXT,
                    hardware_revision TEXT,
                    firmware_revision TEXT,
                    battery_level INTEGER,
                    last_seen INTEGER
                );
            """.trimIndent())

            db.execSQL("""
                CREATE TABLE IF NOT EXISTS baseline_daily (
                    date_key TEXT PRIMARY KEY,
                    recovery_score REAL,
                    sleep_duration_ms INTEGER,
                    sleep_score REAL,
                    hrv_rmssd REAL,
                    stress_average REAL,
                    strain_average REAL,
                    resting_hr REAL,
                    updated_at INTEGER
                );
            """.trimIndent())

            db.execSQL("""
                CREATE TABLE IF NOT EXISTS baseline_30d (
                    metric_type TEXT PRIMARY KEY,
                    mean REAL,
                    std_dev REAL,
                    min_val REAL,
                    max_val REAL,
                    data_points INTEGER,
                    updated_at INTEGER
                );
            """.trimIndent())

            db.execSQL("""
                CREATE TABLE IF NOT EXISTS baseline_90d (
                    metric_type TEXT PRIMARY KEY,
                    mean REAL,
                    std_dev REAL,
                    min_val REAL,
                    max_val REAL,
                    data_points INTEGER,
                    updated_at INTEGER
                );
            """.trimIndent())

            db.execSQL("""
                CREATE TABLE IF NOT EXISTS baseline_lifetime (
                    metric_type TEXT PRIMARY KEY,
                    mean REAL,
                    data_points INTEGER,
                    updated_at INTEGER
                );
            """.trimIndent())

            db.execSQL("""
                CREATE TABLE IF NOT EXISTS insights (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    message TEXT,
                    confidence REAL,
                    importance REAL,
                    category TEXT,
                    timestamp INTEGER
                );
            """.trimIndent())

            db.execSQL("""
                CREATE TABLE IF NOT EXISTS daily_stress_metrics (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    stress_score REAL,
                    state TEXT,
                    hrv_contribution REAL,
                    temp_contribution REAL,
                    timestamp INTEGER
                );
            """.trimIndent())

            db.execSQL("""
                CREATE TABLE IF NOT EXISTS user_profile (
                    id INTEGER PRIMARY KEY,
                    display_name TEXT,
                    avatar_uri TEXT,
                    created_at INTEGER
                );
            """.trimIndent())
            
            Timber.d("Local SQLite schema initialized successfully.")
        } catch (e: Exception) {
            Timber.e(e, "Error initializing local SQLite database schema")
        } finally {
            db?.close()
        }
    }
}
