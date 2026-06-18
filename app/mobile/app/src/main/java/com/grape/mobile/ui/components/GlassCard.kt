package com.grape.mobile.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.grape.mobile.theme.GlassBorder
import com.grape.mobile.theme.GlassSurface

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 28.dp,
    borderWidth: Dp = 1.dp,
    backgroundColor: Color = GlassSurface,
    borderColor: Color = GlassBorder,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(cornerRadius))
            .background(backgroundColor)
            .border(
                width = borderWidth,
                color = borderColor,
                shape = RoundedCornerShape(cornerRadius)
            )
            .padding(24.dp)
    ) {
        content()
    }
}
