package com.grape.mobile.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.grape.mobile.ble.GrapeBleManager
import com.grape.mobile.repository.DeviceRepository
import com.grape.mobile.screens.DashboardScreen

@Composable
fun AppNavigation(
    bleManager: GrapeBleManager,
    repository: DeviceRepository
) {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = "dashboard"
    ) {
        composable("dashboard") {
            DashboardScreen(bleManager, repository)
        }
    }
}
