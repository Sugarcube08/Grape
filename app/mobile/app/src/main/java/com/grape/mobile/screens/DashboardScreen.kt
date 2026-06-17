package com.grape.mobile.screens

import android.bluetooth.BluetoothDevice
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.grape.mobile.ble.BleState
import com.grape.mobile.ble.GrapeBleManager
import com.grape.mobile.repository.DeviceRepository
import com.grape.mobile.repository.DeviceUiState

@Composable
fun DashboardScreen(
    bleManager: GrapeBleManager,
    repository: DeviceRepository
) {
    val bleState by bleManager.state.collectAsState()
    val uiState by repository.uiState.collectAsState()
    val discoveredDevices by bleManager.discoveredDevices.collectAsState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp)
        ) {
            // Header
            HeaderSection()

            Spacer(modifier = Modifier.height(20.dp))

            // Connection State Card
            ConnectionStateCard(bleState, uiState) {
                if (bleState == BleState.Idle || bleState == BleState.Disconnected) {
                    bleManager.scan()
                } else if (bleState == BleState.Scanning) {
                    bleManager.stopScan()
                } else {
                    bleManager.disconnect()
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Show list of discovered devices when scanning or discovered
            if (bleState == BleState.Scanning || bleState == BleState.Discovered) {
                DiscoveredDevicesSection(discoveredDevices) { device ->
                    bleManager.connect(device.address)
                }
            } else {
                // Main stats display
                StatsSection(uiState)
                
                Spacer(modifier = Modifier.height(20.dp))

                // Simulation Control Panel
                SimulationControlPanel(repository)
            }
        }
    }
}

@Composable
fun HeaderSection() {
    Column {
        Text(
            text = "GRAPE",
            fontSize = 32.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 2.sp,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = "Wearable Analytics Engine",
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = Color.Gray
        )
    }
}

@Composable
fun ConnectionStateCard(
    bleState: BleState,
    uiState: DeviceUiState,
    onButtonClick: () -> Unit
) {
    val statusColor = when (bleState) {
        BleState.Connected, BleState.Subscribed, BleState.Monitoring -> MaterialTheme.colorScheme.secondary
        BleState.Scanning, BleState.Connecting -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.tertiary
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = "WHOOP CONNECTION",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Gray
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(RoundedCornerShape(5.dp))
                            .background(statusColor)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = bleState.name.uppercase(),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White
                    )
                }
            }

            Button(
                onClick = onButtonClick,
                colors = ButtonDefaults.buttonColors(containerColor = statusColor),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = when (bleState) {
                        BleState.Scanning -> "STOP SCAN"
                        BleState.Connected, BleState.Subscribed, BleState.Monitoring -> "DISCONNECT"
                        else -> "SCAN"
                    },
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun DiscoveredDevicesSection(
    devices: List<BluetoothDevice>,
    onDeviceClick: (BluetoothDevice) -> Unit
) {
    Text(
        text = "DISCOVERED WHOOP BANDS",
        fontSize = 14.sp,
        fontWeight = FontWeight.Bold,
        color = Color.Gray,
        modifier = Modifier.padding(bottom = 8.dp)
    )

    if (devices.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(150.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surface),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterAlignment) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.height(12.dp))
                Text("Searching for WHOOP straps...", color = Color.Gray, fontSize = 12.sp)
            }
        }
    } else {
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(devices) { device ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .clickable { onDeviceClick(device) }
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = device.name ?: "Unknown WHOOP",
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = device.address,
                            fontSize = 12.sp,
                            color = Color.Gray
                        )
                    }
                    Text(
                        text = "CONNECT",
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}

@Composable
fun StatsSection(uiState: DeviceUiState) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Heart Rate Card
            val infiniteTransition = rememberInfiniteTransition(label = "pulse")
            val pulseScale by infiniteTransition.animateFloat(
                initialValue = 0.95f,
                targetValue = 1.05f,
                animationSpec = infiniteRepeatable(
                    animation = tween(600, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "pulse"
            )

            Card(
                modifier = Modifier
                    .weight(1f)
                    .height(180.dp),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(20.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "HEART RATE",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.Gray
                        )
                        Icon(
                            imageVector = Icons.Default.Favorite,
                            contentDescription = "HR Heart",
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier
                                .size(24.dp)
                                .scale(if (uiState.heartRate > 0) pulseScale else 1f)
                        )
                    }

                    Column {
                        Text(
                            text = if (uiState.heartRate > 0) "${uiState.heartRate}" else "--",
                            fontSize = 48.sp,
                            fontWeight = FontWeight.Black,
                            color = Color.White
                        )
                        Text(
                            text = "BPM",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.Gray
                        )
                    }
                }
            }

            // Battery Card
            Card(
                modifier = Modifier
                    .weight(1f)
                    .height(180.dp),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(20.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "STRAP BATTERY",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Gray
                    )

                    Column {
                        Text(
                            text = "${uiState.batteryPercent}%",
                            fontSize = 48.sp,
                            fontWeight = FontWeight.Black,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = { uiState.batteryPercent / 100f },
                            modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                            color = MaterialTheme.colorScheme.secondary,
                            trackColor = Color.DarkGray
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Data Pipeline Metrics
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            MetricsMiniCard(
                title = "PACKETS RECEIVED",
                value = "${uiState.packetsReceived}",
                modifier = Modifier.weight(1f)
            )
            MetricsMiniCard(
                title = "FRAMES PARSED",
                value = "${uiState.framesParsed}",
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
fun MetricsMiniCard(title: String, value: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.height(100.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = title,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Gray
            )
            Text(
                text = value,
                fontSize = 24.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White
            )
        }
    }
}

@Composable
fun SimulationControlPanel(repository: DeviceRepository) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "PIPELINE SIMULATION CONTROL",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Gray
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        // Simulate active state
                        repository.updateConnectionState("CONNECTED")
                        repository.recordHeartRate((60..120).random())
                        repository.recordBattery((50..100).random())
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Simulate Live", fontSize = 10.sp)
                }

                Button(
                    onClick = {
                        // Feed a mock WHOOP 5.0 notification hex packet
                        // Header: AA 00 05 00 <CRC32> Payload: packet_type=40 (Realtime data)
                        val sampleHex = "aa000500123456784011223344"
                        repository.insertWhoopPacket(sampleHex, "puffin")
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Feed Hex Frame", fontSize = 10.sp)
                }

                IconButton(
                    onClick = { repository.refreshState() },
                    modifier = Modifier.background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                ) {
                    Icon(imageVector = Icons.Default.Refresh, contentDescription = "Refresh")
                }
            }
        }
    }
}
