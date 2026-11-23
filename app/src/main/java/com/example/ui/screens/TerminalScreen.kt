package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.Canvas
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import com.example.domain.engine.TechnicalIndicators
import com.example.domain.model.BotConfig
import com.example.domain.model.BotRunState
import com.example.domain.model.FxPair
import com.example.domain.model.SignalLogEntry
import com.example.domain.model.Timeframe
import com.example.domain.model.TradeDirectionFilter
import com.example.domain.model.TradeSide
import com.example.domain.model.TradingRiskProfile
import com.example.ui.components.BotStatusBadge
import com.example.ui.components.CandlestickTerminalChart
import com.example.ui.components.EnvironmentSafetyBanner
import com.example.ui.components.PairSelectorStrip
import com.example.ui.components.TimeframePillRow
import com.example.ui.theme.AmberWarn
import com.example.ui.theme.BearRed
import com.example.ui.theme.BearRedBorder
import com.example.ui.theme.BearRedMuted
import com.example.ui.theme.BorderSlate
import com.example.ui.theme.BullGreen
import com.example.ui.theme.BullGreenBorder
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun TerminalScreen(
    state: TradingUiState,
    isWideScreen: Boolean,
    onSelectPair: (FxPair) -> Unit,
    onSelectTimeframe: (Timeframe) -> Unit,
    onToggleBot: () -> Unit,
    onManualOrder: (TradeSide) -> Unit,
    onFlattenAll: () -> Unit,
    onUpdateConfig: (BotConfig) -> Unit,
    onApplyRiskProfile: (TradingRiskProfile) -> Unit,
    onRefreshSpot: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (isWideScreen) {
        Row(
            modifier = modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            LazyColumn(
                modifier = Modifier.weight(1.35f),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                item {
                    EnvironmentSafetyBanner(
                        environment = state.brokerCredentials.environment,
                        spotFeedSource = state.accountSummary.spotFeedSource,
                        mt5BrokerLabel = "${state.brokerCredentials.mt5BrokerName} (${state.brokerCredentials.mt5Server})"
                    )
                }
                item {
                    PairSelectorStrip(
                        selectedPair = state.market.pair,
                        onSelectPair = onSelectPair
                    )
                }
                item {
                    MarketQuoteHeaderCard(
                        state = state,
                        onSelectTimeframe = onSelectTimeframe,
                        onUpdateConfig = onUpdateConfig,
                        onRefreshSpot = onRefreshSpot
                    )
                }
                item {
                    CandlestickTerminalChart(
                        pair = state.market.pair,
                        candles = state.market.candles,
                        config = state.botConfig,
                        openPositions = state.openPositions
                    )
                }
            }

            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                item {
                    FlipChallengeProgressCard(state = state)
                }
                item {
                    BotControlDeckCard(
                        state = state,
                        onToggleBot = onToggleBot,
                        onFlattenAll = onFlattenAll,
                        onUpdateConfig = onUpdateConfig,
                        onApplyRiskProfile = onApplyRiskProfile
                    )
                }
                item {
                    QuickScalpTicketCard(
                        state = state,
                        onManualOrder = onManualOrder
                    )
                }
                item {
                    SectionHeader(
                        title = "SIGNAL & EXECUTION AUDIT",
                        trailing = "${state.signalLogs.size} events"
                    )
                }
                if (state.signalLogs.isEmpty()) {
                    item { EmptyAuditCard() }
                } else {
                    items(state.signalLogs.take(20), key = { it.id }) { log ->
                        SignalLogRow(entry = log)
                    }
                }
            }
        }
    } else {
        LazyColumn(
            modifier = modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(top = 10.dp, bottom = 24.dp)
        ) {
            item {
                EnvironmentSafetyBanner(
                    environment = state.brokerCredentials.environment,
                    spotFeedSource = state.accountSummary.spotFeedSource,
                    mt5BrokerLabel = "${state.brokerCredentials.mt5BrokerName} (${state.brokerCredentials.mt5Server})"
                )
            }
            item {
                FlipChallengeProgressCard(state = state)
            }
            item {
                PairSelectorStrip(
                    selectedPair = state.market.pair,
                    onSelectPair = onSelectPair
                )
            }
            item {
                MarketQuoteHeaderCard(
                    state = state,
                    onSelectTimeframe = onSelectTimeframe,
                    onUpdateConfig = onUpdateConfig,
                    onRefreshSpot = onRefreshSpot
                )
            }
            item {
                CandlestickTerminalChart(
                    pair = state.market.pair,
                    candles = state.market.candles,
                    config = state.botConfig,
                    openPositions = state.openPositions
                )
            }
            item {
                BotControlDeckCard(
                    state = state,
                    onToggleBot = onToggleBot,
                    onFlattenAll = onFlattenAll,
                    onUpdateConfig = onUpdateConfig,
                    onApplyRiskProfile = onApplyRiskProfile
                )
            }
            item {
                QuickScalpTicketCard(
                    state = state,
                    onManualOrder = onManualOrder
                )
            }
            item {
                SectionHeader(
                    title = "SIGNAL & EXECUTION AUDIT",
                    trailing = "${state.signalLogs.size} events"
                )
            }
            if (state.signalLogs.isEmpty()) {
                item { EmptyAuditCard() }
            } else {
                items(state.signalLogs.take(20), key = { it.id }) { log ->
                    SignalLogRow(entry = log)
                }
            }
        }
    }
}

@Composable
private fun FlipChallengeProgressCard(state: TradingUiState) {
    val acct = state.accountSummary
    val effectiveLot = TechnicalIndicators.computeEffectiveLotSize(state.botConfig, acct.equity)
    val pctComplete = (acct.flipProgressFraction * 100f)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(ElevatedCard)
            .border(1.dp, BorderSlate, RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .testTag("flip_progress_card")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "ZAR FLIP CHALLENGE (R200 → R10,000)",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMuted
                )
                Spacer(modifier = Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = acct.formatMoney(acct.equity),
                        style = MonoTextStyle.copy(
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (acct.equity >= acct.startingBalance) BullGreen else BearRed
                        )
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = String.format(Locale.US, "(%.2fx • %.1f%%)", acct.flipMultiplier, pctComplete),
                        style = MonoTextStyle.copy(fontSize = 10.sp, color = CyberCyan)
                    )
                }
            }

            // Live mini sparkline of real-time balance growth
            val sparkPoints = state.liveEquityTicks.takeLast(30)
            if (sparkPoints.size >= 2) {
                Canvas(
                    modifier = Modifier
                        .width(78.dp)
                        .height(30.dp)
                        .padding(horizontal = 6.dp)
                        .testTag("flip_mini_sparkline")
                ) {
                    val minV = sparkPoints.minOf { it.equity }
                    val maxV = sparkPoints.maxOf { it.equity }
                    val span = (maxV - minV).coerceAtLeast(1.0)
                    val stepX = size.width / (sparkPoints.size - 1).coerceAtLeast(1)
                    val color = if (sparkPoints.last().equity >= acct.startingBalance) BullGreen else BearRed
                    val line = Path()
                    val area = Path()
                    sparkPoints.forEachIndexed { idx, pt ->
                        val x = idx * stepX
                        val y = size.height - (((pt.equity - minV) / span).toFloat() * size.height)
                        if (idx == 0) {
                            line.moveTo(x, y)
                            area.moveTo(x, size.height)
                            area.lineTo(x, y)
                        } else {
                            line.lineTo(x, y)
                            area.lineTo(x, y)
                        }
                        if (idx == sparkPoints.lastIndex) {
                            area.lineTo(x, size.height)
                            area.close()
                        }
                    }
                    drawPath(
                        path = area,
                        brush = Brush.verticalGradient(listOf(color.copy(alpha = 0.25f), Color.Transparent))
                    )
                    drawPath(
                        path = line,
                        color = color,
                        style = Stroke(width = 1.6.dp.toPx(), cap = StrokeCap.Round)
                    )
                }
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "LAYERS ${state.openPositions.size}/${state.botConfig.maxOpenTrades}",
                    style = MonoTextStyle.copy(
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (state.openPositions.isNotEmpty()) BullGreen else TextSecondary
                    )
                )
                Text(
                    text = String.format(Locale.US, "Lot %.2f • USD/ZAR %.2f", effectiveLot, acct.usdToZarRate),
                    style = MonoTextStyle.copy(fontSize = 10.sp, color = TextMuted)
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        LinearProgressIndicator(
            progress = { acct.flipProgressFraction },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(999.dp)),
            color = BullGreen,
            trackColor = ObsidianBg
        )
    }
}

@Composable
private fun MarketQuoteHeaderCard(
    state: TradingUiState,
    onSelectTimeframe: (Timeframe) -> Unit,
    onUpdateConfig: (BotConfig) -> Unit,
    onRefreshSpot: () -> Unit
) {
    val quote = state.market.quote
    val pair = state.market.pair
    val spreadOk = quote.spreadPips <= state.botConfig.maxSpreadPips

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(ElevatedCard, CarbonSurface)
                )
            )
            .border(1.dp, BorderSlate, RoundedCornerShape(14.dp))
            .padding(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = pair.displayName,
                        style = MaterialTheme.typography.headlineSmall,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (spreadOk) BullGreenMuted else BearRedMuted)
                            .padding(horizontal = 7.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = String.format(Locale.US, "SPR %.1fp", quote.spreadPips),
                            style = MonoTextStyle.copy(
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (spreadOk) BullGreen else BearRed
                            )
                        )
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                val upl = state.accountSummary.unrealizedPnl
                Text(
                    text = "Floating P&L: ${state.accountSummary.formatMoney(upl, signed = true)}  •  1m Scalp",
                    style = MonoTextStyle.copy(
                        fontSize = 11.sp,
                        color = if (upl >= 0) BullGreen else BearRed
                    )
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onRefreshSpot,
                    modifier = Modifier.testTag("refresh_spot_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Refresh,
                        contentDescription = "Refresh live spot FX rates",
                        tint = CyberCyan
                    )
                }
                BotStatusBadge(runState = state.botRunState)
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Column {
                    Text("BID", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                    Text(
                        text = pair.formatPrice(quote.bid),
                        style = MonoTextStyle.copy(
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = BearRed
                        )
                    )
                }
                Column {
                    Text("ASK", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                    Text(
                        text = pair.formatPrice(quote.ask),
                        style = MonoTextStyle.copy(
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = BullGreen
                        )
                    )
                }
            }

            TimeframePillRow(
                selected = state.market.timeframe,
                onSelect = onSelectTimeframe
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OverlayToggleChip(
                    label = "EMA ${state.botConfig.fastEmaPeriod}/${state.botConfig.slowEmaPeriod}",
                    active = state.botConfig.showEmaLines,
                    onClick = {
                        onUpdateConfig(state.botConfig.copy(showEmaLines = !state.botConfig.showEmaLines))
                    },
                    testTag = "toggle_ema_overlay"
                )
                OverlayToggleChip(
                    label = "BB(20)",
                    active = state.botConfig.showBollingerBands,
                    onClick = {
                        onUpdateConfig(state.botConfig.copy(showBollingerBands = !state.botConfig.showBollingerBands))
                    },
                    testTag = "toggle_bb_overlay"
                )
            }

            val bias = state.market.indicators.trendBias
            val biasColor = when (bias) {
                "BULLISH" -> BullGreen
                "BEARISH" -> BearRed
                else -> TextSecondary
            }
            Text(
                text = "BIAS: $bias",
                style = MonoTextStyle.copy(
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = biasColor
                )
            )
        }
    }
}

@Composable
private fun OverlayToggleChip(
    label: String,
    active: Boolean,
    onClick: () -> Unit,
    testTag: String
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(if (active) CyberCyanMuted else ObsidianBg)
            .border(1.dp, if (active) CyberCyan.copy(alpha = 0.6f) else BorderSlate, RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 9.dp, vertical = 5.dp)
            .testTag(testTag)
    ) {
        Text(
            text = label,
            style = MonoTextStyle.copy(
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (active) CyberCyan else TextMuted
            )
        )
    }
}

@Composable
private fun BotControlDeckCard(
    state: TradingUiState,
    onToggleBot: () -> Unit,
    onFlattenAll: () -> Unit,
    onUpdateConfig: (BotConfig) -> Unit,
    onApplyRiskProfile: (TradingRiskProfile) -> Unit
) {
    val isRunning = state.botRunState == BotRunState.SCANNING || state.botRunState == BotRunState.IN_POSITION
    val isHaltedByRisk = state.botRunState == BotRunState.CIRCUIT_BREAKER_HALTED
    val eval = state.market.lastEvaluation
    val effectiveLot = TechnicalIndicators.computeEffectiveLotSize(state.botConfig, state.accountSummary.equity)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(ElevatedCard)
            .border(
                width = 1.dp,
                color = when {
                    isHaltedByRisk -> BearRed
                    isRunning -> CyberCyan.copy(alpha = 0.65f)
                    else -> BorderSlate
                },
                shape = RoundedCornerShape(14.dp)
            )
            .padding(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.Bolt,
                        contentDescription = "Automated Bot",
                        tint = if (isRunning) CyberCyan else TextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = state.botConfig.strategy.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = String.format(
                        Locale.US,
                        "%.2fL • Max %d pos • Scale +%sp • SL %.0fp / TP %.0fp",
                        effectiveLot,
                        state.botConfig.maxOpenTrades,
                        if (state.botConfig.scaleInOnProfit) String.format(Locale.US, "%.1f", state.botConfig.scaleInStepPips) else "OFF",
                        state.botConfig.stopLossPips,
                        state.botConfig.takeProfitPips
                    ),
                    style = MonoTextStyle.copy(fontSize = 11.sp, color = TextSecondary)
                )
            }
        }

        Spacer(modifier = Modifier.height(9.dp))

        // Quick Trading Options Strip (Direction Mode + Risk Profile + Scale-In + API badge)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val nextDir = TradeDirectionFilter.entries[
                (TradeDirectionFilter.entries.indexOf(state.botConfig.directionFilter) + 1) %
                    TradeDirectionFilter.entries.size
            ]
            val dirColor = when (state.botConfig.directionFilter) {
                TradeDirectionFilter.BOTH -> CyberCyan
                TradeDirectionFilter.BUY_ONLY -> BullGreen
                TradeDirectionFilter.SELL_ONLY -> BearRed
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(dirColor.copy(alpha = 0.14f))
                    .border(1.dp, dirColor.copy(alpha = 0.55f), RoundedCornerShape(6.dp))
                    .clickable { onUpdateConfig(state.botConfig.copy(directionFilter = nextDir)) }
                    .padding(horizontal = 9.dp, vertical = 5.dp)
                    .testTag("quick_direction_chip")
            ) {
                Text(
                    text = "DIR: ${state.botConfig.directionFilter.shortLabel}",
                    style = MonoTextStyle.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold, color = dirColor)
                )
            }

            TradingRiskProfile.entries.forEach { profile ->
                val active = state.botConfig.riskProfile == profile
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (active) CyberCyanMuted else ObsidianBg)
                        .border(1.dp, if (active) CyberCyan else BorderSlate, RoundedCornerShape(6.dp))
                        .clickable { onApplyRiskProfile(profile) }
                        .padding(horizontal = 9.dp, vertical = 5.dp)
                        .testTag("quick_profile_${profile.name}")
                ) {
                    Text(
                        text = profile.title,
                        style = MonoTextStyle.copy(
                            fontSize = 10.sp,
                            fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                            color = if (active) CyberCyan else TextMuted
                        )
                    )
                }
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (state.botConfig.scaleInOnProfit) BullGreenMuted else ObsidianBg)
                    .border(
                        1.dp,
                        if (state.botConfig.scaleInOnProfit) BullGreen.copy(alpha = 0.5f) else BorderSlate,
                        RoundedCornerShape(6.dp)
                    )
                    .clickable {
                        onUpdateConfig(state.botConfig.copy(scaleInOnProfit = !state.botConfig.scaleInOnProfit))
                    }
                    .padding(horizontal = 9.dp, vertical = 5.dp)
                    .testTag("quick_scale_chip")
            ) {
                Text(
                    text = if (state.botConfig.scaleInOnProfit) "PYRAMID ${state.botConfig.maxOpenTrades}x ON" else "PYRAMID OFF",
                    style = MonoTextStyle.copy(
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (state.botConfig.scaleInOnProfit) BullGreen else TextMuted
                    )
                )
            }
        }

        if (eval != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(ObsidianBg.copy(alpha = 0.7f))
                    .padding(horizontal = 10.dp, vertical = 7.dp)
            ) {
                Text(
                    text = "${eval.actionLabel}: ${eval.reason}",
                    style = MonoTextStyle.copy(
                        fontSize = 11.sp,
                        color = when {
                            eval.signal == TradeSide.BUY -> BullGreen
                            eval.signal == TradeSide.SELL -> BearRed
                            isHaltedByRisk -> BearRed
                            else -> TextSecondary
                        }
                    ),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Button(
                onClick = onToggleBot,
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .testTag("toggle_bot_button"),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isRunning) BearRedMuted else CyberCyan,
                    contentColor = if (isRunning) BearRed else ObsidianBg
                )
            ) {
                Icon(
                    imageVector = if (isRunning) Icons.Filled.Stop else Icons.Filled.PlayArrow,
                    contentDescription = if (isRunning) "Halt Bot" else "Engage Bot",
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = when {
                        isRunning -> "HALT BOT"
                        isHaltedByRisk -> "RESET & ENGAGE"
                        else -> "ENGAGE 1M BOT"
                    },
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
            }

            AnimatedVisibility(visible = state.openPositions.isNotEmpty()) {
                OutlinedButton(
                    onClick = onFlattenAll,
                    modifier = Modifier
                        .height(48.dp)
                        .testTag("flatten_all_button"),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = BearRed),
                    border = androidx.compose.foundation.BorderStroke(1.dp, BearRedBorder)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "Emergency Flatten All Positions",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("FLATTEN", style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

@Composable
private fun QuickScalpTicketCard(
    state: TradingUiState,
    onManualOrder: (TradeSide) -> Unit
) {
    val effectiveLot = TechnicalIndicators.computeEffectiveLotSize(state.botConfig, state.accountSummary.equity)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(CarbonSurface)
            .border(1.dp, BorderSlate, RoundedCornerShape(14.dp))
            .padding(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "QUICK MANUAL 1M SCALP",
                style = MaterialTheme.typography.labelMedium,
                color = TextSecondary
            )
            Text(
                text = String.format(
                    Locale.US,
                    "Size %.2fL  |  R:R 1:%.2f",
                    effectiveLot,
                    state.botConfig.riskRewardRatio
                ),
                style = MonoTextStyle.copy(fontSize = 11.sp, color = CyberCyan)
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Button(
                onClick = { onManualOrder(TradeSide.SELL) },
                modifier = Modifier
                    .weight(1f)
                    .height(50.dp)
                    .testTag("manual_sell_button"),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = BearRedMuted,
                    contentColor = BearRed
                ),
                border = androidx.compose.foundation.BorderStroke(1.dp, BearRedBorder.copy(alpha = 0.65f))
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.TrendingDown,
                    contentDescription = "Sell Market",
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Column(horizontalAlignment = Alignment.Start) {
                    Text("SELL MKT", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                    Text(
                        text = state.market.pair.formatPrice(state.market.quote.bid),
                        style = MonoTextStyle.copy(fontSize = 10.sp)
                    )
                }
            }

            Button(
                onClick = { onManualOrder(TradeSide.BUY) },
                modifier = Modifier
                    .weight(1f)
                    .height(50.dp)
                    .testTag("manual_buy_button"),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = BullGreenMuted,
                    contentColor = BullGreen
                ),
                border = androidx.compose.foundation.BorderStroke(1.dp, BullGreenBorder.copy(alpha = 0.65f))
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.TrendingUp,
                    contentDescription = "Buy Market",
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Column(horizontalAlignment = Alignment.Start) {
                    Text("BUY MKT", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                    Text(
                        text = state.market.pair.formatPrice(state.market.quote.ask),
                        style = MonoTextStyle.copy(fontSize = 10.sp)
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String, trailing: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            color = TextSecondary
        )
        Text(
            text = trailing,
            style = MonoTextStyle.copy(fontSize = 11.sp, color = TextMuted)
        )
    }
}

@Composable
private fun EmptyAuditCard() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(CarbonSurface)
            .border(1.dp, BorderSlate, RoundedCornerShape(12.dp))
            .padding(18.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "Engage the 1m bot to scan signals, open up to 4 positions, and scale in when the market favors your trade.",
            style = MaterialTheme.typography.bodySmall,
            color = TextMuted
        )
    }
}

@Composable
private fun SignalLogRow(entry: SignalLogEntry) {
    val timeFmt = SimpleDateFormat("HH:mm:ss", Locale.US)
    val accent = when {
        entry.action.contains("CIRCUIT") || entry.action.contains("ERROR") || entry.action.contains("SL HIT") -> BearRed
        entry.side == TradeSide.BUY || entry.action.contains("TP HIT") -> BullGreen
        entry.side == TradeSide.SELL -> BearRed
        entry.action.contains("SPREAD") -> AmberWarn
        else -> CyberCyan
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(if (entry.isExecution) ElevatedCard else CarbonSurface)
            .border(1.dp, if (entry.isExecution) accent.copy(alpha = 0.35f) else BorderSlate, RoundedCornerShape(10.dp))
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = entry.action,
                    style = MonoTextStyle.copy(
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = accent
                    )
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "${entry.pair.displayName} ${entry.timeframe.label}",
                    style = MonoTextStyle.copy(fontSize = 10.sp, color = TextSecondary)
                )
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = entry.reason,
                style = MaterialTheme.typography.bodySmall,
                color = TextPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = timeFmt.format(Date(entry.timestamp)),
            style = MonoTextStyle.copy(fontSize = 10.sp, color = TextMuted)
        )
    }
}
