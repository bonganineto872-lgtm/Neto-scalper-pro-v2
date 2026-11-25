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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.model.BotConfig
import com.example.domain.model.BotRunState
import com.example.domain.model.ConfirmationStage
import com.example.domain.model.FxPair
import com.example.domain.model.GoldBtcStrategyType
import com.example.domain.model.SignalHistoryRecord
import com.example.domain.model.SignalStatus
import com.example.domain.model.Timeframe
import com.example.domain.model.TimeframeSignalRow
import com.example.domain.model.TradeSide
import com.example.domain.model.TradeSignal
import com.example.ui.components.BotStatusBadge
import com.example.ui.components.CandlestickTerminalChart
import com.example.ui.components.TimeframePillRow
import com.example.ui.theme.AmberWarn
import com.example.ui.theme.AmberWarnMuted
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
import kotlin.math.abs

@Composable
fun SignalDashboardScreen(
    state: TradingUiState,
    isWideScreen: Boolean,
    onSelectPair: (FxPair) -> Unit,
    onSelectTimeframe: (Timeframe) -> Unit,
    onSelectPairAndTimeframe: (FxPair, Timeframe) -> Unit,
    onToggleBot: () -> Unit,
    onForceScanNow: () -> Unit,
    onDismissSignal: (String) -> Unit,
    onUpdateConfig: (BotConfig) -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val clipboard = LocalClipboardManager.current
    var copiedBanner by remember { mutableStateOf<String?>(null) }

    val selectedPair = state.market.pair
    val selectedTf = state.market.timeframe
    val pairMatrix = remember(state.timeframeMatrix, selectedPair) {
        state.timeframeMatrix.filter { it.pair == selectedPair }
    }
    val activeForSelection = remember(state.activeSignals, selectedPair, selectedTf) {
        state.activeSignals.find { it.pair == selectedPair && it.timeframe == selectedTf && it.isLive }
    }

    if (isWideScreen) {
        Row(
            modifier = modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            LazyColumn(
                modifier = Modifier.weight(1.25f),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                item {
                    DashboardTopHeader(
                        state = state,
                        onToggleBot = onToggleBot,
                        onForceScanNow = onForceScanNow,
                        onOpenSettings = onOpenSettings
                    )
                }
                item {
                    GoldBitcoinSwitcherRow(
                        state = state,
                        onSelectPair = onSelectPair
                    )
                }
                item {
                    TimeframePillRow(
                        selected = selectedTf,
                        matrixRows = pairMatrix,
                        onSelect = onSelectTimeframe,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                item {
                    HeroSignalCard(
                        state = state,
                        activeSignal = activeForSelection,
                        copiedBanner = copiedBanner,
                        onCopySignal = { sig ->
                            clipboard.setText(AnnotatedString(sig.formattedCopyText()))
                            copiedBanner = "Copied ${sig.pair.displayName} ${sig.timeframe.label} signal!"
                        },
                        onDismissSignal = { sig -> onDismissSignal(sig.id) },
                        onForceScanNow = onForceScanNow
                    )
                }
                item {
                    ChartHeaderControls(
                        state = state,
                        onUpdateConfig = onUpdateConfig
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    CandlestickTerminalChart(
                        pair = selectedPair,
                        candles = state.market.candles,
                        config = state.botConfig,
                        activeSignal = activeForSelection
                    )
                }
            }

            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                item {
                    MultiTimeframeSignalMatrixCard(
                        rows = state.timeframeMatrix,
                        selectedPair = selectedPair,
                        selectedTf = selectedTf,
                        onRowClick = onSelectPairAndTimeframe
                    )
                }
                item {
                    StrategyQuickSelectorCard(
                        selectedStrategy = state.botConfig.strategy,
                        onSelectStrategy = { strat ->
                            onUpdateConfig(state.botConfig.copy(strategy = strat))
                        }
                    )
                }
                item {
                    SmallToBigFlipCard(
                        state = state,
                        onOpenSettings = onOpenSettings
                    )
                }
                item {
                    SectionLabel(
                        title = "CONFIRMED SIGNAL TRACK RECORD",
                        trailing = "${state.signalHistory.size} completed"
                    )
                }
                if (state.signalHistory.isEmpty()) {
                    item { EmptySignalHistoryCard() }
                } else {
                    items(state.signalHistory.take(15), key = { "${it.id}_${it.signalId}" }) { rec ->
                        SignalHistoryItemCard(record = rec)
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
            contentPadding = PaddingValues(top = 8.dp, bottom = 28.dp)
        ) {
            item {
                DashboardTopHeader(
                    state = state,
                    onToggleBot = onToggleBot,
                    onForceScanNow = onForceScanNow,
                    onOpenSettings = onOpenSettings
                )
            }
            item {
                GoldBitcoinSwitcherRow(
                    state = state,
                    onSelectPair = onSelectPair
                )
            }
            item {
                TimeframePillRow(
                    selected = selectedTf,
                    matrixRows = pairMatrix,
                    onSelect = onSelectTimeframe,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            item {
                HeroSignalCard(
                    state = state,
                    activeSignal = activeForSelection,
                    copiedBanner = copiedBanner,
                    onCopySignal = { sig ->
                        clipboard.setText(AnnotatedString(sig.formattedCopyText()))
                        copiedBanner = "Copied ${sig.pair.displayName} ${sig.timeframe.label} signal!"
                    },
                    onDismissSignal = { sig -> onDismissSignal(sig.id) },
                    onForceScanNow = onForceScanNow
                )
            }
            item {
                MultiTimeframeSignalMatrixCard(
                    rows = state.timeframeMatrix,
                    selectedPair = selectedPair,
                    selectedTf = selectedTf,
                    onRowClick = onSelectPairAndTimeframe
                )
            }
            item {
                ChartHeaderControls(
                    state = state,
                    onUpdateConfig = onUpdateConfig
                )
                Spacer(modifier = Modifier.height(6.dp))
                CandlestickTerminalChart(
                    pair = selectedPair,
                    candles = state.market.candles,
                    config = state.botConfig,
                    activeSignal = activeForSelection
                )
            }
            item {
                StrategyQuickSelectorCard(
                    selectedStrategy = state.botConfig.strategy,
                    onSelectStrategy = { strat ->
                        onUpdateConfig(state.botConfig.copy(strategy = strat))
                    }
                )
            }
            item {
                SmallToBigFlipCard(
                    state = state,
                    onOpenSettings = onOpenSettings
                )
            }
            item {
                SectionLabel(
                    title = "CONFIRMED SIGNAL TRACK RECORD",
                    trailing = "${state.signalHistory.size} completed"
                )
            }
            if (state.signalHistory.isEmpty()) {
                item { EmptySignalHistoryCard() }
            } else {
                items(state.signalHistory.take(15), key = { "${it.id}_${it.signalId}" }) { rec ->
                    SignalHistoryItemCard(record = rec)
                }
            }
        }
    }
}

@Composable
private fun DashboardTopHeader(
    state: TradingUiState,
    onToggleBot: () -> Unit,
    onForceScanNow: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val isRunning = state.botRunState != BotRunState.IDLE

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(
                Brush.linearGradient(listOf(ElevatedCard, CarbonSurface))
            )
            .border(1.dp, BorderSlate, RoundedCornerShape(14.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "NETO MASTER",
                    style = MaterialTheme.typography.titleLarge,
                    color = TextPrimary
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(BullGreen)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "${state.flipPlan.spotFeedSource} • No Paper Trading",
                        style = MonoTextStyle.copy(fontSize = 10.sp, color = TextSecondary),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            BotStatusBadge(runState = state.botRunState)
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
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
                    contentDescription = if (isRunning) "Pause Signal Engine" else "Start Signal Engine",
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (isRunning) "PAUSE BOT" else "START SIGNAL BOT",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
            }

            OutlinedButton(
                onClick = onForceScanNow,
                modifier = Modifier
                    .height(48.dp)
                    .testTag("force_scan_button"),
                shape = RoundedCornerShape(10.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, BorderSlate),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = CyberCyan)
            ) {
                Icon(
                    imageVector = Icons.Filled.Refresh,
                    contentDescription = "Scan M5 M15 H1 now",
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("SCAN", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            }

            IconButton(
                onClick = onOpenSettings,
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(ObsidianBg)
                    .border(1.dp, BorderSlate, RoundedCornerShape(10.dp))
                    .testTag("open_settings_button")
            ) {
                Icon(
                    imageVector = Icons.Filled.Tune,
                    contentDescription = "Strategy & Account Flip Settings",
                    tint = AmberWarn
                )
            }
        }
    }
}

@Composable
private fun GoldBitcoinSwitcherRow(
    state: TradingUiState,
    onSelectPair: (FxPair) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        FxPair.entries.forEach { pair ->
            val selected = pair == state.market.pair
            val quote = state.market.quotesByPair[pair] ?: state.market.quote
            val liveSignalForPair = state.activeSignals.firstOrNull { it.pair == pair && it.isLive }
            val accent = if (pair == FxPair.XAU_USD) AmberWarn else CyberCyan

            Column(
                modifier = Modifier
                    .weight(1f)
                    .minimumInteractiveComponentSize()
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (selected) ElevatedCard else CarbonSurface)
                    .border(
                        width = if (selected) 1.5.dp else 1.dp,
                        color = if (selected) accent else BorderSlate,
                        shape = RoundedCornerShape(12.dp)
                    )
                    .clickable { onSelectPair(pair) }
                    .padding(12.dp)
                    .testTag("pair_chip_${pair.name}"),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${pair.shortTitle} • ${pair.displayName}",
                        style = MonoTextStyle.copy(
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (selected) accent else TextSecondary
                        )
                    )
                    if (liveSignalForPair != null) {
                        val sigColor = if (liveSignalForPair.side == TradeSide.BUY) BullGreen else BearRed
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(sigColor.copy(alpha = 0.2f))
                                .padding(horizontal = 5.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "${liveSignalForPair.side.name} ${liveSignalForPair.timeframe.label}",
                                style = MonoTextStyle.copy(fontSize = 9.sp, fontWeight = FontWeight.Bold, color = sigColor)
                            )
                        }
                    }
                }

                Text(
                    text = pair.formatPrice(quote.mid),
                    style = MonoTextStyle.copy(
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "B ${pair.formatPrice(quote.bid)}",
                        style = MonoTextStyle.copy(fontSize = 10.sp, color = TextMuted)
                    )
                    Text(
                        text = String.format(Locale.US, "SPR %.1fp", quote.spreadPips),
                        style = MonoTextStyle.copy(fontSize = 10.sp, color = CyberCyan)
                    )
                }
            }
        }
    }
}

@Composable
private fun HeroSignalCard(
    state: TradingUiState,
    activeSignal: TradeSignal?,
    copiedBanner: String?,
    onCopySignal: (TradeSignal) -> Unit,
    onDismissSignal: (TradeSignal) -> Unit,
    onForceScanNow: () -> Unit
) {
    val pair = state.market.pair
    val tf = state.market.timeframe
    val eval = state.market.lastEvaluation

    val isConfirmed = activeSignal != null
    val isConfirming = !isConfirmed && eval?.candidateSide != null
    val side = activeSignal?.side ?: eval?.candidateSide

    val borderColor = when {
        isConfirmed && side == TradeSide.BUY -> BullGreenBorder
        isConfirmed && side == TradeSide.SELL -> BearRedBorder
        isConfirming -> AmberWarn
        else -> BorderSlate
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(ElevatedCard)
            .border(1.5.dp, borderColor, RoundedCornerShape(14.dp))
            .padding(14.dp)
            .testTag("hero_signal_card"),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Row 1: Timeframe + Signal Status + Clean-Sheet / Confidence Badges
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(CyberCyanMuted)
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "${pair.shortTitle} • ${tf.label}",
                        style = MonoTextStyle.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold, color = CyberCyan)
                    )
                }

                when {
                    isConfirmed && activeSignal != null -> {
                        val accent = if (activeSignal.side == TradeSide.BUY) BullGreen else BearRed
                        val bg = if (activeSignal.side == TradeSide.BUY) BullGreenMuted else BearRedMuted
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(bg)
                                .padding(horizontal = 9.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (activeSignal.side == TradeSide.BUY) Icons.AutoMirrored.Filled.TrendingUp else Icons.AutoMirrored.Filled.TrendingDown,
                                contentDescription = activeSignal.side.name,
                                tint = accent,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "CONFIRMED ${activeSignal.side.name}",
                                style = MonoTextStyle.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold, color = accent)
                            )
                        }
                    }
                    isConfirming && eval?.candidateSide != null -> {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(AmberWarnMuted)
                                .padding(horizontal = 9.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "CONFIRMING ${eval.candidateSide.name}...",
                                style = MonoTextStyle.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold, color = AmberWarn)
                            )
                        }
                    }
                    else -> {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(ObsidianBg)
                                .padding(horizontal = 9.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = eval?.actionLabel ?: "SCANNING",
                                style = MonoTextStyle.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextSecondary)
                            )
                        }
                    }
                }
            }

            if (activeSignal != null) {
                Text(
                    text = "${activeSignal.confidencePercent}% CONF",
                    style = MonoTextStyle.copy(fontSize = 12.sp, fontWeight = FontWeight.Bold, color = BullGreen)
                )
            } else if (isConfirming && eval != null) {
                Text(
                    text = "${eval.confidencePercent}% CONF",
                    style = MonoTextStyle.copy(fontSize = 12.sp, fontWeight = FontWeight.Bold, color = AmberWarn)
                )
            }
        }

        if (activeSignal != null) {
            // Confirmed Signal Details: Entry, SL, TP1, TP2, TP3 + Pips to Target!
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(text = "ENTRY PRICE", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                    Text(
                        text = pair.formatPrice(activeSignal.entryPrice),
                        style = MonoTextStyle.copy(fontSize = 20.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = "STOP LOSS", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                    Text(
                        text = pair.formatPrice(if (activeSignal.breakevenProtected) activeSignal.entryPrice else activeSignal.stopLoss),
                        style = MonoTextStyle.copy(fontSize = 15.sp, fontWeight = FontWeight.Bold, color = BearRed)
                    )
                    Text(
                        text = if (activeSignal.breakevenProtected) "BE LOCKED (0p DD)" else String.format(Locale.US, "-%.1f pips", activeSignal.slPips),
                        style = MonoTextStyle.copy(fontSize = 10.sp, color = if (activeSignal.breakevenProtected) BullGreen else BearRed)
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(text = "LIVE MOVE / MAX DD", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                    val moveColor = if (activeSignal.pipsMove >= 0) BullGreen else BearRed
                    Text(
                        text = String.format(Locale.US, "%+.1fp", activeSignal.pipsMove),
                        style = MonoTextStyle.copy(fontSize = 16.sp, fontWeight = FontWeight.Bold, color = moveColor)
                    )
                    Text(
                        text = String.format(Locale.US, "DD: %.1fp • %.2fL", activeSignal.maxDrawdownPips, activeSignal.recommendedLot),
                        style = MonoTextStyle.copy(fontSize = 10.sp, color = if (activeSignal.isCleanSheet) BullGreen else AmberWarn)
                    )
                }
            }

            // Take Profit Ladder with Pips to Target
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(ObsidianBg)
                    .border(1.dp, BorderSlate, RoundedCornerShape(10.dp))
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "TAKE PROFIT TARGETS & PIPS TO TARGET",
                    style = MaterialTheme.typography.labelSmall,
                    color = CyberCyan
                )
                TakeProfitTargetRow(
                    label = "TP1 (BE Lock)",
                    price = pair.formatPrice(activeSignal.takeProfit1),
                    targetPips = activeSignal.tp1Pips,
                    remainingPips = activeSignal.remainingPipsToTp1,
                    hit = activeSignal.status == SignalStatus.TP1_HIT || activeSignal.status == SignalStatus.TP2_HIT || activeSignal.status == SignalStatus.TP3_HIT
                )
                TakeProfitTargetRow(
                    label = "TP2 (Primary)",
                    price = pair.formatPrice(activeSignal.takeProfit2),
                    targetPips = activeSignal.tp2Pips,
                    remainingPips = activeSignal.remainingPipsToTp2,
                    hit = activeSignal.status == SignalStatus.TP2_HIT || activeSignal.status == SignalStatus.TP3_HIT
                )
                TakeProfitTargetRow(
                    label = "TP3 (Runner)",
                    price = pair.formatPrice(activeSignal.takeProfit3),
                    targetPips = activeSignal.tp3Pips,
                    remainingPips = activeSignal.remainingPipsToTp3,
                    hit = activeSignal.status == SignalStatus.TP3_HIT
                )
            }

            if (activeSignal.alignedStrategies.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (activeSignal.isCleanSheet) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(BullGreenMuted)
                                .border(1.dp, BullGreenBorder, RoundedCornerShape(6.dp))
                                .padding(horizontal = 7.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = "CLEAN SHEET • ${String.format(Locale.US, "%.1fp DD", activeSignal.maxDrawdownPips)}",
                                style = MonoTextStyle.copy(fontSize = 9.sp, fontWeight = FontWeight.Bold, color = BullGreen)
                            )
                        }
                    }
                    activeSignal.alignedStrategies.forEach { stratTag ->
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(CyberCyanMuted)
                                .padding(horizontal = 7.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = stratTag,
                                style = MonoTextStyle.copy(fontSize = 9.sp, fontWeight = FontWeight.SemiBold, color = CyberCyan)
                            )
                        }
                    }
                }
            }

            if (activeSignal.alignedStrategies.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (activeSignal.isCleanSheet) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(BullGreenMuted)
                                .border(1.dp, BullGreenBorder, RoundedCornerShape(6.dp))
                                .padding(horizontal = 7.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = "CLEAN SHEET • ${String.format(Locale.US, "%.1fp DD", activeSignal.maxDrawdownPips)}",
                                style = MonoTextStyle.copy(fontSize = 9.sp, fontWeight = FontWeight.Bold, color = BullGreen)
                            )
                        }
                    }
                    activeSignal.alignedStrategies.forEach { stratTag ->
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(CyberCyanMuted)
                                .padding(horizontal = 7.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = stratTag,
                                style = MonoTextStyle.copy(fontSize = 9.sp, fontWeight = FontWeight.SemiBold, color = CyberCyan)
                            )
                        }
                    }
                }
            }

            Text(
                text = String.format(
                    Locale.US,
                    "%s • Confirmed in %.1fs • %s",
                    activeSignal.strategyName,
                    activeSignal.confirmationDurationMs / 1000.0,
                    activeSignal.reason
                ),
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )

            if (copiedBanner != null) {
                Text(
                    text = copiedBanner,
                    style = MonoTextStyle.copy(fontSize = 11.sp, color = BullGreen, fontWeight = FontWeight.Bold)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { onCopySignal(activeSignal) },
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("copy_signal_button"),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = CyberCyan, contentColor = ObsidianBg)
                ) {
                    Icon(Icons.Filled.ContentCopy, contentDescription = "Copy Signal", modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("COPY SIGNAL", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                }
                OutlinedButton(
                    onClick = { onDismissSignal(activeSignal) },
                    modifier = Modifier
                        .height(48.dp)
                        .testTag("close_signal_button"),
                    shape = RoundedCornerShape(10.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, BorderSlate),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TextSecondary)
                ) {
                    Icon(Icons.Filled.Close, contentDescription = "Close Signal", modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("CLOSE", style = MaterialTheme.typography.labelMedium)
                }
            }
        } else if (isConfirming && eval != null) {
            // Fast Confirmation In-Progress Card
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "Fast Confirmation (${state.market.confirmationTicks}/${state.market.requiredConfirmTicks} ticks) — Verifying clean-sheet momentum...",
                    style = MonoTextStyle.copy(fontSize = 11.sp, color = AmberWarn, fontWeight = FontWeight.SemiBold)
                )
                LinearProgressIndicator(
                    progress = { state.market.confirmationProgress.coerceIn(0.1f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(999.dp)),
                    color = AmberWarn,
                    trackColor = ObsidianBg
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Est. Entry: ${eval.entryPrice?.let { pair.formatPrice(it) } ?: "--"}",
                        style = MonoTextStyle.copy(fontSize = 11.sp, color = TextPrimary)
                    )
                    Text(
                        text = String.format(
                            Locale.US,
                            "TP1 +%.1fp | TP2 +%.1fp | TP3 +%.1fp",
                            eval.tp1Pips ?: 0.0,
                            eval.tp2Pips ?: 0.0,
                            eval.tp3Pips ?: 0.0
                        ),
                        style = MonoTextStyle.copy(fontSize = 11.sp, color = BullGreen)
                    )
                }
                Text(
                    text = eval.reason,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }
        } else {
            // Clean Scanning State
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = eval?.reason ?: "Scanning Gold & Bitcoin across M1, M5, M15, M30, H1, H4, D1 for zero-drawdown confluence...",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
                val ind = state.market.indicators
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    MetricPill("BIAS", ind.trendBias, if (ind.trendBias == "BULLISH") BullGreen else if (ind.trendBias == "BEARISH") BearRed else TextSecondary)
                    MetricPill("RSI", ind.rsi?.let { String.format(Locale.US, "%.1f", it) } ?: "--", CyberCyan)
                    MetricPill("ADX", ind.adx?.let { String.format(Locale.US, "%.1f", it) } ?: "--", AmberWarn)
                    MetricPill("ATR", ind.atrPips?.let { String.format(Locale.US, "%.1fp", it) } ?: "--", TextPrimary)
                }
                OutlinedButton(
                    onClick = onForceScanNow,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(10.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, CyberCyan.copy(alpha = 0.5f)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = CyberCyan)
                ) {
                    Icon(Icons.Filled.Bolt, contentDescription = "Instant Scan", modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("REFRESH LIVE CANDLES & SCAN NOW", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun TakeProfitTargetRow(
    label: String,
    price: String,
    targetPips: Double,
    remainingPips: Double,
    hit: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            if (hit) {
                Icon(Icons.Filled.CheckCircle, contentDescription = "Target Hit", tint = BullGreen, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(5.dp))
            }
            Text(
                text = label,
                style = MonoTextStyle.copy(fontSize = 11.sp, color = if (hit) BullGreen else TextSecondary)
            )
        }
        Text(
            text = price,
            style = MonoTextStyle.copy(fontSize = 12.sp, fontWeight = FontWeight.Bold, color = BullGreen),
            modifier = Modifier.weight(0.9f)
        )
        Text(
            text = if (hit) {
                String.format(Locale.US, "✓ +%.1fp HIT", targetPips)
            } else {
                String.format(Locale.US, "+%.1fp (%.1fp left)", targetPips, remainingPips)
            },
            style = MonoTextStyle.copy(
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (hit) BullGreen else CyberCyan
            )
        )
    }
}

@Composable
private fun MultiTimeframeSignalMatrixCard(
    rows: List<TimeframeSignalRow>,
    selectedPair: FxPair,
    selectedTf: Timeframe,
    onRowClick: (FxPair, Timeframe) -> Unit
) {
    var pairFilter by remember { mutableStateOf<FxPair?>(null) }
    val filteredRows = remember(rows, pairFilter) {
        if (pairFilter == null) rows else rows.filter { it.pair == pairFilter }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(ElevatedCard)
            .border(1.dp, BorderSlate, RoundedCornerShape(14.dp))
            .padding(12.dp)
            .testTag("mtf_signal_matrix"),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "ALL TIMEFRAMES (M1–D1)",
                style = MaterialTheme.typography.labelMedium,
                color = CyberCyan
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                MatrixFilterChip(label = "ALL", active = pairFilter == null) { pairFilter = null }
                MatrixFilterChip(label = "GOLD", active = pairFilter == FxPair.XAU_USD) { pairFilter = FxPair.XAU_USD }
                MatrixFilterChip(label = "BTC", active = pairFilter == FxPair.BTC_USD) { pairFilter = FxPair.BTC_USD }
            }
        }

        // Table Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(6.dp))
                .background(ObsidianBg)
                .padding(horizontal = 8.dp, vertical = 6.dp)
        ) {
            Text("ASSET / TF", style = MonoTextStyle.copy(fontSize = 9.sp, color = TextMuted), modifier = Modifier.weight(1.05f))
            Text("SIGNAL", style = MonoTextStyle.copy(fontSize = 9.sp, color = TextMuted), modifier = Modifier.weight(0.8f))
            Text("ENTRY", style = MonoTextStyle.copy(fontSize = 9.sp, color = TextMuted), modifier = Modifier.weight(0.9f))
            Text("TP1 / TP2 / TP3", style = MonoTextStyle.copy(fontSize = 9.sp, color = TextMuted), modifier = Modifier.weight(1.15f))
            Text("PIPS TO TP", style = MonoTextStyle.copy(fontSize = 9.sp, color = TextMuted), modifier = Modifier.weight(1.1f))
        }

        filteredRows.forEach { row ->
            val isSelected = row.pair == selectedPair && row.timeframe == selectedTf
            val signalText = when {
                row.stage == ConfirmationStage.CONFIRMED && row.side != null -> row.side.name
                row.stage == ConfirmationStage.CONFIRMING && row.side != null -> "${row.side.name}…"
                else -> "SCAN"
            }
            val signalColor = when {
                row.stage == ConfirmationStage.CONFIRMED && row.side == TradeSide.BUY -> BullGreen
                row.stage == ConfirmationStage.CONFIRMED && row.side == TradeSide.SELL -> BearRed
                row.stage == ConfirmationStage.CONFIRMING -> AmberWarn
                else -> TextMuted
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .minimumInteractiveComponentSize()
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (isSelected) CyberCyanMuted.copy(alpha = 0.45f) else CarbonSurface)
                    .border(
                        1.dp,
                        if (isSelected) CyberCyan else BorderSlate.copy(alpha = 0.7f),
                        RoundedCornerShape(8.dp)
                    )
                    .clickable { onRowClick(row.pair, row.timeframe) }
                    .padding(horizontal = 8.dp, vertical = 8.dp)
                    .testTag("matrix_row_${row.pair.name}_${row.timeframe.name}"),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${row.pair.shortTitle} ${row.timeframe.label}",
                    style = MonoTextStyle.copy(
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (row.pair == FxPair.XAU_USD) AmberWarn else CyberCyan
                    ),
                    modifier = Modifier.weight(1.05f)
                )
                Text(
                    text = signalText,
                    style = MonoTextStyle.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold, color = signalColor),
                    modifier = Modifier.weight(0.8f)
                )
                Text(
                    text = row.entryPrice?.let { row.pair.formatPrice(it) } ?: "--",
                    style = MonoTextStyle.copy(fontSize = 10.sp, color = TextPrimary),
                    modifier = Modifier.weight(0.9f)
                )
                Text(
                    text = if (row.tp1 != null && row.tp2 != null && row.tp3 != null) {
                        "${row.pair.formatPrice(row.tp1)}\n${row.pair.formatPrice(row.tp2)}\n${row.pair.formatPrice(row.tp3)}"
                    } else {
                        "--"
                    },
                    style = MonoTextStyle.copy(fontSize = 9.sp, color = BullGreen),
                    modifier = Modifier.weight(1.15f)
                )
                val activeSig = row.activeSignal
                val pipsText = when {
                    activeSig != null -> String.format(
                        Locale.US,
                        "+%.0fp (%.0fp)\n+%.0fp (%.0fp)\n+%.0fp (%.0fp)",
                        activeSig.tp1Pips,
                        activeSig.remainingPipsToTp1,
                        activeSig.tp2Pips,
                        activeSig.remainingPipsToTp2,
                        activeSig.tp3Pips,
                        activeSig.remainingPipsToTp3
                    )
                    row.tp1Pips != null && row.tp2Pips != null && row.tp3Pips != null -> String.format(
                        Locale.US,
                        "+%.0fp\n+%.0fp\n+%.0fp",
                        row.tp1Pips,
                        row.tp2Pips,
                        row.tp3Pips
                    )
                    else -> "--"
                }
                Text(
                    text = pipsText,
                    style = MonoTextStyle.copy(fontSize = 9.sp, fontWeight = FontWeight.SemiBold, color = CyberCyan),
                    modifier = Modifier.weight(1.1f)
                )
            }
        }
    }
}

@Composable
private fun MatrixFilterChip(
    label: String,
    active: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .minimumInteractiveComponentSize()
            .clip(RoundedCornerShape(6.dp))
            .background(if (active) CyberCyanMuted else CarbonSurface)
            .border(1.dp, if (active) CyberCyan else BorderSlate, RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MonoTextStyle.copy(
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                color = if (active) CyberCyan else TextMuted
            )
        )
    }
}

@Composable
private fun ChartHeaderControls(
    state: TradingUiState,
    onUpdateConfig: (BotConfig) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "${state.market.pair.displayName} • ${state.market.timeframe.label} LIVE CHART",
            style = MaterialTheme.typography.labelMedium,
            color = TextSecondary
        )
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OverlayChip(
                label = "EMA",
                active = state.botConfig.showEmaLines,
                onClick = { onUpdateConfig(state.botConfig.copy(showEmaLines = !state.botConfig.showEmaLines)) }
            )
            OverlayChip(
                label = "VWAP",
                active = state.botConfig.showVwapLine,
                onClick = { onUpdateConfig(state.botConfig.copy(showVwapLine = !state.botConfig.showVwapLine)) }
            )
            OverlayChip(
                label = "BB",
                active = state.botConfig.showBollingerBands,
                onClick = { onUpdateConfig(state.botConfig.copy(showBollingerBands = !state.botConfig.showBollingerBands)) }
            )
        }
    }
}

@Composable
private fun OverlayChip(label: String, active: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .minimumInteractiveComponentSize()
            .clip(RoundedCornerShape(6.dp))
            .background(if (active) CyberCyanMuted else CarbonSurface)
            .border(1.dp, if (active) CyberCyan else BorderSlate, RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp)
    ) {
        Text(
            text = label,
            style = MonoTextStyle.copy(
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = if (active) CyberCyan else TextMuted
            )
        )
    }
}

@Composable
private fun StrategyQuickSelectorCard(
    selectedStrategy: GoldBtcStrategyType,
    onSelectStrategy: (GoldBtcStrategyType) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(CarbonSurface)
            .border(1.dp, BorderSlate, RoundedCornerShape(14.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "GOLD & BITCOIN PRO STRATEGIES",
            style = MaterialTheme.typography.labelMedium,
            color = TextSecondary
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            GoldBtcStrategyType.entries.forEach { strat ->
                val selected = strat == selectedStrategy
                Box(
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (selected) CyberCyanMuted else ObsidianBg)
                        .border(1.dp, if (selected) CyberCyan else BorderSlate, RoundedCornerShape(8.dp))
                        .clickable { onSelectStrategy(strat) }
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                        .testTag("strategy_chip_${strat.name}"),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = strat.shortName,
                        style = MonoTextStyle.copy(
                            fontSize = 11.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            color = if (selected) CyberCyan else TextSecondary
                        )
                    )
                }
            }
        }
        Text(
            text = "${selectedStrategy.title}: ${selectedStrategy.description}",
            style = MaterialTheme.typography.bodySmall,
            color = TextMuted
        )
    }
}

@Composable
private fun SmallToBigFlipCard(
    state: TradingUiState,
    onOpenSettings: () -> Unit
) {
    val flip = state.flipPlan
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(ElevatedCard)
            .border(1.dp, AmberWarn.copy(alpha = 0.45f), RoundedCornerShape(14.dp))
            .padding(14.dp)
            .testTag("small_to_big_flip_card"),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "SMALL-TO-BIG ACCOUNT FLIP PLANNER",
                    style = MaterialTheme.typography.labelMedium,
                    color = AmberWarn
                )
                Text(
                    text = "${flip.formatMoney(flip.startingCapital)} → ${flip.formatMoney(flip.targetCapital)} (${String.format(Locale.US, "%.2fx", flip.multiplier)})",
                    style = MonoTextStyle.copy(fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                )
            }
            Box(
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .clip(RoundedCornerShape(6.dp))
                    .clickable(onClick = onOpenSettings)
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "EDIT",
                    style = MonoTextStyle.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold, color = CyberCyan)
                )
            }
        }

        LinearProgressIndicator(
            progress = { flip.progressFraction },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(999.dp)),
            color = BullGreen,
            trackColor = ObsidianBg
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            MetricPill("PROJECTED", flip.formatMoney(flip.projectedCapital), BullGreen)
            MetricPill("GOLD LOT", String.format(Locale.US, "%.2fL", flip.nextRecommendedLotGold), AmberWarn)
            MetricPill("BTC LOT", String.format(Locale.US, "%.2fL", flip.nextRecommendedLotBtc), CyberCyan)
            MetricPill("STEPS LEFT", "${flip.estimatedStepsRemaining}", TextPrimary)
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            MetricPill("WIN RATE", String.format(Locale.US, "%.1f%%", flip.cleanSheetWinRate), BullGreen)
            MetricPill("ZERO-DD WINS", "${flip.zeroDrawdownCount}", CyberCyan)
            MetricPill("NET PIPS", String.format(Locale.US, "%+.1fp", flip.netPipsCaptured), if (flip.netPipsCaptured >= 0) BullGreen else BearRed)
        }
    }
}

@Composable
private fun MetricPill(label: String, value: String, color: Color) {
    Column {
        Text(text = label, style = MonoTextStyle.copy(fontSize = 9.sp, color = TextMuted))
        Text(text = value, style = MonoTextStyle.copy(fontSize = 12.sp, fontWeight = FontWeight.Bold, color = color))
    }
}

@Composable
private fun SectionLabel(title: String, trailing: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = title, style = MaterialTheme.typography.labelMedium, color = TextSecondary)
        Text(text = trailing, style = MonoTextStyle.copy(fontSize = 11.sp, color = TextMuted))
    }
}

@Composable
private fun EmptySignalHistoryCard() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(CarbonSurface)
            .border(1.dp, BorderSlate, RoundedCornerShape(12.dp))
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Filled.Shield, contentDescription = "Clean Sheet Signals", tint = CyberCyan, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = "Confirmed M1–D1 Gold & Bitcoin signals appear here once TP1/TP2/TP3 targets complete. Zero paper trading.",
            style = MaterialTheme.typography.bodySmall,
            color = TextMuted
        )
    }
}

@Composable
private fun SignalHistoryItemCard(record: SignalHistoryRecord) {
    val isWin = record.capturedPips >= 0
    val fmt = SimpleDateFormat("MM/dd HH:mm", Locale.US)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(CarbonSurface)
            .border(1.dp, BorderSlate, RoundedCornerShape(12.dp))
            .padding(12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "${record.pair.shortTitle} ${record.timeframe.label} • ${record.side.name}",
                    style = MonoTextStyle.copy(
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (record.side == TradeSide.BUY) BullGreen else BearRed
                    )
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = record.outcome.label,
                    style = MonoTextStyle.copy(fontSize = 10.sp, color = CyberCyan)
                )
            }
            Text(
                text = "Entry ${record.pair.formatPrice(record.entryPrice)} → ${record.pair.formatPrice(record.exitPrice)} • Max DD ${String.format(Locale.US, "%.1fp", record.maxDrawdownPips)}",
                style = MonoTextStyle.copy(fontSize = 10.sp, color = TextSecondary)
            )
            Text(
                text = "${record.strategyName} • ${fmt.format(Date(record.closedAt))}",
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted
            )
        }
        Text(
            text = String.format(Locale.US, "%+.1fp", record.capturedPips),
            style = MonoTextStyle.copy(
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = if (isWin) BullGreen else BearRed
            )
        )
    }
}
