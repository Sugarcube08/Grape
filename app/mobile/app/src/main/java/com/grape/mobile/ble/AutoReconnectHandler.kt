package com.grape.mobile.ble

import android.content.Context
import com.grape.mobile.repository.DeviceSettingsRepository
import timber.log.Timber

class AutoReconnectHandler(
    private val context: Context,
    private val bleManager: GrapeBleManager,
    private val settingsRepository: DeviceSettingsRepository
) {
    fun attemptAutoReconnect() {
        val lastAddress = settingsRepository.getLastConnectedAddress()
        if (lastAddress != null) {
            Timber.d("AutoReconnect: Found last connected address $lastAddress. Attempting reconnection...")
            bleManager.connect(lastAddress)
        } else {
            Timber.d("AutoReconnect: No last connected address found in storage.")
        }
    }
}