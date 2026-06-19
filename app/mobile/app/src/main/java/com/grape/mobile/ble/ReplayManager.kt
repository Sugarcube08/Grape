package com.grape.mobile.ble

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import com.grape.mobile.repository.DeviceRepository
import timber.log.Timber
import java.io.File
import java.io.FileNotFoundException

object ReplayManager {
    var isReplayActive: Boolean = false
        private set

    fun replayCaptureDatabase(context: Context, sourceFile: File, deviceRepository: DeviceRepository): Result<Int> {
        isReplayActive = true
        return try {
            if (!sourceFile.exists()) {
                isReplayActive = false
                return Result.failure(FileNotFoundException("Source file does not exist: ${sourceFile.absolutePath}"))
            }
            
            val db = SQLiteDatabase.openDatabase(sourceFile.absolutePath, null, SQLiteDatabase.OPEN_READONLY)
            val cursor = db.rawQuery("SELECT frame_hex, packet_type FROM historical_packets ORDER BY timestamp ASC", null)
            
            var count = 0
            if (cursor.moveToFirst()) {
                do {
                    val frameHex = cursor.getString(0)
                    val deviceType = cursor.getString(1) ?: "puffin"
                    
                    // Feed to device repository which calls GrapeRustBridge.insertPacket
                    deviceRepository.insertWhoopPacket(frameHex, deviceType)
                    count++
                } while (cursor.moveToNext())
            }
            cursor.close()
            db.close()
            
            // Refresh state to calculate and update trends, sleep, recovery, etc.
            deviceRepository.refreshState()
            
            Result.success(count)
        } catch (e: Exception) {
            Timber.e(e, "Error replaying capture database: ${e.message}")
            Result.failure(e)
        } finally {
            isReplayActive = false
        }
    }

    fun replayAssetDatabase(context: Context, assetName: String, deviceRepository: DeviceRepository): Result<Int> {
        return try {
            val tempFile = File(context.cacheDir, assetName)
            if (tempFile.exists()) tempFile.delete()
            
            context.assets.open(assetName).use { input ->
                tempFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            
            val result = replayCaptureDatabase(context, tempFile, deviceRepository)
            tempFile.delete() // clean up temp file
            result
        } catch (e: Exception) {
            Timber.e(e, "Error copying or replaying asset database: $assetName")
            Result.failure(e)
        }
    }
}
