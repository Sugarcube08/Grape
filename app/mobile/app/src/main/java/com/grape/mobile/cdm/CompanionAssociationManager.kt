package com.grape.mobile.cdm

import android.annotation.SuppressLint
import android.app.Activity
import android.companion.AssociationRequest
import android.companion.BluetoothLeDeviceFilter
import android.companion.CompanionDeviceManager
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.bluetooth.BluetoothDevice
import android.os.Build
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber
import java.util.regex.Pattern

const val ENABLE_CDM = false

@SuppressLint("MissingPermission")
class CompanionAssociationManager(
    private val context: Context,
    private val repository: AssociationRepository
) {
    private val cdm = context.getSystemService(Context.COMPANION_DEVICE_SERVICE) as CompanionDeviceManager
    
    private val _state = MutableStateFlow<AssociationState>(AssociationState.Unassociated)
    val state: StateFlow<AssociationState> = _state.asStateFlow()

    // Callback to pass the IntentSender to the caller (e.g. Activity/UI)
    var onIntentSenderAvailable: ((IntentSender) -> Unit)? = null

    fun observeAssociationState(): StateFlow<AssociationState> = state

    fun hasAssociation(): Boolean {
        return repository.getPrimary() != null
    }

    fun getAssociations(): List<Pair<String, String>> {
        val primary = repository.getPrimary()
        return if (primary != null) listOf(primary) else emptyList()
    }

    fun disassociate(mac: String) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                cdm.myAssociations.forEach { association ->
                    if (association.deviceMacAddress?.toString()?.equals(mac, ignoreCase = true) == true) {
                        cdm.disassociate(association.id)
                    }
                }
            } else {
                @Suppress("DEPRECATION")
                cdm.disassociate(mac)
            }
            repository.delete(mac)
            _state.value = AssociationState.Unassociated
            Timber.d("Disassociated device: $mac")
        } catch (e: Exception) {
            Timber.e(e, "Error disassociating device: $mac")
        }
    }

    fun requestAssociation() {
        if (!ENABLE_CDM) {
            Timber.w("requestAssociation skipped because Companion Device Manager is disabled (ENABLE_CDM = false)")
            _state.value = AssociationState.Failed("CDM Disabled")
            return
        }
        _state.value = AssociationState.Associating
        Timber.d("Requesting companion association without initial filtering")

        val filter = BluetoothLeDeviceFilter.Builder().build()

        val request = AssociationRequest.Builder()
            .setSingleDevice(true)
            .addDeviceFilter(filter)
            .build()

        val callback = object : CompanionDeviceManager.Callback() {
            override fun onDeviceFound(intentSender: IntentSender) {
                Timber.d("Device found, intent sender available")
                onIntentSenderAvailable?.invoke(intentSender)
            }

            override fun onAssociationPending(intentSender: IntentSender) {
                Timber.d("Association pending, intent sender available")
                onIntentSenderAvailable?.invoke(intentSender)
            }

            override fun onFailure(error: CharSequence?) {
                val errorMsg = error?.toString() ?: "Failed to find device"
                Timber.e("Association failed: $errorMsg")
                _state.value = AssociationState.Failed(errorMsg)
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            cdm.associate(request, context.mainExecutor, callback)
        } else {
            cdm.associate(request, callback, Handler(Looper.getMainLooper()))
        }
    }

    @Suppress("DEPRECATION")
    fun handleActivityResult(resultCode: Int, data: Intent?) {
        if (resultCode == Activity.RESULT_OK && data != null) {
            val device: BluetoothDevice? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                data.getParcelableExtra(CompanionDeviceManager.EXTRA_DEVICE, BluetoothDevice::class.java)
            } else {
                @Suppress("DEPRECATION")
                data.getParcelableExtra(CompanionDeviceManager.EXTRA_DEVICE)
            }

            if (device != null) {
                val name = device.name ?: "WHOOP Device"
                val mac = device.address
                
                var assocId = 0
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    cdm.myAssociations.forEach { assoc ->
                        if (assoc.deviceMacAddress?.toString()?.equals(mac, ignoreCase = true) == true) {
                            assocId = assoc.id
                        }
                    }
                }
                
                repository.insert(mac, name, assocId)
                _state.value = AssociationState.Associated(mac, name)
                Timber.d("Successfully associated with $name ($mac)")
            } else {
                _state.value = AssociationState.Failed("No device returned from system dialog")
            }
        } else {
            _state.value = AssociationState.Failed("User cancelled device selection")
        }
    }

    fun associateDeviceDirectly(mac: String, name: String) {
        repository.insert(mac, name, 0)
        _state.value = AssociationState.Associated(mac, name)
        Timber.d("Directly associated with $name ($mac)")
    }
}
