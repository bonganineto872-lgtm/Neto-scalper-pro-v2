package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.domain.model.BotConfig
import com.example.domain.model.ConfirmationSpeed
import com.example.domain.model.FlipCurrency
import com.example.domain.model.GoldBtcStrategyType
import com.example.domain.model.SessionFilter
import com.example.domain.model.TradeDirectionFilter
import com.example.ui.theme.AmberWarn
import com.example.ui.theme.BearRed
import com.example.ui.theme.BorderSlate
import com.example.ui.theme.BullGreen
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
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun StrategySettingsScreen(
    state: TradingUiState,
    onUpdateConfig: (BotConfig) -> Unit,
    onResetFlipProgress: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onBack)

    val config = state.botConfig
    val context = LocalContext.current
    val notifPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            onUpdateConfig(config.copy(keepRunningOnPhone = true))
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(top = 10.dp, bottom = 28.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("settings_back_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back to Signal Dashboard",
                        tint = CyberCyan
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
                Column {
                    Text(
                        text = "Strategy, Fast Confirmation & Flip Setup",
                        style = MaterialTheme.typography.titleLarge,
                        color = TextPrimary
                    )
                    Text(
                        text = "Calibrate Gold & Bitcoin M1–D1 filters and small-account flip sizing",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
            }
        }

        // Fast Confirmation Speed & Min Confidence
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(ElevatedCard)
                    .border(1.dp, CyberCyan.copy(alpha = 0.45f), RoundedCornerShape(14.dp))
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "FAST CONFIRMATION & CLEAN-SHEET GUARD",
                    style = MaterialTheme.typography.labelMedium,
                    color = CyberCyan
                )

                ConfirmationSpeed.entries.forEach { speed ->
                    val selected = config.confirmationSpeed == speed
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .minimumInteractiveComponentSize()
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (selected) CyberCyanMuted.copy(alpha = 0.5f) else CarbonSurface)
                            .border(1.dp, if (selected) CyberCyan else BorderSlate, RoundedCornerShape(10.dp))
                            .clickable { onUpdateConfig(config.copy(confirmationSpeed = speed)) }
                            .padding(12.dp)
                            .testTag("confirm_speed_${speed.name}"),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = speed.label,
                                style = MaterialTheme.typography.titleSmall,
                                color = if (selected) CyberCyan else TextPrimary
                            )
                            Text(
                                text = speed.subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                        }
                        if (selected) {
                            Text("ACTIVE", style = MonoTextStyle.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold, color = CyberCyan))
                        }
                    }
                }

                SettingsSlider(
                    label = "Minimum Signal Confidence",
                    valueText = "${config.minConfidencePercent}%",
                    value = config.minConfidencePercent.toFloat(),
                    range = 74f..95f,
                    onValueChange = { v -> onUpdateConfig(config.copy(minConfidencePercent = v.roundToInt())) },
                    accent = BullGreen,
                    testTag = "slider_min_confidence"
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TradeDirectionFilter.entries.forEach { dir ->
                        val active = config.directionFilter == dir
                        val accent = when (dir) {
                            TradeDirectionFilter.BOTH -> CyberCyan
                            TradeDirectionFilter.BUY_ONLY -> BullGreen
                            TradeDirectionFilter.SELL_ONLY -> BearRed
                        }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .minimumInteractiveComponentSize()
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (active) accent.copy(alpha = 0.16f) else CarbonSurface)
                                .border(1.dp, if (active) accent else BorderSlate, RoundedCornerShape(10.dp))
                                .clickable { onUpdateConfig(config.copy(directionFilter = dir)) }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = dir.label,
                                style = MonoTextStyle.copy(
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (active) accent else TextSecondary
                                )
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Zero-Drawdown Breakeven on TP1", style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                        Text("Moves SL to Entry automatically once TP1 hits", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                    }
                    Switch(
                        checked = config.breakevenOnTp1,
                        onCheckedChange = { onUpdateConfig(config.copy(breakevenOnTp1 = it)) },
                        colors = SwitchDefaults.colors(checkedThumbColor = ObsidianBg, checkedTrackColor = BullGreen)
                    )
                }
            }
        }

        // Small-to-Big Account Flip Compounding Setup
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(ElevatedCard)
                    .border(1.dp, AmberWarn.copy(alpha = 0.45f), RoundedCornerShape(14.dp))
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "SMALL-TO-BIG ACCOUNT FLIP PLAN",
                    style = MaterialTheme.typography.labelMedium,
                    color = AmberWarn
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FlipCurrency.entries.forEach { cur ->
                        val active = config.flipCurrency == cur
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .minimumInteractiveComponentSize()
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (active) CyberCyanMuted else CarbonSurface)
                                .border(1.dp, if (active) CyberCyan else BorderSlate, RoundedCornerShape(10.dp))
                                .clickable {
                                    val defaultStart = if (cur == FlipCurrency.USD) 50.0 else 200.0
                                    val defaultTarget = if (cur == FlipCurrency.USD) 2500.0 else 10000.0
                                    onUpdateConfig(
                                        config.copy(
                                            flipCurrency = cur,
                                            startingCapital = defaultStart,
                                            targetFlipCapital = defaultTarget
                                        )
                                    )
                                }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "${cur.code} (${cur.symbol})",
                                style = MonoTextStyle.copy(
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (active) CyberCyan else TextSecondary
                                )
                            )
                        }
                    }
                }

                SettingsSlider(
                    label = "Starting Small Account",
                    valueText = String.format(Locale.US, "%s%,.0f", config.flipCurrency.symbol, config.startingCapital),
                    value = config.startingCapital.toFloat(),
                    range = if (config.flipCurrency == FlipCurrency.USD) 10f..1000f else 100f..5000f,
                    onValueChange = { v ->
                        val step = if (config.flipCurrency == FlipCurrency.USD) 10f else 50f
                        val snapped = ((v / step).roundToInt() * step).toDouble()
                        onUpdateConfig(config.copy(startingCapital = snapped.coerceAtLeast(10.0)))
                    },
                    accent = CyberCyan,
                    testTag = "slider_start_capital"
                )

                SettingsSlider(
                    label = "Target Big Account Goal",
                    valueText = String.format(Locale.US, "%s%,.0f", config.flipCurrency.symbol, config.targetFlipCapital),
                    value = config.targetFlipCapital.toFloat(),
                    range = if (config.flipCurrency == FlipCurrency.USD) 500f..25000f else 2000f..100000f,
                    onValueChange = { v ->
                        val step = if (config.flipCurrency == FlipCurrency.USD) 250f else 1000f
                        val snapped = ((v / step).roundToInt() * step).toDouble()
                        onUpdateConfig(config.copy(targetFlipCapital = snapped.coerceAtLeast(config.startingCapital + 100.0)))
                    },
                    accent = BullGreen,
                    testTag = "slider_target_capital"
                )

                SettingsSlider(
                    label = "Risk Per Signal (% Compound)",
                    valueText = String.format(Locale.US, "%.1f%%", config.riskPercentPerTrade),
                    value = config.riskPercentPerTrade.toFloat(),
                    range = 1f..15f,
                    onValueChange = { v ->
                        onUpdateConfig(config.copy(riskPercentPerTrade = (v * 2).roundToInt() / 2.0))
                    },
                    accent = AmberWarn,
                    testTag = "slider_risk_percent"
                )

                OutlinedButton(
                    onClick = onResetFlipProgress,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(10.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, BorderSlate),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = AmberWarn)
                ) {
                    Icon(Icons.Filled.RestartAlt, contentDescription = "Reset Flip Stats", modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Reset Signal Track Record", style = MaterialTheme.typography.labelLarge)
                }
            }
        }

        // All World-Class Gold & Bitcoin Strategies
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("SELECT INTRADAY GOLD & BITCOIN STRATEGY", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                GoldBtcStrategyType.entries.forEach { strat ->
                    val selected = strat == config.strategy
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (selected) CyberCyanMuted.copy(alpha = 0.5f) else CarbonSurface)
                            .border(1.dp, if (selected) CyberCyan else BorderSlate, RoundedCornerShape(12.dp))
                            .clickable { onUpdateConfig(config.copy(strategy = strat)) }
                            .padding(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(strat.title, style = MaterialTheme.typography.titleSmall, color = if (selected) CyberCyan else TextPrimary)
                            Text(strat.subtitle, style = MonoTextStyle.copy(fontSize = 10.sp, color = TextMuted))
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(strat.description, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                    }
                }
            }
        }

        // Session Window & Background Engine
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
                Text("SESSION FILTER & BACKGROUND MONITOR", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                SessionFilter.entries.forEach { session ->
                    val selected = session == config.sessionFilter
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .minimumInteractiveComponentSize()
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (selected) CyberCyanMuted else ObsidianBg.copy(alpha = 0.55f))
                            .border(1.dp, if (selected) CyberCyan else BorderSlate, RoundedCornerShape(10.dp))
                            .clickable { onUpdateConfig(config.copy(sessionFilter = session)) }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(session.label, style = MaterialTheme.typography.bodyMedium, color = if (selected) CyberCyan else TextPrimary)
                        if (selected) {
                            Text("ACTIVE", style = MonoTextStyle.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold, color = CyberCyan))
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Keep Signal Bot Running on Phone", style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                        Text("Foreground monitor keeps M1–D1 signals live in background", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                    }
                    Switch(
                        checked = config.keepRunningOnPhone,
                        onCheckedChange = { enable ->
                            if (enable && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                val granted = ContextCompat.checkSelfPermission(
                                    context,
                                    Manifest.permission.POST_NOTIFICATIONS
                                ) == PackageManager.PERMISSION_GRANTED
                                if (granted) {
                                    onUpdateConfig(config.copy(keepRunningOnPhone = true))
                                } else {
                                    notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                }
                            } else {
                                onUpdateConfig(config.copy(keepRunningOnPhone = enable))
                            }
                        },
                        colors = SwitchDefaults.colors(checkedThumbColor = ObsidianBg, checkedTrackColor = CyberCyan)
                    )
                }

                Button(
                    onClick = onBack,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = CyberCyan, contentColor = ObsidianBg)
                ) {
                    Text("DONE • BACK TO DASHBOARD", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun SettingsSlider(
    label: String,
    valueText: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    accent: Color,
    testTag: String
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = label, style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
            Text(text = valueText, style = MonoTextStyle.copy(fontSize = 12.sp, fontWeight = FontWeight.Bold, color = accent))
        }
        Slider(
            value = value.coerceIn(range.start, range.endInclusive),
            onValueChange = onValueChange,
            valueRange = range,
            modifier = Modifier.testTag(testTag),
            colors = SliderDefaults.colors(thumbColor = accent, activeTrackColor = accent, inactiveTrackColor = BorderSlate)
        )
    }
}
