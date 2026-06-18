package com.grape.mobile.screens

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.grape.mobile.R
import com.grape.mobile.repository.DeviceRepository
import com.grape.mobile.ui.components.BackgroundContainer
import com.grape.mobile.ui.components.GlassCard
import com.grape.mobile.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.util.Calendar

@Composable
fun ProfileScreen(repository: DeviceRepository) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val dbPath = remember {
        File(context.filesDir, "grape.sqlite").absolutePath
    }

    BackgroundContainer {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(top = 24.dp, bottom = 140.dp), // Prevent bottom bar overlap
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header Title
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Start
            ) {
                Text(
                    text = "PROFILE & SETTINGS",
                    style = GrapeTypography.labelSmall,
                    color = TextSecondary,
                    letterSpacing = 1.5.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(28.dp))

            // Hero Section: Avatar, Name, Member Since, Recovery Streak
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            colors = listOf(GrapePrimary, GrapeAccent)
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Person,
                    contentDescription = "User Avatar",
                    modifier = Modifier.size(56.dp),
                    tint = Color.White
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Harsh",
                style = GrapeTypography.displaySmall,
                color = TextPrimary,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Member since Jun 2026",
                style = GrapeTypography.labelMedium,
                color = TextSecondary
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Recovery Streak Badge
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(GrapePrimary.copy(alpha = 0.15f))
                    .border(
                        width = 1.dp,
                        color = GrapePrimary.copy(alpha = 0.3f),
                        shape = RoundedCornerShape(16.dp)
                    )
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Favorite,
                    contentDescription = "Streak Icon",
                    tint = GrapeAccent,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "14 Day Recovery Streak",
                    style = GrapeTypography.labelLarge.copy(fontWeight = FontWeight.Bold),
                    color = TextPrimary
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            // --- SECTION: ABOUT ---
            ProfileHeaderSection("ABOUT")
            Spacer(modifier = Modifier.height(8.dp))
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column {
                    Text(
                        text = "Grape Platform",
                        style = GrapeTypography.headlineMedium,
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Grape is a local-first, privacy-focused wearable intelligence platform. It processes raw biosensor packets directly on your device using a high-performance Rust core, keeping your biometric insights entirely offline and secure.",
                        style = GrapeTypography.bodyMedium,
                        lineHeight = 20.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // --- SECTION: VERSION ---
            ProfileHeaderSection("VERSION")
            Spacer(modifier = Modifier.height(8.dp))
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    ProfileInfoRow(label = "Application Version", value = "0.2.0-alpha")
                    ProfileInfoRow(label = "Build Target", value = "Android Native Debug")
                    ProfileInfoRow(label = "Physiological Algorithms", value = "Sleep V1, Recovery V0")
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // --- SECTION: EXPORT ---
            ProfileHeaderSection("EXPORT DATA")
            Spacer(modifier = Modifier.height(8.dp))
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column {
                    Text(
                        text = "Local Storage Backup",
                        style = GrapeTypography.headlineMedium,
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Export the raw SQLite database containing your synchronized heart rate packets, sleep metrics, and calculated recoveries to your device's external storage.",
                        style = GrapeTypography.bodyMedium,
                        lineHeight = 20.sp
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = {
                            try {
                                val dbFile = File(dbPath)
                                val destFile = File(context.getExternalFilesDir(null), "grape_export.sqlite")
                                dbFile.inputStream().use { input ->
                                    destFile.outputStream().use { output ->
                                        input.copyTo(output)
                                    }
                                }
                                Toast.makeText(context, "Database exported: ${destFile.name}", Toast.LENGTH_LONG).show()
                            } catch (e: Exception) {
                                Toast.makeText(context, "Export Failed: ${e.message}", Toast.LENGTH_SHORT).show()
                                Timber.e(e, "DB export error")
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = GrapePrimary),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Share,
                                contentDescription = "Export Icon",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Export Data",
                                color = Color.White,
                                style = GrapeTypography.labelLarge,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // --- SECTION: DIAGNOSTICS ---
            ProfileHeaderSection("DIAGNOSTICS")
            Spacer(modifier = Modifier.height(8.dp))
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    ProfileInfoRow(label = "Rust Core Version", value = "v1.0")
                    ProfileInfoRow(label = "Database Engine", value = "SQLite 3")
                    ProfileInfoRow(label = "Database File", value = "grape.sqlite")
                    ProfileInfoRow(label = "Storage Path", value = "/data/user/0/.../files/")
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // --- SECTION: PRIVACY ---
            ProfileHeaderSection("PRIVACY & SECURITY")
            Spacer(modifier = Modifier.height(8.dp))
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column {
                    Text(
                        text = "100% Offline Processing",
                        style = GrapeTypography.headlineMedium,
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Your biometric information is processed entirely client-side. Grape does not collect telemetry, usage metrics, or synchronize your historical raw packets with any cloud servers.",
                        style = GrapeTypography.bodyMedium,
                        lineHeight = 20.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // --- SECTION: DEVELOPER MODE ---
            ProfileHeaderSection("DEVELOPER MODE")
            Spacer(modifier = Modifier.height(8.dp))
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column {
                    Text(
                        text = "Simulate Biometric Data",
                        style = GrapeTypography.headlineMedium,
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Simulate 5 days of physiological telemetry (RHR, HRV, and sleep stages) to check charts and score calculations.",
                        style = GrapeTypography.bodyMedium,
                        lineHeight = 20.sp
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = {
                            coroutineScope.launch(Dispatchers.IO) {
                                try {
                                    injectMockBiometricData(repository)
                                    withContext(Dispatchers.Main) {
                                        Toast.makeText(context, "Demo data successfully injected!", Toast.LENGTH_SHORT).show()
                                    }
                                } catch (e: Exception) {
                                    withContext(Dispatchers.Main) {
                                        Toast.makeText(context, "Failed to inject data: ${e.message}", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = CyanAccent),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.PlayArrow,
                                contentDescription = "Play Icon",
                                tint = Color(0xFF0F172A),
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Inject Demo Data",
                                color = Color(0xFF0F172A),
                                style = GrapeTypography.labelLarge,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(36.dp))

            // --- SECTION: DANGER ZONE ---
            ProfileHeaderSection("DANGER ZONE")
            Spacer(modifier = Modifier.height(8.dp))
            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                borderColor = StressRed.copy(alpha = 0.3f),
                backgroundColor = StressRed.copy(alpha = 0.04f)
            ) {
                Column {
                    Text(
                        text = "Reset Database Cache",
                        style = GrapeTypography.headlineMedium,
                        color = StressRed,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Permanently delete all synchronized raw packets, sleep stages, sync histories, and recovery metrics. This action is irreversible.",
                        style = GrapeTypography.bodyMedium,
                        color = TextSecondary,
                        lineHeight = 20.sp
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = {
                            coroutineScope.launch(Dispatchers.IO) {
                                var db: SQLiteDatabase? = null
                                try {
                                    db = SQLiteDatabase.openOrCreateDatabase(dbPath, null)
                                    db.execSQL("DELETE FROM external_sleep_sessions")
                                    db.execSQL("DELETE FROM external_sleep_stages")
                                    db.execSQL("DELETE FROM daily_recovery_metrics")
                                    db.execSQL("DELETE FROM sync_sessions")
                                    db.execSQL("DELETE FROM sync_progress")
                                    db.execSQL("DELETE FROM historical_packets")
                                    withContext(Dispatchers.Main) {
                                        Toast.makeText(context, "All cached biometrics cleared.", Toast.LENGTH_SHORT).show()
                                    }
                                } catch (e: Exception) {
                                    withContext(Dispatchers.Main) {
                                        Toast.makeText(context, "Failed to clear database.", Toast.LENGTH_SHORT).show()
                                    }
                                    Timber.e(e, "Clear cache error")
                                } finally {
                                    db?.close()
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = StressRed),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Warning,
                                contentDescription = "Warning Icon",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "CLEAR DATA CACHE",
                                color = Color.White,
                                style = GrapeTypography.labelLarge,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ProfileHeaderSection(title: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start
    ) {
        Text(
            text = title,
            style = GrapeTypography.labelSmall.copy(fontWeight = FontWeight.Bold),
            color = TextSecondary,
            letterSpacing = 1.sp
        )
    }
}

@Composable
fun ProfileInfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, style = GrapeTypography.bodyLarge, color = TextSecondary)
        Text(text = value, style = GrapeTypography.bodyLarge, color = TextPrimary, fontWeight = FontWeight.Bold)
    }
}

private fun injectMockBiometricData(repository: DeviceRepository) {
    val now = System.currentTimeMillis()
    val oneDayMs = 24 * 60 * 60 * 1000L

    for (i in 0..4) {
        val dateOffset = now - i * oneDayMs

        val calendarStart = Calendar.getInstance().apply {
            timeInMillis = dateOffset - (8 * 60 * 60 * 1000L) // Shift back 8h
        }
        val calendarEnd = Calendar.getInstance().apply {
            timeInMillis = dateOffset
        }

        val startMs = calendarStart.timeInMillis
        val endMs = calendarEnd.timeInMillis

        val hrv = when (i) {
            0 -> 68.0
            1 -> 52.0
            2 -> 74.0
            3 -> 45.0
            else -> 60.0
        }
        val restingHr = when (i) {
            0 -> 48.0
            1 -> 55.0
            2 -> 46.0
            3 -> 58.0
            else -> 50.0
        }
        val tempDelta = when (i) {
            0 -> -0.1
            1 -> 0.3
            2 -> -0.4
            3 -> 0.6
            else -> 0.0
        }

        val rem = 90.0 - (i * 5)
        val deep = 105.0 + (i * 10)
        val light = 240.0 - (i * 8)
        val awake = 30.0 + (i * 4)

        repository.insertMockSleepSession(
            startTimeUnixMs = startMs,
            endTimeUnixMs = endMs,
            remMinutes = rem,
            deepMinutes = deep,
            coreMinutes = light,
            awakeMinutes = awake
        )

        repository.insertMockRecoveryMetric(
            startTimeUnixMs = startMs,
            endTimeUnixMs = endMs,
            restingHr = restingHr,
            hrv = hrv,
            tempDelta = tempDelta
        )
    }
}
