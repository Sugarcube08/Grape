package com.grape.mobile.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.grape.mobile.R

val PlusJakartaSans = FontFamily.SansSerif

val GrapeTypography = Typography(
    // Hero: 48sp, 800
    displayLarge = TextStyle(
        fontFamily = PlusJakartaSans,
        fontWeight = FontWeight.ExtraBold, // 800
        fontSize = 48.sp,
        lineHeight = 56.sp,
        color = TextPrimary
    ),
    // Metric: 36sp, 700
    displayMedium = TextStyle(
        fontFamily = PlusJakartaSans,
        fontWeight = FontWeight.Bold, // 700
        fontSize = 36.sp,
        lineHeight = 44.sp,
        color = TextPrimary
    ),
    // Title: 28sp, 700
    displaySmall = TextStyle(
        fontFamily = PlusJakartaSans,
        fontWeight = FontWeight.Bold, // 700
        fontSize = 28.sp,
        lineHeight = 36.sp,
        color = TextPrimary
    ),
    // Subtitle: 18sp, 500
    headlineMedium = TextStyle(
        fontFamily = PlusJakartaSans,
        fontWeight = FontWeight.Medium, // 500
        fontSize = 18.sp,
        lineHeight = 24.sp,
        color = TextPrimary
    ),
    headlineLarge = TextStyle(
        fontFamily = PlusJakartaSans,
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        color = TextPrimary
    ),
    titleLarge = TextStyle(
        fontFamily = PlusJakartaSans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        color = TextPrimary
    ),
    bodyLarge = TextStyle(
        fontFamily = PlusJakartaSans,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        color = TextPrimary
    ),
    bodyMedium = TextStyle(
        fontFamily = PlusJakartaSans,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        color = TextSecondary
    ),
    // Label: 13sp, 500
    labelLarge = TextStyle(
        fontFamily = PlusJakartaSans,
        fontWeight = FontWeight.Medium, // 500
        fontSize = 13.sp,
        lineHeight = 18.sp,
        color = TextPrimary
    ),
    labelMedium = TextStyle(
        fontFamily = PlusJakartaSans,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        color = TextSecondary
    ),
    // Caption: 11sp, 400
    labelSmall = TextStyle(
        fontFamily = PlusJakartaSans,
        fontWeight = FontWeight.Normal, // 400
        fontSize = 11.sp,
        lineHeight = 16.sp,
        color = TextSecondary
    )
)

private val DarkColorScheme = darkColorScheme(
    primary = GrapePrimary,
    secondary = GrapeSecondary,
    tertiary = GrapeAccent,
    background = BackgroundPrimary,
    surface = BackgroundSecondary,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onBackground = TextPrimary,
    onSurface = TextPrimary
)

private val LightColorScheme = lightColorScheme(
    primary = GrapePrimary,
    secondary = GrapeSecondary,
    tertiary = GrapeAccent,
    background = BackgroundPrimary,
    surface = BackgroundSecondary,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onBackground = TextPrimary,
    onSurface = TextPrimary
)

@Composable
fun GrapeTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = GrapeTypography,
        content = content
    )
}

