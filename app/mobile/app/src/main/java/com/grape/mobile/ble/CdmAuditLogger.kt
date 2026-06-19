package com.grape.mobile.ble

import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.util.Log
import timber.log.Timber

object CdmAuditLogger {
    private const val TAG = "CDMAudit"

    fun logScanResult(result: ScanResult) {
        val device = result.device
        val record = result.scanRecord
        
        val name = device.name ?: "Unknown"
        val address = device.address
        val rssi = result.rssi
        
        val serviceUuids = record?.serviceUuids ?: emptyList()
        val manufacturerData = record?.manufacturerSpecificData
        
        Timber.d("--- WHOOP CDM Audit ---")
        Timber.d("Device: $name ($address) | RSSI: $rssi")
        Timber.d("Service UUIDs: $serviceUuids")
        
        if (manufacturerData != null) {
            for (i in 0 until manufacturerData.size()) {
                val id = manufacturerData.keyAt(i)
                val data = manufacturerData.valueAt(i)
                val hex = data.joinToString("") { "%02x".format(it) }
                Timber.d("Manufacturer ID 0x${Integer.toHexString(id)}: $hex")
            }
        }
        Timber.d("-----------------------")
    }
}