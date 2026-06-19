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
import android.os.Build
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import android.util.Log
import com.grape.mobile.repository.DeviceRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.util.UUID
import timber.log.Timber

import com.grape.mobile.repository.DeviceSettingsRepository

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
    private val repository: DeviceRepository,
    private val settingsRepository: DeviceSettingsRepository
) {
    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter = bluetoothManager.adapter
    private var bluetoothGatt: BluetoothGatt? = null
    private var isScanning = false
    private val handler = Handler(Looper.getMainLooper())

    private var commandSequence: Byte = 0
    private val _incomingFrames = MutableSharedFlow<ByteArray>(extraBufferCapacity = 128)
    val incomingFrames: SharedFlow<ByteArray> = _incomingFrames.asSharedFlow()

    private val _state = MutableStateFlow(BleState.Idle)
    val state: StateFlow<BleState> = _state.asStateFlow()

    private val _discoveredDevices = MutableStateFlow<List<BluetoothDevice>>(emptyList())
    val discoveredDevices: StateFlow<List<BluetoothDevice>> = _discoveredDevices.asStateFlow()

    private val _scanCallbackFiring = MutableStateFlow(false)
    val scanCallbackFiring = _scanCallbackFiring.asStateFlow()

    private val _advertisementsSeen = MutableStateFlow(0)
    val advertisementsSeen = _advertisementsSeen.asStateFlow()

    private val _whoopVisible = MutableStateFlow(false)
    val whoopVisible = _whoopVisible.asStateFlow()

    private val _servicesDiscoveredCount = MutableStateFlow(0)
    val servicesDiscoveredCount = _servicesDiscoveredCount.asStateFlow()

    private val _characteristicsCount = MutableStateFlow(0)
    val characteristicsCount = _characteristicsCount.asStateFlow()

    private val _notificationsEnabledCount = MutableStateFlow(0)
    val notificationsEnabledCount = _notificationsEnabledCount.asStateFlow()

    private val _packetsReceivedCount = MutableStateFlow(0)
    val packetsReceivedCount = _packetsReceivedCount.asStateFlow()

    private val _parserSuccessPercent = MutableStateFlow(100f)
    val parserSuccessPercent = _parserSuccessPercent.asStateFlow()

    // Whoop Services
    private val WHOOP_GEN4_SERVICE = UUID.fromString("61080001-8d6d-82b8-614a-1c8cb0f8dcc6")
    private val WHOOP_GEN5_SERVICE = UUID.fromString("fd4b0001-cce1-4033-93ce-002d5875f58a")
    private val HEART_RATE_SERVICE = UUID.fromString("0000180d-0000-1000-8000-00805f9b34fb")
    private val BATTERY_SERVICE = UUID.fromString("0000180f-0000-1000-8000-00805f9b34fb")
    private val DIS_SERVICE = UUID.fromString("0000180a-0000-1000-8000-00805f9b34fb")

    // Characteristics
    private val HR_MEASUREMENT_CHAR = UUID.fromString("00002a37-0000-1000-8000-00805f9b34fb")
    private val BATTERY_LEVEL_CHAR = UUID.fromString("00002a19-0000-1000-8000-00805f9b34fb")
    private val DIS_MANUFACTURER_CHAR = UUID.fromString("00002a29-0000-1000-8000-00805f9b34fb")
    private val DIS_SERIAL_CHAR = UUID.fromString("00002a25-0000-1000-8000-00805f9b34fb")
    private val DIS_HARDWARE_CHAR = UUID.fromString("00002a27-0000-1000-8000-00805f9b34fb")
    private val DIS_FIRMWARE_CHAR = UUID.fromString("00002a26-0000-1000-8000-00805f9b34fb")

    private val CLIENT_CONFIG_DESC = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    private var frameAccumulator = FrameAccumulator("puffin")
    private var currentDeviceType = "puffin"

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            CdmAuditLogger.logScanResult(result)
            _scanCallbackFiring.value = true
            _advertisementsSeen.value = _advertisementsSeen.value + 1
            
            val device = result.device
            val name = device.name ?: "Unknown"
            val address = device.address
            val rssi = result.rssi
            val services = result.scanRecord?.serviceUuids ?: emptyList()
            Log.d("BLE", "BLE\n$address\n$name\nRSSI=$rssi\nServices=$services")

            val isWhoop = name.contains("WHOOP", ignoreCase = true) || services.any {
                it.uuid == WHOOP_GEN4_SERVICE || it.uuid == WHOOP_GEN5_SERVICE
            }
            if (isWhoop) {
                _whoopVisible.value = true
                Timber.d("Discovered WHOOP device: $name ($address)")
                val currentList = _discoveredDevices.value.toMutableList()
                if (!currentList.any { it.address == address }) {
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
                    repository.setSyncStage("Connected")
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
            repository.setSyncStage("Services discovered")

            // Log discovered services and characteristics
            gatt.services.forEach { service ->
                Log.d("SERVICE", service.uuid.toString())
                service.characteristics.forEach { characteristic ->
                    Log.d("CHAR", characteristic.uuid.toString())
                }
            }

            // Expose counts for Developer Diagnostics
            _servicesDiscoveredCount.value = gatt.services.size
            var totalChars = 0
            gatt.services.forEach { s ->
                totalChars += s.characteristics.size
            }
            _characteristicsCount.value = totalChars

            // Write local report on device
            writeServiceDumpReport(gatt)

            // Detect device type based on discovered services
            val hasGen4 = gatt.getService(WHOOP_GEN4_SERVICE) != null
            currentDeviceType = if (hasGen4) "maverick" else "puffin"
            frameAccumulator = FrameAccumulator(currentDeviceType)

            subscribeToCharacteristics(gatt)

            // Trigger DIS reads sequentially
            val disService = gatt.getService(DIS_SERVICE)
            if (disService != null) {
                val charsToRead = listOf(
                    DIS_MANUFACTURER_CHAR,
                    DIS_SERIAL_CHAR,
                    DIS_HARDWARE_CHAR,
                    DIS_FIRMWARE_CHAR
                )
                var delay = 1000L
                for (charUuid in charsToRead) {
                    val char = disService.getCharacteristic(charUuid)
                    if (char != null) {
                        handler.postDelayed({
                            if (gatt.device != null && bluetoothGatt != null) {
                                Timber.d("Reading DIS characteristic: $charUuid")
                                gatt.readCharacteristic(char)
                            }
                        }, delay)
                        delay += 300L
                    }
                }
            }
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            handleCharacteristicValue(characteristic.uuid, value)
        }

        @Deprecated("Deprecated in Java")
        @Suppress("DEPRECATION")
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
        @Suppress("DEPRECATION")
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

    private var totalParserPackets = 0
    private var successParserPackets = 0

    private fun handleCharacteristicValue(uuid: UUID, value: ByteArray) {
        _packetsReceivedCount.value = _packetsReceivedCount.value + 1

        // Find which service this characteristic belongs to
        var serviceUuid: String? = null
        val gatt = bluetoothGatt
        if (gatt != null) {
            for (service in gatt.services) {
                if (service.getCharacteristic(uuid) != null) {
                    serviceUuid = service.uuid.toString()
                    break
                }
            }
        }
        
        recordRawPacket(serviceUuid, uuid.toString(), value)

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
                bluetoothGatt?.device?.address?.let { mac ->
                    settingsRepository.saveDeviceInfo(mac, "battery_level", level.toString())
                }
            }
            DIS_MANUFACTURER_CHAR -> {
                val manufacturer = String(value).trim()
                Timber.d("DIS Manufacturer: $manufacturer")
                bluetoothGatt?.device?.address?.let { mac ->
                    settingsRepository.saveDeviceInfo(mac, "manufacturer", manufacturer)
                }
            }
            DIS_SERIAL_CHAR -> {
                val serial = String(value).trim()
                Timber.d("DIS Serial: $serial")
                bluetoothGatt?.device?.address?.let { mac ->
                    settingsRepository.saveDeviceInfo(mac, "serial_number", serial)
                }
            }
            DIS_HARDWARE_CHAR -> {
                val hardware = String(value).trim()
                Timber.d("DIS Hardware Revision: $hardware")
                bluetoothGatt?.device?.address?.let { mac ->
                    settingsRepository.saveDeviceInfo(mac, "hardware_revision", hardware)
                }
            }
            DIS_FIRMWARE_CHAR -> {
                val firmware = String(value).trim()
                Timber.d("DIS Firmware Revision: $firmware")
                bluetoothGatt?.device?.address?.let { mac ->
                    settingsRepository.saveDeviceInfo(mac, "firmware_revision", firmware)
                }
            }
            else -> {
                // proprietary data
                if (value.isNotEmpty() && value[0] == 0xAA.toByte()) {
                    totalParserPackets++
                    _state.value = BleState.Monitoring
                    try {
                        val frames = frameAccumulator.feed(value)
                        if (frames.isNotEmpty()) {
                            successParserPackets += frames.size
                            for (frame in frames) {
                                val hex = frame.toHexString()
                                Timber.d("Whoop proprietary frame parsed: $hex")
                                repository.insertWhoopPacket(hex, currentDeviceType)
                                _incomingFrames.tryEmit(frame)
                            }
                        }
                    } catch (e: Exception) {
                        Timber.e(e, "Error feeding frameAccumulator")
                    }
                    val percent = if (totalParserPackets > 0) (successParserPackets.toFloat() / totalParserPackets.toFloat()) * 100f else 100f
                    _parserSuccessPercent.value = percent.coerceIn(0f, 100f)
                } else {
                    Timber.d("Non-Whoop notification value from $uuid: ${value.toHexString()}")
                }
            }
        }
    }

    fun scan() {
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {
            Timber.w("Bluetooth is not enabled or supported")
            return
        }

        val scanGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
        val connectGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
        val locationGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

        Timber.d("scanGranted=$scanGranted")
        Timber.d("connectGranted=$connectGranted")
        Timber.d("locationGranted=$locationGranted")

        if (!scanGranted || !connectGranted || !locationGranted) {
            Timber.w("Aborting scan due to missing permissions")
            return
        }

        if (isScanning) return
        isScanning = true
        _state.value = BleState.Scanning
        _discoveredDevices.value = emptyList()

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        bluetoothAdapter.bluetoothLeScanner?.startScan(emptyList(), settings, scanCallback)
        Timber.d("BLE Scan started")

        // Stop scan after 60 seconds to save battery
        handler.postDelayed({
            stopScan()
        }, 60000)
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
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                gatt.writeDescriptor(descriptor, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
            } else {
                @Suppress("DEPRECATION")
                descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                @Suppress("DEPRECATION")
                gatt.writeDescriptor(descriptor)
            }
            Timber.d("Subscribed to characteristic: ${characteristic.uuid}")
        }
    }

    fun write(characteristic: BluetoothGattCharacteristic, value: ByteArray) {
        val gatt = bluetoothGatt ?: return
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            gatt.writeCharacteristic(characteristic, value, characteristic.writeType)
        } else {
            @Suppress("DEPRECATION")
            characteristic.value = value
            @Suppress("DEPRECATION")
            gatt.writeCharacteristic(characteristic)
        }
        Timber.d("Wrote to characteristic: ${characteristic.uuid} value: ${value.toHexString()}")
    }

    fun writeCommand(command: Byte, data: ByteArray) {
        val gatt = bluetoothGatt ?: return
        val whoopServiceUuid = if (currentDeviceType == "maverick") WHOOP_GEN4_SERVICE else WHOOP_GEN5_SERVICE
        val whoopService = gatt.getService(whoopServiceUuid) ?: return
        val baseUuid = whoopServiceUuid.toString()
        val c3Uuid = UUID.fromString(baseUuid.replace("0001", "0003"))
        val c3 = whoopService.getCharacteristic(c3Uuid) ?: return

        val frame = com.grape.mobile.ffi.GrapeRustBridge.buildCommandFrame(commandSequence, command, data)
        commandSequence = ((commandSequence + 1) % 256).toByte()
        write(c3, frame)
    }

    private fun subscribeToCharacteristics(gatt: BluetoothGatt) {
        Timber.d("Auditing and subscribing to notifying characteristics...")
        gatt.services.forEach { service ->
            service.characteristics.forEach { characteristic ->
                val properties = characteristic.properties
                val isNotify = (properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0
                val isIndicate = (properties and BluetoothGattCharacteristic.PROPERTY_INDICATE) != 0
                if (isNotify || isIndicate) {
                    Timber.d("Subscribing to characteristic: ${characteristic.uuid} in service: ${service.uuid}")
                    subscribe(characteristic)
                }
            }
        }

        // Detect device type based on discovered services (kept for parsing and framing type)
        val hasGen4 = gatt.getService(WHOOP_GEN4_SERVICE) != null
        currentDeviceType = if (hasGen4) "maverick" else "puffin"
        frameAccumulator = FrameAccumulator(currentDeviceType)
        repository.setSyncStage("Notifications enabled")
    }

    private var packetCount = 0
    private fun recordRawPacket(serviceUuid: String?, characteristicUuid: String, value: ByteArray) {
        val timestamp = System.currentTimeMillis()
        val hex = value.joinToString("") { "%02x".format(it) }
        
        // 1. Save to SQLite
        var db: android.database.sqlite.SQLiteDatabase? = null
        try {
            val dbPath = context.filesDir.absolutePath + "/grape.sqlite"
            db = android.database.sqlite.SQLiteDatabase.openOrCreateDatabase(dbPath, null)
            val cv = android.content.ContentValues().apply {
                put("session_id", "LIVE_CAPTURE")
                put("packet_type", serviceUuid ?: "UNKNOWN")
                put("timestamp", timestamp)
                put("source", characteristicUuid)
                put("frame_hex", hex)
            }
            db.insert("historical_packets", null, cv)
        } catch (e: Exception) {
            Timber.e(e, "Error saving raw packet to SQLite")
        } finally {
            db?.close()
        }
        
        // 2. Save to file
        try {
            packetCount++
            val dir = context.getExternalFilesDir("reports/raw_packets")
            if (dir != null) {
                if (!dir.exists()) dir.mkdirs()
                val filename = "packet_${String.format("%05d", packetCount)}.bin"
                val file = java.io.File(dir, filename)
                file.writeBytes(value)
            }
        } catch (e: Exception) {
            Timber.e(e, "Error saving raw packet to file")
        }
    }

    private fun writeServiceDumpReport(gatt: BluetoothGatt) {
        try {
            val dir = context.getExternalFilesDir("reports")
            if (dir != null) {
                if (!dir.exists()) dir.mkdirs()
                val file = java.io.File(dir, "service_dump.md")
                val sb = java.lang.StringBuilder()
                sb.append("# BLE Service Discovery Dump\n\n")
                sb.append("- **Device Name**: ${gatt.device.name ?: "Unknown"}\n")
                sb.append("- **Device MAC**: ${gatt.device.address}\n")
                sb.append("- **Timestamp**: ${java.util.Date()}\n\n")
                sb.append("## Discovered Services and Characteristics\n\n")
                
                gatt.services.forEach { service ->
                    sb.append("### Service: ${service.uuid}\n")
                    service.characteristics.forEach { characteristic ->
                        val properties = getPropertiesString(characteristic.properties)
                        sb.append("- Characteristic: `${characteristic.uuid}` (Properties: $properties)\n")
                    }
                    sb.append("\n")
                }
                
                file.writeText(sb.toString())
                Timber.d("Service discovery dump report written to ${file.absolutePath}")
            }
        } catch (e: Exception) {
            Timber.e(e, "Error writing service dump report")
        }
    }

    private fun getPropertiesString(properties: Int): String {
        val list = mutableListOf<String>()
        if ((properties and BluetoothGattCharacteristic.PROPERTY_READ) != 0) list.add("READ")
        if ((properties and BluetoothGattCharacteristic.PROPERTY_WRITE) != 0) list.add("WRITE")
        if ((properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0) list.add("NOTIFY")
        if ((properties and BluetoothGattCharacteristic.PROPERTY_INDICATE) != 0) list.add("INDICATE")
        return list.joinToString(", ")
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
