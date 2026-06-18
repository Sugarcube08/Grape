package com.grape.mobile.ble

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import org.koin.android.ext.android.inject
import timber.log.Timber

class GrapeBleService : Service() {
    private val bleManager: GrapeBleManager by inject()
    private val binder = LocalBinder()

    inner class LocalBinder : Binder() {
        fun getService(): GrapeBleService = this@GrapeBleService
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        Timber.d("GrapeBleService created")
        startForegroundServiceNotification()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Timber.d("GrapeBleService started")
        return START_STICKY
    }

    private fun startForegroundServiceNotification() {
        val channelId = "grape_ble_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Grape Wearable Sync"
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel(channelId, name, importance)
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }

        val notification: Notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Grape Active Sync")
            .setContentText("Connected to WHOOP strap and receiving telemetry...")
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                1,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
            )
        } else {
            startForeground(1, notification)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Timber.d("GrapeBleService destroyed")
    }
}

