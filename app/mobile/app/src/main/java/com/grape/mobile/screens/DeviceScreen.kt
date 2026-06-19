package com.grape.mobile.screens

import android.bluetooth.BluetoothDevice
import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.grape.mobile.ble.BleState
import com.grape.mobile.ble.GrapeBleManager
import com.grape.mobile.ble.GrapeBleService
import com.grape.mobile.ble.ReplayManager
import com.grape.mobile.cdm.CompanionAssociationManager
import kotlinx.coroutines.launch
import com.grape.mobile.cdm.DeviceDiagnostics
import com.grape.mobile.cdm.AssociationState
import com.grape.mobile.cdm.DiagnosticStatus
import com.grape.mobile.cdm.DiagnosticItem
import com.grape.mobile.repository.DeviceRepository
import com.grape.mobile.repository.DeviceSettingsRepository
import com.grape.mobile.repository.DeviceInfo
import com.grape.mobile.ui.components.BackgroundContainer
import com.grape.mobile.ui.components.GlassCard
import com.grape.mobile.theme.*
import kotlinx.coroutines.flow.collect
import org.koin.compose.koinInject
import java.util.UUID

@Composable
fun DeviceScreen(
    bleManager: GrapeBleManager,
    repository: DeviceRepository,
    associationManager: CompanionAssociationManager = koinInject(),
    diagnostics: DeviceDiagnostics = koinInject(),
    settingsRepository: DeviceSettingsRepository = koinInject()
) {
    val context = LocalContext.current
    val bleState by bleManager.state.collectAsState()
    val uiState by repository.uiState.collectAsState()
    val discoveredDevices by bleManager.discoveredDevices.collectAsState()
    val associationState by associationManager.state.collectAsState()

    val scanCallbackFiring by bleManager.scanCallbackFiring.collectAsState()
    val advertisementsSeen by bleManager.advertisementsSeen.collectAsState()
    val whoopVisible by bleManager.whoopVisible.collectAsState()
    val servicesDiscoveredCount by bleManager.servicesDiscoveredCount.collectAsState()
    val characteristicsCount by bleManager.characteristicsCount.collectAsState()
    val notificationsEnabledCount by bleManager.notificationsEnabledCount.collectAsState()
    val packetsReceivedCount by bleManager.packetsReceivedCount.collectAsState()
    val parserSuccessPercent by bleManager.parserSuccessPercent.collectAsState()

    var syncProgress by remember { mutableStateOf<String?>(null) }
    var currentSessionId by remember { mutableStateOf<String?>(null) }
    var syncStartTime by remember { mutableStateOf(0L) }
    var syncPercent by remember { mutableStateOf(0) }
    var syncBytesStr by remember { mutableStateOf("0.0 MB") }
    var syncEtaStr by remember { mutableStateOf("Calculating...") }

    var showDiagnostics by remember { mutableStateOf(false) }
    var diagnosticItems by remember { mutableStateOf<List<DiagnosticItem>>(emptyList()) }

    // Fetch primary association details from DB
    val primaryPair = remember(associationState) {
        associationManager.getAssociations().firstOrNull()
    }

    var deviceInfo by remember { mutableStateOf<DeviceInfo?>(null) }
    LaunchedEffect(primaryPair, uiState) {
        deviceInfo = primaryPair?.first?.let { mac ->
            settingsRepository.getDeviceInfo(mac)
        }
    }

    // Monitor sync progress if running
    LaunchedEffect(currentSessionId) {
        val sessionId = currentSessionId ?: return@LaunchedEffect
        repository.queryHistoricalProgress(sessionId).collect { progress ->
            if (progress != null) {
                val status = progress.status
                val packets = progress.packetsDownloaded
                val bytes = progress.bytesDownloaded
                syncBytesStr = String.format("%.2f MB", bytes / (1024.0 * 1024.0))
                val oldest = progress.oldestPage ?: 0L
                val newest = progress.newestPage ?: 0L
                val totalPages = (newest - oldest).coerceAtLeast(1)
                val totalExpectedPackets = totalPages * 50
                
                syncPercent = if (totalExpectedPackets > 0) {
                    ((packets.toFloat() / totalExpectedPackets.toFloat()) * 100).toInt().coerceIn(0, 100)
                } else 0

                val elapsedMs = System.currentTimeMillis() - syncStartTime
                val elapsedSec = elapsedMs / 1000.0
                val rate = if (elapsedSec > 0) packets / elapsedSec else 0.0
                val remainingPackets = (totalExpectedPackets - packets).coerceAtLeast(0)
                
                syncEtaStr = if (rate > 0 && remainingPackets > 0) {
                    val etaSec = remainingPackets / rate
                    if (etaSec > 60) {
                        String.format("%d m %d s", etaSec.toInt() / 60, etaSec.toInt() % 60)
                    } else {
                        String.format("%d s", etaSec.toInt())
                    }
                } else {
                    "Calculating..."
                }

                syncProgress = "Status: $status"

                if (progress.status == "Completed" || progress.status == "Failed") {
                    if (progress.status == "Completed") {
                        syncProgress = null
                        currentSessionId = null
                    }
                }
            }
        }
    }

    val isConnected = bleState == BleState.Connected || bleState == BleState.Subscribed || bleState == BleState.Monitoring
    val notificationsEnabled = notificationsEnabledCount > 0
    val syncEnabled = isConnected && notificationsEnabled && packetsReceivedCount > 0

    BackgroundContainer {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp)
                    .padding(top = 24.dp, bottom = 140.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                // Header
                Text(
                    text = "DEVICE SETTINGS",
                    style = GrapeTypography.labelSmall,
                    color = TextSecondary,
                    letterSpacing = 1.5.sp,
                    fontWeight = FontWeight.Bold
                )
                    // Device Connection Hero Card
                    val (statusText, statusColor) = remember(bleState, syncProgress) {
                        if (syncProgress != null && (syncProgress!!.contains("Downloading") || syncProgress!!.contains("Parsing") || syncProgress!!.contains("Persisting"))) {
                            Pair("HISTORICAL SYNC ACTIVE", Color(0xFF8B5CF6)) // Purple
                        } else {
                            when (bleState) {
                                BleState.Idle -> Pair("DISCONNECTED", Color(0xFFEF4444)) // Red
                                BleState.Scanning -> Pair("DISCOVERING SERVICES", Color(0xFFF59E0B)) // Orange
                                BleState.Discovered -> Pair("DISCOVERING SERVICES", Color(0xFFF59E0B)) // Orange
                                BleState.Connecting -> Pair("CONNECTING", Color(0xFFF59E0B)) // Orange
                                BleState.Connected -> Pair("CONNECTED", Color(0xFF22C55E)) // Green
                                BleState.Subscribed -> Pair("SUBSCRIBING", Color(0xFFF59E0B)) // Orange
                                BleState.Monitoring -> Pair("RECEIVING PACKETS", Color(0xFF22C55E)) // Green
                                BleState.Disconnected -> Pair("DISCONNECTED", Color(0xFFEF4444)) // Red
                            }
                        }
                    }

                    GlassCard(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(
                                    text = if (bleState == BleState.Connected || bleState == BleState.Subscribed || bleState == BleState.Monitoring) {
                                        primaryPair?.second ?: "WHOOP Strap"
                                    } else "No Device Connected",
                                    style = GrapeTypography.displaySmall,
                                    color = TextPrimary
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(statusColor)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = statusText,
                                        style = GrapeTypography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                        color = statusColor
                                    )
                                }
                            }

                            if (bleState == BleState.Connected || bleState == BleState.Subscribed || bleState == BleState.Monitoring) {
                                Button(
                                    onClick = { bleManager.disconnect() },
                                    colors = ButtonDefaults.buttonColors(containerColor = StressRed.copy(alpha = 0.8f)),
                                    modifier = Modifier.width(140.dp).height(52.dp),
                                    shape = RoundedCornerShape(12.dp),
                                    contentPadding = PaddingValues(horizontal = 4.dp)
                                ) {
                                    Text(
                                        text = "DISCONNECT",
                                        color = Color.White,
                                        style = GrapeTypography.labelLarge,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }

                    // 1. Companion Device Section
                    Text(
                        text = "COMPANION DEVICE MANAGER",
                        style = GrapeTypography.labelSmall,
                        color = TextSecondary,
                        letterSpacing = 1.5.sp,
                        fontWeight = FontWeight.Bold
                    )

                    GlassCard(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            if (primaryPair != null) {
                                // Associated device info
                                Text("Associated Companion", style = GrapeTypography.titleLarge, color = TextPrimary)
                                Spacer(modifier = Modifier.height(8.dp))
                                
                                HardwareRow(label = "Device Name", value = primaryPair.second)
                                HardwareRow(label = "MAC Address", value = primaryPair.first)
                                HardwareRow(label = "Association State", value = "Associated")
                                
                                Spacer(modifier = Modifier.height(16.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Button(
                                        onClick = {
                                            val intent = Intent(context, GrapeBleService::class.java).apply {
                                                action = GrapeBleService.ACTION_CONNECT_PRIMARY
                                            }
                                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                                                context.startForegroundService(intent)
                                            } else {
                                                context.startService(intent)
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = GrapePrimary),
                                        modifier = Modifier.weight(1f).height(52.dp),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Text("RECONNECT", color = Color.White, style = GrapeTypography.labelMedium, fontWeight = FontWeight.Bold)
                                    }

                                    val forgetInteractionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                                    val forgetPressed by forgetInteractionSource.collectIsPressedAsState()
                                    val forgetBg = if (forgetPressed) StressRed else GrapePrimary.copy(alpha = 0.15f)
                                    val forgetText = if (forgetPressed) Color.White else TextPrimary
                                    val forgetBorder = if (forgetPressed) null else androidx.compose.foundation.BorderStroke(1.dp, GrapePrimary.copy(alpha = 0.3f))

                                    Button(
                                        onClick = {
                                            associationManager.disassociate(primaryPair.first)
                                        },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = forgetBg,
                                            contentColor = forgetText
                                        ),
                                        border = forgetBorder,
                                        interactionSource = forgetInteractionSource,
                                        modifier = Modifier.weight(1f).height(52.dp),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Text("FORGET DEVICE", color = forgetText, style = GrapeTypography.labelMedium, fontWeight = FontWeight.Bold)
                                    }
                                }
                            } else {
                                // Unassociated view
                                Text("No Companion Associated", style = GrapeTypography.titleLarge, color = TextPrimary)
                                Spacer(modifier = Modifier.height(6.dp))
                                Text("Associate Grape with WHOOP to enable seamless background syncing and survive aggressive battery management policies.", style = GrapeTypography.bodyMedium, color = TextSecondary)
                                
                                Spacer(modifier = Modifier.height(16.dp))

                                if (discoveredDevices.isNotEmpty()) {
                                    Text("DISCOVERED DEVICES", style = GrapeTypography.labelSmall, color = TextSecondary, letterSpacing = 1.sp)
                                    Spacer(modifier = Modifier.height(8.dp))
                                    discoveredDevices.forEach { device ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(Color.White.copy(alpha = 0.05f))
                                                .padding(12.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column {
                                                Text(device.name ?: "Unknown", style = GrapeTypography.bodyLarge, color = TextPrimary, fontWeight = FontWeight.Bold)
                                                Text(device.address, style = GrapeTypography.bodyMedium, color = TextSecondary)
                                            }
                                            Button(
                                                onClick = {
                                                    associationManager.associateDeviceDirectly(device.address, device.name ?: "WHOOP Device")
                                                    val intent = Intent(context, GrapeBleService::class.java).apply {
                                                        action = GrapeBleService.ACTION_CONNECT_PRIMARY
                                                    }
                                                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                                                        context.startForegroundService(intent)
                                                    } else {
                                                        context.startService(intent)
                                                    }
                                                },
                                                colors = ButtonDefaults.buttonColors(containerColor = GrapeAccent),
                                                shape = RoundedCornerShape(8.dp)
                                            ) {
                                                Text("SELECT", color = Color.White, style = GrapeTypography.labelMedium, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(8.dp))
                                    }
                                }

                                if (isEmulator()) {
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(StressRed.copy(alpha = 0.1f))
                                            .border(1.dp, StressRed.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                                            .padding(16.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text(
                                            text = "BLE unavailable",
                                            color = StressRed,
                                            style = GrapeTypography.titleMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "Running on Android Emulator",
                                            color = TextPrimary,
                                            style = GrapeTypography.bodyMedium,
                                            modifier = Modifier.padding(top = 4.dp)
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            text = "Physical device required\nfor wearable testing",
                                            color = TextSecondary,
                                            style = GrapeTypography.bodySmall,
                                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                        )
                                    }
                                } else if (bleState == BleState.Scanning) {
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(16.dp),
                                            color = GrapePrimary,
                                            strokeWidth = 2.dp
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Scanning for WHOOP straps...", style = GrapeTypography.bodyMedium, color = TextSecondary)
                                    }
                                } else {
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Button(
                                        onClick = {
                                            bleManager.scan()
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = GrapePrimary),
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Text("SCAN FOR DEVICES", color = Color.White, style = GrapeTypography.labelLarge, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }

                        }
                    }

                    Button(
                        onClick = {
                            diagnosticItems = diagnostics.runDiagnostics()
                            showDiagnostics = true
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.05f)),
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.10f))
                    ) {
                        Text("RUN DIAGNOSTICS", color = TextPrimary, style = GrapeTypography.labelLarge, fontWeight = FontWeight.Bold)
                    }

                    // 2. Diagnostics Results (Animated Panel)
                    AnimatedVisibility(visible = showDiagnostics) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "SYNC DIAGNOSTICS",
                                    style = GrapeTypography.labelSmall,
                                    color = TextSecondary,
                                    letterSpacing = 1.5.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                IconButton(onClick = { showDiagnostics = false }) {
                                    Icon(imageVector = Icons.Default.Close, contentDescription = "Close Diagnostics", tint = TextSecondary)
                                }
                            }
                            Spacer(modifier = Modifier.height(10.dp))

                            GlassCard(modifier = Modifier.fillMaxWidth()) {
                                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    diagnosticItems.forEach { item ->
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(text = item.name, style = GrapeTypography.bodyLarge, color = TextPrimary, fontWeight = FontWeight.Bold)
                                                if (item.details.isNotEmpty()) {
                                                    Text(text = item.details, style = GrapeTypography.bodySmall, color = TextSecondary)
                                                }
                                            }
                                            
                                            when (item.status) {
                                                DiagnosticStatus.PASS -> {
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        Icon(imageVector = Icons.Default.CheckCircle, contentDescription = "Pass", tint = RecoveryGreen, modifier = Modifier.size(20.dp))
                                                        Spacer(modifier = Modifier.width(4.dp))
                                                        Text("PASS", color = RecoveryGreen, style = GrapeTypography.labelSmall.copy(fontWeight = FontWeight.Bold))
                                                    }
                                                }
                                                DiagnosticStatus.WARN -> {
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        Icon(imageVector = Icons.Default.Warning, contentDescription = "Warning", tint = GrapeAccent, modifier = Modifier.size(20.dp))
                                                        Spacer(modifier = Modifier.width(4.dp))
                                                        Text("WARN", color = GrapeAccent, style = GrapeTypography.labelSmall.copy(fontWeight = FontWeight.Bold))
                                                    }
                                                }
                                                DiagnosticStatus.FAIL -> {
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        Icon(imageVector = Icons.Default.Info, contentDescription = "Fail", tint = StressRed, modifier = Modifier.size(20.dp))
                                                        Spacer(modifier = Modifier.width(4.dp))
                                                        Text("FAIL", color = StressRed, style = GrapeTypography.labelSmall.copy(fontWeight = FontWeight.Bold))
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // 3. Historical Sync Card
                    Text(
                        text = "HISTORICAL DATA SYNC",
                        style = GrapeTypography.labelSmall,
                        color = TextSecondary,
                        letterSpacing = 1.5.sp,
                        fontWeight = FontWeight.Bold
                    )

                    GlassCard(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            if (currentSessionId != null) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Sync Progress", style = GrapeTypography.titleLarge, color = TextPrimary)
                                    Text("$syncPercent%", style = GrapeTypography.displayMedium, color = GrapeAccent)
                                }
                                Spacer(modifier = Modifier.height(12.dp))
                                LinearProgressIndicator(
                                    progress = { syncPercent / 100f },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(8.dp)
                                        .clip(RoundedCornerShape(4.dp)),
                                    color = GrapeAccent,
                                    trackColor = Color.White.copy(alpha = 0.05f)
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    SyncStatDetail(label = "Downloaded", value = syncBytesStr)
                                    SyncStatDetail(label = "Packets", value = "${uiState.packetsReceived}")
                                    SyncStatDetail(label = "ETA", value = syncEtaStr)
                                }
                            } else {
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    if (packetsReceivedCount == 0) {
                                        Text("Historical Sync", style = GrapeTypography.headlineMedium, color = TextPrimary)
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text("Unavailable", style = GrapeTypography.bodyMedium, color = StressRed, fontWeight = FontWeight.Bold)
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text("No wearable packets available. Replay mode recommended.", style = GrapeTypography.bodyMedium, color = TextSecondary)
                                    } else {
                                        Text("No Sync Session", style = GrapeTypography.headlineMedium, color = TextPrimary)
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text("Connect a wearable to synchronize history.", style = GrapeTypography.bodyMedium, color = TextSecondary)
                                    }
                                }
                            }

                            if (currentSessionId == null) {
                                Spacer(modifier = Modifier.height(16.dp))
                                val buttonText = when {
                                    !isConnected -> "Connect wearable first"
                                    !notificationsEnabled -> "Enable notifications first"
                                    packetsReceivedCount == 0 -> "No wearable packets available"
                                    else -> "SYNC HISTORY"
                                }
                                Button(
                                    onClick = {
                                        val newSessionId = UUID.randomUUID().toString()
                                        syncStartTime = System.currentTimeMillis()
                                        currentSessionId = newSessionId
                                        repository.beginHistoricalSync(context, newSessionId)
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (syncEnabled) GrapePrimary else Color.White.copy(alpha = 0.05f),
                                        disabledContainerColor = Color.White.copy(alpha = 0.05f)
                                    ),
                                    modifier = Modifier.fillMaxWidth().height(52.dp),
                                    shape = RoundedCornerShape(12.dp),
                                    enabled = syncEnabled
                                ) {
                                    Text(
                                        text = buttonText,
                                        color = if (syncEnabled) Color.White else TextSecondary,
                                        style = GrapeTypography.labelLarge,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }

                    // 4. Hardware Card
                    Text(
                        text = "HARDWARE & PERFORMANCE",
                        style = GrapeTypography.labelSmall,
                        color = TextSecondary,
                        letterSpacing = 1.5.sp,
                        fontWeight = FontWeight.Bold
                    )

                    GlassCard(modifier = Modifier.fillMaxWidth()) {
                        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            HardwareRow(label = "Manufacturer", value = deviceInfo?.manufacturer ?: "--")
                            HardwareRow(label = "Serial Number", value = deviceInfo?.serialNumber ?: "--")
                            HardwareRow(label = "Hardware Revision", value = deviceInfo?.hardwareRevision ?: "--")
                            HardwareRow(label = "Firmware Version", value = deviceInfo?.firmwareRevision ?: "--")
                            HardwareRow(label = "Battery Level", value = deviceInfo?.batteryLevel?.let { "$it%" } ?: "${uiState.batteryPercent}%")
                            HardwareRow(label = "Parsed Packets", value = "${uiState.packetsReceived}")
                            HardwareRow(label = "Parsed Frames", value = "${uiState.framesParsed}")
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // 5. BLE Developer / Connection Diagnostics
                    Text(
                        text = "BLE DEVELOPER DIAGNOSTICS",
                        style = GrapeTypography.labelSmall,
                        color = TextSecondary,
                        letterSpacing = 1.5.sp,
                        fontWeight = FontWeight.Bold
                    )

                    GlassCard(modifier = Modifier.fillMaxWidth()) {
                        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            HardwareRow(label = "Scan callback firing", value = if (scanCallbackFiring) "YES" else "NO")
                            HardwareRow(label = "Advertisements seen", value = advertisementsSeen.toString())
                            HardwareRow(label = "WHOOP visible", value = if (whoopVisible) "YES" else "NO")
                            HardwareRow(label = "Services discovered", value = servicesDiscoveredCount.toString())
                            HardwareRow(label = "Characteristics", value = characteristicsCount.toString())
                            HardwareRow(label = "Notifications enabled", value = notificationsEnabledCount.toString())
                            HardwareRow(label = "Packets received", value = packetsReceivedCount.toString())
                            HardwareRow(label = "Parser success", value = String.format(java.util.Locale.US, "%.1f%%", parserSuccessPercent))

                            HorizontalDivider(color = Color.White.copy(alpha = 0.05f))

                            Text("Packet Replay Simulator", style = GrapeTypography.titleMedium, color = TextPrimary)
                            Text("Feed mock wearable data to test physiology metrics and baseline calculations without physical hardware.", style = GrapeTypography.bodyMedium, color = TextSecondary)

                            var replayingGen4 by remember { mutableStateOf(false) }
                            var replayingGen5 by remember { mutableStateOf(false) }
                            var replayingSleep by remember { mutableStateOf(false) }
                            var replayingRecovery by remember { mutableStateOf(false) }
                            var replayingStress by remember { mutableStateOf(false) }
                            var replayingStrain by remember { mutableStateOf(false) }
                            var replayMessage by remember { mutableStateOf<String?>(null) }
                            val scope = rememberCoroutineScope()

                            if (replayMessage != null) {
                                Text(
                                    text = replayMessage!!,
                                    color = RecoveryGreen,
                                    style = GrapeTypography.bodyMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            val allReplayDisabled = replayingGen4 || replayingGen5 || replayingSleep || replayingRecovery || replayingStress || replayingStrain

                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Button(
                                        onClick = {
                                            scope.launch {
                                                replayingGen4 = true
                                                val res = ReplayManager.replayAssetDatabase(context, "gen4_packets.sqlite", repository)
                                                replayingGen4 = false
                                                replayMessage = if (res.isSuccess) "Gen 4 replayed ${res.getOrNull()} packets!" else "Failed: ${res.exceptionOrNull()?.message}"
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = GrapePrimary),
                                        modifier = Modifier.weight(1f).height(52.dp),
                                        shape = RoundedCornerShape(12.dp),
                                        enabled = !allReplayDisabled
                                    ) {
                                        Text(if (replayingGen4) "REPLAYING..." else "Replay Gen4", color = Color.White, style = GrapeTypography.labelMedium, fontWeight = FontWeight.Bold)
                                    }

                                    Button(
                                        onClick = {
                                            scope.launch {
                                                replayingGen5 = true
                                                val res = ReplayManager.replayAssetDatabase(context, "gen5_packets.sqlite", repository)
                                                replayingGen5 = false
                                                replayMessage = if (res.isSuccess) "Gen 5 replayed ${res.getOrNull()} packets!" else "Failed: ${res.exceptionOrNull()?.message}"
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = GrapeAccent),
                                        modifier = Modifier.weight(1f).height(52.dp),
                                        shape = RoundedCornerShape(12.dp),
                                        enabled = !allReplayDisabled
                                    ) {
                                        Text(if (replayingGen5) "REPLAYING..." else "Replay Gen5", color = Color.White, style = GrapeTypography.labelMedium, fontWeight = FontWeight.Bold)
                                    }
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Button(
                                        onClick = {
                                            scope.launch {
                                                replayingSleep = true
                                                repository.insertMockSleepSession(
                                                    startTimeUnixMs = System.currentTimeMillis() - 8 * 3600 * 1000,
                                                    endTimeUnixMs = System.currentTimeMillis(),
                                                    remMinutes = 95.0,
                                                    deepMinutes = 110.0,
                                                    coreMinutes = 235.0,
                                                    awakeMinutes = 40.0
                                                )
                                                repository.refreshState()
                                                replayingSleep = false
                                                replayMessage = "Replayed Sleep session!"
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.05f)),
                                        modifier = Modifier.weight(1f).height(52.dp),
                                        shape = RoundedCornerShape(12.dp),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.10f)),
                                        enabled = !allReplayDisabled
                                    ) {
                                        Text(if (replayingSleep) "REPLAYING..." else "Replay Sleep", color = TextPrimary, style = GrapeTypography.labelMedium, fontWeight = FontWeight.Bold)
                                    }

                                    Button(
                                        onClick = {
                                            scope.launch {
                                                replayingRecovery = true
                                                repository.insertMockRecoveryMetric(
                                                    startTimeUnixMs = System.currentTimeMillis() - 24 * 3600 * 1000,
                                                    endTimeUnixMs = System.currentTimeMillis(),
                                                    restingHr = 56.0,
                                                    hrv = 75.0,
                                                    tempDelta = -0.1
                                                )
                                                repository.refreshState()
                                                replayingRecovery = false
                                                replayMessage = "Replayed Recovery metric!"
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.05f)),
                                        modifier = Modifier.weight(1f).height(52.dp),
                                        shape = RoundedCornerShape(12.dp),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.10f)),
                                        enabled = !allReplayDisabled
                                    ) {
                                        Text(if (replayingRecovery) "REPLAYING..." else "Replay Recovery", color = TextPrimary, style = GrapeTypography.labelMedium, fontWeight = FontWeight.Bold)
                                    }
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Button(
                                        onClick = {
                                            scope.launch {
                                                replayingStress = true
                                                repository.insertMockStressMetric(
                                                    stressScore = 0.38,
                                                    state = "Low",
                                                    hrvContribution = 0.22,
                                                    tempContribution = 0.08,
                                                    timestamp = System.currentTimeMillis()
                                                )
                                                repository.refreshState()
                                                replayingStress = false
                                                replayMessage = "Replayed Stress metric!"
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.05f)),
                                        modifier = Modifier.weight(1f).height(52.dp),
                                        shape = RoundedCornerShape(12.dp),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.10f)),
                                        enabled = !allReplayDisabled
                                    ) {
                                        Text(if (replayingStress) "REPLAYING..." else "Replay Stress", color = TextPrimary, style = GrapeTypography.labelMedium, fontWeight = FontWeight.Bold)
                                    }

                                    Button(
                                        onClick = {
                                            scope.launch {
                                                replayingStrain = true
                                                repository.insertMockStrainMetric(
                                                    strainScore = 14.5,
                                                    averageHr = 132.0,
                                                    maxHr = 175.0,
                                                    calories = 2400,
                                                    timestamp = System.currentTimeMillis()
                                                )
                                                repository.refreshState()
                                                replayingStrain = false
                                                replayMessage = "Replayed Strain metric!"
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.05f)),
                                        modifier = Modifier.weight(1f).height(52.dp),
                                        shape = RoundedCornerShape(12.dp),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.10f)),
                                        enabled = !allReplayDisabled
                                    ) {
                                        Text(if (replayingStrain) "REPLAYING..." else "Replay Strain", color = TextPrimary, style = GrapeTypography.labelMedium, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }

            // Fallback BLE Scanner FAB
            if (bleState == BleState.Scanning || bleState == BleState.Discovered || bleState == BleState.Idle) {
                FloatingActionButton(
                    onClick = {
                        if (bleState == BleState.Scanning) {
                            bleManager.stopScan()
                        } else {
                            bleManager.scan()
                        }
                    },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(bottom = 100.dp, end = 20.dp), // Adjust for floating bar spacing
                    containerColor = GrapePrimary,
                    contentColor = Color.White,
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(
                        imageVector = if (bleState == BleState.Scanning) Icons.Default.Refresh else Icons.Default.Search,
                        contentDescription = "Scan Toggle"
                    )
                }
            }
        }
    }
}

@Composable
fun SyncStatDetail(label: String, value: String) {
    Column {
        Text(text = label, style = GrapeTypography.labelSmall, color = TextSecondary)
        Text(text = value, style = GrapeTypography.bodyLarge, color = TextPrimary, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun HardwareRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, style = GrapeTypography.bodyLarge, color = TextSecondary)
        Text(text = value, style = GrapeTypography.bodyLarge, color = TextPrimary, fontWeight = FontWeight.Bold)
    }
}

fun isEmulator(): Boolean {
    return (android.os.Build.FINGERPRINT.startsWith("generic")
            || android.os.Build.FINGERPRINT.startsWith("unknown")
            || android.os.Build.MODEL.contains("google_sdk")
            || android.os.Build.MODEL.contains("Emulator")
            || android.os.Build.MODEL.contains("Android SDK built for x86")
            || android.os.Build.MANUFACTURER.contains("Genymotion")
            || (android.os.Build.BRAND.startsWith("generic") && android.os.Build.DEVICE.startsWith("generic"))
            || "google_sdk" == android.os.Build.PRODUCT)
}
