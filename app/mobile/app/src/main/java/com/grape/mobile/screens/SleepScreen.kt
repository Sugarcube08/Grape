package com.grape.mobile.screens

import android.content.Context
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
import com.grape.mobile.ui.components.BackgroundContainer
import com.grape.mobile.ui.components.GlassCard
import com.grape.mobile.ui.components.SleepTimeline
import com.grape.mobile.ui.components.SleepStageSegment
import com.grape.mobile.ui.components.WeeklyHeatmap
import com.grape.mobile.ui.components.HeatmapType
import com.grape.mobile.theme.*
import kotlinx.coroutines.flow.collect
import timber.log.Timber
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch

@Composable
fun SleepScreen(
    repository: DeviceRepository
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val actualDbPath = remember {
        val filesDir = context.filesDir
        java.io.File(filesDir, "grape.sqlite").absolutePath
    }

    var sleepReport by remember { mutableStateOf<uniffi.grape.SleepReport?>(null) }
    var syncProgress by remember { mutableStateOf<String?>(null) }
    var currentSessionId by remember { mutableStateOf<String?>(null) }
    var syncStartTime by remember { mutableStateOf(0L) }

    fun refreshData() {
        coroutineScope.launch(Dispatchers.IO) {
            try {
                val report = GrapeRustBridge.computeSleepV1(actualDbPath)
                withContext(Dispatchers.Main) {
                    sleepReport = report
                    Timber.d("Sleep screen refreshed: score=${report?.score}")
                }
            } catch (e: Exception) {
                Timber.e(e, "Error computing sleep report")
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
                val status = progress.status
                val packets = progress.packetsDownloaded
                val bytes = progress.bytesDownloaded
                val mb = String.format("%.2f", bytes / (1024.0 * 1024.0))
                val oldest = progress.oldestPage ?: 0L
                val newest = progress.newestPage ?: 0L
                val totalPages = (newest - oldest).coerceAtLeast(1)
                val totalExpectedPackets = totalPages * 50
                
                val elapsedMs = System.currentTimeMillis() - syncStartTime
                val elapsedSec = elapsedMs / 1000.0
                val rate = if (elapsedSec > 0) packets / elapsedSec else 0.0
                val remainingPackets = (totalExpectedPackets - packets).coerceAtLeast(0)
                
                val etaStr = if (rate > 0 && remainingPackets > 0) {
                    val etaSec = remainingPackets / rate
                    if (etaSec > 60) {
                        String.format("%d min %d sec", etaSec.toInt() / 60, etaSec.toInt() % 60)
                    } else {
                        String.format("%d sec", etaSec.toInt())
                    }
                } else {
                    "Calculating..."
                }

                syncProgress = "Syncing: $status | Packets: $packets/$totalExpectedPackets | ETA: $etaStr"

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

    BackgroundContainer {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(top = 24.dp, bottom = 100.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Start
            ) {
                Text(
                    text = "SLEEP METRICS",
                    style = GrapeTypography.labelSmall,
                    color = TextSecondary,
                    letterSpacing = 1.5.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            if (sleepReport == null) {
                NoSleepDataView(
                    syncProgress = syncProgress,
                    onSimulateSync = {
                        val newSessionId = UUID.randomUUID().toString()
                        syncStartTime = System.currentTimeMillis()
                        currentSessionId = newSessionId
                        repository.beginHistoricalSync(context, newSessionId)
                    },
                    onInjectMockData = {
                        val now = System.currentTimeMillis()
                        // Insert mock sleep session: 480 min (8h 0m): 85m REM, 110m Deep, 250m Light, 35m Awake
                        repository.insertMockSleepSession(
                            startTimeUnixMs = now - 9 * 3600 * 1000,
                            endTimeUnixMs = now - 1 * 3600 * 1000,
                            remMinutes = 85.0,
                            deepMinutes = 110.0,
                            coreMinutes = 250.0,
                            awakeMinutes = 35.0
                        )
                        refreshData()
                    }
                )
            } else {
                val report = sleepReport!!

                // Sleep Score Gauge
                SleepScoreRadialGauge(score = report.score)

                Spacer(modifier = Modifier.height(24.dp))

                // Stats Section
                val totalMinutes = report.remMinutes + report.deepMinutes + report.lightMinutes + report.awakeMinutes
                val hours = (totalMinutes / 60).toInt()
                val minutes = (totalMinutes % 60).toInt()

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    SleepStatCard(
                        title = "SLEEP DURATION",
                        value = "${hours}h ${minutes}m",
                        subText = "Total Time Asleep",
                        modifier = Modifier.weight(1f)
                    )
                    SleepStatCard(
                        title = "EFFICIENCY",
                        value = String.format("%.0f%%", report.efficiency),
                        subText = "Sleep Efficiency",
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    SleepStatCard(
                        title = "SLEEP NEED",
                        value = String.format("%.1fh", report.need / 60.0),
                        subText = "Target Sleep Duration",
                        modifier = Modifier.weight(1f)
                    )
                    SleepStatCard(
                        title = "SLEEP DEBT",
                        value = String.format("%.0fm", report.debt),
                        subText = "Accumulated Sleep Debt",
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Sleep Stages Timeline
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Start
                ) {
                    Text(
                        text = "SLEEP STAGES & ARCHITECTURE",
                        style = GrapeTypography.labelSmall,
                        color = TextSecondary,
                        letterSpacing = 1.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))

                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    val segments = listOf(
                        SleepStageSegment("Deep", report.deepMinutes.toInt(), SleepPurple),
                        SleepStageSegment("Light", report.lightMinutes.toInt(), SleepBlue),
                        SleepStageSegment("REM", report.remMinutes.toInt(), GrapeAccent),
                        SleepStageSegment("Awake", report.awakeMinutes.toInt(), StressOrange)
                    )
                    SleepTimeline(segments = segments)
                }

                Spacer(modifier = Modifier.height(32.dp))

                // Heatmap Section
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Start
                ) {
                    Text(
                        text = "WEEKLY SLEEP TREND",
                        style = GrapeTypography.labelSmall,
                        color = TextSecondary,
                        letterSpacing = 1.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))

                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    WeeklyHeatmap(
                        days = listOf("M", "T", "W", "T", "F", "S", "S"),
                        scores = listOf(85, 78, 90, report.score.toInt(), 0, 0, 0),
                        type = HeatmapType.SLEEP
                    )
                }
            }
        }
    }
}

@Composable
fun SleepScoreRadialGauge(score: Double) {
    Box(
        modifier = Modifier.size(220.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize().padding(12.dp)) {
            // Track background arc
            drawArc(
                color = Color.White.copy(alpha = 0.05f),
                startAngle = -220f,
                sweepAngle = 260f,
                useCenter = false,
                style = Stroke(width = 16.dp.toPx(), cap = StrokeCap.Round)
            )

            // Progress arc
            val gradientBrush = Brush.linearGradient(
                colors = listOf(SleepBlue, SleepPurple)
            )
            drawArc(
                brush = gradientBrush,
                startAngle = -220f,
                sweepAngle = (score / 100.0 * 260.0).toFloat(),
                useCenter = false,
                style = Stroke(width = 16.dp.toPx(), cap = StrokeCap.Round)
            )
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = String.format("%.0f", score),
                style = GrapeTypography.displayLarge.copy(fontSize = 54.sp, fontWeight = FontWeight.ExtraBold),
                color = TextPrimary
            )
            Text(
                text = "SLEEP SCORE",
                style = GrapeTypography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = TextSecondary,
                letterSpacing = 1.5.sp
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
    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 20.dp),
        cornerRadius = 28.dp
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "No Sleep Reports Yet",
                style = GrapeTypography.headlineLarge,
                color = TextPrimary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Perform a sync or load mock sleep session metrics to calculate sleep stages and architectural quality metrics.",
                style = GrapeTypography.bodyMedium,
                color = TextSecondary,
                lineHeight = 18.sp
            )
            Spacer(modifier = Modifier.height(24.dp))

            if (syncProgress != null) {
                Text(
                    text = syncProgress,
                    style = GrapeTypography.bodyMedium,
                    color = GrapeAccent,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(16.dp))
                CircularProgressIndicator(color = GrapeAccent)
            } else {
                Button(
                    onClick = onSimulateSync,
                    colors = ButtonDefaults.buttonColors(containerColor = GrapePrimary),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text("START HISTORICAL SYNC", style = GrapeTypography.labelLarge, color = Color.White)
                }
                Spacer(modifier = Modifier.height(12.dp))
                TextButton(onClick = onInjectMockData) {
                    Text("INJECT MOCK SLEEP SESSION DATA", color = GrapeAccent, style = GrapeTypography.labelSmall)
                }
            }
        }
    }
}

@Composable
fun SleepStatCard(
    title: String,
    value: String,
    subText: String,
    modifier: Modifier = Modifier
) {
    GlassCard(
        modifier = modifier.height(110.dp),
        cornerRadius = 24.dp
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = title,
                style = GrapeTypography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = TextSecondary
            )
            Column {
                Text(
                    text = value,
                    style = GrapeTypography.headlineLarge,
                    color = TextPrimary
                )
                Text(
                    text = subText,
                    style = GrapeTypography.labelSmall.copy(fontSize = 11.sp),
                    color = TextSecondary
                )
            }
        }
    }
}

