package com.grape.mobile.screens

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.grape.mobile.ble.BleState
import com.grape.mobile.ble.GrapeBleManager
import com.grape.mobile.repository.DeviceRepository
import timber.log.Timber
import java.util.UUID

@Composable
fun SettingsScreen(
    bleManager: GrapeBleManager,
    repository: DeviceRepository,
    onNavigateToAbout: () -> Unit
) {
    val context = LocalContext.current
    val bleState by bleManager.state.collectAsState()
    val uiState by repository.uiState.collectAsState()
    
    val actualDbPath = remember {
        val filesDir = context.filesDir
        java.io.File(filesDir, "grape.sqlite").absolutePath
    }

    var lastSyncTime by remember { mutableStateOf("Never") }
    var syncProgress by remember { mutableStateOf<String?>(null) }
    var currentSessionId by remember { mutableStateOf<String?>(null) }

    fun queryLastSync() {
        var db: SQLiteDatabase? = null
        try {
            db = SQLiteDatabase.openOrCreateDatabase(actualDbPath, null)
            db.rawQuery("SELECT started_at FROM sync_sessions WHERE status = 'Completed' ORDER BY started_at DESC LIMIT 1", null).use { cursor ->
                if (cursor.moveToFirst()) {
                    lastSyncTime = cursor.getString(0) ?: "Never"
                }
            }
        } catch (e: Exception) {
            Timber.e(e)
        } finally {
            db?.close()
        }
    }

    LaunchedEffect(Unit) {
        queryLastSync()
    }

    // Monitor sync progress if running
    LaunchedEffect(currentSessionId) {
        val sessionId = currentSessionId ?: return@LaunchedEffect
        repository.queryHistoricalProgress(sessionId).collect { progress ->
            if (progress != null) {
                syncProgress = "Sync Status: ${progress.status} (Packets: ${progress.packetsDownloaded})"
                if (progress.status == "Completed" || progress.status == "Failed") {
                    queryLastSync()
                    if (progress.status == "Completed") {
                        syncProgress = null
                        currentSessionId = null
                    }
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F0F12))
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Title
        Text(
            text = "DEVICE & SETTINGS",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF8A8A93),
            letterSpacing = 2.sp,
            modifier = Modifier.align(Alignment.Start)
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Device Info Card (UX 8.9)
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF16161A))
        ) {
            Column(
                modifier = Modifier.padding(20.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(imageVector = Icons.Default.Info, contentDescription = "Device Info", tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "DEVICE INFORMATION",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                InfoRow(label = "FIRMWARE", value = "WHOOP Strap 5.0 (v1.8.2)")
                InfoRow(label = "BATTERY", value = "${uiState.batteryPercent}%")
                InfoRow(label = "LAST SYNC", value = lastSyncTime)
                InfoRow(label = "PACKETS RECEIVED", value = "${uiState.packetsReceived}")
                InfoRow(label = "FRAMES PARSED", value = "${uiState.framesParsed}")
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Reconnect & Sync Management (UX 8.9)
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF16161A))
        ) {
            Column(
                modifier = Modifier.padding(20.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(imageVector = Icons.Default.Refresh, contentDescription = "Sync controls", tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "SYNC & CONNECTION",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                if (syncProgress != null) {
                    Text(
                        text = syncProgress!!,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.primary)
                } else {
                    Button(
                        onClick = {
                            val newSessionId = UUID.randomUUID().toString()
                            currentSessionId = newSessionId
                            repository.beginHistoricalSync(context, newSessionId)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("FORCE HISTORICAL SYNC", fontWeight = FontWeight.Bold)
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = {
                            bleManager.disconnect()
                            bleManager.scan()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2C2C35)),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("FORCE RECONNECT DEVICE", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Database Utilities (UX 8.9)
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF16161A))
        ) {
            Column(
                modifier = Modifier.padding(20.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(imageVector = Icons.Default.Build, contentDescription = "Database Admin", tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "DATABASE MAINTENANCE",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                Button(
                    onClick = {
                        try {
                            val dbFile = java.io.File(actualDbPath)
                            val destFile = java.io.File(context.getExternalFilesDir(null), "grape_export.sqlite")
                            dbFile.inputStream().use { input ->
                                destFile.outputStream().use { output ->
                                    input.copyTo(output)
                                }
                            }
                            Toast.makeText(context, "DB Exported: ${destFile.name}", Toast.LENGTH_LONG).show()
                        } catch (e: Exception) {
                            Toast.makeText(context, "Export Failed: ${e.message}", Toast.LENGTH_SHORT).show()
                            Timber.e(e, "DB export error")
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0D9488)),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("EXPORT DATABASE", fontWeight = FontWeight.Bold, color = Color.White)
                }

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = {
                        var db: SQLiteDatabase? = null
                        try {
                            db = SQLiteDatabase.openOrCreateDatabase(actualDbPath, null)
                            db.execSQL("DELETE FROM external_sleep_sessions")
                            db.execSQL("DELETE FROM external_sleep_stages")
                            db.execSQL("DELETE FROM daily_recovery_metrics")
                            db.execSQL("DELETE FROM sync_sessions")
                            db.execSQL("DELETE FROM sync_progress")
                            db.execSQL("DELETE FROM historical_packets")
                            Toast.makeText(context, "Cache database cleared successfully.", Toast.LENGTH_SHORT).show()
                        } catch (e: Exception) {
                            Toast.makeText(context, "Cache clear failed.", Toast.LENGTH_SHORT).show()
                            Timber.e(e, "DB cache clear error")
                        } finally {
                            db?.close()
                        }
                        queryLastSync()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF991B1B)),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("CLEAR CACHE DATA", fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
        }
        Spacer(modifier = Modifier.height(20.dp))

        // About section (UX 8.9)
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF16161A))
        ) {
            Column(
                modifier = Modifier.padding(20.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(imageVector = Icons.Default.Info, contentDescription = "About Grape", tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "ABOUT & SYSTEM INFO",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                Button(
                    onClick = onNavigateToAbout,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2C2C35)),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("VIEW VERSION & SYSTEM INFO", fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
        }
    }
}

@Composable
fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = Color.Gray
        )
        Text(
            text = value,
            fontSize = 13.sp,
            fontWeight = FontWeight.ExtraBold,
            color = Color.White
        )
    }
}
