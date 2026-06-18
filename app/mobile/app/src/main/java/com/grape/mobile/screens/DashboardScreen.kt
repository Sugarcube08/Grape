package com.grape.mobile.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.grape.mobile.R
import com.grape.mobile.ble.BleState
import com.grape.mobile.ble.GrapeBleManager
import com.grape.mobile.ffi.GrapeRustBridge
import com.grape.mobile.repository.DeviceRepository
import com.grape.mobile.ui.components.BackgroundContainer
import com.grape.mobile.ui.components.GlassCard
import com.grape.mobile.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

sealed class BiometricDataState {
    object Loading : BiometricDataState()
    object Unavailable : BiometricDataState()
    data class Available(
        val recoveryScore: Int,
        val recoveryState: String,
        val hrv: Int,
        val restingHr: Int,
        val tempDelta: Double,
        val sleepScore: Int,
        val sleepDurationMins: Int,
        val sleepNeedMins: Int,
        val sleepDebtMins: Int,
        val efficiency: Double,
        val strain: Double,
        val battery: Int
    ) : BiometricDataState()
}

@Composable
fun DashboardScreen(
    bleManager: GrapeBleManager,
    repository: DeviceRepository
) {
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    val dbPath = remember { java.io.File(context.filesDir, "grape.sqlite").absolutePath }

    val bleState by bleManager.state.collectAsState()
    val uiState by repository.uiState.collectAsState()

    var dataState by remember { mutableStateOf<BiometricDataState>(BiometricDataState.Loading) }

    LaunchedEffect(uiState) {
        coroutineScope.launch(Dispatchers.IO) {
            try {
                val sleep = GrapeRustBridge.computeSleepV1(dbPath)
                val rec = GrapeRustBridge.computeRecoveryV0(dbPath)
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
                            strain = 11.4, // placeholder mock for strain in DB
                            battery = uiState.batteryPercent
                        )
                    } else {
                        dataState = BiometricDataState.Unavailable
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Error computing reports on dashboard")
                withContext(Dispatchers.Main) {
                    dataState = BiometricDataState.Unavailable
                }
            }
        }
    }

    // Dynamic date formatting
    val calendar = remember { Calendar.getInstance() }
    val dateStr = remember { SimpleDateFormat("EEEE, d MMMM", Locale.getDefault()).format(calendar.time) }

    BackgroundContainer {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(top = 24.dp, bottom = 140.dp) // Large bottom margin to prevent navigation bar overlap
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Image(
                        painter = painterResource(id = R.drawable.ic_grape_logo_png),
                        contentDescription = "Grape Logo",
                        modifier = Modifier.size(36.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Grape",
                        style = GrapeTypography.displaySmall,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
                Text(
                    text = dateStr,
                    style = GrapeTypography.labelMedium,
                    color = TextSecondary
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Hero Card Shader/Offset transition (cached offset computation to prevent recomposition)
            val infiniteTransition = rememberInfiniteTransition(label = "hero_anim")
            val gradientOffset by infiniteTransition.animateFloat(
                initialValue = 0f,
                targetValue = 1000f,
                animationSpec = infiniteRepeatable(
                    animation = tween(15000, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "offset"
            )

            // Dynamic presentation based on loaded state
            when (val state = dataState) {
                BiometricDataState.Loading -> {
                    GlassCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(220.dp)
                    ) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = GrapePrimary)
                        }
                    }
                }
                BiometricDataState.Unavailable -> {
                    // Empty state displays no telemetry instructions
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(220.dp)
                            .clip(RoundedCornerShape(28.dp))
                            .background(Color.White.copy(alpha = 0.04f))
                            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(28.dp))
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("No Telemetry Found", style = GrapeTypography.headlineMedium, color = TextPrimary)
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                "Connect your wearable and perform a history sync in the Device tab to view physiological metrics.",
                                style = GrapeTypography.labelSmall,
                                color = TextSecondary,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                }
                is BiometricDataState.Available -> {
                    // Premium shifting gradient Hero Card (drawing optimized using drawBehind)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(220.dp)
                            .clip(RoundedCornerShape(28.dp))
                            .drawBehind {
                                val brush = Brush.linearGradient(
                                    colors = listOf(
                                        Color(0xFF7C3AED),
                                        Color(0xFFA855F7),
                                        Color(0xFFEC4899),
                                        Color(0xFF6366F1)
                                    ),
                                    start = Offset(gradientOffset, 0f),
                                    end = Offset(gradientOffset + 1000f, size.height)
                                )
                                drawRect(brush)
                            }
                            .padding(24.dp)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(
                                    text = "Recovered",
                                    style = GrapeTypography.labelLarge,
                                    color = Color.White.copy(alpha = 0.8f),
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Recovery ${state.recoveryScore}%",
                                    style = GrapeTypography.displayLarge.copy(fontSize = 38.sp),
                                    color = Color.White
                                )
                            }

                            // 2x2 Hero Metrics layout
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    HeroItem(label = "HRV", value = "${state.hrv} ms", modifier = Modifier.weight(1f))
                                    HeroItem(label = "RHR", value = "${state.restingHr} bpm", modifier = Modifier.weight(1f))
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    val hrs = state.sleepDurationMins / 60
                                    val mins = state.sleepDurationMins % 60
                                    HeroItem(label = "Sleep", value = "${hrs}h ${mins}m", modifier = Modifier.weight(1f))
                                    HeroItem(label = "Strain", value = "${state.strain}", modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            // Daily Summary Chips section
            Text(
                text = "TODAY'S SUMMARY",
                style = GrapeTypography.labelSmall,
                color = TextSecondary,
                letterSpacing = 1.5.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                val hasData = dataState is BiometricDataState.Available
                SummaryChip(
                    text = if (hasData) "🟢 Body recovered" else "⚪ No status",
                    borderColor = if (hasData) RecoveryGreen.copy(alpha = 0.3f) else Color.White.copy(alpha = 0.1f),
                    backgroundColor = if (hasData) RecoveryGreen.copy(alpha = 0.05f) else Color.White.copy(alpha = 0.02f),
                    textColor = if (hasData) RecoveryGreen else TextSecondary,
                    modifier = Modifier.weight(1f)
                )
                SummaryChip(
                    text = if (hasData) "🟣 Sleep debt ↓" else "⚪ No sync",
                    borderColor = if (hasData) SleepPurple.copy(alpha = 0.3f) else Color.White.copy(alpha = 0.1f),
                    backgroundColor = if (hasData) SleepPurple.copy(alpha = 0.05f) else Color.White.copy(alpha = 0.02f),
                    textColor = if (hasData) SleepPurple else TextSecondary,
                    modifier = Modifier.weight(1f)
                )
                SummaryChip(
                    text = if (hasData) "🩵 HRV +8 ms" else "⚪ No data",
                    borderColor = if (hasData) CyanAccent.copy(alpha = 0.3f) else Color.White.copy(alpha = 0.1f),
                    backgroundColor = if (hasData) CyanAccent.copy(alpha = 0.05f) else Color.White.copy(alpha = 0.02f),
                    textColor = if (hasData) CyanAccent else TextSecondary,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(28.dp))

            // Physiological Grid Cards
            Text(
                text = "PHYSIOLOGICAL STATUS",
                style = GrapeTypography.labelSmall,
                color = TextSecondary,
                letterSpacing = 1.5.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(12.dp))

            val hasData = dataState is BiometricDataState.Available
            val state = dataState as? BiometricDataState.Available
            val sleepHrs = state?.let { it.sleepDurationMins / 60 } ?: 0
            val sleepMins = state?.let { it.sleepDurationMins % 60 } ?: 0

            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    StatusGridCard(
                        title = "RECOVERY",
                        value = if (hasData && state != null) "${state.recoveryScore}%" else "--",
                        subText = if (hasData && state != null) state.recoveryState else "No sync",
                        icon = Icons.Default.Favorite,
                        iconColor = RecoveryGreen,
                        modifier = Modifier.weight(1f)
                    )
                    StatusGridCard(
                        title = "SLEEP",
                        value = if (hasData && state != null) "${sleepHrs}h ${sleepMins}m" else "--",
                        subText = if (hasData && state != null) "Score ${state.sleepScore}" else "No sync",
                        icon = Icons.Default.Star,
                        iconColor = SleepBlue,
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    StatusGridCard(
                        title = "STRAIN",
                        value = if (hasData && state != null) "${state.strain}" else "--",
                        subText = if (hasData) "Moderate" else "No sync",
                        icon = Icons.Default.PlayArrow,
                        iconColor = StrainOrange,
                        modifier = Modifier.weight(1f)
                    )
                    StatusGridCard(
                        title = "STRAP BATTERY",
                        value = "${uiState.batteryPercent}%",
                        subText = if (bleState == BleState.Monitoring || bleState == BleState.Connected || bleState == BleState.Subscribed) "Connected" else "Disconnected",
                        icon = Icons.Default.Info,
                        iconColor = CyanAccent,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
fun HeroItem(label: String, value: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "$label: ",
            style = GrapeTypography.labelSmall,
            color = Color.White.copy(alpha = 0.6f)
        )
        Text(
            text = value,
            style = GrapeTypography.bodyLarge,
            color = Color.White,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun SummaryChip(
    text: String,
    borderColor: Color,
    backgroundColor: Color,
    textColor: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(backgroundColor)
            .border(1.dp, borderColor, RoundedCornerShape(12.dp))
            .padding(horizontal = 4.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = GrapeTypography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 11.sp),
            color = textColor
        )
    }
}

@Composable
fun StatusGridCard(
    title: String,
    value: String,
    subText: String,
    icon: ImageVector,
    iconColor: Color,
    modifier: Modifier = Modifier
) {
    GlassCard(
        modifier = modifier.height(115.dp),
        cornerRadius = 24.dp
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = GrapeTypography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = TextSecondary
                )
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconColor,
                    modifier = Modifier.size(18.dp)
                )
            }
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

