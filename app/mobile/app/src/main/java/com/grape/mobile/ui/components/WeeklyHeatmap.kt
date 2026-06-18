package com.grape.mobile.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.grape.mobile.theme.*

enum class HeatmapType {
    RECOVERY, SLEEP, STRAIN
}

@Composable
fun WeeklyHeatmap(
    days: List<String>,
    scores: List<Int>,
    type: HeatmapType,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        days.forEachIndexed { index, day ->
            val score = scores.getOrElse(index) { 0 }
            
            val boxColor = when (type) {
                HeatmapType.RECOVERY -> {
                    when {
                        score == 0 -> Color.White.copy(alpha = 0.03f)
                        score < 33 -> StressRed.copy(alpha = 0.8f)
                        score < 67 -> StressYellow.copy(alpha = 0.8f)
                        else -> RecoveryGreen.copy(alpha = 0.8f)
                    }
                }
                HeatmapType.SLEEP -> {
                    when {
                        score == 0 -> Color.White.copy(alpha = 0.03f)
                        score < 50 -> SleepBlue.copy(alpha = 0.4f)
                        score < 80 -> SleepBlue.copy(alpha = 0.7f)
                        else -> SleepPurple.copy(alpha = 0.9f)
                    }
                }
                HeatmapType.STRAIN -> {
                    val normalizedScore = score * 5
                    when {
                        score == 0 -> Color.White.copy(alpha = 0.03f)
                        normalizedScore < 50 -> StrainOrange.copy(alpha = 0.6f)
                        else -> StrainRed.copy(alpha = 0.8f)
                    }
                }
            }

            val textColor = when {
                score == 0 -> TextSecondary
                type == HeatmapType.RECOVERY && score >= 33 && score < 67 -> Color(0xFF1E293B) // Dark text for yellow bg
                else -> TextPrimary
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.weight(1f).padding(horizontal = 2.dp)
            ) {
                Text(
                    text = day,
                    style = GrapeTypography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = TextSecondary,
                    fontSize = 10.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(boxColor)
                        .border(
                            width = 1.dp,
                            color = Color.White.copy(alpha = 0.05f),
                            shape = RoundedCornerShape(8.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (score > 0) {
                        Text(
                            text = score.toString(),
                            style = GrapeTypography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = textColor,
                                fontSize = 11.sp
                            )
                        )
                    }
                }
            }
        }
    }
}
