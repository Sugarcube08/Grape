package com.grape.mobile.screens

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.grape.mobile.ffi.GrapeRustBridge
import com.grape.mobile.repository.DeviceRepository
import kotlinx.coroutines.flow.collect
import timber.log.Timber
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch

@Composable
fun RecoveryScreen(
    repository: DeviceRepository
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val actualDbPath = remember {
        val filesDir = context.filesDir
        java.io.File(filesDir, "grape.sqlite").absolutePath
    }

    var recoveryReport by remember { mutableStateOf<uniffi.grape.RecoveryReport?>(null) }
    var syncProgress by remember { mutableStateOf<String?>(null) }
    var currentSessionId by remember { mutableStateOf<String?>(null) }

    fun refreshData() {
        coroutineScope.launch(Dispatchers.IO) {
            try {
                val report = GrapeRustBridge.computeRecoveryV0(actualDbPath)
                withContext(Dispatchers.Main) {
                    recoveryReport = report
                    Timber.d("Recovery screen refreshed: score=${report?.recoveryScore}")
                }
            } catch (e: Exception) {
                Timber.e(e, "Error computing recovery report")
            }
        }
    }

    LaunchedEffect(Unit) {
        refreshData()
    }

    // Monitor sync progress if running
    LaunchedEffect(currentSessionId) {
        val sessionId = currentSessionId ?: return@LaunchedEffect
        repository.queryHistoricalProgress(sessionId).collect { progress ->
            if (progress != null) {
                syncProgress = "Sync Status: ${progress.status} (Packets: ${progress.packetsDownloaded})"
                if (progress.status == "Completed" || progress.status == "Failed") {
                    refreshData()
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
            text = "RECOVERY ANALYSIS",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF8A8A93),
            letterSpacing = 2.sp,
            modifier = Modifier.align(Alignment.Start)
        )

        Spacer(modifier = Modifier.height(16.dp))

        if (recoveryReport == null) {
            NoRecoveryDataView(
                syncProgress = syncProgress,
                onSimulateSync = {
                    val newSessionId = UUID.randomUUID().toString()
                    currentSessionId = newSessionId
                    repository.beginHistoricalSync(context, newSessionId)
                },
                onInjectMockData = {
                    val now = System.currentTimeMillis()
                    // First insert a sleep session because recovery depends on sleep score
                    repository.insertMockSleepSession(
                        startTimeUnixMs = now - 9 * 3600 * 1000,
                        endTimeUnixMs = now - 1 * 3600 * 1000,
                        remMinutes = 85.0,
                        deepMinutes = 110.0,
                        coreMinutes = 250.0,
                        awakeMinutes = 35.0
                    )
                    // Insert recovery metrics: resting HR 54 bpm, HRV 75 ms, skin temp delta -0.2 C
                    repository.insertMockRecoveryMetric(
                        startTimeUnixMs = now - 9 * 3600 * 1000,
                        endTimeUnixMs = now - 1 * 3600 * 1000,
                        restingHr = 54.0,
                        hrv = 75.0,
                        tempDelta = -0.2
                    )
                    refreshData()
                }
            )
        } else {
            val report = recoveryReport!!

            // Recovery Gauge
            RecoveryRadialGauge(
                score = report.recoveryScore,
                state = report.recoveryState
            )

            Spacer(modifier = Modifier.height(28.dp))

            // Main stats grid
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                RecoveryMetricCard(
                    title = "HEART RATE VARIABILITY",
                    value = String.format("%.0f", report.hrv),
                    unit = "MS",
                    label = "HRV (RMSSD)",
                    modifier = Modifier.weight(1f)
                )
                RecoveryMetricCard(
                    title = "RESTING HEART RATE",
                    value = String.format("%.0f", report.restingHr),
                    unit = "BPM",
                    label = "RHR",
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                RecoveryMetricCard(
                    title = "SKIN TEMP DEVIATION",
                    value = String.format("%+.1f", report.temperatureDelta),
                    unit = "°C",
                    label = "Skin Temp Delta",
                    modifier = Modifier.weight(1f)
                )
                // We show recovery state card
                Card(
                    modifier = Modifier
                        .weight(1f)
                        .height(110.dp),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF16161A))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "RECOVERY STATUS",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.Gray,
                            letterSpacing = 1.sp
                        )
                        val badgeColor = when (report.recoveryState) {
                            "GREEN" -> Color(0xFF10B981)
                            "YELLOW" -> Color(0xFFFBBF24)
                            else -> Color(0xFFEF4444)
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(badgeColor.copy(alpha = 0.15f))
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = report.recoveryState,
                                color = badgeColor,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 16.sp,
                                letterSpacing = 1.sp
                            )
                        }
                    }
                }
            }


        }
    }
}

@Composable
fun NoRecoveryDataView(
    syncProgress: String?,
    onSimulateSync: () -> Unit,
    onInjectMockData: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 20.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF16161A))
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "NO RECOVERY METRICS YET",
                fontSize = 18.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Perform a sync or inject mock physiological markers to run the Recovery v0 engine (HRV, RHR, sleep quality, skin temp).",
                fontSize = 13.sp,
                color = Color.Gray,
                lineHeight = 18.sp,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
            Spacer(modifier = Modifier.height(20.dp))

            if (syncProgress != null) {
                Text(
                    text = syncProgress,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
                Spacer(modifier = Modifier.height(16.dp))
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            } else {
                Button(
                    onClick = onSimulateSync,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("START HISTORICAL SYNC", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun RecoveryRadialGauge(score: Double, state: String) {
    Box(
        modifier = Modifier.size(220.dp),
        contentAlignment = Alignment.Center
    ) {
        val stateColor = when (state) {
            "GREEN" -> Color(0xFF10B981)
            "YELLOW" -> Color(0xFFFBBF24)
            else -> Color(0xFFEF4444)
        }

        Canvas(modifier = Modifier.fillMaxSize()) {
            // Background arc (dash/dot track style)
            drawArc(
                color = Color(0xFF1C1C24),
                startAngle = -220f,
                sweepAngle = 260f,
                useCenter = false,
                style = Stroke(width = 16.dp.toPx(), cap = StrokeCap.Round)
            )

            // Value arc
            drawArc(
                color = stateColor,
                startAngle = -220f,
                sweepAngle = (score / 100.0 * 260.0).toFloat(),
                useCenter = false,
                style = Stroke(width = 16.dp.toPx(), cap = StrokeCap.Round)
            )
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = String.format("%.0f", score),
                fontSize = 64.sp,
                fontWeight = FontWeight.Black,
                color = Color.White
            )
            Text(
                text = "% RECOVERY",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Gray,
                letterSpacing = 2.sp
            )
        }
    }
}

@Composable
fun RecoveryMetricCard(
    title: String,
    value: String,
    unit: String,
    label: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.height(110.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF16161A))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = title,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Gray,
                letterSpacing = 0.5.sp
            )
            Row(
                verticalAlignment = Alignment.Bottom
            ) {
                Text(
                    text = value,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Black,
                    color = Color.White
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = unit,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Gray,
                    modifier = Modifier.padding(bottom = 6.dp)
                )
            }
        }
    }
}
