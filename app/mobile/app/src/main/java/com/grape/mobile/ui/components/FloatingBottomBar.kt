package com.grape.mobile.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.grape.mobile.theme.*

data class NavigationTabItem(
    val route: String,
    val title: String,
    val icon: ImageVector
)

@Composable
fun FloatingBottomBar(
    tabs: List<NavigationTabItem>,
    currentRoute: String?,
    onTabSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 12.dp)
            .height(78.dp)
            .clip(RoundedCornerShape(32.dp))
            .background(Color.White.copy(alpha = 0.05f))
            .border(
                width = 1.dp,
                color = Color.White.copy(alpha = 0.10f),
                shape = RoundedCornerShape(32.dp)
            ),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            tabs.forEach { tab ->
                val selected = currentRoute == tab.route
                
                val iconColor by animateColorAsState(
                    targetValue = if (selected) GrapeAccent else TextSecondary,
                    animationSpec = tween(durationMillis = 200),
                    label = "tab_icon_color"
                )
                
                val indicatorWidth by animateDpAsState(
                    targetValue = if (selected) 24.dp else 0.dp,
                    animationSpec = tween(durationMillis = 200),
                    label = "tab_indicator_width"
                )

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null, // Disable default grey ripple
                            onClick = { onTabSelected(tab.route) }
                        )
                ) {
                    Icon(
                        imageVector = tab.icon,
                        contentDescription = tab.title,
                        tint = iconColor,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = tab.title,
                        style = GrapeTypography.labelSmall.copy(
                            fontSize = 10.sp,
                            color = if (selected) TextPrimary else TextSecondary
                        )
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Box(
                        modifier = Modifier
                            .height(3.dp)
                            .width(indicatorWidth)
                            .clip(RoundedCornerShape(1.5.dp))
                            .background(
                                Brush.horizontalGradient(
                                    listOf(GrapePrimary, GrapeAccent)
                                )
                            )
                    )
                }
            }
        }
    }
}
