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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.grape.mobile.ui.components.BackgroundContainer
import com.grape.mobile.ui.components.GlassCard
import com.grape.mobile.ui.components.WeeklyHeatmap
import com.grape.mobile.ui.components.HeatmapType
import com.grape.mobile.theme.*

@Composable
fun StrainScreen() {
    var animationStarted by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        animationStarted = true
    }

    val animatedStrain by animateFloatAsState(
        targetValue = if (animationStarted) 11.4f else 0f,
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
                        text = "11.4",
                        style = GrapeTypography.displayLarge.copy(fontSize = 56.sp, fontWeight = FontWeight.ExtraBold),
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

            // Metrics Grid
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                StrainStatCard(
                    title = "CARDIO LOAD",
                    value = "5.2",
                    subText = "Heart Rate Intensity",
                    modifier = Modifier.weight(1f)
                )
                StrainStatCard(
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
                StrainStatCard(
                    title = "ACTIVE CALORIES",
                    value = "540 kcal",
                    subText = "Energy Burned Today",
                    modifier = Modifier.weight(1f)
                )
                StrainStatCard(
                    title = "TOTAL STEPS",
                    value = "8,600",
                    subText = "Daily Movement",
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Weekly Trend
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Start
            ) {
                Text(
                    text = "WEEKLY STRAIN TREND",
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
                    scores = listOf(14, 8, 12, 11, 0, 0, 0),
                    type = HeatmapType.STRAIN
                )
            }
        }
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
