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
}
