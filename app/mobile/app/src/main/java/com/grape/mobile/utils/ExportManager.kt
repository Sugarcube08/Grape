package com.grape.mobile.utils

import android.content.Context
import android.content.Intent
import android.database.sqlite.SQLiteDatabase
import androidx.core.content.FileProvider
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object ExportManager {
    fun exportDataBundle(context: Context, dbPath: String): File? {
        return try {
            val exportDir = File(context.cacheDir, "exports")
            if (exportDir.exists()) exportDir.deleteRecursively()
            exportDir.mkdirs()

            // 1. Copy SQLite database
            val dbFile = File(dbPath)
            val dbExport = File(exportDir, "grape.sqlite")
            if (dbFile.exists()) {
                dbFile.inputStream().use { input ->
                    dbExport.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
            }

            // Open database to query JSONs
            var db: SQLiteDatabase? = null
            try {
                db = SQLiteDatabase.openDatabase(dbPath, null, SQLiteDatabase.OPEN_READONLY)

                // 2. Dump daily recovery metrics and sleep sessions -> metrics.json
                val metricsJson = JSONObject().apply {
                    put("daily_recovery_metrics", queryTableToJsonArray(db, "daily_recovery_metrics"))
                    put("external_sleep_sessions", queryTableToJsonArray(db, "external_sleep_sessions"))
                }
                File(exportDir, "metrics.json").writeText(metricsJson.toString(4))

                // 3. Dump baselines -> baselines.json
                val baselinesJson = JSONObject().apply {
                    put("baseline_daily", queryTableToJsonArray(db, "baseline_daily"))
                    put("baseline_30d", queryTableToJsonArray(db, "baseline_30d"))
                    put("baseline_90d", queryTableToJsonArray(db, "baseline_90d"))
                    put("baseline_lifetime", queryTableToJsonArray(db, "baseline_lifetime"))
                }
                File(exportDir, "baselines.json").writeText(baselinesJson.toString(4))

                // 4. Dump trends -> trends.json
                val trendsJson = JSONObject().apply {
                    put("trends_summary", queryTableToJsonArray(db, "baseline_daily"))
                }
                File(exportDir, "trends.json").writeText(trendsJson.toString(4))

                // 5. Dump insights -> insights.json
                val insightsJson = JSONObject().apply {
                    put("insights", queryTableToJsonArray(db, "insights"))
                }
                File(exportDir, "insights.json").writeText(insightsJson.toString(4))

                // 6. Dump device info -> device_info.json
                val deviceInfoJson = JSONObject().apply {
                    put("device_info", queryTableToJsonArray(db, "device_info"))
                    put("device_settings", queryTableToJsonArray(db, "device_settings"))
                }
                File(exportDir, "device_info.json").writeText(deviceInfoJson.toString(4))

            } catch (e: Exception) {
                Timber.e(e, "Error extracting JSON data from SQLite")
            } finally {
                db?.close()
            }

            // 7. Zip all files
            val zipFile = File(context.cacheDir, "grape_export_${System.currentTimeMillis()}.zip")
            ZipOutputStream(FileOutputStream(zipFile)).use { zos ->
                exportDir.listFiles()?.forEach { file ->
                    zos.putNextEntry(ZipEntry(file.name))
                    file.inputStream().use { input ->
                        input.copyTo(zos)
                    }
                    zos.closeEntry()
                }
            }

            // Clean up export directory
            exportDir.deleteRecursively()
            zipFile
        } catch (e: Exception) {
            Timber.e(e, "Error creating export bundle")
            null
        }
    }

    private fun queryTableToJsonArray(db: SQLiteDatabase, tableName: String): JSONArray {
        val jsonArray = JSONArray()
        var cursor: android.database.Cursor? = null
        try {
            cursor = db.rawQuery("SELECT * FROM $tableName", null)
            if (cursor != null && cursor.moveToFirst()) {
                val columnNames = cursor.columnNames
                do {
                    val row = JSONObject()
                    for (i in 0 until cursor.columnCount) {
                        val colName = columnNames[i]
                        when (cursor.getType(i)) {
                            android.database.Cursor.FIELD_TYPE_INTEGER -> row.put(colName, cursor.getLong(i))
                            android.database.Cursor.FIELD_TYPE_FLOAT -> row.put(colName, cursor.getDouble(i))
                            android.database.Cursor.FIELD_TYPE_STRING -> row.put(colName, cursor.getString(i))
                            android.database.Cursor.FIELD_TYPE_NULL -> row.put(colName, JSONObject.NULL)
                            android.database.Cursor.FIELD_TYPE_BLOB -> row.put(colName, "[BLOB]")
                        }
                    }
                    jsonArray.put(row)
                } while (cursor.moveToNext())
            }
        } catch (e: Exception) {
            Timber.e(e, "Error querying table $tableName to JSON")
        } finally {
            cursor?.close()
        }
        return jsonArray
    }

    fun shareExportFile(context: Context, file: File) {
        try {
            val authority = "${context.packageName}.fileprovider"
            val uri = FileProvider.getUriForFile(context, authority, file)
            
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/zip"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "Grape Wearable Data Export")
                putExtra(Intent.EXTRA_TEXT, "Here is your exported Grape wearable data bundle.")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            
            val chooser = Intent.createChooser(intent, "Share Wearable Data Export").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
        } catch (e: Exception) {
            Timber.e(e, "Error sharing export file")
        }
    }
}
