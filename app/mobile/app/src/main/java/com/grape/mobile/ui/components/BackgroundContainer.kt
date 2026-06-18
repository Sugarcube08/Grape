package com.grape.mobile.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import com.grape.mobile.theme.BackgroundGradient
import kotlin.random.Random

@Composable
fun BackgroundContainer(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(BackgroundGradient)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = size.width
            val height = size.height
            if (width > 0f && height > 0f) {
                // Low-density noise (120 dots) performs exceptionally well and retains the visual style
                val random = Random(42)
                for (i in 0 until 120) {
                    val x = random.nextFloat() * width
                    val y = random.nextFloat() * height
                    val isWhite = random.nextBoolean()
                    val dotColor = if (isWhite) Color.White else Color.Black
                    drawCircle(
                        color = dotColor.copy(alpha = 0.02f),
                        radius = random.nextFloat() * 1.5f + 0.5f,
                        center = Offset(x, y)
                    )
                }
            }
        }
        content()
    }
}
