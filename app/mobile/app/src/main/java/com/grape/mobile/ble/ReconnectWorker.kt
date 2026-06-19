package com.grape.mobile.ble

import android.content.Context
import android.bluetooth.BluetoothAdapter
import androidx.work.*
import com.grape.mobile.cdm.AssociationRepository
import com.grape.mobile.repository.DeviceSettingsRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import timber.log.Timber
import java.util.concurrent.TimeUnit

class ReconnectWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params), KoinComponent {

    private val bleManager: GrapeBleManager by inject()
    private val associationRepository: AssociationRepository by inject()
    private val settingsRepository: DeviceSettingsRepository by inject()

    override suspend fun doWork(): Result {
        val attempt = inputData.getInt("attempt", 0)
        Timber.d("ReconnectWorker started: attempt=$attempt")

        val primary = associationRepository.getPrimary()
        if (primary == null) {
            Timber.i("ReconnectWorker: No associated device found (storedMac is null). Stopping reconnect attempts.")
            return Result.success()
        }
        val address = primary.first

        val bluetoothManager = applicationContext.getSystemService(Context.BLUETOOTH_SERVICE) as android.bluetooth.BluetoothManager
        val adapter = bluetoothManager.adapter
        if (adapter == null || !adapter.isEnabled) {
            Timber.w("ReconnectWorker: Bluetooth is disabled. Rescheduling...")
            scheduleNextAttempt(attempt)
            return Result.success()
        }

        if (bleManager.state.value == BleState.Connected) {
            Timber.d("ReconnectWorker: Already connected. Success.")
            return Result.success()
        }

        Timber.d("ReconnectWorker: Attempting reconnection to $address")
        bleManager.connect(address)

        // Wait up to 15 seconds for connection
        val resultState = withTimeoutOrNull(15000) {
            bleManager.state.first { it == BleState.Connected || it == BleState.Disconnected }
        }

        if (resultState == BleState.Connected) {
            Timber.i("ReconnectWorker: Reconnected successfully to $address")
            associationRepository.updateLastSeen(address)
            return Result.success()
        } else {
            Timber.w("ReconnectWorker: Connection attempt failed (state: $resultState). Rescheduling...")
            scheduleNextAttempt(attempt)
            return Result.success()
        }
    }

    private fun scheduleNextAttempt(currentAttempt: Int) {
        val nextDelaySec = when (currentAttempt) {
            0 -> 10
            1 -> 30
            2 -> 60
            else -> 300 // Max 5 minutes
        }

        Timber.d("Scheduling next reconnect attempt in $nextDelaySec seconds")

        val workRequest = OneTimeWorkRequestBuilder<ReconnectWorker>()
            .setInputData(workDataOf("attempt" to currentAttempt + 1))
            .setInitialDelay(nextDelaySec.toLong(), TimeUnit.SECONDS)
            .build()

        WorkManager.getInstance(applicationContext).enqueueUniqueWork(
            WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            workRequest
        )
    }

    companion object {
        const val WORK_NAME = "whoop_reconnect_work"

        fun enqueueImmediately(context: Context) {
            val workRequest = OneTimeWorkRequestBuilder<ReconnectWorker>()
                .setInputData(workDataOf("attempt" to 0))
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                workRequest
            )
        }

        fun cancelAll(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }
}
