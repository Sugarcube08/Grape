package com.grape.mobile.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.grape.mobile.theme.*

@Composable
fun RecoveryGauge(
    score: Int, // 0 to 100
    modifier: Modifier = Modifier,
    size: Dp = 280.dp,
    thickness: Dp = 18.dp
) {
    var animationStarted by remember { mutableStateOf(false) }
    LaunchedEffect(score) {
        animationStarted = true
    }
    
    val animatedProgress by animateFloatAsState(
        targetValue = if (animationStarted) score / 100f else 0f,
        animationSpec = tween(durationMillis = 1200),
        label = "recovery_gauge_anim"
    )

    val gaugeColor = when {
        score < 33 -> StressRed
        score < 67 -> StressYellow
        else -> RecoveryGreen
    }

    val gradientBrush = Brush.linearGradient(
        colors = when {
            score < 33 -> listOf(StressRed, StressOrange)
            score < 67 -> listOf(StressOrange, StressYellow)
            else -> listOf(RecoveryDark, RecoveryGreen, RecoveryLight)
        }
    )

    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize().padding(thickness / 2)) {
            // Background track arc (270 degrees total)
            drawArc(
                color = Color.White.copy(alpha = 0.05f),
                startAngle = 135f,
                sweepAngle = 270f,
                useCenter = false,
                style = Stroke(width = thickness.toPx(), cap = StrokeCap.Round)
            )

            // Active progress arc
            drawArc(
                brush = gradientBrush,
                startAngle = 135f,
                sweepAngle = 270f * animatedProgress,
                useCenter = false,
                style = Stroke(width = thickness.toPx(), cap = StrokeCap.Round)
            )
        }

        // Inner Labels
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "RECOVERY",
                style = GrapeTypography.labelSmall,
                color = TextSecondary,
                letterSpacing = 1.5.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "$score%",
                style = GrapeTypography.displayLarge.copy(fontSize = 54.sp, fontWeight = FontWeight.ExtraBold),
                color = TextPrimary
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = when {
                    score < 33 -> "Resting"
                    score < 67 -> "Moderate"
                    else -> "Optimal"
                },
                style = GrapeTypography.titleLarge,
                color = gaugeColor,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
