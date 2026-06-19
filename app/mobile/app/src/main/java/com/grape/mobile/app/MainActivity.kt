package com.grape.mobile.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.grape.mobile.ble.GrapeBleManager
import com.grape.mobile.ble.GrapeBleService
import com.grape.mobile.navigation.AppNavigation
import com.grape.mobile.repository.DeviceRepository
import com.grape.mobile.theme.GrapeTheme
import com.grape.mobile.cdm.CompanionAssociationManager
import androidx.activity.result.IntentSenderRequest
import org.koin.android.ext.android.inject
import timber.log.Timber

class MainActivity : ComponentActivity() {
    private val bleManager: GrapeBleManager by inject()
    private val repository: DeviceRepository by inject()
    private val updateRepository: com.grape.mobile.repository.UpdateRepository by inject()
    private val associationManager: CompanionAssociationManager by inject()

    private val cdmLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        associationManager.handleActivityResult(result.resultCode, result.data)
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val scanGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions[Manifest.permission.BLUETOOTH_SCAN] ?: (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED)
        } else {
            true
        }
        val connectGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions[Manifest.permission.BLUETOOTH_CONNECT] ?: (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED)
        } else {
            true
        }
        val locationGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED)
        
        Timber.d("scanGranted=$scanGranted")
        Timber.d("connectGranted=$connectGranted")
        Timber.d("locationGranted=$locationGranted")
        
        if (connectGranted) {
            startWearableSyncService()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        Timber.d("MainActivity onCreate")

        associationManager.onIntentSenderAvailable = { intentSender ->
            try {
                cdmLauncher.launch(IntentSenderRequest.Builder(intentSender).build())
            } catch (e: Exception) {
                Timber.e(e, "Error launching Companion IntentSender")
            }
        }

        checkAndRequestPermissions()

        setContent {
            GrapeTheme {
                AppNavigation(bleManager, repository, updateRepository)
            }
        }
    }

    private fun checkAndRequestPermissions() {
        val permissionsToRequest = mutableListOf<String>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.BLUETOOTH_SCAN)
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.BLUETOOTH_CONNECT)
            }
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        if (permissionsToRequest.isNotEmpty()) {
            requestPermissionLauncher.launch(permissionsToRequest.toTypedArray())
        } else {
            startWearableSyncService()
        }
    }

    private fun startWearableSyncService() {
        try {
            val serviceIntent = Intent(this, GrapeBleService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }
            Timber.d("GrapeBleService started successfully")
        } catch (e: Exception) {
            Timber.e(e, "Error starting GrapeBleService")
        }
    }
}
