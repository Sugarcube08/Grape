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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.grape.mobile.BuildConfig
import com.grape.mobile.repository.DeviceRepository
import com.grape.mobile.repository.UserProfile
import com.grape.mobile.repository.DbDiagnostics
import com.grape.mobile.repository.ConnectedDevice
import com.grape.mobile.app.AlgorithmRegistry
import com.grape.mobile.ui.components.BackgroundContainer
import com.grape.mobile.ui.components.GlassCard
import com.grape.mobile.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import timber.log.Timber
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun ProfileScreen(repository: DeviceRepository) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // Telemetries
    var profile by remember { mutableStateOf<UserProfile?>(null) }
    var streak by remember { mutableStateOf(0) }
    var dbDiagnostics by remember { mutableStateOf<DbDiagnostics?>(null) }
    var companionDevice by remember { mutableStateOf<ConnectedDevice?>(null) }
    var coreMetadata by remember { mutableStateOf<JSONObject?>(null) }

    // Dialog state
    var showEditDialog by remember { mutableStateOf(false) }
    var editNameInput by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            profile = repository.getUserProfile(context)
            streak = repository.getRecoveryStreak()
            dbDiagnostics = repository.getDbDiagnostics(context)
            companionDevice = repository.connectedDevice()

            try {
                val metadataJson = uniffi.grape.getCoreMetadata()
                coreMetadata = JSONObject(metadataJson)
            } catch (e: Exception) {
                Timber.e(e, "Error loading core metadata from Rust FFI")
            }
        }
    }

    LaunchedEffect(showEditDialog) {
        if (showEditDialog) {
            editNameInput = profile?.displayName ?: "Athlete"
        }
    }

    if (showEditDialog) {
        AlertDialog(
            onDismissRequest = { showEditDialog = false },
            title = { Text(text = "Edit Profile Name", color = TextPrimary, style = GrapeTypography.headlineLarge, fontWeight = FontWeight.Bold) },
            text = {
                Column(modifier = Modifier.padding(top = 8.dp)) {
                    OutlinedTextField(
                        value = editNameInput,
                        onValueChange = { editNameInput = it },
                        label = { Text("Display Name", color = TextSecondary) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            focusedBorderColor = GrapePrimary,
                            unfocusedBorderColor = TextSecondary.copy(alpha = 0.5f),
                            focusedLabelColor = GrapePrimary,
                            unfocusedLabelColor = TextSecondary
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (editNameInput.isNotBlank()) {
                            coroutineScope.launch(Dispatchers.IO) {
                                repository.saveUserProfile(editNameInput.trim())
                                profile = repository.getUserProfile(context)
                                withContext(Dispatchers.Main) {
                                    showEditDialog = false
                                    Toast.makeText(context, "Profile updated!", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    }
                ) {
                    Text("Save", color = GrapeAccent, style = GrapeTypography.labelLarge, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditDialog = false }) {
                    Text("Cancel", color = TextSecondary, style = GrapeTypography.labelLarge)
                }
            },
            containerColor = BackgroundSecondary,
            shape = RoundedCornerShape(24.dp)
        )
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

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = profile?.displayName ?: "Athlete",
                    style = GrapeTypography.displaySmall,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(
                    onClick = { showEditDialog = true },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Edit,
                        contentDescription = "Edit Name",
                        tint = GrapeAccent,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            
            val memberSinceText = remember(profile) {
                if (profile != null) {
                    try {
                        val formatter = DateTimeFormatter.ofPattern("MMM yyyy", Locale.US)
                        Instant.ofEpochMilli(profile!!.createdAt)
                            .atZone(ZoneId.systemDefault())
                            .format(formatter)
                    } catch (e: Exception) {
                        "Jun 2026"
                    }
                } else {
                    "Jun 2026"
                }
            }

            Text(
                text = "Member since $memberSinceText",
                style = GrapeTypography.labelMedium,
                color = TextSecondary
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Recovery Streak Badge
            if (streak > 0) {
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
                        text = "$streak Day Recovery Streak",
                        style = GrapeTypography.labelLarge.copy(fontWeight = FontWeight.Bold),
                        color = TextPrimary
                    )
                }
            } else {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(TextSecondary.copy(alpha = 0.10f))
                        .border(
                            width = 1.dp,
                            color = TextSecondary.copy(alpha = 0.2f),
                            shape = RoundedCornerShape(16.dp)
                        )
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.FavoriteBorder,
                        contentDescription = "No Streak Icon",
                        tint = TextSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "No active streak",
                        style = GrapeTypography.labelLarge,
                        color = TextSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // --- SECTION: ABOUT ---
            ProfileHeaderSection("ABOUT")
            Spacer(modifier = Modifier.height(8.dp))
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Grape",
                        style = GrapeTypography.headlineMedium,
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Local-first wearable intelligence platform",
                        style = GrapeTypography.bodyMedium,
                        color = TextPrimary
                    )
                    HorizontalDivider(color = Color.White.copy(alpha = 0.05f))
                    ProfileInfoRow(label = "Build", value = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
                    ProfileInfoRow(label = "Commit", value = coreMetadata?.optString("git_commit", "6e7ab1f") ?: "6e7ab1f")
                    ProfileInfoRow(label = "Privacy", value = "Offline only")
                    ProfileInfoRow(label = "License", value = "Apache 2.0")
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // --- SECTION: ALGORITHMS & VERSION ---
            ProfileHeaderSection("VERSION & ALGORITHMS")
            Spacer(modifier = Modifier.height(8.dp))
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    ProfileInfoRow(label = "Application Version", value = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
                    val target = if (BuildConfig.DEBUG) "Debug" else "Release"
                    ProfileInfoRow(label = "Build Target", value = "Android Native $target")
                    
                    HorizontalDivider(color = Color.White.copy(alpha = 0.05f))
                    
                    ProfileInfoRow(label = "Sleep Engine", value = AlgorithmRegistry.SLEEP_VERSION)
                    ProfileInfoRow(label = "Recovery Engine", value = AlgorithmRegistry.RECOVERY_VERSION)
                    ProfileInfoRow(label = "Strain Engine", value = AlgorithmRegistry.STRAIN_VERSION)
                    ProfileInfoRow(label = "Stress Engine", value = AlgorithmRegistry.STRESS_VERSION)
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // --- SECTION: COMPANION DEVICE STATUS ---
            ProfileHeaderSection("COMPANION WEARABLE STATUS")
            Spacer(modifier = Modifier.height(8.dp))
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                val dev = companionDevice
                if (dev != null) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        ProfileInfoRow(label = "Wearable", value = dev.name)
                        ProfileInfoRow(label = "Status", value = dev.status)
                        ProfileInfoRow(label = "Battery", value = if (dev.battery > 0) "${dev.battery}%" else "--")
                        ProfileInfoRow(label = "Last Sync", value = dev.lastSync)
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Warning,
                            contentDescription = "Not connected",
                            tint = TextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "No wearable connected",
                            style = GrapeTypography.bodyMedium,
                            color = TextSecondary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // --- SECTION: DEVICE INFORMATION ---
            ProfileHeaderSection("DEVICE INFORMATION")
            Spacer(modifier = Modifier.height(8.dp))
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    ProfileInfoRow(label = "Device", value = "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}")
                    ProfileInfoRow(label = "Android Version", value = android.os.Build.VERSION.RELEASE)
                    ProfileInfoRow(label = "SDK Level", value = android.os.Build.VERSION.SDK_INT.toString())
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // --- SECTION: DIAGNOSTICS ---
            ProfileHeaderSection("DIAGNOSTICS")
            Spacer(modifier = Modifier.height(8.dp))
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(text = "RUST CORE", style = GrapeTypography.labelSmall.copy(fontWeight = FontWeight.Bold), color = TextSecondary)
                    ProfileInfoRow(label = "Core Version", value = coreMetadata?.optString("version", "0.3.0-alpha") ?: "0.3.0-alpha")
                    ProfileInfoRow(label = "Git Commit", value = coreMetadata?.optString("git_commit", "6e7ab1f") ?: "6e7ab1f")
                    ProfileInfoRow(label = "Schema Version", value = (coreMetadata?.optInt("schema_version", 12) ?: 12).toString())
                    ProfileInfoRow(label = "Compiled Date", value = coreMetadata?.optString("build_date", "2026-06-19") ?: "2026-06-19")
                    
                    HorizontalDivider(color = Color.White.copy(alpha = 0.05f))
                    
                    Text(text = "DATABASE FILE", style = GrapeTypography.labelSmall.copy(fontWeight = FontWeight.Bold), color = TextSecondary)
                    ProfileInfoRow(label = "Database", value = dbDiagnostics?.path?.let { File(it).name } ?: "grape.sqlite")
                    ProfileInfoRow(label = "Size", value = dbDiagnostics?.size ?: "0 B")
                    ProfileInfoRow(label = "Storage Path", value = dbDiagnostics?.path?.let { File(it).parent } ?: "/data/user/0/com.grape.mobile/files")
                    ProfileInfoRow(label = "Packet Count", value = (dbDiagnostics?.packets ?: 0).toString())
                    ProfileInfoRow(label = "Sleep Sessions", value = (dbDiagnostics?.sleepSessions ?: 0).toString())
                    ProfileInfoRow(label = "Recovery Metrics", value = (dbDiagnostics?.recoveryMetrics ?: 0).toString())
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
                                val dbPath = dbDiagnostics?.path ?: File(context.filesDir, "grape.sqlite").absolutePath
                                val zipFile = com.grape.mobile.utils.ExportManager.exportDataBundle(context, dbPath)
                                if (zipFile != null) {
                                    com.grape.mobile.utils.ExportManager.shareExportFile(context, zipFile)
                                } else {
                                    Toast.makeText(context, "Export Failed: Could not build ZIP bundle", Toast.LENGTH_SHORT).show()
                                }
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

            if (BuildConfig.DEBUG) {
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
                                        injectMockBiometricData(context)
                                        // Refresh stats in UI
                                        dbDiagnostics = repository.getDbDiagnostics(context)
                                        streak = repository.getRecoveryStreak()
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
            }

            Spacer(modifier = Modifier.height(24.dp))

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
                                    val path = dbDiagnostics?.path ?: File(context.filesDir, "grape.sqlite").absolutePath
                                    db = SQLiteDatabase.openOrCreateDatabase(path, null)
                                    db.execSQL("DELETE FROM external_sleep_sessions")
                                    db.execSQL("DELETE FROM external_sleep_stages")
                                    db.execSQL("DELETE FROM daily_recovery_metrics")
                                    db.execSQL("DELETE FROM sync_sessions")
                                    db.execSQL("DELETE FROM sync_progress")
                                    db.execSQL("DELETE FROM historical_packets")
                                    // Refresh diagnostics
                                    dbDiagnostics = repository.getDbDiagnostics(context)
                                    streak = 0
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

private fun injectMockBiometricData(context: Context) {
    if (!BuildConfig.DEBUG) return
    val now = System.currentTimeMillis()
    val oneDayMs = 24 * 60 * 60 * 1000L

    try {
        val debugClass = Class.forName("com.grape.mobile.provider.DebugDataProvider")
        
        val insertSleepMethod = debugClass.getMethod(
            "insertMockSleepSession",
            Context::class.java,
            Long::class.javaPrimitiveType ?: Long::class.java,
            Long::class.javaPrimitiveType ?: Long::class.java,
            Double::class.javaPrimitiveType ?: Double::class.java,
            Double::class.javaPrimitiveType ?: Double::class.java,
            Double::class.javaPrimitiveType ?: Double::class.java,
            Double::class.javaPrimitiveType ?: Double::class.java
        )

        val insertRecoveryMethod = debugClass.getMethod(
            "insertMockRecoveryMetric",
            Context::class.java,
            Long::class.javaPrimitiveType ?: Long::class.java,
            Long::class.javaPrimitiveType ?: Long::class.java,
            Double::class.javaPrimitiveType ?: Double::class.java,
            Double::class.javaPrimitiveType ?: Double::class.java,
            Double::class.javaPrimitiveType ?: Double::class.java
        )

        for (i in 0..4) {
            val dateOffset = now - i * oneDayMs
            val startMs = dateOffset - (8 * 60 * 60 * 1000L)
            val endMs = dateOffset

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

            insertSleepMethod.invoke(
                null,
                context,
                startMs,
                endMs,
                rem,
                deep,
                light,
                awake
            )

            insertRecoveryMethod.invoke(
                null,
                context,
                startMs,
                endMs,
                restingHr,
                hrv,
                tempDelta
            )
        }
    } catch (e: Exception) {
        Timber.e(e, "Error injecting mock data via reflection")
        throw e
    }
}
