package com.grape.mobile.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.grape.mobile.theme.*

data class SleepStageSegment(
    val name: String,
    val durationMinutes: Int,
    val color: Color
)

@Composable
fun SleepTimeline(
    segments: List<SleepStageSegment>,
    modifier: Modifier = Modifier,
    height: Dp = 72.dp
) {
    val totalMinutes = segments.sumOf { it.durationMinutes }.toFloat()
    
    Column(modifier = modifier.fillMaxWidth()) {
        // Custom segmented row representing sleep stages
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(height)
                .clip(RoundedCornerShape(16.dp))
                .background(Color.White.copy(alpha = 0.03f))
        ) {
            segments.forEachIndexed { index, segment ->
                val weight = if (totalMinutes > 0) segment.durationMinutes / totalMinutes else 0f
                if (weight > 0f) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .weight(weight)
                            .background(segment.color)
                            .padding(horizontal = 0.5.dp) // Subtle divider lines
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Detailed legends with dynamic time formats
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            segments.forEach { segment ->
                val hours = segment.durationMinutes / 60
                val mins = segment.durationMinutes % 60
                val timeStr = if (hours > 0) "${hours}h ${mins}m" else "${mins}m"
                
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(segment.color)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Column {
                        Text(
                            text = segment.name,
                            style = GrapeTypography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = TextSecondary
                        )
                        Text(
                            text = timeStr,
                            style = GrapeTypography.labelSmall.copy(fontSize = 11.sp),
                            color = TextPrimary
                        )
                    }
                }
            }
        }
    }
}
