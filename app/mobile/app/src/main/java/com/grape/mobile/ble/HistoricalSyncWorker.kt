package com.grape.mobile.ble

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.os.Build
import android.content.pm.ServiceInfo
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.grape.mobile.database.DatabaseHelper
import com.grape.mobile.ffi.GrapeRustBridge
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import timber.log.Timber
import java.util.UUID

data class ParsedFrameRow(
    val packetType: Int,
    val responseToCommand: Int?,
    val eventName: String?,
    val dataHex: String
)

class HistoricalSyncWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams), KoinComponent {

    private val bleManager: GrapeBleManager by inject()
    private val databaseHelper: DatabaseHelper by inject()

    override suspend fun doWork(): Result {
        val sessionId = inputData.getString("session_id") ?: UUID.randomUUID().toString()
        try {
            val dbPath = databaseHelper.getDatabasePath()
            
            setForegroundSafely("Preparing historical sync...")
            
            var oldestPage: Long = 0
            var newestPage: Long = 0
            var currentPage: Long = 0
            var status = "QueryRange"
            
            val checkpoints = loadCheckpoints(sessionId)
            if (checkpoints != null) {
                oldestPage = checkpoints.first
                newestPage = checkpoints.second
                currentPage = checkpoints.third
                Timber.d("Resuming sync session $sessionId from page $currentPage (Oldest: $oldestPage, Newest: $newestPage)")
                status = "ReadPointer"
            } else {
                // Initialize session in SQLite
                updateSession(sessionId, "QueryRange", 0, 0)
            }
            
            val incomingFlow = bleManager.incomingFrames
            
            if (status == "QueryRange") {
                // Send Data Range Command (opcode 34)
                Timber.d("Sending get_data_range command (34)")
                bleManager.writeCommand(34, byteArrayOf())
                
                var rangeResponseReceived = false
                withTimeoutOrNull(10000) {
                    incomingFlow.collect { frame ->
                        delay(50) // wait for DB insert to complete
                        val latestParsed = getLatestParsedFrame(dbPath)
                        if (latestParsed != null && latestParsed.responseToCommand == 34) {
                            val pages = parsePagesFromHex(latestParsed.dataHex)
                            if (pages != null) {
                                oldestPage = pages.first
                                newestPage = pages.second
                                currentPage = oldestPage
                                rangeResponseReceived = true
                                throw CancellationException("Range response received")
                            }
                        }
                    }
                }
                
                if (!rangeResponseReceived) {
                    Timber.e("Timeout waiting for data range response")
                    updateSession(sessionId, "Failed", 0, 0)
                    return Result.failure()
                }
            }
            
            Timber.d("Data range received: Oldest=$oldestPage, Newest=$newestPage")
            updateSessionProgress(sessionId, "oldest_page", oldestPage.toString())
            updateSessionProgress(sessionId, "newest_page", newestPage.toString())
            updateSessionProgress(sessionId, "current_page", currentPage.toString())
            
            // 3. Move pointer (opcode 33)
            status = "ReadPointer"
            updateSession(sessionId, status, 0, 0)
            Timber.d("Sending set_read_pointer command (33) to page $currentPage")
            val pageBytes = u32ToBytes(currentPage)
            bleManager.writeCommand(33, pageBytes)
            
            // Wait brief moment
            delay(100)

            // 4. Start downloading (opcode 22)
            status = "Downloading"
            updateSession(sessionId, status, 0, 0)
            setForegroundSafely("Downloading historical data (0%)...")
            Timber.d("Sending send_historical_data command (22)")
            bleManager.writeCommand(22, byteArrayOf())
            
            var packetsDownloaded = 0
            var bytesDownloaded = 0
            var syncCompleted = false
            
            try {
                withTimeoutOrNull(120000) {
                    incomingFlow.collect { frame ->
                        val classification = classifyPacket(frame)
                        val timestamp = extractTimestamp(frame)
                        
                        insertHistoricalPacketMetadata(sessionId, classification, timestamp, "historical", frame.toHexString())
                        
                        packetsDownloaded++
                        bytesDownloaded += frame.size
                        
                        updateSessionProgress(sessionId, "packets_downloaded", packetsDownloaded.toString())
                        updateSessionProgress(sessionId, "bytes_downloaded", bytesDownloaded.toString())
                        
                        if (packetsDownloaded % 10 == 0) {
                            val estimatedPageOffset = packetsDownloaded / 50
                            val updatedCurrentPage = (oldestPage + estimatedPageOffset).coerceAtMost(newestPage)
                            updateSessionProgress(sessionId, "current_page", updatedCurrentPage.toString())
                            
                            updateSession(sessionId, "Downloading", bytesDownloaded, packetsDownloaded)
                            val totalPages = (newestPage - oldestPage).coerceAtLeast(1)
                            val progressPercent = (packetsDownloaded * 100 / (totalPages * 50).coerceAtLeast(1)).coerceIn(0, 99)
                            setForegroundSafely("Downloading historical data ($progressPercent%)...")
                        }

                        delay(30)
                        val latestParsed = getLatestParsedFrame(dbPath)
                        if (latestParsed != null) {
                            if (latestParsed.eventName == "HistoryEnd" || latestParsed.eventName == "HistoryComplete") {
                                Timber.d("Sync completed event seen: ${latestParsed.eventName}")
                                syncCompleted = true
                                throw CancellationException("Sync finished")
                            }
                        }
                    }
                }
            } catch (e: CancellationException) {
                if (!syncCompleted) throw e
            }
            
            if (!syncCompleted) {
                Timber.e("Historical sync failed or timed out")
                updateSession(sessionId, "Failed", bytesDownloaded, packetsDownloaded)
                return Result.failure()
            }
            
            // Send Historical Data Result Ack (opcode 23)
            Timber.d("Acknowledging transmission success (opcode 23)")
            bleManager.writeCommand(23, byteArrayOf(0))
            
            // 5. Parsing & Persisting
            status = "Parsing"
            updateSession(sessionId, status, bytesDownloaded, packetsDownloaded)
            setForegroundSafely("Parsing and syncing data...")
            delay(500)
            
            status = "Persisting"
            updateSession(sessionId, status, bytesDownloaded, packetsDownloaded)
            setForegroundSafely("Persisting to database...")
            delay(500)
            
            // 6. Complete
            status = "Completed"
            updateSession(sessionId, status, bytesDownloaded, packetsDownloaded)
            updateSessionEnded(sessionId, status)
            setForegroundSafely("Historical sync completed successfully.")
            
            return Result.success()
            
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            Timber.e(e, "Error running historical sync")
        }
        
        updateSession(sessionId, "Failed", 0, 0)
        return Result.failure()
    }

    private suspend fun setForegroundSafely(progressText: String) {
        try {
            setForeground(createForegroundInfo(progressText))
        } catch (t: Throwable) {
            Timber.e(t, "Failed to set foreground service info: $progressText")
        }
    }

    private fun createForegroundInfo(progressText: String): ForegroundInfo {
        val channelId = "grape_sync_worker_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Grape Historical Sync"
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel(channelId, name, importance)
            val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(applicationContext, channelId)
            .setContentTitle("Grape Historical Sync")
            .setContentText(progressText)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOngoing(true)
            .build()

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(2, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
        } else {
            ForegroundInfo(2, notification)
        }
    }

    private fun getLatestParsedFrame(dbPath: String): ParsedFrameRow? {
        var db: SQLiteDatabase? = null
        var cursor: android.database.Cursor? = null
        try {
            db = SQLiteDatabase.openOrCreateDatabase(dbPath, null)
            cursor = db.rawQuery(
                "SELECT packet_type, parsed_payload_json FROM decoded_frames ORDER BY frame_id DESC LIMIT 1",
                null
            )
            if (cursor.moveToFirst()) {
                val packetType = cursor.getInt(0)
                val jsonStr = cursor.getString(1) ?: "{}"
                val json = org.json.JSONObject(jsonStr)
                val responseToCmd = if (json.has("response_to_command")) json.optInt("response_to_command", -1).takeIf { it != -1 } else null
                val eventName = if (json.has("event_name")) json.optString("event_name", "").takeIf { it.isNotEmpty() } else null
                val dataHex = if (json.has("data_hex")) json.optString("data_hex", "") else ""
                return ParsedFrameRow(packetType, responseToCmd, eventName, dataHex)
            }
        } catch (e: Throwable) {
            Timber.e(e, "Error reading latest parsed frame")
        } finally {
            cursor?.close()
            db?.close()
        }
        return null
    }

    private fun parsePagesFromHex(dataHex: String): Pair<Long, Long>? {
        val bytes = try {
            dataHex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        } catch (e: Throwable) {
            return null
        }
        if (bytes.size < 8) return null
        val oldest = readU32Le(bytes, 0)
        val newest = readU32Le(bytes, 4)
        return Pair(oldest, newest)
    }

    private fun u32ToBytes(value: Long): ByteArray {
        return byteArrayOf(
            (value and 0xFF).toByte(),
            ((value shl 8) and 0xFF).toByte(),
            ((value shl 16) and 0xFF).toByte(),
            ((value shl 24) and 0xFF).toByte()
        )
    }

    private fun classifyPacket(frame: ByteArray): String {
        if (frame.size < 4) return "Unknown"
        val headerLen = if (frame[1].toInt() == 0x01) 8 else 4
        if (frame.size <= headerLen) return "Unknown"
        val packetType = frame[headerLen].toInt() and 0xFF
        return when (packetType) {
            47, 52 -> "History"
            40, 43, 51, 53, 54 -> "Realtime"
            48 -> "Alarm"
            50, 55, 49, 56 -> "Debug"
            35, 36, 37, 38 -> {
                if (frame.size > headerLen + 2) {
                    val commandCode = frame[headerLen + 2].toInt() and 0xFF
                    when (commandCode) {
                        66, 67, 68, 69 -> "Alarm"
                        36, 37, 38 -> "Firmware"
                        else -> "Debug"
                    }
                } else {
                    "Debug"
                }
            }
            else -> "Debug"
        }
    }

    private fun extractTimestamp(frame: ByteArray): Long? {
        val headerLen = if (frame[1].toInt() == 0x01) 8 else 4
        if (frame.size <= headerLen) return null
        val packetType = frame[headerLen].toInt() and 0xFF
        return when (packetType) {
            48, 53, 54 -> readU32Le(frame, headerLen + 4)
            40, 43, 47, 51, 52 -> readU32Le(frame, headerLen + 7)
            else -> null
        }
    }

    private fun readU32Le(bytes: ByteArray, offset: Int): Long {
        if (offset + 4 > bytes.size) return 0
        return ((bytes[offset].toLong() and 0xFF) or
                ((bytes[offset + 1].toLong() and 0xFF) shl 8) or
                ((bytes[offset + 2].toLong() and 0xFF) shl 16) or
                ((bytes[offset + 3].toLong() and 0xFF) shl 24))
    }

    private fun updateSession(sessionId: String, status: String, bytesDownloaded: Int, packetsDownloaded: Int) {
        val dbPath = databaseHelper.getDatabasePath()
        var db: SQLiteDatabase? = null
        try {
            db = SQLiteDatabase.openOrCreateDatabase(dbPath, null)
            val cv = ContentValues().apply {
                put("session_id", sessionId)
                put("status", status)
                put("bytes_downloaded", bytesDownloaded)
                put("packets_downloaded", packetsDownloaded)
            }
            val exists = db.rawQuery("SELECT 1 FROM sync_sessions WHERE session_id = ?", arrayOf(sessionId)).use { it.moveToFirst() }
            if (exists) {
                db.update("sync_sessions", cv, "session_id = ?", arrayOf(sessionId))
            } else {
                cv.put("started_at", java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US).apply {
                    timeZone = java.util.TimeZone.getTimeZone("UTC")
                }.format(java.util.Date()))
                db.insert("sync_sessions", null, cv)
            }
        } catch (e: Throwable) {
            Timber.e(e, "Error updating sync session")
        } finally {
            db?.close()
        }
    }

    private fun updateSessionEnded(sessionId: String, status: String) {
        val dbPath = databaseHelper.getDatabasePath()
        var db: SQLiteDatabase? = null
        try {
            db = SQLiteDatabase.openOrCreateDatabase(dbPath, null)
            val cv = ContentValues().apply {
                put("status", status)
                put("ended_at", java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US).apply {
                    timeZone = java.util.TimeZone.getTimeZone("UTC")
                }.format(java.util.Date()))
            }
            db.update("sync_sessions", cv, "session_id = ?", arrayOf(sessionId))
        } catch (e: Throwable) {
            Timber.e(e, "Error updating sync session end")
        } finally {
            db?.close()
        }
    }

    private fun updateSessionProgress(sessionId: String, key: String, value: String) {
        val dbPath = databaseHelper.getDatabasePath()
        var db: SQLiteDatabase? = null
        try {
            db = SQLiteDatabase.openOrCreateDatabase(dbPath, null)
            val cv = ContentValues().apply {
                put("session_id", sessionId)
                put("checkpoint_key", key)
                put("checkpoint_value", value)
            }
            db.insertWithOnConflict("sync_progress", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
        } catch (e: Throwable) {
            Timber.e(e, "Error updating sync progress")
        } finally {
            db?.close()
        }
    }

    private fun insertHistoricalPacketMetadata(
        sessionId: String,
        packetType: String,
        timestamp: Long?,
        source: String,
        frameHex: String
    ) {
        val dbPath = databaseHelper.getDatabasePath()
        var db: SQLiteDatabase? = null
        try {
            db = SQLiteDatabase.openOrCreateDatabase(dbPath, null)
            val cv = ContentValues().apply {
                put("session_id", sessionId)
                put("packet_type", packetType)
                if (timestamp != null) put("timestamp", timestamp)
                put("source", source)
                put("frame_hex", frameHex)
            }
            db.insert("historical_packets", null, cv)
        } catch (e: Throwable) {
            Timber.e(e, "Error inserting historical packet metadata")
        } finally {
            db?.close()
        }
    }

    private fun loadCheckpoints(sessionId: String): Triple<Long, Long, Long>? {
        val dbPath = databaseHelper.getDatabasePath()
        var db: SQLiteDatabase? = null
        var cursor: android.database.Cursor? = null
        try {
            db = SQLiteDatabase.openOrCreateDatabase(dbPath, null)
            var oldest: Long? = null
            var newest: Long? = null
            var current: Long? = null
            cursor = db.rawQuery("SELECT checkpoint_key, checkpoint_value FROM sync_progress WHERE session_id = ?", arrayOf(sessionId))
            while (cursor.moveToNext()) {
                val key = cursor.getString(0)
                val value = cursor.getString(1)
                when (key) {
                    "oldest_page" -> oldest = value.toLongOrNull()
                    "newest_page" -> newest = value.toLongOrNull()
                    "current_page" -> current = value.toLongOrNull()
                }
            }
            if (oldest != null && newest != null && current != null) {
                return Triple(oldest, newest, current)
            }
        } catch (e: Throwable) {
            Timber.e(e, "Error loading checkpoints from DB")
        } finally {
            cursor?.close()
            db?.close()
        }
        return null
    }

    private fun ByteArray.toHexString() = joinToString("") { "%02x".format(it) }
}
