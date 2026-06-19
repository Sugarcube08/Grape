package com.grape.mobile.models

enum class Origin {
    Measured,
    Derived,
    Estimated,
    Imported,
    Unavailable
}

data class Metric<T>(
    val value: T?,
    val origin: Origin,
    val confidence: Float,
    val timestamp: Long,
    val source: String,
    val baseline: String
)