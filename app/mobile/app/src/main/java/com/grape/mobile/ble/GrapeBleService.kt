package com.grape.mobile.ble

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.database.sqlite.SQLiteDatabase
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.grape.mobile.cdm.AssociationRepository
import com.grape.mobile.database.DatabaseHelper
import com.grape.mobile.repository.DeviceRepository
import com.grape.mobile.repository.DeviceSettingsRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect
import org.koin.android.ext.android.getKoin
import timber.log.Timber

class GrapeBleService : Service() {
    private lateinit var bleManager: GrapeBleManager
    private lateinit var repository: DeviceRepository
    private lateinit var associationRepository: AssociationRepository
    private lateinit var settingsRepository: DeviceSettingsRepository
    private lateinit var databaseHelper: DatabaseHelper

    private val binder = LocalBinder()
    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)
    private var userDisconnected = false

    inner class LocalBinder : Binder() {
        fun getService(): GrapeBleService = this@GrapeBleService
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        running = true
        super.onCreate()
        
        // 1. Setup notification channel and start foreground immediately with minimal helper
        setupNotificationChannel()
        val initialNotification = buildInitialNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                initialNotification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
            )
        } else {
            startForeground(NOTIFICATION_ID, initialNotification)
        }

        // 2. Initialize dependencies after starting foreground service
        bleManager = getKoin().get()
        repository = getKoin().get()
        associationRepository = getKoin().get()
        settingsRepository = getKoin().get()
        databaseHelper = getKoin().get()

        Timber.d("GrapeBleService dependencies initialized")

        // 3. Update the foreground notification with actual status
        updateNotification()

        // Monitor BLE state changes
        serviceScope.launch {
            bleManager.state.collect { state ->
                Timber.d("GrapeBleService: BLE State changed to $state")
                updateNotification()

                if (state == BleState.Disconnected) {
                    if (!userDisconnected) {
                        Timber.d("GrapeBleService: Disconnected state observed, enqueuing ReconnectWorker")
                        ReconnectWorker.enqueueImmediately(this@GrapeBleService)
                    }
                } else if (state == BleState.Connected || state == BleState.Subscribed || state == BleState.Monitoring) {
                    Timber.d("GrapeBleService: Connected state observed, cancelling ReconnectWorker")
                    ReconnectWorker.cancelAll(this@GrapeBleService)
                    // Save last connected address
                    val primary = associationRepository.getPrimary()
                    if (primary != null) {
                        settingsRepository.saveLastConnectedAddress(primary.first)
                        associationRepository.updateLastSeen(primary.first)
                    }
                }
            }
        }

        // Monitor Device UI State changes (e.g. Battery, Heart Rate)
        serviceScope.launch {
            repository.uiState.collect {
                updateNotification()
            }
        }

        // Periodically refresh notification to update "Last Sync" duration
        serviceScope.launch {
            while (isActive) {
                delay(60000) // 1 minute
                updateNotification()
            }
        }
    }

    private fun buildInitialNotification(): Notification {
        val openAppIntent = Intent(this, com.grape.mobile.app.MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val openAppPendingIntent = PendingIntent.getActivity(
            this, 0, openAppIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("🍇 Grape: Initializing")
            .setContentText("Starting wearable service...")
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setContentIntent(openAppPendingIntent)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        Timber.d("GrapeBleService onStartCommand action: $action")

        when (action) {
            ACTION_DISCONNECT -> {
                Timber.d("Disconnect action triggered from notification")
                userDisconnected = true
                bleManager.disconnect()
                ReconnectWorker.cancelAll(this)
                updateNotification()
            }
            ACTION_SYNC -> {
                Timber.d("Sync action triggered from notification")
                val primary = associationRepository.getPrimary()
                val address = primary?.first ?: settingsRepository.getLastConnectedAddress()
                if (address != null) {
                    val sessionId = java.util.UUID.randomUUID().toString()
                    repository.beginHistoricalSync(this, sessionId)
                } else {
                    Timber.w("Sync action triggered but no device address found")
                }
            }
            ACTION_CONNECT_PRIMARY -> {
                Timber.d("Connect action triggered")
                loadAndConnectPrimary()
            }
            else -> {
                loadAndConnectPrimary()
            }
        }

        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        Timber.d("GrapeBleService task removed: Attempting self-recovery")
        val intent = Intent(this, GrapeBleService::class.java).apply {
            action = ACTION_CONNECT_PRIMARY
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    private fun loadAndConnectPrimary() {
        userDisconnected = false
        val primary = associationRepository.getPrimary()
        if (primary != null) {
            val mac = primary.first
            Timber.i("GrapeBleService: Connecting to primary companion device $mac")
            bleManager.connect(mac)
        } else {
            Timber.i("GrapeBleService: No associated device found to connect to (storedMac is null)")
        }
    }

    private fun setupNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Grape Wearable Sync"
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = "Keeps Grape connected to your WHOOP strap"
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val state = bleManager.state.value
        val statusText = when (state) {
            BleState.Connected -> "Connected"
            BleState.Connecting -> "Connecting..."
            BleState.Scanning -> "Scanning..."
            BleState.Subscribed, BleState.Monitoring -> "Connected"
            BleState.Disconnected -> "Disconnected"
            else -> "Disconnected"
        }

        val battery = repository.uiState.value.batteryPercent
        val recovery = getRecoveryScore()
        val lastSync = getLastSyncTimeMinutesAgo()

        val batteryText = if (battery > 0) "$battery%" else "--"
        val recoveryText = if (recovery != null && recovery > 0) "$recovery%" else "--"

        val summaryText = "Battery: $batteryText | Recovery: $recoveryText"
        val bigText = """
            Battery: $batteryText
            Recovery: $recoveryText
            Last Sync: $lastSync
        """.trimIndent()

        val openAppIntent = Intent(this, com.grape.mobile.app.MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val openAppPendingIntent = PendingIntent.getActivity(
            this, 0, openAppIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val disconnectIntent = Intent(this, GrapeBleService::class.java).apply {
            action = ACTION_DISCONNECT
        }
        val disconnectPendingIntent = PendingIntent.getService(
            this, 1, disconnectIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val syncIntent = Intent(this, GrapeBleService::class.java).apply {
            action = ACTION_SYNC
        }
        val syncPendingIntent = PendingIntent.getService(
            this, 2, syncIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("🍇 Grape: WHOOP $statusText")
            .setContentText(summaryText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(bigText))
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setContentIntent(openAppPendingIntent)
            .setOnlyAlertOnce(true)
            .apply {
                if (state == BleState.Connected || state == BleState.Subscribed || state == BleState.Monitoring) {
                    addAction(android.R.drawable.ic_menu_close_clear_cancel, "Disconnect", disconnectPendingIntent)
                    addAction(android.R.drawable.stat_notify_sync, "Sync", syncPendingIntent)
                }
            }
            .build()
    }

    private fun updateNotification() {
        val notification = buildNotification()
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, notification)
    }

    private fun getRecoveryScore(): Int? {
        return try {
            val report = uniffi.grape.computeRecoveryV0(databaseHelper.getDatabasePath())
            report?.recoveryScore?.toInt()
        } catch (e: Exception) {
            null
        }
    }

    private fun getLastSyncTimeMinutesAgo(): String {
        var db: SQLiteDatabase? = null
        try {
            db = SQLiteDatabase.openOrCreateDatabase(databaseHelper.getDatabasePath(), null)
            val cursor = db.rawQuery(
                "SELECT ended_at FROM sync_sessions WHERE status = 'SUCCESS' OR ended_at IS NOT NULL ORDER BY ended_at DESC LIMIT 1",
                null
            )
            if (cursor.moveToFirst()) {
                val endedAtStr = cursor.getString(0)
                cursor.close()
                val sdf = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US).apply {
                    timeZone = java.util.TimeZone.getTimeZone("UTC")
                }
                val endedAtDate = sdf.parse(endedAtStr)
                if (endedAtDate != null) {
                    val diffMs = System.currentTimeMillis() - endedAtDate.time
                    val diffMin = diffMs / 1000 / 60
                    return if (diffMin < 1) "Just now" else "${diffMin}m ago"
                }
            } else {
                cursor.close()
            }
        } catch (e: Exception) {
            Timber.e(e, "Error querying last sync time")
        } finally {
            db?.close()
        }
        return "Never"
    }

    override fun onDestroy() {
        running = false
        super.onDestroy()
        serviceJob.cancel()
        Timber.d("GrapeBleService destroyed")
    }

    companion object {
        private const val NOTIFICATION_ID = 1
        private const val CHANNEL_ID = "grape_ble_channel"

        const val ACTION_DISCONNECT = "com.grape.mobile.ACTION_DISCONNECT"
        const val ACTION_SYNC = "com.grape.mobile.ACTION_SYNC"
        const val ACTION_CONNECT_PRIMARY = "com.grape.mobile.ACTION_CONNECT_PRIMARY"

        @Volatile
        private var running = false

        fun isRunning(): Boolean = running
    }
}