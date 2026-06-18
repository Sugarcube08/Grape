package com.grape.mobile.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush

// Background Colors
val BackgroundPrimary = Color(0xFF05010B)
val BackgroundSecondary = Color(0xFF0B0816)
val BackgroundTertiary = Color(0xFF121022)
val BackgroundGradientTop = Color(0xFF090013)
val BackgroundGradientBottom = Color(0xFF1A1035)

// The vertical gradient background as a Brush (Top -> Middle -> Bottom)
val BackgroundGradient = Brush.verticalGradient(
    listOf(
        Color(0xFF05010B),
        Color(0xFF0B0816),
        Color(0xFF151026)
    )
)

// Glass Surfaces
val GlassSurface = Color(0xFFFFFFFF).copy(alpha = 0.06f)
val GlassSurfaceHover = Color(0xFFFFFFFF).copy(alpha = 0.10f)
val GlassBorder = Color(0xFFFFFFFF).copy(alpha = 0.12f)
val GlassShadow = Color(0xFF000000).copy(alpha = 0.25f)

// Brand Colors
val GrapePrimary = Color(0xFF8B5CF6)
val GrapeSecondary = Color(0xFFA855F7)
val GrapeAccent = Color(0xFFEC4899)
val CyanAccent = Color(0xFF5EEAD4)
val BlueAccent = Color(0xFF60A5FA)

// Gradients
val PrimaryGradient = Brush.verticalGradient(
    listOf(
        Color(0xFF7C3AED),
        Color(0xFFA855F7),
        Color(0xFFEC4899)
    )
)

val PrimaryGradientHorizontal = Brush.horizontalGradient(
    listOf(
        Color(0xFF7C3AED),
        Color(0xFFA855F7),
        Color(0xFFEC4899)
    )
)

val RecoveryGradient = Brush.verticalGradient(
    listOf(
        Color(0xFF22C55E),
        Color(0xFF84CC16)
    )
)

val StrainGradient = Brush.verticalGradient(
    listOf(
        Color(0xFFF97316),
        Color(0xFFEF4444)
    )
)

val SleepGradient = Brush.verticalGradient(
    listOf(
        Color(0xFF6366F1),
        Color(0xFF8B5CF6)
    )
)

// Physiological Colors
val RecoveryGreen = Color(0xFF22C55E)
val RecoveryLight = Color(0xFF84CC16)
val RecoveryDark = Color(0xFF15803D)
val StressYellow = Color(0xFFFACC15)
val StressOrange = Color(0xFFFB923C)
val StressRed = Color(0xFFEF4444)
val SleepBlue = Color(0xFF6366F1)
val SleepPurple = Color(0xFF8B5CF6)
val StrainOrange = Color(0xFFF97316)
val StrainRed = Color(0xFFDC2626)

// Typography
val TextPrimary = Color(0xFFF8FAFC)
val TextSecondary = Color(0xFF94A3B8)

