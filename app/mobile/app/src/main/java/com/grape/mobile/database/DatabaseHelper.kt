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
            
            Timber.d("Local SQLite schema initialized successfully.")
        } catch (e: Exception) {
            Timber.e(e, "Error initializing local SQLite database schema")
        } finally {
            db?.close()
        }
    }
}
