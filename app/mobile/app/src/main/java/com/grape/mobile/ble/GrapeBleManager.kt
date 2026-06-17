package com.grape.mobile.ble

import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import com.grape.mobile.repository.DeviceRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID
import timber.log.Timber

enum class BleState {
    Idle,
    Scanning,
    Discovered,
    Connecting,
    Connected,
    Subscribed,
    Monitoring,
    Disconnected
}

@SuppressLint("MissingPermission")
class GrapeBleManager(
    private val context: Context,
    private val repository: DeviceRepository
) {
    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter = bluetoothManager.adapter
    private var bluetoothGatt: BluetoothGatt? = null
    private var isScanning = false
    private val handler = Handler(Looper.getMainLooper())

    private val _state = MutableStateFlow(BleState.Idle)
    val state: StateFlow<BleState> = _state.asStateFlow()

    private val _discoveredDevices = MutableStateFlow<List<BluetoothDevice>>(emptyList())
    val discoveredDevices: StateFlow<List<BluetoothDevice>> = _discoveredDevices.asStateFlow()

    // Whoop Services
    private val WHOOP_GEN4_SERVICE = UUID.fromString("61080001-8d6d-82b8-614a-1c8cb0f8dcc6")
    private val WHOOP_GEN5_SERVICE = UUID.fromString("fd4b0001-cce1-4033-93ce-002d5875f58a")
    private val HEART_RATE_SERVICE = UUID.fromString("0000180d-0000-1000-8000-00805f9b34fb")
    private val BATTERY_SERVICE = UUID.fromString("0000180f-0000-1000-8000-00805f9b34fb")

    // Characteristics
    private val HR_MEASUREMENT_CHAR = UUID.fromString("00002a37-0000-1000-8000-00805f9b34fb")
    private val BATTERY_LEVEL_CHAR = UUID.fromString("00002a19-0000-1000-8000-00805f9b34fb")

    private val CLIENT_CONFIG_DESC = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    private var frameAccumulator = FrameAccumulator("puffin")
    private var currentDeviceType = "puffin"

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device
            val name = device.name ?: return
            if (name.contains("WHOOP", ignoreCase = true)) {
                Timber.d("Discovered WHOOP device: $name (${device.address})")
                val currentList = _discoveredDevices.value.toMutableList()
                if (!currentList.any { it.address == device.address }) {
                    currentList.add(device)
                    _discoveredDevices.value = currentList
                }
                _state.value = BleState.Discovered
            }
        }

        override fun onScanFailed(errorCode: Int) {
            Timber.e("Scan failed with error code: $errorCode")
            _state.value = BleState.Idle
            isScanning = false
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                Timber.e("GATT connection state change failed with status: $status")
                disconnect()
                return
            }

            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    Timber.d("GATT Connected to: ${gatt.device.name}")
                    _state.value = BleState.Connected
                    repository.updateConnectionState("CONNECTED")
                    // Start service discovery
                    handler.post {
                        gatt.discoverServices()
                    }
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    Timber.d("GATT Disconnected")
                    _state.value = BleState.Disconnected
                    repository.updateConnectionState("DISCONNECTED")
                    disconnect()
                }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                Timber.e("Service discovery failed with status: $status")
                return
            }

            Timber.d("Services discovered successfully")
            _state.value = BleState.Subscribed

            // Detect device type based on discovered services
            val hasGen4 = gatt.getService(WHOOP_GEN4_SERVICE) != null
            currentDeviceType = if (hasGen4) "maverick" else "puffin"
            frameAccumulator = FrameAccumulator(currentDeviceType)

            subscribeToCharacteristics(gatt)
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            handleCharacteristicValue(characteristic.uuid, value)
        }

        @Deprecated("Deprecated in Java")
        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            val value = characteristic.value ?: return
            handleCharacteristicValue(characteristic.uuid, value)
        }

        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
            status: Int
        ) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                handleCharacteristicValue(characteristic.uuid, value)
            }
        }

        @Deprecated("Deprecated in Java")
        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            val value = characteristic.value ?: return
            if (status == BluetoothGatt.GATT_SUCCESS) {
                handleCharacteristicValue(characteristic.uuid, value)
            }
        }
    }

    private fun handleCharacteristicValue(uuid: UUID, value: ByteArray) {
        when (uuid) {
            HR_MEASUREMENT_CHAR -> {
                val flags = value[0].toInt()
                val bpm = if ((flags and 1) == 0) {
                    value[1].toInt() and 0xFF
                } else {
                    (value[1].toInt() and 0xFF) or ((value[2].toInt() and 0xFF) shl 8)
                }
                Timber.d("HR Notification value: $bpm bpm")
                repository.recordHeartRate(bpm)
            }
            BATTERY_LEVEL_CHAR -> {
                val level = value[0].toInt() and 0xFF
                Timber.d("Battery Notification value: $level%")
                repository.recordBattery(level)
            }
            else -> {
                // Whoop proprietary characteristic notification
                _state.value = BleState.Monitoring
                val frames = frameAccumulator.feed(value)
                for (frame in frames) {
                    val hex = frame.toHexString()
                    Timber.d("Whoop proprietary frame parsed: $hex")
                    repository.insertWhoopPacket(hex, currentDeviceType)
                }
            }
        }
    }

    fun scan() {
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {
            Timber.w("Bluetooth is not enabled or supported")
            return
        }

        if (isScanning) return
        isScanning = true
        _state.value = BleState.Scanning
        _discoveredDevices.value = emptyList()

        val filters = listOf(
            ScanFilter.Builder().setServiceUuid(ParcelUuid(WHOOP_GEN5_SERVICE)).build(),
            ScanFilter.Builder().setServiceUuid(ParcelUuid(WHOOP_GEN4_SERVICE)).build()
        )
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        bluetoothAdapter.bluetoothLeScanner?.startScan(filters, settings, scanCallback)
        Timber.d("BLE Scan started")

        // Stop scan after 15 seconds to save battery
        handler.postDelayed({
            stopScan()
        }, 15000)
    }

    fun stopScan() {
        if (!isScanning) return
        bluetoothAdapter?.bluetoothLeScanner?.stopScan(scanCallback)
        isScanning = false
        if (_state.value == BleState.Scanning) {
            _state.value = BleState.Idle
        }
        Timber.d("BLE Scan stopped")
    }

    fun connect(deviceAddress: String) {
        stopScan()
        _state.value = BleState.Connecting

        val device = bluetoothAdapter?.getRemoteDevice(deviceAddress) ?: return
        Timber.d("Connecting to device: ${device.name ?: deviceAddress}")
        bluetoothGatt = device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
    }

    fun disconnect() {
        Timber.d("Disconnecting GATT")
        bluetoothGatt?.disconnect()
        bluetoothGatt?.close()
        bluetoothGatt = null
        _state.value = BleState.Disconnected
        repository.updateConnectionState("DISCONNECTED")
    }

    fun subscribe(characteristic: BluetoothGattCharacteristic) {
        val gatt = bluetoothGatt ?: return
        gatt.setCharacteristicNotification(characteristic, true)
        val descriptor = characteristic.getDescriptor(CLIENT_CONFIG_DESC)
        if (descriptor != null) {
            descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            gatt.writeDescriptor(descriptor)
            Timber.d("Subscribed to characteristic: ${characteristic.uuid}")
        }
    }

    fun write(characteristic: BluetoothGattCharacteristic, value: ByteArray) {
        val gatt = bluetoothGatt ?: return
        characteristic.value = value
        gatt.writeCharacteristic(characteristic)
        Timber.d("Wrote to characteristic: ${characteristic.uuid} value: ${value.toHexString()}")
    }

    private fun subscribeToCharacteristics(gatt: BluetoothGatt) {
        // Subscribe to standard HR
        gatt.getService(HEART_RATE_SERVICE)?.getCharacteristic(HR_MEASUREMENT_CHAR)?.let {
            subscribe(it)
        }

        // Subscribe to standard Battery Level
        gatt.getService(BATTERY_SERVICE)?.getCharacteristic(BATTERY_LEVEL_CHAR)?.let {
            subscribe(it)
        }

        // Subscribe to Whoop proprietary streams
        val whoopServiceUuid = if (currentDeviceType == "maverick") WHOOP_GEN4_SERVICE else WHOOP_GEN5_SERVICE
        val whoopService = gatt.getService(whoopServiceUuid)
        if (whoopService != null) {
            // Whoop data characteristic suffix sequence: 0003 (Command Response), 0004 (Events), 0005 (Data)
            val baseUuid = whoopServiceUuid.toString()
            val c3 = UUID.fromString(baseUuid.replace("0001", "0003"))
            val c4 = UUID.fromString(baseUuid.replace("0001", "0004"))
            val c5 = UUID.fromString(baseUuid.replace("0001", "0005"))

            whoopService.getCharacteristic(c3)?.let { subscribe(it) }
            whoopService.getCharacteristic(c4)?.let { subscribe(it) }
            whoopService.getCharacteristic(c5)?.let { subscribe(it) }
        }
    }

    private fun ByteArray.toHexString() = joinToString("") { "%02x".format(it) }
}

class FrameAccumulator(private val deviceType: String) {
    private var buffer = ByteArray(0)

    fun feed(chunk: ByteArray): List<ByteArray> {
        buffer = buffer + chunk
        val frames = mutableListOf<ByteArray>()

        while (true) {
            val startIndex = buffer.indexOf(0xAA.toByte())
            if (startIndex == -1) {
                if (buffer.isNotEmpty()) {
                    buffer = ByteArray(0)
                }
                break
            }

            if (startIndex > 0) {
                buffer = buffer.copyOfRange(startIndex, buffer.size)
            }

            val headerLen = if (deviceType.lowercase() == "gen4" || deviceType.lowercase() == "whoop4" || deviceType.lowercase() == "maverick") 4 else 8
            if (buffer.size < headerLen) {
                break
            }

            val payloadLen = if (headerLen == 4) {
                (buffer[1].toInt() and 0xFF) or ((buffer[2].toInt() and 0xFF) shl 8)
            } else {
                (buffer[2].toInt() and 0xFF) or ((buffer[3].toInt() and 0xFF) shl 8)
            }

            val expectedTotalLen = headerLen + payloadLen
            if (buffer.size < expectedTotalLen) {
                break
            }

            val frame = buffer.copyOfRange(0, expectedTotalLen)
            frames.add(frame)
            buffer = buffer.copyOfRange(expectedTotalLen, buffer.size)
        }

        return frames
    }
}
