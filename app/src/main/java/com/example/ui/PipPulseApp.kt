package com.example.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.ui.components.DashboardAlertBanner
import com.example.ui.screens.SignalDashboardScreen
import com.example.ui.screens.StrategySettingsScreen
import com.example.ui.theme.ObsidianBg
import com.example.ui.viewmodel.TradingViewModel

const val ROUTE_DASHBOARD = "dashboard"
const val ROUTE_SETTINGS = "settings"

@Composable
fun PipPulseApp(
    viewModel: TradingViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val navController = rememberNavController()
    val context = LocalContext.current
    val notifPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(ObsidianBg)
    ) {
        val isWideScreen = maxWidth >= 720.dp

        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = ObsidianBg,
            contentWindowInsets = WindowInsets.safeDrawing
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                DashboardAlertBanner(
                    alert = uiState.activeAlertBanner,
                    onDismiss = viewModel::dismissActiveAlert
                )

                NavHost(
                    navController = navController,
                    startDestination = ROUTE_DASHBOARD,
                    modifier = Modifier.weight(1f)
                ) {
                    composable(ROUTE_DASHBOARD) {
                        SignalDashboardScreen(
                            state = uiState,
                            isWideScreen = isWideScreen,
                            onSelectPair = viewModel::selectPair,
                            onSelectTimeframe = viewModel::selectTimeframe,
                            onSelectPairAndTimeframe = viewModel::selectPairAndTimeframe,
                            onToggleBot = viewModel::toggleBot,
                            onForceScanNow = viewModel::forceScanNow,
                            onDismissSignal = viewModel::dismissActiveSignal,
                            onUpdateConfig = viewModel::updateBotConfig,
                            onOpenSettings = {
                                navController.navigate(ROUTE_SETTINGS) {
                                    launchSingleTop = true
                                }
                            }
                        )
                    }

                    composable(ROUTE_SETTINGS) {
                        val navigateBack = {
                            if (!navController.popBackStack()) {
                                navController.navigate(ROUTE_DASHBOARD) {
                                    launchSingleTop = true
                                }
                            }
                        }
                        BackHandler(onBack = navigateBack)
                        StrategySettingsScreen(
                            state = uiState,
                            onUpdateConfig = viewModel::updateBotConfig,
                            onResetFlipProgress = viewModel::resetFlipProgress,
                            onBack = navigateBack
                        )
                    }
                }
            }
        }
    }
}
