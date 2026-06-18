package com.grape.mobile.ffi

import org.json.JSONObject

data class StrainReport(
    val strainScore: Double,
    val cardioLoad: Double,
    val muscularLoad: Double,
    val acuteLoad: Double,
    val intensityClass: String,
    val durationMinutes: Double,
    val activeKcal: Double,
    val steps: Long,
    val averageHr: Double,
    val maxHr: Double,
    val restingHr: Double,
    val recoveryScore: Double,
    val baselineStrain30: Double,
    val trainingTrendDelta: Double,
    val trainingTrendDirection: String
)

object GrapeRustBridge {
    init {
        System.loadLibrary("grape_core")
    }

    fun computeSleep(dbPath: String): String {
        return uniffi.grape.computeSleep(dbPath)
    }

    fun computeRecovery(dbPath: String): String {
        return uniffi.grape.computeRecovery(dbPath)
    }

    fun computeStrain(dbPath: String): String {
        return uniffi.grape.computeStrain(dbPath)
    }

    fun computeStrainV1(dbPath: String): StrainReport? {
        val json = computeStrain(dbPath)
        if (json.isBlank() || json == "{}") return null
        val root = JSONObject(json)
        val baseline30 = root.optJSONObject("baselines")
            ?.optJSONObject("rolling_30_day")
        val trainingTrend = root.optJSONObject("trends")
            ?.optJSONObject("training_trend")
        return StrainReport(
            strainScore = root.optDouble("strain_score", Double.NaN).takeIf { !it.isNaN() } ?: return null,
            cardioLoad = root.optDouble("cardio_load", 0.0),
            muscularLoad = root.optDouble("muscular_load", 0.0),
            acuteLoad = root.optDouble("acute_load", 0.0),
            intensityClass = root.optString("intensity_class", "Light"),
            durationMinutes = root.optDouble("duration_minutes", 0.0),
            activeKcal = root.optDouble("active_kcal", 0.0),
            steps = root.optLong("steps", 0L),
            averageHr = root.optDouble("average_hr", 0.0),
            maxHr = root.optDouble("max_hr", 0.0),
            restingHr = root.optDouble("resting_hr", 0.0),
            recoveryScore = root.optDouble("recovery_score", 0.0),
            baselineStrain30 = baseline30?.optDouble("baseline_strain", 0.0) ?: 0.0,
            trainingTrendDelta = trainingTrend?.optDouble("delta", 0.0) ?: 0.0,
            trainingTrendDirection = trainingTrend?.optString("direction", "FLAT") ?: "FLAT"
        )
    }

    fun computeStress(dbPath: String): String {
        return uniffi.grape.computeStress(dbPath)
    }

    fun insertPacket(dbPath: String, frameHex: String, deviceType: String): String {
        return uniffi.grape.insertPacket(dbPath, frameHex, deviceType)
    }

    fun getDeviceState(dbPath: String): String {
        return uniffi.grape.getDeviceState(dbPath)
    }

    fun computeSleepV1(dbPath: String): uniffi.grape.SleepReport? {
        return uniffi.grape.computeSleepV1(dbPath)
    }

    fun sleepSummary(dbPath: String): String {
        return uniffi.grape.sleepSummary(dbPath)
    }

    fun sleepStageDistribution(dbPath: String): String {
        return uniffi.grape.sleepStageDistribution(dbPath)
    }

    fun sleepEfficiency(dbPath: String): Double {
        return uniffi.grape.sleepEfficiency(dbPath)
    }

    fun sleepDebt(dbPath: String): Double {
        return uniffi.grape.sleepDebt(dbPath)
    }

    fun computeRecoveryV0(dbPath: String): uniffi.grape.RecoveryReport? {
        return uniffi.grape.computeRecoveryV0(dbPath)
    }

    fun buildCommandFrame(sequence: Byte, command: Byte, data: ByteArray): ByteArray {
        val uSeq = sequence.toUByte()
        val uCmd = command.toUByte()
        val byteList = data.map { it.toUByte() }
        val resList = uniffi.grape.buildCommandFrame(uSeq, uCmd, byteList)
        return resList.map { it.toByte() }.toByteArray()
    }
}
