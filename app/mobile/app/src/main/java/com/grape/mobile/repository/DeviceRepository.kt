package com.grape.mobile.repository

import com.grape.mobile.database.DatabaseHelper
import com.grape.mobile.ffi.GrapeRustBridge
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import timber.log.Timber

data class DeviceUiState(
    val connectionState: String = "DISCONNECTED",
    val heartRate: Int = 0,
    val batteryPercent: Int = 0,
    val packetsReceived: Int = 0,
    val framesParsed: Int = 0
)

class DeviceRepository(private val databaseHelper: DatabaseHelper) {
    private val _uiState = MutableStateFlow(DeviceUiState())
    val uiState: StateFlow<DeviceUiState> = _uiState.asStateFlow()

    init {
        refreshState()
    }

    private val dbPath: String
        get() = databaseHelper.getDatabasePath()

    fun updateConnectionState(status: String) {
        val json = GrapeRustBridge.insertPacket(dbPath, "CONN:$status", "puffin")
        parseAndApplyState(json)
    }

    fun recordHeartRate(bpm: Int) {
        val json = GrapeRustBridge.insertPacket(dbPath, "HR:$bpm", "puffin")
        parseAndApplyState(json)
    }

    fun recordBattery(percent: Int) {
        val json = GrapeRustBridge.insertPacket(dbPath, "BAT:$percent", "puffin")
        parseAndApplyState(json)
    }

    fun insertWhoopPacket(frameHex: String, deviceType: String) {
        val json = GrapeRustBridge.insertPacket(dbPath, frameHex, deviceType)
        parseAndApplyState(json)
    }

    fun refreshState() {
        val json = GrapeRustBridge.getDeviceState(dbPath)
        parseAndApplyState(json)
    }

    private fun parseAndApplyState(jsonString: String) {
        try {
            val json = JSONObject(jsonString)
            val state = DeviceUiState(
                connectionState = json.optString("connection_state", "DISCONNECTED"),
                heartRate = json.optInt("heart_rate", 0),
                batteryPercent = json.optInt("battery", 0),
                packetsReceived = json.optInt("packets_received", 0),
                framesParsed = json.optInt("frames_parsed", 0)
            )
            _uiState.value = state
            Timber.d("Device uiState updated: $state")
        } catch (e: Exception) {
            Timber.e(e, "Error parsing Rust state JSON: $jsonString")
        }
    }
}
