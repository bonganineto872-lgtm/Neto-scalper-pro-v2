package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.filled.Api
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.core.content.ContextCompat
import com.example.domain.engine.Mt5BridgeScriptGenerator
import com.example.domain.model.BotConfig
import com.example.domain.model.BrokerCredentials
import com.example.domain.model.BrokerEnvironment
import com.example.domain.model.Mt5BridgeType
import com.example.domain.model.Mt5BrokerPreset
import com.example.ui.theme.AmberWarn
import com.example.ui.theme.AmberWarnMuted
import com.example.ui.theme.BearRed
import com.example.ui.theme.BearRedMuted
import com.example.ui.theme.BorderSlate
import com.example.ui.theme.BullGreen
import com.example.ui.theme.BullGreenMuted
import com.example.ui.theme.CarbonSurface
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.CyberCyanMuted
import com.example.ui.theme.ElevatedCard
import com.example.ui.theme.MonoTextStyle
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.viewmodel.TradingUiState

@Composable
fun BrokerScreen(
    state: TradingUiState,
    onSaveCredentials: (BrokerCredentials) -> Unit,
    onTestConnection: (BrokerCredentials) -> Unit,
    onResetPaperAccount: () -> Unit,
    onUpdateConfig: (BotConfig) -> Unit,
    onRegenerateLocalApiKey: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val notifPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            onUpdateConfig(state.botConfig.copy(keepRunningOnPhone = true))
        }
    }
    var showBridgePreview by remember { mutableStateOf(false) }
    var apiActionFeedback by remember { mutableStateOf<String?>(null) }
    var selectedEnv by remember { mutableStateOf(state.brokerCredentials.environment) }
    var mt5BrokerName by remember { mutableStateOf(state.brokerCredentials.mt5BrokerName) }
    var mt5Server by remember { mutableStateOf(state.brokerCredentials.mt5Server) }
    var mt5Login by remember { mutableStateOf(state.brokerCredentials.mt5Login) }
    var mt5BridgeType by remember { mutableStateOf(state.brokerCredentials.mt5BridgeType) }
    var mt5BridgeUrl by remember { mutableStateOf(state.brokerCredentials.mt5BridgeUrl) }
    var mt5SymbolSuffix by remember { mutableStateOf(state.brokerCredentials.mt5SymbolSuffix) }
    var apiToken by remember { mutableStateOf(state.brokerCredentials.apiToken) }
    var accountId by remember { mutableStateOf(state.brokerCredentials.accountId) }
    var showToken by remember { mutableStateOf(false) }
    var showResetDialog by remember { mutableStateOf(false) }

    LaunchedEffect(state.brokerCredentials) {
        selectedEnv = state.brokerCredentials.environment
        if (mt5BrokerName.isBlank()) mt5BrokerName = state.brokerCredentials.mt5BrokerName
        if (mt5Server.isBlank()) mt5Server = state.brokerCredentials.mt5Server
        if (mt5Login.isBlank()) mt5Login = state.brokerCredentials.mt5Login
        mt5BridgeType = state.brokerCredentials.mt5BridgeType
        if (mt5BridgeUrl.isBlank()) mt5BridgeUrl = state.brokerCredentials.mt5BridgeUrl
        if (mt5SymbolSuffix.isBlank()) mt5SymbolSuffix = state.brokerCredentials.mt5SymbolSuffix
        if (apiToken.isEmpty() && state.brokerCredentials.apiToken.isNotEmpty()) {
            apiToken = state.brokerCredentials.apiToken
        }
        if (accountId.isEmpty() && state.brokerCredentials.accountId.isNotEmpty()) {
            accountId = state.brokerCredentials.accountId
        }
    }

    fun currentCreds(): BrokerCredentials {
        return BrokerCredentials(
            environment = selectedEnv,
            apiToken = apiToken.trim(),
            accountId = accountId.trim(),
            mt5BrokerName = mt5BrokerName.trim().ifEmpty { "XM Global" },
            mt5Server = mt5Server.trim().ifEmpty { "XMGlobal-MT5" },
            mt5Login = mt5Login.trim(),
            mt5BridgeType = mt5BridgeType,
            mt5BridgeUrl = mt5BridgeUrl.trim(),
            mt5SymbolSuffix = mt5SymbolSuffix.trim(),
            localApiEnabled = state.brokerCredentials.localApiEnabled,
            localApiPort = state.brokerCredentials.localApiPort,
            localApiKey = state.brokerCredentials.localApiKey
        )
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(top = 12.dp, bottom = 28.dp)
    ) {
        item {
            Column {
                Text(
                    text = "Neto Scalper Pro • MT5 & Broker Hub",
                    style = MaterialTheme.typography.headlineSmall,
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Connect MetaTrader 5 Live or Demo on XM Global (Eswatini / SADC) or any MT5 broker, OANDA v20, or Paper ZAR Flip.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }
        }

        // Environment Selector Cards
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "EXECUTION ENVIRONMENT",
                    style = MaterialTheme.typography.labelMedium,
                    color = TextSecondary
                )
                BrokerEnvironment.entries.forEach { env ->
                    val isSelected = selectedEnv == env
                    val (accent, badge) = when (env) {
                        BrokerEnvironment.PAPER_SIMULATION -> AmberWarn to "ZERO RISK"
                        BrokerEnvironment.MT5_DEMO -> CyberCyan to "MT5 DEMO"
                        BrokerEnvironment.MT5_LIVE -> BearRed to "MT5 LIVE"
                        BrokerEnvironment.OANDA_PRACTICE -> CyberCyan to "OANDA DEMO"
                        BrokerEnvironment.OANDA_LIVE -> BearRed to "OANDA LIVE"
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .minimumInteractiveComponentSize()
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isSelected) ElevatedCard else CarbonSurface)
                            .border(
                                width = 1.dp,
                                color = if (isSelected) accent else BorderSlate,
                                shape = RoundedCornerShape(12.dp)
                            )
                            .clickable {
                                selectedEnv = env
                                if (env == BrokerEnvironment.PAPER_SIMULATION) {
                                    onSaveCredentials(currentCreds().copy(environment = env))
                                }
                            }
                            .padding(14.dp)
                            .testTag("broker_env_${env.name}"),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = env.label,
                                style = MaterialTheme.typography.titleMedium,
                                color = if (isSelected) accent else TextPrimary
                            )
                            Text(
                                text = when (env) {
                                    BrokerEnvironment.PAPER_SIMULATION -> "Live Frankfurter FX feed + R200 → R10,000 ZAR/SZL flip engine"
                                    BrokerEnvironment.MT5_DEMO -> "Connect XM Global / any MT5 Demo account via MetaApi or VPS Bridge"
                                    BrokerEnvironment.MT5_LIVE -> "Connect XM Global / any MT5 Live account via MetaApi or VPS Bridge"
                                    BrokerEnvironment.OANDA_PRACTICE -> "fxTrade Practice REST v3 (api-fxpractice.oanda.com)"
                                    BrokerEnvironment.OANDA_LIVE -> "fxTrade Live REST v3 (api-fxtrade.oanda.com)"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = badge,
                            style = MonoTextStyle.copy(
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = accent
                            )
                        )
                    }
                }
            }
        }

        // MT5 Broker Setup Panel (XM Global + Any Broker)
        if (selectedEnv == BrokerEnvironment.MT5_DEMO || selectedEnv == BrokerEnvironment.MT5_LIVE) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(ElevatedCard)
                        .border(1.dp, CyberCyan.copy(alpha = 0.45f), RoundedCornerShape(14.dp))
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.Hub,
                            contentDescription = "MT5 Broker Setup",
                            tint = CyberCyan,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "MT5 BROKER & SERVER (${if (selectedEnv == BrokerEnvironment.MT5_LIVE) "LIVE" else "DEMO"})",
                            style = MaterialTheme.typography.labelMedium,
                            color = CyberCyan
                        )
                    }

                    // Broker Quick Presets (XM Global highlighted first for Eswatini)
                    Text(
                        text = "Quick Broker Preset (or type any MT5 broker below)",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Mt5BrokerPreset.entries.forEach { preset ->
                            val active = mt5BrokerName.equals(preset.brokerName, ignoreCase = true)
                            Box(
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (active) CyberCyanMuted else CarbonSurface)
                                    .border(1.dp, if (active) CyberCyan else BorderSlate, RoundedCornerShape(8.dp))
                                    .clickable {
                                        mt5BrokerName = preset.brokerName
                                        mt5Server = preset.defaultServers.first()
                                        mt5SymbolSuffix = preset.defaultSuffix
                                    }
                                    .padding(horizontal = 12.dp, vertical = 8.dp)
                                    .testTag("preset_${preset.name}"),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = if (preset == Mt5BrokerPreset.XM_GLOBAL) "XM Global (Eswatini)" else preset.brokerName,
                                    style = MonoTextStyle.copy(
                                        fontSize = 11.sp,
                                        fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                                        color = if (active) CyberCyan else TextSecondary
                                    )
                                )
                            }
                        }
                    }

                    val activePreset = Mt5BrokerPreset.entries.find {
                        it.brokerName.equals(mt5BrokerName, ignoreCase = true)
                    } ?: Mt5BrokerPreset.XM_GLOBAL

                    // Server Quick Chips
                    Text(
                        text = "Server Presets for ${activePreset.brokerName}",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        activePreset.defaultServers.forEach { srv ->
                            val selectedSrv = mt5Server == srv
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (selectedSrv) BullGreenMuted else ObsidianBg)
                                    .border(1.dp, if (selectedSrv) BullGreen else BorderSlate, RoundedCornerShape(6.dp))
                                    .clickable { mt5Server = srv }
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                                    .testTag("server_chip_$srv")
                            ) {
                                Text(
                                    text = srv,
                                    style = MonoTextStyle.copy(
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (selectedSrv) BullGreen else TextSecondary
                                    )
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = mt5BrokerName,
                        onValueChange = { mt5BrokerName = it },
                        label = { Text("Broker Name (e.g. XM Global, Exness, Deriv, HFM)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("input_mt5_broker"),
                        colors = textFieldColors()
                    )

                    OutlinedTextField(
                        value = mt5Server,
                        onValueChange = { mt5Server = it },
                        label = { Text("MT5 Server Name (e.g. XMGlobal-MT5 2)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("input_mt5_server"),
                        colors = textFieldColors()
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = mt5Login,
                            onValueChange = { mt5Login = it },
                            label = { Text("MT5 Login ID") },
                            singleLine = true,
                            modifier = Modifier.weight(1.4f).testTag("input_mt5_login"),
                            colors = textFieldColors()
                        )
                        OutlinedTextField(
                            value = mt5SymbolSuffix,
                            onValueChange = { mt5SymbolSuffix = it },
                            label = { Text("Suffix (opt)") },
                            placeholder = { Text("e.g. m or #") },
                            singleLine = true,
                            modifier = Modifier.weight(1f).testTag("input_mt5_suffix"),
                            colors = textFieldColors()
                        )
                    }

                    // Bridge Transport Mode
                    Text(
                        text = "MT5 GATEWAY TRANSPORT",
                        style = MaterialTheme.typography.labelMedium,
                        color = TextSecondary
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Mt5BridgeType.entries.forEach { bridge ->
                            val isChosen = mt5BridgeType == bridge
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .minimumInteractiveComponentSize()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (isChosen) CyberCyanMuted else CarbonSurface)
                                    .border(1.dp, if (isChosen) CyberCyan else BorderSlate, RoundedCornerShape(10.dp))
                                    .clickable { mt5BridgeType = bridge }
                                    .padding(10.dp)
                                    .testTag("bridge_${bridge.name}")
                            ) {
                                Text(
                                    text = bridge.label,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = if (isChosen) CyberCyan else TextPrimary
                                )
                                Text(
                                    text = bridge.subtitle,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextMuted
                                )
                            }
                        }
                    }

                    if (mt5BridgeType == Mt5BridgeType.META_API) {
                        OutlinedTextField(
                            value = accountId,
                            onValueChange = { accountId = it },
                            label = { Text("MetaApi Account UUID (linked to MT5 Login)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().testTag("input_metaapi_account_id"),
                            colors = textFieldColors()
                        )
                    } else {
                        OutlinedTextField(
                            value = mt5BridgeUrl,
                            onValueChange = { mt5BridgeUrl = it },
                            label = { Text("VPS MT5 Bridge URL (https://your-vps:8080)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().testTag("input_vps_bridge_url"),
                            colors = textFieldColors()
                        )
                    }

                    OutlinedTextField(
                        value = apiToken,
                        onValueChange = { apiToken = it },
                        label = {
                            Text(
                                if (mt5BridgeType == Mt5BridgeType.META_API) "MetaApi Auth Token" else "VPS Bridge Bearer Secret"
                            )
                        },
                        singleLine = true,
                        visualTransformation = if (showToken) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { showToken = !showToken }) {
                                Icon(
                                    imageVector = if (showToken) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                    contentDescription = if (showToken) "Hide token" else "Show token",
                                    tint = TextSecondary
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth().testTag("input_mt5_token"),
                        colors = textFieldColors()
                    )

                    VerificationBanner(state.brokerTestMessage)

                    Button(
                        onClick = { onTestConnection(currentCreds()) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("verify_mt5_button"),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (selectedEnv == BrokerEnvironment.MT5_LIVE) BearRed else CyberCyan,
                            contentColor = ObsidianBg
                        )
                    ) {
                        Text(
                            text = "VERIFY & CONNECT ${mt5BrokerName.uppercase()} MT5",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // OANDA v20 Credentials Form
        if (selectedEnv == BrokerEnvironment.OANDA_PRACTICE || selectedEnv == BrokerEnvironment.OANDA_LIVE) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(ElevatedCard)
                        .border(1.dp, BorderSlate, RoundedCornerShape(14.dp))
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.Key,
                            contentDescription = "OANDA Credentials",
                            tint = CyberCyan,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "OANDA v20 API CREDENTIALS",
                            style = MaterialTheme.typography.labelMedium,
                            color = TextSecondary
                        )
                    }

                    OutlinedTextField(
                        value = accountId,
                        onValueChange = { accountId = it },
                        label = { Text("OANDA Account ID (e.g. 101-001-12345678-001)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("input_oanda_account_id"),
                        colors = textFieldColors()
                    )

                    OutlinedTextField(
                        value = apiToken,
                        onValueChange = { apiToken = it },
                        label = { Text("Personal Access Token (Bearer)") },
                        singleLine = true,
                        visualTransformation = if (showToken) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { showToken = !showToken }) {
                                Icon(
                                    imageVector = if (showToken) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                    contentDescription = if (showToken) "Hide token" else "Show token",
                                    tint = TextSecondary
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth().testTag("input_oanda_token"),
                        colors = textFieldColors()
                    )

                    VerificationBanner(state.brokerTestMessage)

                    Button(
                        onClick = { onTestConnection(currentCreds()) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("verify_oanda_button"),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = CyberCyan,
                            contentColor = ObsidianBg
                        )
                    ) {
                        Text(
                            text = "VERIFY & CONNECT ${selectedEnv.label.uppercase()}",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // Built-In Local API Server & MT5 Bridge API Generator
        item {
            val apiStatus = state.localApiStatus
            val effectiveKey = apiStatus.apiKey.ifBlank { state.brokerCredentials.localApiKey }
            val bridgeScript = remember(
                mt5BrokerName,
                mt5Server,
                mt5Login,
                mt5SymbolSuffix,
                apiToken,
                effectiveKey
            ) {
                Mt5BridgeScriptGenerator.generatePythonMt5BridgeScript(currentCreds().copy(localApiKey = effectiveKey))
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(ElevatedCard)
                    .border(1.dp, CyberCyan.copy(alpha = 0.45f), RoundedCornerShape(14.dp))
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Icon(
                            imageVector = Icons.Filled.Api,
                            contentDescription = "Built-in Local API",
                            tint = CyberCyan,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "BUILT-IN BOT API & MT5 GENERATOR",
                            style = MaterialTheme.typography.labelMedium,
                            color = CyberCyan
                        )
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (apiStatus.running) BullGreenMuted else AmberWarnMuted)
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = if (apiStatus.running) "API ONLINE" else "API PAUSED",
                            style = MonoTextStyle.copy(
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (apiStatus.running) BullGreen else AmberWarn
                            )
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "On-Device REST API (${apiStatus.baseUrl})",
                            style = MaterialTheme.typography.titleSmall,
                            color = TextPrimary
                        )
                        Text(
                            text = "Local HTTP endpoints for quotes, positions & orders • ${apiStatus.requestCount} calls",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                    Switch(
                        checked = state.brokerCredentials.localApiEnabled,
                        onCheckedChange = { enabled ->
                            onSaveCredentials(currentCreds().copy(localApiEnabled = enabled))
                        },
                        modifier = Modifier.testTag("switch_local_api"),
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = ObsidianBg,
                            checkedTrackColor = CyberCyan
                        )
                    )
                }

                // Local API Key Display + Copy/Regenerate
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(ObsidianBg)
                        .border(1.dp, BorderSlate, RoundedCornerShape(10.dp))
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "X-API-Key: ${effectiveKey.ifBlank { "Generating..." }}",
                        style = MonoTextStyle.copy(fontSize = 11.sp, color = BullGreen)
                    )
                    Text(
                        text = "GET /api/v1/health | /account | /quote | /positions\nPOST /api/v1/order {\"side\":\"BUY\"} | /bot/toggle | /flatten",
                        style = MonoTextStyle.copy(fontSize = 10.sp, color = TextMuted)
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = {
                                clipboard.setText(AnnotatedString(effectiveKey))
                                apiActionFeedback = "Local API Key copied to clipboard"
                            },
                            modifier = Modifier.weight(1f).height(48.dp).testTag("copy_local_api_key_button"),
                            shape = RoundedCornerShape(8.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, BorderSlate),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = CyberCyan)
                        ) {
                            Icon(Icons.Filled.ContentCopy, contentDescription = "Copy API key", modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Copy Key", style = MaterialTheme.typography.labelMedium)
                        }
                        OutlinedButton(
                            onClick = {
                                onRegenerateLocalApiKey()
                                apiActionFeedback = "Generated new Local API Key"
                            },
                            modifier = Modifier.weight(1f).height(48.dp).testTag("rotate_local_api_key_button"),
                            shape = RoundedCornerShape(8.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, BorderSlate),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = AmberWarn)
                        ) {
                            Icon(Icons.Filled.RestartAlt, contentDescription = "Regenerate API key", modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("New Key", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }

                // Generate Custom MT5 Bridge API Script for XM Global / Any Broker
                Text(
                    text = "YOUR OWN MT5 BRIDGE API (${mt5BrokerName.uppercase()})",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary
                )
                Text(
                    text = "Generate a pre-configured FastAPI + MetaTrader5 Python bridge (neto_mt5_bridge.py) pre-filled with ${mt5BrokerName} (${mt5Server}). Run on any PC/VPS alongside MT5.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            clipboard.setText(AnnotatedString(bridgeScript))
                            apiActionFeedback = "MT5 Bridge API script copied! Save as neto_mt5_bridge.py"
                        },
                        modifier = Modifier.weight(1f).height(48.dp).testTag("copy_mt5_bridge_button"),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = CyberCyan, contentColor = ObsidianBg)
                    ) {
                        Icon(Icons.Filled.ContentCopy, contentDescription = "Copy MT5 bridge API", modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Copy MT5 API", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    }
                    OutlinedButton(
                        onClick = {
                            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_SUBJECT, "neto_mt5_bridge.py (${mt5BrokerName})")
                                putExtra(Intent.EXTRA_TEXT, bridgeScript)
                            }
                            context.startActivity(Intent.createChooser(sendIntent, "Export MT5 Bridge API"))
                        },
                        modifier = Modifier.weight(1f).height(48.dp).testTag("share_mt5_bridge_button"),
                        shape = RoundedCornerShape(10.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, CyberCyan.copy(alpha = 0.55f)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = CyberCyan)
                    ) {
                        Icon(Icons.Filled.Share, contentDescription = "Share MT5 bridge API", modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Share / Save", style = MaterialTheme.typography.labelMedium)
                    }
                }

                OutlinedButton(
                    onClick = { showBridgePreview = !showBridgePreview },
                    modifier = Modifier.fillMaxWidth().height(48.dp).testTag("preview_mt5_bridge_button"),
                    shape = RoundedCornerShape(8.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, BorderSlate),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TextSecondary)
                ) {
                    Icon(Icons.Filled.Code, contentDescription = "Toggle bridge script preview", modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        if (showBridgePreview) "Hide Generated Python API" else "Preview Generated neto_mt5_bridge.py",
                        style = MaterialTheme.typography.labelMedium
                    )
                }

                if (showBridgePreview) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(ObsidianBg)
                            .border(1.dp, BorderSlate, RoundedCornerShape(8.dp))
                            .padding(10.dp)
                    ) {
                        Text(
                            text = bridgeScript.take(1400) + "\n... (full script copied/shared via buttons above)",
                            style = MonoTextStyle.copy(fontSize = 10.sp, color = TextSecondary)
                        )
                    }
                }

                apiActionFeedback?.let { feedback ->
                    Text(
                        text = feedback,
                        style = MonoTextStyle.copy(fontSize = 11.sp, color = BullGreen)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Keep Bot Resident on Phone",
                            style = MaterialTheme.typography.titleSmall,
                            color = TextPrimary
                        )
                        Text(
                            text = "Runs foreground service so the APK stays active on your phone even when minimized",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                    Switch(
                        checked = state.botConfig.keepRunningOnPhone,
                        onCheckedChange = { enable ->
                            if (enable && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                val granted = ContextCompat.checkSelfPermission(
                                    context,
                                    Manifest.permission.POST_NOTIFICATIONS
                                ) == PackageManager.PERMISSION_GRANTED
                                if (granted) {
                                    onUpdateConfig(state.botConfig.copy(keepRunningOnPhone = true))
                                } else {
                                    notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                }
                            } else {
                                onUpdateConfig(state.botConfig.copy(keepRunningOnPhone = enable))
                            }
                        },
                        modifier = Modifier.testTag("broker_switch_keep_running"),
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = ObsidianBg,
                            checkedTrackColor = BullGreen
                        )
                    )
                }
            }
        }

        // Paper Simulation Reset & Feed Status
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(CarbonSurface)
                    .border(1.dp, BorderSlate, RoundedCornerShape(14.dp))
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "DATA FEED & PAPER FLIP ACCOUNT (ZAR / SZL 1:1)",
                    style = MaterialTheme.typography.labelMedium,
                    color = TextSecondary
                )
                Text(
                    text = "Active Status: ${state.accountSummary.brokerStatusText}\nSpot Feed: ${state.accountSummary.spotFeedSource}",
                    style = MonoTextStyle.copy(fontSize = 11.sp, color = TextPrimary)
                )

                OutlinedButton(
                    onClick = { showResetDialog = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("reset_paper_button"),
                    shape = RoundedCornerShape(10.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, BorderSlate),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = AmberWarn)
                ) {
                    Icon(
                        imageVector = Icons.Filled.RestartAlt,
                        contentDescription = "Reset paper account",
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Reset Paper Flip (R200 → R10,000) & Clear History", style = MaterialTheme.typography.labelLarge)
                }
            }
        }

        // Risk Disclaimer
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(AmberWarnMuted.copy(alpha = 0.45f))
                    .border(1.dp, AmberWarn.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                    .padding(12.dp),
                verticalAlignment = Alignment.Top
            ) {
                Icon(
                    imageVector = Icons.Filled.Security,
                    contentDescription = "Risk notice",
                    tint = AmberWarn,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "Attempting to flip 200 ZAR to 10,000 ZAR (50x return) via 1-minute pyramiding requires high leverage and carries significant risk of rapid account drawdown. Always validate your XM Global or MT5 Demo account before switching to MT5 Live.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }
        }
    }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            containerColor = ElevatedCard,
            title = { Text("Reset Paper Flip Account?", color = TextPrimary) },
            text = {
                Text(
                    "This will close all open paper positions, reset your virtual balance to R200.00 ZAR (Target: R10,000.00), and clear local trade history.",
                    color = TextSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onResetPaperAccount()
                        showResetDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AmberWarn, contentColor = ObsidianBg)
                ) {
                    Text("Reset R200")
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            }
        )
    }
}

@Composable
private fun VerificationBanner(brokerTestMessage: Pair<Boolean, String>?) {
    brokerTestMessage?.let { (ok, msg) ->
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(if (ok) BullGreenMuted else BearRedMuted)
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (ok) Icons.Filled.CheckCircle else Icons.Filled.ErrorOutline,
                contentDescription = msg,
                tint = if (ok) BullGreen else BearRed,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = msg,
                style = MaterialTheme.typography.bodySmall,
                color = if (ok) BullGreen else BearRed
            )
        }
    }
}

@Composable
private fun textFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = CyberCyan,
    unfocusedBorderColor = BorderSlate,
    focusedTextColor = TextPrimary,
    unfocusedTextColor = TextPrimary
)
