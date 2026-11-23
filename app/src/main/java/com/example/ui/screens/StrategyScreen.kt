package com.example.ui.screens

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.example.domain.model.BotConfig
import com.example.domain.model.ScalpingStrategyType
import com.example.domain.model.SessionFilter
import com.example.domain.model.TradeDirectionFilter
import com.example.domain.model.TradingRiskProfile
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
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun StrategyScreen(
    config: BotConfig,
    onUpdateConfig: (BotConfig) -> Unit,
    onApplyRiskProfile: (TradingRiskProfile) -> Unit,
    modifier: Modifier = Modifier
) {
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
        contentPadding = PaddingValues(top = 12.dp, bottom = 28.dp)
    ) {
        item {
            Column {
                Text(
                    text = "Trading Options & 1m Strategy",
                    style = MaterialTheme.typography.headlineSmall,
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Choose trade direction, risk style, 4-position favorable pyramiding, flip profit auto-lock, and on-device persistence.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }
        }

        // Trading Style Presets & Direction Filter
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(ElevatedCard)
                    .border(1.dp, BorderSlate, RoundedCornerShape(14.dp))
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "TRADING OPTIONS & DIRECTION",
                    style = MaterialTheme.typography.labelMedium,
                    color = CyberCyan
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
                                .padding(vertical = 10.dp)
                                .testTag("direction_option_${dir.name}"),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = dir.label,
                                style = MonoTextStyle.copy(
                                    fontSize = 11.sp,
                                    fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                                    color = if (active) accent else TextSecondary
                                )
                            )
                        }
                    }
                }

                Text(
                    text = "ONE-TAP RISK PROFILE PRESETS",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMuted
                )

                TradingRiskProfile.entries.forEach { profile ->
                    val selected = config.riskProfile == profile
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .minimumInteractiveComponentSize()
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (selected) CyberCyanMuted.copy(alpha = 0.45f) else CarbonSurface)
                            .border(1.dp, if (selected) CyberCyan else BorderSlate, RoundedCornerShape(10.dp))
                            .clickable { onApplyRiskProfile(profile) }
                            .padding(horizontal = 12.dp, vertical = 10.dp)
                            .testTag("profile_preset_${profile.name}"),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = profile.title,
                                style = MaterialTheme.typography.titleSmall,
                                color = if (selected) CyberCyan else TextPrimary
                            )
                            Text(
                                text = profile.subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                        }
                        if (selected) {
                            Text(
                                text = "ACTIVE",
                                style = MonoTextStyle.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold, color = CyberCyan)
                            )
                        }
                    }
                }
            }
        }

        // Strategy Selection Cards
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "EXECUTION ALGORITHM",
                    style = MaterialTheme.typography.labelMedium,
                    color = TextSecondary
                )
                ScalpingStrategyType.entries.forEach { strat ->
                    val selected = strat == config.strategy
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (selected) CyberCyanMuted.copy(alpha = 0.55f) else CarbonSurface)
                            .border(
                                width = 1.dp,
                                color = if (selected) CyberCyan else BorderSlate,
                                shape = RoundedCornerShape(12.dp)
                            )
                            .clickable { onUpdateConfig(config.copy(strategy = strat)) }
                            .padding(14.dp)
                            .testTag("strategy_card_${strat.name}")
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = strat.title,
                                style = MaterialTheme.typography.titleMedium,
                                color = if (selected) CyberCyan else TextPrimary
                            )
                            Text(
                                text = strat.subtitle,
                                style = MonoTextStyle.copy(
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (selected) CyberCyan else TextMuted
                                )
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = strat.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                }
            }
        }

        // 4-Position Pyramid Scaling & Auto-Compound Card
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
                    text = "PYRAMID SCALE-IN & FLIP COMPOUNDING",
                    style = MaterialTheme.typography.labelMedium,
                    color = CyberCyan
                )

                ParameterSlider(
                    label = "Max Open Positions (Stack)",
                    valueText = "${config.maxOpenTrades} positions",
                    value = config.maxOpenTrades.toFloat(),
                    range = 1f..8f,
                    onValueChange = { v ->
                        onUpdateConfig(config.copy(maxOpenTrades = v.roundToInt()))
                    },
                    accent = BullGreen,
                    testTag = "slider_max_trades"
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Add Position When Market Favors",
                            style = MaterialTheme.typography.titleSmall,
                            color = TextPrimary
                        )
                        Text(
                            text = "Pyramids winning layers up to max stack & locks earlier SL",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                    Switch(
                        checked = config.scaleInOnProfit,
                        onCheckedChange = { onUpdateConfig(config.copy(scaleInOnProfit = it)) },
                        modifier = Modifier.testTag("switch_scale_in"),
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = ObsidianBg,
                            checkedTrackColor = CyberCyan
                        )
                    )
                }

                if (config.scaleInOnProfit) {
                    ParameterSlider(
                        label = "Favorable Step Before Adding Layer",
                        valueText = String.format(Locale.US, "+%.1f pips", config.scaleInStepPips),
                        value = config.scaleInStepPips.toFloat(),
                        range = 1.0f..10.0f,
                        onValueChange = { v ->
                            onUpdateConfig(config.copy(scaleInStepPips = (v * 2).roundToInt() / 2.0))
                        },
                        accent = CyberCyan,
                        testTag = "slider_scale_in_step"
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Auto-Compound Flip Sizing (R200 → R10k)",
                            style = MaterialTheme.typography.titleSmall,
                            color = TextPrimary
                        )
                        Text(
                            text = "Scales lot size dynamically as ZAR equity grows",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                    Switch(
                        checked = config.autoCompoundSizing,
                        onCheckedChange = { onUpdateConfig(config.copy(autoCompoundSizing = it)) },
                        modifier = Modifier.testTag("switch_auto_compound"),
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = ObsidianBg,
                            checkedTrackColor = BullGreen
                        )
                    )
                }
            }
        }

        // Position Sizing & SL/TP Risk Card
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(ElevatedCard)
                    .border(1.dp, BorderSlate, RoundedCornerShape(14.dp))
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "RISK & TARGETING",
                        style = MaterialTheme.typography.labelMedium,
                        color = TextSecondary
                    )
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (config.riskRewardRatio >= 1.5) BullGreen.copy(alpha = 0.18f) else AmberWarn.copy(alpha = 0.18f))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = String.format(Locale.US, "R:R  1 : %.2f", config.riskRewardRatio),
                            style = MonoTextStyle.copy(
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (config.riskRewardRatio >= 1.5) BullGreen else AmberWarn
                            )
                        )
                    }
                }

                ParameterSlider(
                    label = "Base Lot Size (Min)",
                    valueText = String.format(Locale.US, "%.2f lots (%d units)", config.lotSize, (config.lotSize * 100_000).roundToInt()),
                    value = config.lotSize.toFloat(),
                    range = 0.01f..1.00f,
                    onValueChange = { v ->
                        val snapped = (v * 100).roundToInt() / 100.0
                        onUpdateConfig(config.copy(lotSize = snapped.coerceAtLeast(0.01)))
                    },
                    accent = CyberCyan,
                    testTag = "slider_lot_size"
                )

                ParameterSlider(
                    label = "Stop Loss",
                    valueText = String.format(Locale.US, "%.1f pips", config.stopLossPips),
                    value = config.stopLossPips.toFloat(),
                    range = 3f..30f,
                    onValueChange = { v ->
                        onUpdateConfig(config.copy(stopLossPips = (v * 2).roundToInt() / 2.0))
                    },
                    accent = BearRed,
                    testTag = "slider_stop_loss"
                )

                ParameterSlider(
                    label = "Take Profit",
                    valueText = String.format(Locale.US, "%.1f pips", config.takeProfitPips),
                    value = config.takeProfitPips.toFloat(),
                    range = 4f..50f,
                    onValueChange = { v ->
                        onUpdateConfig(config.copy(takeProfitPips = (v * 2).roundToInt() / 2.0))
                    },
                    accent = BullGreen,
                    testTag = "slider_take_profit"
                )

                ParameterSlider(
                    label = "Max Spread Guard",
                    valueText = String.format(Locale.US, "%.1f pips", config.maxSpreadPips),
                    value = config.maxSpreadPips.toFloat(),
                    range = 0.5f..8.0f,
                    onValueChange = { v ->
                        onUpdateConfig(config.copy(maxSpreadPips = (v * 10).roundToInt() / 10.0))
                    },
                    accent = AmberWarn,
                    testTag = "slider_max_spread"
                )

                ParameterSlider(
                    label = "Daily Loss Circuit Breaker (ZAR)",
                    valueText = String.format(Locale.US, "-R%.0f", config.dailyLossLimitZar),
                    value = config.dailyLossLimitZar.toFloat(),
                    range = 50f..2500f,
                    onValueChange = { v ->
                        val snapped = ((v / 25f).roundToInt() * 25).toDouble()
                        onUpdateConfig(config.copy(dailyLossLimitZar = snapped.coerceAtLeast(50.0)))
                    },
                    accent = BearRed,
                    testTag = "slider_daily_loss"
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Trailing Stop Protection",
                            style = MaterialTheme.typography.titleSmall,
                            color = TextPrimary
                        )
                        Text(
                            text = String.format(Locale.US, "Activates after +%.1f pips in profit", config.trailingTriggerPips),
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                    Switch(
                        checked = config.trailingStopEnabled,
                        onCheckedChange = { onUpdateConfig(config.copy(trailingStopEnabled = it)) },
                        modifier = Modifier.testTag("switch_trailing_stop"),
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = ObsidianBg,
                            checkedTrackColor = BullGreen
                        )
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Breakeven Auto-Lock",
                            style = MaterialTheme.typography.titleSmall,
                            color = TextPrimary
                        )
                        Text(
                            text = String.format(Locale.US, "Moves SL above entry after +%.1f pips", config.breakevenTriggerPips),
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                    Switch(
                        checked = config.breakevenLockEnabled,
                        onCheckedChange = { onUpdateConfig(config.copy(breakevenLockEnabled = it)) },
                        modifier = Modifier.testTag("switch_breakeven_lock"),
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = ObsidianBg,
                            checkedTrackColor = CyberCyan
                        )
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Auto-Stop When Flip Target Hit",
                            style = MaterialTheme.typography.titleSmall,
                            color = TextPrimary
                        )
                        Text(
                            text = String.format(Locale.US, "Locks profit when equity reaches R%,.0f", config.targetProfitStopZar),
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                    Switch(
                        checked = config.stopOnTargetReached,
                        onCheckedChange = { onUpdateConfig(config.copy(stopOnTargetReached = it)) },
                        modifier = Modifier.testTag("switch_target_stop"),
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = ObsidianBg,
                            checkedTrackColor = BullGreen
                        )
                    )
                }

                if (config.stopOnTargetReached) {
                    ParameterSlider(
                        label = "Flip Target Goal (ZAR)",
                        valueText = String.format(Locale.US, "R%,.0f", config.targetProfitStopZar),
                        value = config.targetProfitStopZar.toFloat(),
                        range = 1000f..50000f,
                        onValueChange = { v ->
                            val snapped = ((v / 500f).roundToInt() * 500).toDouble()
                            onUpdateConfig(config.copy(targetProfitStopZar = snapped.coerceAtLeast(1000.0)))
                        },
                        accent = BullGreen,
                        testTag = "slider_target_profit"
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Keep Bot Running on Phone",
                            style = MaterialTheme.typography.titleSmall,
                            color = TextPrimary
                        )
                        Text(
                            text = "Persistent foreground engine keeps 1m scalper & local API alive in background",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
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
                        modifier = Modifier.testTag("switch_keep_running"),
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = ObsidianBg,
                            checkedTrackColor = CyberCyan
                        )
                    )
                }
            }
        }

        // Indicator Parameters
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
                    text = "1M INDICATOR CALIBRATION",
                    style = MaterialTheme.typography.labelMedium,
                    color = TextSecondary
                )

                ParameterSlider(
                    label = "Fast EMA Period",
                    valueText = "${config.fastEmaPeriod} bars",
                    value = config.fastEmaPeriod.toFloat(),
                    range = 3f..20f,
                    onValueChange = { v ->
                        val fast = v.roundToInt().coerceAtMost(config.slowEmaPeriod - 2)
                        onUpdateConfig(config.copy(fastEmaPeriod = fast))
                    },
                    accent = CyberCyan,
                    testTag = "slider_fast_ema"
                )

                ParameterSlider(
                    label = "Slow EMA Period",
                    valueText = "${config.slowEmaPeriod} bars",
                    value = config.slowEmaPeriod.toFloat(),
                    range = 10f..50f,
                    onValueChange = { v ->
                        val slow = v.roundToInt().coerceAtLeast(config.fastEmaPeriod + 2)
                        onUpdateConfig(config.copy(slowEmaPeriod = slow))
                    },
                    accent = AmberWarn,
                    testTag = "slider_slow_ema"
                )

                ParameterSlider(
                    label = "RSI Period",
                    valueText = "${config.rsiPeriod} bars",
                    value = config.rsiPeriod.toFloat(),
                    range = 5f..21f,
                    onValueChange = { v ->
                        onUpdateConfig(config.copy(rsiPeriod = v.roundToInt()))
                    },
                    accent = CyberCyan,
                    testTag = "slider_rsi_period"
                )
            }
        }

        // Session Filter
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(CarbonSurface)
                    .border(1.dp, BorderSlate, RoundedCornerShape(14.dp))
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "TRADING SESSION WINDOW",
                    style = MaterialTheme.typography.labelMedium,
                    color = TextSecondary
                )
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
                            .padding(horizontal = 12.dp, vertical = 10.dp)
                            .testTag("session_option_${session.name}"),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = session.label,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (selected) CyberCyan else TextPrimary,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                        )
                        if (selected) {
                            Text(
                                text = "ACTIVE",
                                style = MonoTextStyle.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold, color = CyberCyan)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ParameterSlider(
    label: String,
    valueText: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    accent: androidx.compose.ui.graphics.Color,
    testTag: String
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = label, style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
            Text(
                text = valueText,
                style = MonoTextStyle.copy(fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = accent)
            )
        }
        Slider(
            value = value.coerceIn(range.start, range.endInclusive),
            onValueChange = onValueChange,
            valueRange = range,
            modifier = Modifier.testTag(testTag),
            colors = SliderDefaults.colors(
                thumbColor = accent,
                activeTrackColor = accent,
                inactiveTrackColor = BorderSlate
            )
        )
    }
}
