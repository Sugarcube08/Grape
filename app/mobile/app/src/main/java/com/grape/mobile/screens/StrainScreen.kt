package com.grape.mobile.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.grape.mobile.ffi.GrapeRustBridge
import com.grape.mobile.ffi.StrainReport
import com.grape.mobile.ui.components.BackgroundContainer
import com.grape.mobile.ui.components.GlassCard
import com.grape.mobile.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun StrainScreen() {
    val context = LocalContext.current
    val dbPath = remember { java.io.File(context.filesDir, "grape.sqlite").absolutePath }
    var report by remember { mutableStateOf<StrainReport?>(null) }
    var loading by remember { mutableStateOf(true) }
    var animationStarted by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        report = withContext(Dispatchers.IO) {
            runCatching { GrapeRustBridge.computeStrainV1(dbPath) }.getOrNull()
        }
        loading = false
        animationStarted = true
    }

    val strainScore = report?.strainScore?.toFloat() ?: 0f
    val animatedStrain by animateFloatAsState(
        targetValue = if (animationStarted) strainScore else 0f,
        animationSpec = tween(durationMillis = 1200),
        label = "strain_gauge_anim"
    )

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
                    text = "ATHLETIC STRAIN",
                    style = GrapeTypography.labelSmall,
                    color = TextSecondary,
                    letterSpacing = 1.5.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            if (loading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = StrainOrange)
                }
                return@Column
            }

            val currentReport = report
            if (currentReport == null) {
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(160.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No strain data available",
                            style = GrapeTypography.headlineMedium,
                            color = TextPrimary
                        )
                    }
                }
                return@Column
            }

            // Strain Gauge
            Box(
                modifier = Modifier.size(240.dp),
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

                    // Gradient for active strain (Orange -> Red)
                    val gradientBrush = Brush.linearGradient(
                        colors = listOf(StrainOrange, StrainRed)
                    )

                    // Active Arc
                    drawArc(
                        brush = gradientBrush,
                        startAngle = -220f,
                        sweepAngle = (animatedStrain / 21f * 260f),
                        useCenter = false,
                        style = Stroke(width = 16.dp.toPx(), cap = StrokeCap.Round)
                    )
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "%.1f".format(currentReport.strainScore),
                        style = GrapeTypography.displayLarge.copy(fontSize = 56.sp, fontWeight = FontWeight.ExtraBold),
                        color = TextPrimary
                    )
                    Text(
                        text = "${currentReport.intensityClass.uppercase()} STRAIN",
                        style = GrapeTypography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = StrainOrange,
                        letterSpacing = 1.5.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Metrics Grid
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                StrainStatCard(
                    title = "CARDIO LOAD",
                    value = "%.1f".format(currentReport.cardioLoad),
                    subText = "Heart Rate Intensity",
                    modifier = Modifier.weight(1f)
                )
                StrainStatCard(
                    title = "MUSCULAR LOAD",
                    value = "%.1f".format(currentReport.muscularLoad),
                    subText = "Steps & Cadence",
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                StrainStatCard(
                    title = "ACTIVE CALORIES",
                    value = "${currentReport.activeKcal.toInt()} kcal",
                    subText = "Energy Burned Today",
                    modifier = Modifier.weight(1f)
                )
                StrainStatCard(
                    title = "TOTAL STEPS",
                    value = "%,d".format(currentReport.steps),
                    subText = "Daily Movement",
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Trend
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Start
            ) {
                Text(
                    text = "TRAINING TREND",
                    style = GrapeTypography.labelSmall,
                    color = TextSecondary,
                    letterSpacing = 1.5.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.height(12.dp))

            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    StrainTrendValue(
                        title = "30D BASELINE",
                        value = "%.1f".format(currentReport.baselineStrain30),
                        modifier = Modifier.weight(1f)
                    )
                    StrainTrendValue(
                        title = currentReport.trainingTrendDirection,
                        value = "%+.1f".format(currentReport.trainingTrendDelta),
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
fun StrainTrendValue(
    title: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = title,
            style = GrapeTypography.labelSmall.copy(fontWeight = FontWeight.Bold),
            color = TextSecondary
        )
        Text(
            text = value,
            style = GrapeTypography.headlineLarge,
            color = TextPrimary
        )
    }
}

@Composable
fun StrainStatCard(
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
