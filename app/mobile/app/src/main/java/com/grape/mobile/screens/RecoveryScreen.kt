package com.grape.mobile.screens

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import com.grape.mobile.ui.components.*
import com.grape.mobile.theme.*
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
        java.io.File(context.filesDir, "grape.sqlite").absolutePath
    }

    var dataState by remember { mutableStateOf<BiometricDataState>(BiometricDataState.Loading) }

    fun refreshData() {
        coroutineScope.launch(Dispatchers.IO) {
            try {
                val sleep = GrapeRustBridge.computeSleepV1(actualDbPath)
                val rec = GrapeRustBridge.computeRecoveryV0(actualDbPath)
                withContext(Dispatchers.Main) {
                    if (sleep != null && rec != null) {
                        val durationMins = (sleep.remMinutes + sleep.deepMinutes + sleep.lightMinutes + sleep.awakeMinutes).toInt()
                        dataState = BiometricDataState.Available(
                            recoveryScore = rec.recoveryScore.toInt(),
                            recoveryState = rec.recoveryState,
                            hrv = rec.hrv.toInt(),
                            restingHr = rec.restingHr.toInt(),
                            tempDelta = rec.temperatureDelta,
                            sleepScore = sleep.score.toInt(),
                            sleepDurationMins = durationMins,
                            sleepNeedMins = sleep.need.toInt(),
                            sleepDebtMins = sleep.debt.toInt(),
                            efficiency = sleep.efficiency,
                            strain = 11.4,
                            battery = 0
                        )
                    } else {
                        dataState = BiometricDataState.Unavailable
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Error computing reports on recovery tab")
                withContext(Dispatchers.Main) {
                    dataState = BiometricDataState.Unavailable
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        refreshData()
    }

    BackgroundContainer {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(top = 24.dp, bottom = 140.dp), // Prevent bottom bar overlap
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header Title
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Start
            ) {
                Text(
                    text = "PHYSIOLOGICAL HUB",
                    style = GrapeTypography.labelSmall,
                    color = TextSecondary,
                    letterSpacing = 1.5.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            when (val state = dataState) {
                BiometricDataState.Loading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = GrapePrimary)
                    }
                }
                BiometricDataState.Unavailable -> {
                    // Empty sync details screen
                    GlassCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 20.dp)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "No Analytics Available",
                                style = GrapeTypography.headlineLarge,
                                color = TextPrimary
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Perform a sync with your WHOOP wearable on the Device tab or inject mock physiological markers inside the Profile screen to calculate biometric score parameters.",
                                style = GrapeTypography.bodyMedium,
                                color = TextSecondary,
                                lineHeight = 18.sp,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                }
                is BiometricDataState.Available -> {
                    // --- SECTION 1: RECOVERY ---
                    RecoveryHeaderSection("RECOVERY DIAGNOSTICS")
                    Spacer(modifier = Modifier.height(16.dp))

                    RecoveryGauge(score = state.recoveryScore)

                    Spacer(modifier = Modifier.height(24.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        RecoveryStatCard(
                            title = "HRV",
                            value = "${state.hrv} ms",
                            subText = "Heart Rate Variability",
                            modifier = Modifier.weight(1f)
                        )
                        RecoveryStatCard(
                            title = "RHR",
                            value = "${state.restingHr} bpm",
                            subText = "Resting Heart Rate",
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        RecoveryStatCard(
                            title = "TEMP",
                            value = String.format("%+.1f °C", state.tempDelta),
                            subText = "Skin Temp Delta",
                            modifier = Modifier.weight(1f)
                        )
                        RecoveryStatCard(
                            title = "RECOVERY STATUS",
                            value = state.recoveryState,
                            subText = "Current State",
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(40.dp))

                    // --- SECTION 2: SLEEP SUMMARY ---
                    RecoveryHeaderSection("SLEEP PERFORMANCE")
                    Spacer(modifier = Modifier.height(16.dp))

                    val sleepHrs = state.sleepDurationMins / 60
                    val sleepMins = state.sleepDurationMins % 60
                    GlassCard(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Sleep Score", style = GrapeTypography.headlineMedium, color = TextPrimary)
                                Text("${state.sleepScore}%", style = GrapeTypography.displayMedium, color = SleepBlue)
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Duration: ${sleepHrs}h ${sleepMins}m | Efficiency: ${String.format("%.0f%%", state.efficiency)}",
                                style = GrapeTypography.bodyMedium,
                                color = TextSecondary
                            )
                            Spacer(modifier = Modifier.height(20.dp))

                            // Custom horizontal timeline stages block
                            val segments = listOf(
                                SleepStageSegment("Deep", 110, SleepPurple),
                                SleepStageSegment("Light", 250, SleepBlue),
                                SleepStageSegment("REM", 85, GrapeAccent),
                                SleepStageSegment("Awake", 35, StressOrange)
                            )
                            SleepTimeline(segments = segments)
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        RecoveryStatCard(
                            title = "SLEEP NEED",
                            value = String.format("%.1fh", state.sleepNeedMins / 60.0),
                            subText = "Target Sleep Duration",
                            modifier = Modifier.weight(1f)
                        )
                        RecoveryStatCard(
                            title = "SLEEP DEBT",
                            value = "${state.sleepDebtMins.toInt()}m",
                            subText = "Sleep Deficit Accumulated",
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(40.dp))

                    // --- SECTION 3: ATHLETIC STRAIN ---
                    RecoveryHeaderSection("ATHLETIC STRAIN")
                    Spacer(modifier = Modifier.height(16.dp))

                    // Strain Gauge Canvas
                    Box(
                        modifier = Modifier.size(240.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize().padding(12.dp)) {
                            drawArc(
                                color = Color.White.copy(alpha = 0.05f),
                                startAngle = -220f,
                                sweepAngle = 260f,
                                useCenter = false,
                                style = Stroke(width = 16.dp.toPx(), cap = StrokeCap.Round)
                            )
                            val gradientBrush = Brush.linearGradient(
                                colors = listOf(StrainOrange, StrainRed)
                            )
                            drawArc(
                                brush = gradientBrush,
                                startAngle = -220f,
                                sweepAngle = (state.strain.toFloat() / 21f * 260f),
                                useCenter = false,
                                style = Stroke(width = 16.dp.toPx(), cap = StrokeCap.Round)
                            )
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "${state.strain}",
                                style = GrapeTypography.displayLarge.copy(fontSize = 54.sp, fontWeight = FontWeight.ExtraBold),
                                color = TextPrimary
                            )
                            Text(
                                text = "MODERATE STRAIN",
                                style = GrapeTypography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = StrainOrange,
                                letterSpacing = 1.5.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        RecoveryStatCard(
                            title = "CARDIO LOAD",
                            value = "5.2",
                            subText = "Heart Rate Intensity",
                            modifier = Modifier.weight(1f)
                        )
                        RecoveryStatCard(
                            title = "MUSCULAR LOAD",
                            value = "4.8",
                            subText = "Weight & Resistance",
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        RecoveryStatCard(
                            title = "ACTIVE ENERGY",
                            value = "540 kcal",
                            subText = "Calories Burned",
                            modifier = Modifier.weight(1f)
                        )
                        RecoveryStatCard(
                            title = "TOTAL STEPS",
                            value = "8,600",
                            subText = "Daily Movement",
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(40.dp))

                    // --- SECTION 4: WEEKLY BIO TRENDS ---
                    RecoveryHeaderSection("WEEKLY ANALYTICS TREND")
                    Spacer(modifier = Modifier.height(16.dp))

                    GlassCard(modifier = Modifier.fillMaxWidth()) {
                        Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
                            Column {
                                Text("Recovery Score Trend", style = GrapeTypography.labelSmall.copy(fontWeight = FontWeight.Bold), color = TextSecondary)
                                Spacer(modifier = Modifier.height(8.dp))
                                WeeklyHeatmap(
                                    days = listOf("M", "T", "W", "T", "F", "S", "S"),
                                    scores = listOf(78, 54, 88, state.recoveryScore, 0, 0, 0),
                                    type = HeatmapType.RECOVERY
                                )
                            }
                            Column {
                                Text("Sleep Score Trend", style = GrapeTypography.labelSmall.copy(fontWeight = FontWeight.Bold), color = TextSecondary)
                                Spacer(modifier = Modifier.height(8.dp))
                                WeeklyHeatmap(
                                    days = listOf("M", "T", "W", "T", "F", "S", "S"),
                                    scores = listOf(85, 78, 90, state.sleepScore, 0, 0, 0),
                                    type = HeatmapType.SLEEP
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun RecoveryHeaderSection(title: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start
    ) {
        Text(
            text = title,
            style = GrapeTypography.labelSmall.copy(fontWeight = FontWeight.Bold),
            color = TextSecondary,
            letterSpacing = 1.sp
        )
    }
}

@Composable
fun NoRecoveryDataView(
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
                text = "No Recovery Metrics Yet",
                style = GrapeTypography.headlineLarge,
                color = TextPrimary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Perform a sync with your connected wearable or load mock physiological markers to calculate Recovery Score (HRV, RHR, temperature delta).",
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
                    Text("INJECT MOCK PHYSIOLOGICAL DATA", color = GrapeAccent, style = GrapeTypography.labelSmall)
                }
            }
        }
    }
}

@Composable
fun RecoveryStatCard(
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

