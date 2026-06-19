package com.grape.mobile.navigation

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.grape.mobile.ble.GrapeBleManager
import com.grape.mobile.repository.DeviceRepository
import com.grape.mobile.repository.UpdateRepository
import com.grape.mobile.screens.DashboardScreen
import com.grape.mobile.screens.SleepScreen
import com.grape.mobile.screens.RecoveryScreen
import com.grape.mobile.screens.StrainScreen
import com.grape.mobile.screens.DeviceScreen
import com.grape.mobile.screens.ProfileScreen
import com.grape.mobile.ui.components.FloatingBottomBar
import com.grape.mobile.ui.components.NavigationTabItem
import com.grape.mobile.theme.*
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.haze

sealed class Screen(val route: String, val title: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    object Home : Screen("home", "Home", Icons.Rounded.Home)
    object Recovery : Screen("recovery", "Recovery", Icons.Rounded.Favorite)
    object Device : Screen("device", "Device", Icons.Rounded.Build)
    object Profile : Screen("profile", "Profile", Icons.Rounded.Person)
}

@Composable
fun AppNavigation(
    bleManager: GrapeBleManager,
    repository: DeviceRepository,
    updateRepository: UpdateRepository
) {
    val hazeState = remember { HazeState() }
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    var showUpdateDialog by remember { mutableStateOf(false) }
    var latestVersionMeta by remember { mutableStateOf<com.grape.mobile.repository.VersionMetadata?>(null) }

    LaunchedEffect(Unit) {
        val meta = updateRepository.checkForUpdates()
        if (meta != null) {
            if (updateRepository.compareVersions(meta.version, meta.build)) {
                latestVersionMeta = meta
                showUpdateDialog = true
            }
        }
    }

    if (showUpdateDialog && latestVersionMeta != null) {
        val meta = latestVersionMeta!!
        AlertDialog(
            onDismissRequest = {
                if (!meta.mandatory) {
                    showUpdateDialog = false
                    updateRepository.dismissVersion(meta.version)
                }
            },
            title = { Text(text = "New Version Available", color = TextPrimary) },
            text = {
                Column {
                    Text(text = "v${meta.version} is now available.", color = TextSecondary)
                    if (meta.notes.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(text = "Changelog:", fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, color = TextPrimary)
                        meta.notes.forEach { note ->
                            Text(text = "• $note", color = TextSecondary)
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        updateRepository.openReleasePage(meta.version)
                        if (!meta.mandatory) {
                            showUpdateDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = GrapePrimary)
                ) {
                    Text("Update", color = Color.White)
                }
            },
            dismissButton = {
                if (!meta.mandatory) {
                    TextButton(
                        onClick = {
                            showUpdateDialog = false
                            updateRepository.dismissVersion(meta.version)
                        }
                    ) {
                        Text("Later", color = GrapeAccent)
                    }
                }
            },
            containerColor = BackgroundSecondary,
            titleContentColor = TextPrimary,
            textContentColor = TextSecondary
        )
    }

    val items = listOf(
        Screen.Home,
        Screen.Recovery,
        Screen.Device,
        Screen.Profile
    )

    val tabItems = remember {
        items.map { screen ->
            NavigationTabItem(
                route = screen.route,
                title = screen.title,
                icon = screen.icon
            )
        }
    }

    Scaffold(
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets.safeDrawing,
        bottomBar = {
            FloatingBottomBar(
                hazeState = hazeState,
                tabs = tabItems,
                currentRoute = currentRoute,
                onTabSelected = { route ->
                    navController.navigate(route) {
                        popUpTo(navController.graph.findStartDestination().id) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                }
            )
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Home.route,
            modifier = Modifier
                .fillMaxSize()
                .haze(hazeState)
                .padding(top = innerPadding.calculateTopPadding())
        ) {
            composable(Screen.Home.route) {
                DashboardScreen(bleManager, repository)
            }
            composable(Screen.Recovery.route) {
                RecoveryScreen(repository)
            }
            composable(Screen.Device.route) {
                DeviceScreen(bleManager, repository)
            }
            composable(Screen.Profile.route) {
                ProfileScreen(repository)
            }
        }
    }
}


