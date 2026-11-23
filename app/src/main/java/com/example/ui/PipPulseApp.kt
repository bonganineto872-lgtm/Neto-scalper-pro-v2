package com.example.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.CandlestickChart
import androidx.compose.material.icons.filled.ListAlt
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.CandlestickChart
import androidx.compose.material.icons.outlined.ListAlt
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.R
import com.example.ui.components.DashboardAlertBanner
import com.example.ui.screens.BrokerScreen
import com.example.ui.screens.PositionsScreen
import com.example.ui.screens.StrategyScreen
import com.example.ui.screens.TerminalScreen
import com.example.ui.theme.BorderSlate
import com.example.ui.theme.BullGreen
import com.example.ui.theme.CarbonSurface
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.CyberCyanMuted
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.viewmodel.TradingViewModel

const val ROUTE_TERMINAL = "terminal"
const val ROUTE_STRATEGY = "strategy"
const val ROUTE_POSITIONS = "positions"
const val ROUTE_BROKER = "broker"

private data class NavDestination(
    val route: String,
    val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
)

private val TopDestinations = listOf(
    NavDestination(
        route = ROUTE_TERMINAL,
        labelRes = R.string.nav_terminal,
        selectedIcon = Icons.Filled.CandlestickChart,
        unselectedIcon = Icons.Outlined.CandlestickChart,
        testTag = "nav_terminal"
    ),
    NavDestination(
        route = ROUTE_STRATEGY,
        labelRes = R.string.nav_strategy,
        selectedIcon = Icons.Filled.Tune,
        unselectedIcon = Icons.Outlined.Tune,
        testTag = "nav_strategy"
    ),
    NavDestination(
        route = ROUTE_POSITIONS,
        labelRes = R.string.nav_positions,
        selectedIcon = Icons.Filled.ListAlt,
        unselectedIcon = Icons.Outlined.ListAlt,
        testTag = "nav_positions"
    ),
    NavDestination(
        route = ROUTE_BROKER,
        labelRes = R.string.nav_broker,
        selectedIcon = Icons.Filled.AccountBalance,
        unselectedIcon = Icons.Outlined.AccountBalance,
        testTag = "nav_broker"
    )
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PipPulseApp(
    viewModel: TradingViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route ?: ROUTE_TERMINAL

    fun navigateTo(route: String) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
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
            contentWindowInsets = WindowInsets.safeDrawing,
            bottomBar = {
                if (!isWideScreen) {
                    NavigationBar(
                        containerColor = CarbonSurface,
                        tonalElevation = 0.dp
                    ) {
                        TopDestinations.forEach { dest ->
                            val selected = currentRoute == dest.route
                            val openCount = uiState.openPositions.size
                            NavigationBarItem(
                                selected = selected,
                                onClick = { navigateTo(dest.route) },
                                modifier = Modifier.testTag(dest.testTag),
                                icon = {
                                    if (dest.route == ROUTE_POSITIONS && openCount > 0) {
                                        BadgedBox(
                                            badge = {
                                                Badge(containerColor = BullGreen, contentColor = ObsidianBg) {
                                                    Text("$openCount")
                                                }
                                            }
                                        ) {
                                            Icon(
                                                imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                                contentDescription = stringResource(dest.labelRes)
                                            )
                                        }
                                    } else {
                                        Icon(
                                            imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                            contentDescription = stringResource(dest.labelRes)
                                        )
                                    }
                                },
                                label = {
                                    Text(
                                        text = stringResource(dest.labelRes),
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = CyberCyan,
                                    selectedTextColor = CyberCyan,
                                    indicatorColor = CyberCyanMuted,
                                    unselectedIconColor = TextMuted,
                                    unselectedTextColor = TextMuted
                                )
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (isWideScreen) {
                    NavigationRail(
                        containerColor = CarbonSurface,
                        modifier = Modifier.fillMaxHeight()
                    ) {
                        Spacer(modifier = Modifier.size(12.dp))
                        TopDestinations.forEach { dest ->
                            val selected = currentRoute == dest.route
                            val openCount = uiState.openPositions.size
                            NavigationRailItem(
                                selected = selected,
                                onClick = { navigateTo(dest.route) },
                                modifier = Modifier.testTag(dest.testTag),
                                icon = {
                                    if (dest.route == ROUTE_POSITIONS && openCount > 0) {
                                        BadgedBox(
                                            badge = {
                                                Badge(containerColor = BullGreen, contentColor = ObsidianBg) {
                                                    Text("$openCount")
                                                }
                                            }
                                        ) {
                                            Icon(
                                                imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                                contentDescription = stringResource(dest.labelRes)
                                            )
                                        }
                                    } else {
                                        Icon(
                                            imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                            contentDescription = stringResource(dest.labelRes)
                                        )
                                    }
                                },
                                label = {
                                    Text(
                                        text = stringResource(dest.labelRes),
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                },
                                colors = NavigationRailItemDefaults.colors(
                                    selectedIconColor = CyberCyan,
                                    selectedTextColor = CyberCyan,
                                    indicatorColor = CyberCyanMuted,
                                    unselectedIconColor = TextMuted,
                                    unselectedTextColor = TextMuted
                                )
                            )
                        }
                    }
                    Spacer(
                        modifier = Modifier
                            .width(1.dp)
                            .fillMaxHeight()
                            .background(BorderSlate)
                    )
                }

                Box(modifier = Modifier.weight(1f)) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        DashboardAlertBanner(
                            alert = uiState.activeAlertBanner,
                            onDismiss = viewModel::dismissActiveAlert
                        )
                        NavHost(
                            navController = navController,
                            startDestination = ROUTE_TERMINAL,
                            modifier = Modifier.weight(1f)
                        ) {
                            composable(ROUTE_TERMINAL) {
                                TerminalScreen(
                                    state = uiState,
                                    isWideScreen = isWideScreen,
                                    onSelectPair = viewModel::selectPair,
                                    onSelectTimeframe = viewModel::selectTimeframe,
                                    onToggleBot = viewModel::toggleBot,
                                    onManualOrder = viewModel::placeManualOrder,
                                    onFlattenAll = viewModel::emergencyFlattenAll,
                                    onUpdateConfig = viewModel::updateBotConfig,
                                    onApplyRiskProfile = viewModel::applyRiskProfile,
                                    onRefreshSpot = viewModel::refreshSpotRatesNow
                                )
                            }
                            composable(ROUTE_STRATEGY) {
                                BackHandler { navigateTo(ROUTE_TERMINAL) }
                                StrategyScreen(
                                    config = uiState.botConfig,
                                    onUpdateConfig = viewModel::updateBotConfig,
                                    onApplyRiskProfile = viewModel::applyRiskProfile
                                )
                            }
                            composable(ROUTE_POSITIONS) {
                                BackHandler { navigateTo(ROUTE_TERMINAL) }
                                PositionsScreen(
                                    state = uiState,
                                    onClosePosition = viewModel::closePosition,
                                    onFlattenAll = viewModel::emergencyFlattenAll,
                                    onClearAlerts = viewModel::clearAlertHistory
                                )
                            }
                            composable(ROUTE_BROKER) {
                                BackHandler { navigateTo(ROUTE_TERMINAL) }
                                BrokerScreen(
                                    state = uiState,
                                    onSaveCredentials = viewModel::saveBrokerCredentials,
                                    onTestConnection = viewModel::testAndSyncBrokerConnection,
                                    onResetPaperAccount = viewModel::resetPaperAccount,
                                    onUpdateConfig = viewModel::updateBotConfig,
                                    onRegenerateLocalApiKey = viewModel::regenerateLocalApiKey
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
