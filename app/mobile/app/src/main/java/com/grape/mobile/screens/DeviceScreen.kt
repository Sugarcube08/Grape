package com.grape.mobile.screens

import android.bluetooth.BluetoothDevice
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Refresh
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
import com.grape.mobile.repository.DeviceRepository
import com.grape.mobile.ui.components.BackgroundContainer
import com.grape.mobile.ui.components.GlassCard
import com.grape.mobile.theme.*
import kotlinx.coroutines.flow.collect
import java.util.UUID

@Composable
fun DeviceScreen(
    bleManager: GrapeBleManager,
    repository: DeviceRepository
) {
    val context = LocalContext.current
    val bleState by bleManager.state.collectAsState()
    val uiState by repository.uiState.collectAsState()
    val discoveredDevices by bleManager.discoveredDevices.collectAsState()

    var syncProgress by remember { mutableStateOf<String?>(null) }
    var currentSessionId by remember { mutableStateOf<String?>(null) }
    var syncStartTime by remember { mutableStateOf(0L) }
    var syncPercent by remember { mutableStateOf(0) }
    var syncBytesStr by remember { mutableStateOf("0.0 MB") }
    var syncEtaStr by remember { mutableStateOf("Calculating...") }

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

    BackgroundContainer {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp)
                    .padding(top = 24.dp, bottom = 140.dp) // Avoid navigation overlap
            ) {
                // Header
                Text(
                    text = "DEVICE SETTINGS",
                    style = GrapeTypography.labelSmall,
                    color = TextSecondary,
                    letterSpacing = 1.5.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Device Connection Hero Card
                val statusColor = when (bleState) {
                    BleState.Connected, BleState.Subscribed, BleState.Monitoring -> RecoveryGreen
                    BleState.Scanning, BleState.Connecting -> GrapePrimary
                    else -> StressRed
                }

                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = if (bleState == BleState.Connected || bleState == BleState.Subscribed || bleState == BleState.Monitoring) "WHOOP 5.0" else "No Device Connected",
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
                                    text = bleState.name.uppercase(),
                                    style = GrapeTypography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = statusColor
                                )
                            }
                        }

                        if (bleState == BleState.Connected || bleState == BleState.Subscribed || bleState == BleState.Monitoring) {
                            Button(
                                onClick = { bleManager.disconnect() },
                                colors = ButtonDefaults.buttonColors(containerColor = StressRed.copy(alpha = 0.8f)),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("DISCONNECT", color = Color.White, style = GrapeTypography.labelLarge, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Scan results display
                if (bleState == BleState.Scanning || bleState == BleState.Discovered) {
                    Text(
                        text = "DISCOVERED DEVICES",
                        style = GrapeTypography.labelSmall,
                        color = TextSecondary,
                        letterSpacing = 1.5.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 10.dp)
                    )

                    if (discoveredDevices.isEmpty()) {
                        GlassCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(120.dp),
                            backgroundColor = Color.White.copy(alpha = 0.03f)
                        ) {
                            Column(
                                modifier = Modifier.fillMaxSize(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                CircularProgressIndicator(color = GrapePrimary, modifier = Modifier.size(24.dp))
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("Searching for WHOOP straps...", style = GrapeTypography.bodyMedium, color = TextSecondary)
                            }
                        }
                    } else {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            items(discoveredDevices) { device ->
                                GlassCard(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { bleManager.connect(device.address) },
                                    cornerRadius = 16.dp,
                                    backgroundColor = Color.White.copy(alpha = 0.04f)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column {
                                            Text(
                                                text = device.name ?: "Unknown WHOOP",
                                                style = GrapeTypography.titleLarge,
                                                color = TextPrimary
                                            )
                                            Text(
                                                text = device.address,
                                                style = GrapeTypography.bodyMedium,
                                                color = TextSecondary
                                            )
                                        }
                                        Text(
                                            text = "PAIR",
                                            color = GrapeAccent,
                                            style = GrapeTypography.labelLarge,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }
                } else {
                    // Device stats & sync progress
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                    ) {
                        // Sync Progress Card
                        Text(
                            text = "HISTORICAL DATA SYNC",
                            style = GrapeTypography.labelSmall,
                            color = TextSecondary,
                            letterSpacing = 1.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(10.dp))

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
                                    // Inactive state displays instructions
                                    Column(modifier = Modifier.fillMaxWidth()) {
                                        Text("No Sync Session", style = GrapeTypography.headlineMedium, color = TextPrimary)
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text("Connect a wearable to synchronize history.", style = GrapeTypography.bodyMedium, color = TextSecondary)
                                    }
                                }

                                if (currentSessionId == null && (bleState == BleState.Connected || bleState == BleState.Subscribed || bleState == BleState.Monitoring)) {
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Button(
                                        onClick = {
                                            val newSessionId = UUID.randomUUID().toString()
                                            syncStartTime = System.currentTimeMillis()
                                            currentSessionId = newSessionId
                                            repository.beginHistoricalSync(context, newSessionId)
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = GrapePrimary),
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Text("SYNC HISTORY", color = Color.White, style = GrapeTypography.labelLarge, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        // Strap Metrics Card
                        Text(
                            text = "HARDWARE & PERFORMANCE",
                            style = GrapeTypography.labelSmall,
                            color = TextSecondary,
                            letterSpacing = 1.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(10.dp))

                        GlassCard(modifier = Modifier.fillMaxWidth()) {
                            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                HardwareRow(label = "Battery Level", value = "${uiState.batteryPercent}%")
                                HardwareRow(label = "Firmware Version", value = "41.17")
                                HardwareRow(label = "Hardware Model", value = "WHOOP Band 5.0")
                                HardwareRow(label = "Parsed Packets", value = "${uiState.packetsReceived}")
                                HardwareRow(label = "Parsed Frames", value = "${uiState.framesParsed}")
                                HardwareRow(label = "Last Connection", value = "Just now")
                            }
                        }
                    }
                }
            }

            // FAB for Scan (in bottom right corner)
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
