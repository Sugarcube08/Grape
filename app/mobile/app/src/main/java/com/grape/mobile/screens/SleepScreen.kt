package com.grape.mobile.screens

import android.content.Context
import android.database.sqlite.SQLiteDatabase
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

@Composable
fun SleepScreen(
    repository: DeviceRepository
) {
    val context = LocalContext.current
    val actualDbPath = remember {
        val filesDir = context.filesDir
        java.io.File(filesDir, "grape.sqlite").absolutePath
    }

    var sleepReport by remember { mutableStateOf<uniffi.grape.SleepReport?>(null) }
    var syncProgress by remember { mutableStateOf<String?>(null) }
    var currentSessionId by remember { mutableStateOf<String?>(null) }

    fun refreshData() {
        try {
            sleepReport = GrapeRustBridge.computeSleepV1(actualDbPath)
            Timber.d("Sleep screen refreshed: score=${sleepReport?.score}")
        } catch (e: Exception) {
            Timber.e(e, "Error computing sleep report")
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
            text = "SLEEP METRICS",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF8A8A93),
            letterSpacing = 2.sp,
            modifier = Modifier.align(Alignment.Start)
        )
        
        Spacer(modifier = Modifier.height(16.dp))

        if (sleepReport == null) {
            NoSleepDataView(
                syncProgress = syncProgress,
                onSimulateSync = {
                    val newSessionId = UUID.randomUUID().toString()
                    currentSessionId = newSessionId
                    repository.beginHistoricalSync(context, newSessionId)
                },
                onInjectMockData = {
                    val now = System.currentTimeMillis()
                    // Insert a mock 8 hr sleep session (480 min): 80m REM, 100m Deep, 260m Light/Core, 40m Awake
                    repository.insertMockSleepSession(
                        startTimeUnixMs = now - 9 * 3600 * 1000,
                        endTimeUnixMs = now - 1 * 3600 * 1000,
                        remMinutes = 80.0,
                        deepMinutes = 100.0,
                        coreMinutes = 260.0,
                        awakeMinutes = 40.0
                    )
                    refreshData()
                }
            )
        } else {
            val report = sleepReport!!
            
            // Sleep Score Radial Ring
            SleepScoreRadialGauge(score = report.score)

            Spacer(modifier = Modifier.height(24.dp))

            // Main stats grid
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                SleepMetricCard(
                    title = "SLEEP NEED",
                    value = String.format("%.1f", report.need / 60.0),
                    unit = "HRS",
                    modifier = Modifier.weight(1f)
                )
                SleepMetricCard(
                    title = "SLEEP DEBT",
                    value = String.format("%.1f", report.debt / 60.0),
                    unit = "HRS",
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                SleepMetricCard(
                    title = "EFFICIENCY",
                    value = String.format("%.0f", report.efficiency),
                    unit = "%",
                    modifier = Modifier.weight(1f)
                )
                SleepMetricCard(
                    title = "DISTURBANCES",
                    value = "${report.disturbances}",
                    unit = "TIMES",
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Sleep Stages Donut/Bar Chart
            SleepStagesChart(
                rem = report.remMinutes,
                deep = report.deepMinutes,
                light = report.lightMinutes,
                awake = report.awakeMinutes
            )


        }
    }
}

@Composable
fun NoSleepDataView(
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
                text = "NO SLEEP REPORT YET",
                fontSize = 18.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Perform a historical sync with your WHOOP band or inject mock data to test the Sleep Engine v1 algorithm.",
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
fun SleepScoreRadialGauge(score: Double) {
    Box(
        modifier = Modifier.size(200.dp),
        contentAlignment = Alignment.Center
    ) {
        val scoreColor = when {
            score >= 67.0 -> Color(0xFF10B981) // Green
            score >= 34.0 -> Color(0xFFFBBF24) // Yellow
            else -> Color(0xFFEF4444) // Red
        }

        Canvas(modifier = Modifier.fillMaxSize()) {
            // Draw background circle
            drawArc(
                color = Color(0xFF1C1C24),
                startAngle = -220f,
                sweepAngle = 260f,
                useCenter = false,
                style = Stroke(width = 16.dp.toPx(), cap = StrokeCap.Round)
            )

            // Draw active score arc
            drawArc(
                color = scoreColor,
                startAngle = -220f,
                sweepAngle = (score / 100.0 * 260.0).toFloat(),
                useCenter = false,
                style = Stroke(width = 16.dp.toPx(), cap = StrokeCap.Round)
            )
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = String.format("%.0f", score),
                fontSize = 56.sp,
                fontWeight = FontWeight.Black,
                color = Color.White
            )
            Text(
                text = "SLEEP PERFORMANCE",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Gray,
                letterSpacing = 1.sp
            )
        }
    }
}

@Composable
fun SleepMetricCard(
    title: String,
    value: String,
    unit: String,
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
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Gray,
                letterSpacing = 1.sp
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

@Composable
fun SleepStagesChart(
    rem: Double,
    deep: Double,
    light: Double,
    awake: Double
) {
    val total = rem + deep + light + awake
    val remPct = if (total > 0) rem / total else 0.0
    val deepPct = if (total > 0) deep / total else 0.0
    val lightPct = if (total > 0) light / total else 0.0
    val awakePct = if (total > 0) awake / total else 0.0

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF16161A))
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            Text(
                text = "SLEEP STAGES & ARCHITECTURE",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Gray,
                letterSpacing = 1.sp
            )
            
            Spacer(modifier = Modifier.height(20.dp))

            // Canvas-drawn proportional segment bar
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(24.dp)
                    .clip(RoundedCornerShape(12.dp))
            ) {
                var currentX = 0f
                val w = size.width

                // Deep Sleep (Purple)
                val deepW = (deepPct * w).toFloat()
                drawRect(
                    color = Color(0xFF7C3AED),
                    topLeft = Offset(currentX, 0f),
                    size = Size(deepW, size.height)
                )
                currentX += deepW

                // REM Sleep (Pink/Cyan)
                val remW = (remPct * w).toFloat()
                drawRect(
                    color = Color(0xFFEC4899),
                    topLeft = Offset(currentX, 0f),
                    size = Size(remW, size.height)
                )
                currentX += remW

                // Light Sleep (Blue)
                val lightW = (lightPct * w).toFloat()
                drawRect(
                    color = Color(0xFF3B82F6),
                    topLeft = Offset(currentX, 0f),
                    size = Size(lightW, size.height)
                )
                currentX += lightW

                // Awake (Orange)
                val awakeW = (awakePct * w).toFloat()
                drawRect(
                    color = Color(0xFFF59E0B),
                    topLeft = Offset(currentX, 0f),
                    size = Size(awakeW, size.height)
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Stage proportions legends
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                SleepStageLegendRow(name = "DEEP", minutes = deep, color = Color(0xFF7C3AED), pct = deepPct * 100)
                SleepStageLegendRow(name = "REM", minutes = rem, color = Color(0xFFEC4899), pct = remPct * 100)
                SleepStageLegendRow(name = "LIGHT (CORE)", minutes = light, color = Color(0xFF3B82F6), pct = lightPct * 100)
                SleepStageLegendRow(name = "AWAKE", minutes = awake, color = Color(0xFFF59E0B), pct = awakePct * 100)
            }
        }
    }
}

@Composable
fun SleepStageLegendRow(name: String, minutes: Double, color: Color, pct: Double) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(color)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = name,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }
        Text(
            text = String.format("%.0f min (%.0f%%)", minutes, pct),
            fontSize = 13.sp,
            color = Color.Gray
        )
    }
}
