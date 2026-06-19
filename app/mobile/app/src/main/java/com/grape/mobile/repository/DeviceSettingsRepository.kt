package com.grape.mobile.repository

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.content.ContentValues
import com.grape.mobile.database.DatabaseHelper
import timber.log.Timber

class DeviceSettingsRepository(private val databaseHelper: DatabaseHelper) {
    private val dbPath: String
        get() = databaseHelper.getDatabasePath()

    fun saveLastConnectedAddress(address: String) {
        var db: SQLiteDatabase? = null
        try {
            db = SQLiteDatabase.openOrCreateDatabase(dbPath, null)
            val cv = ContentValues().apply {
                put("key", "last_connected_address")
                put("value", address)
            }
            db.insertWithOnConflict("device_settings", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
        } catch (e: Exception) {
            Timber.e(e, "Error saving connected address")
        } finally {
            db?.close()
        }
    }

    fun getLastConnectedAddress(): String? {
        var db: SQLiteDatabase? = null
        try {
            db = SQLiteDatabase.openOrCreateDatabase(dbPath, null)
            val cursor = db.rawQuery("SELECT value FROM device_settings WHERE key = 'last_connected_address'", null)
            val address = if (cursor.moveToFirst()) {
                cursor.getString(0)
            } else {
                null
            }
            cursor.close()
            return address
        } catch (e: Exception) {
            Timber.e(e, "Error retrieving connected address")
            return null
        } finally {
            db?.close()
        }
    }

    fun saveDeviceInfo(mac: String, key: String, value: String) {
        var db: SQLiteDatabase? = null
        try {
            db = SQLiteDatabase.openOrCreateDatabase(dbPath, null)
            val cursor = db.rawQuery("SELECT 1 FROM device_info WHERE mac = ?", arrayOf(mac))
            val exists = cursor.moveToFirst()
            cursor.close()
            
            val cv = ContentValues().apply {
                put(key, value)
                put("last_seen", System.currentTimeMillis())
            }
            
            if (exists) {
                db.update("device_info", cv, "mac = ?", arrayOf(mac))
            } else {
                cv.put("mac", mac)
                db.insert("device_info", null, cv)
            }
            Timber.d("Saved device info: $key = $value for MAC $mac")
        } catch (e: Exception) {
            Timber.e(e, "Error saving device info: $key")
        } finally {
            db?.close()
        }
    }

    fun getDeviceInfo(mac: String): DeviceInfo? {
        var db: SQLiteDatabase? = null
        try {
            db = SQLiteDatabase.openOrCreateDatabase(dbPath, null)
            val cursor = db.rawQuery("SELECT manufacturer, serial_number, hardware_revision, firmware_revision, battery_level, last_seen FROM device_info WHERE mac = ?", arrayOf(mac))
            val info = if (cursor.moveToFirst()) {
                DeviceInfo(
                    mac = mac,
                    manufacturer = cursor.getString(0),
                    serialNumber = cursor.getString(1),
                    hardwareRevision = cursor.getString(2),
                    firmwareRevision = cursor.getString(3),
                    batteryLevel = if (cursor.isNull(4)) null else cursor.getInt(4),
                    lastSeen = if (cursor.isNull(5)) null else cursor.getLong(5)
                )
            } else {
                null
            }
            cursor.close()
            return info
        } catch (e: Exception) {
            Timber.e(e, "Error retrieving device info")
            return null
        } finally {
            db?.close()
        }
    }
}

data class DeviceInfo(
    val mac: String,
    val manufacturer: String?,
    val serialNumber: String?,
    val hardwareRevision: String?,
    val firmwareRevision: String?,
    val batteryLevel: Int?,
    val lastSeen: Long?
)