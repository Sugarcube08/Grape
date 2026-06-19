package com.grape.mobile.cdm

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import android.os.PowerManager
import androidx.core.content.ContextCompat

enum class DiagnosticStatus {
    PASS,
    WARN,
    FAIL
}

data class DiagnosticItem(
    val name: String,
    val status: DiagnosticStatus,
    val details: String = ""
)

class DeviceDiagnostics(
    private val context: Context,
    private val associationRepository: AssociationRepository
) {

    fun runDiagnostics(): List<DiagnosticItem> {
        val list = mutableListOf<DiagnosticItem>()

        // 1. Bluetooth Enabled
        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as android.bluetooth.BluetoothManager
        val bluetoothAdapter = bluetoothManager.adapter
        val btStatus = if (bluetoothAdapter == null) {
            DiagnosticItem("Bluetooth", DiagnosticStatus.FAIL, "Not supported")
        } else if (bluetoothAdapter.isEnabled) {
            DiagnosticItem("Bluetooth", DiagnosticStatus.PASS, "Enabled")
        } else {
            DiagnosticItem("Bluetooth", DiagnosticStatus.FAIL, "Disabled")
        }
        list.add(btStatus)

        // 2. Location Enabled
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val locEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        list.add(
            DiagnosticItem(
                "Location Services",
                if (locEnabled) DiagnosticStatus.PASS else DiagnosticStatus.WARN,
                if (locEnabled) "Enabled" else "Disabled (Needed for BLE scan fallback)"
            )
        )

        // 3. Notification Permission
        val hasNotifications = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
        list.add(
            DiagnosticItem(
                "Notifications",
                if (hasNotifications) DiagnosticStatus.PASS else DiagnosticStatus.FAIL,
                if (hasNotifications) "Granted" else "Denied"
            )
        )

        // 4. Foreground Service Permission
        val hasFgs = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.FOREGROUND_SERVICE) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
        val hasConnectedDeviceType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.FOREGROUND_SERVICE_CONNECTED_DEVICE) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
        val fgsStatus = if (hasFgs && hasConnectedDeviceType) DiagnosticStatus.PASS else DiagnosticStatus.FAIL
        list.add(DiagnosticItem("Foreground Service", fgsStatus, if (fgsStatus == DiagnosticStatus.PASS) "Granted" else "Denied"))

        // 5. Companion Association
        val primary = associationRepository.getPrimary()
        list.add(
            DiagnosticItem(
                "Association",
                if (primary != null) DiagnosticStatus.PASS else DiagnosticStatus.WARN,
                if (primary != null) "Associated with ${primary.second}" else "No companion association active"
            )
        )

        // 6. Battery Optimization Disabled
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val ignoringBatteryOptimizations = pm.isIgnoringBatteryOptimizations(context.packageName)
        list.add(
            DiagnosticItem(
                "Battery Optimization",
                if (ignoringBatteryOptimizations) DiagnosticStatus.PASS else DiagnosticStatus.WARN,
                if (ignoringBatteryOptimizations) "Optimizations disabled" else "Optimizations enabled (Background service may be killed)"
            )
        )

        // 7. WakeLock Permission
        val hasWakeLock = ContextCompat.checkSelfPermission(context, Manifest.permission.WAKE_LOCK) == PackageManager.PERMISSION_GRANTED
        list.add(DiagnosticItem("WakeLock Permission", if (hasWakeLock) DiagnosticStatus.PASS else DiagnosticStatus.FAIL, if (hasWakeLock) "Granted" else "Missing"))

        // 8. Internet Permission
        val hasInternet = ContextCompat.checkSelfPermission(context, Manifest.permission.INTERNET) == PackageManager.PERMISSION_GRANTED
        list.add(DiagnosticItem("Internet Permission", if (hasInternet) DiagnosticStatus.PASS else DiagnosticStatus.FAIL, if (hasInternet) "Granted" else "Missing"))

        return list
    }
}
