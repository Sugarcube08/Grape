package com.grape.mobile.ffi

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
