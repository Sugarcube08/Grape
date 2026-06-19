package com.grape.mobile.cdm

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.content.ContentValues
import com.grape.mobile.database.DatabaseHelper
import timber.log.Timber

class AssociationRepository(private val databaseHelper: DatabaseHelper) {
    private val dbPath: String
        get() = databaseHelper.getDatabasePath()

    fun insert(mac: String, name: String, associationId: Int) {
        var db: SQLiteDatabase? = null
        try {
            db = SQLiteDatabase.openOrCreateDatabase(dbPath, null)
            val cv = ContentValues().apply {
                put("mac", mac)
                put("name", name)
                put("association_id", associationId)
                put("paired_at", System.currentTimeMillis())
                put("last_seen", System.currentTimeMillis())
            }
            db.insertWithOnConflict("companion_devices", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
            Timber.d("Inserted/updated association for $mac ($name)")
        } catch (e: Exception) {
            Timber.e(e, "Error inserting association")
        } finally {
            db?.close()
        }
    }

    fun delete(mac: String) {
        var db: SQLiteDatabase? = null
        try {
            db = SQLiteDatabase.openOrCreateDatabase(dbPath, null)
            db.delete("companion_devices", "mac = ?", arrayOf(mac))
            Timber.d("Deleted association for mac: $mac")
        } catch (e: Exception) {
            Timber.e(e, "Error deleting association for mac: $mac")
        } finally {
            db?.close()
        }
    }

    fun getPrimary(): Pair<String, String>? {
        var db: SQLiteDatabase? = null
        try {
            db = SQLiteDatabase.openOrCreateDatabase(dbPath, null)
            val cursor = db.rawQuery("SELECT mac, name FROM companion_devices LIMIT 1", null)
            val result = if (cursor.moveToFirst()) {
                val mac = cursor.getString(0)
                val name = cursor.getString(1)
                Pair(mac, name)
            } else {
                null
            }
            cursor.close()
            return result
        } catch (e: Exception) {
            Timber.e(e, "Error retrieving association")
            return null
        } finally {
            db?.close()
        }
    }

    fun updateLastSeen(mac: String) {
        var db: SQLiteDatabase? = null
        try {
            db = SQLiteDatabase.openOrCreateDatabase(dbPath, null)
            val cv = ContentValues().apply {
                put("last_seen", System.currentTimeMillis())
            }
            db.update("companion_devices", cv, "mac = ?", arrayOf(mac))
            Timber.d("Updated last_seen for mac: $mac")
        } catch (e: Exception) {
            Timber.e(e, "Error updating last_seen for mac: $mac")
        } finally {
            db?.close()
        }
    }

    fun clearAssociations() {
        var db: SQLiteDatabase? = null
        try {
            db = SQLiteDatabase.openOrCreateDatabase(dbPath, null)
            db.execSQL("DELETE FROM companion_devices")
            Timber.d("Cleared all associations")
        } catch (e: Exception) {
            Timber.e(e, "Error clearing associations")
        } finally {
            db?.close()
        }
    }
}