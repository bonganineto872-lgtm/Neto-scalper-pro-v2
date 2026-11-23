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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.model.AccountSummary
import com.example.domain.model.ClosedTradeRecord
import com.example.domain.model.TradePosition
import com.example.domain.model.TradeSide
import com.example.ui.components.DashboardAlertsPanel
import com.example.ui.components.FlipGrowthDashboard
import com.example.ui.theme.BearRed
import com.example.ui.theme.BearRedBorder
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

@Composable
fun PositionsScreen(
    state: TradingUiState,
    onClosePosition: (String) -> Unit,
    onFlattenAll: () -> Unit,
    onClearAlerts: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val acct = state.accountSummary

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(top = 12.dp, bottom = 28.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Positions & Flip Ledger",
                    style = MaterialTheme.typography.headlineSmall,
                    color = TextPrimary
                )
                if (state.openPositions.isNotEmpty()) {
                    OutlinedButton(
                        onClick = onFlattenAll,
                        modifier = Modifier.testTag("positions_flatten_all_button"),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = BearRed),
                        border = androidx.compose.foundation.BorderStroke(1.dp, BearRedBorder)
                    ) {
                        Text("Close All (${state.openPositions.size})", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }

        // KPI Metric Grid
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    KpiStatCard(
                        label = "ACCOUNT EQUITY (${acct.currency})",
                        value = acct.formatMoney(acct.equity),
                        subValue = "Bal ${acct.formatMoney(acct.balance)} • ${String.format(Locale.US, "%.1f%%", acct.flipProgressFraction * 100f)} of R10k",
                        accent = TextPrimary,
                        modifier = Modifier.weight(1f)
                    )
                    val upl = acct.unrealizedPnl
                    KpiStatCard(
                        label = "UNREALIZED P&L",
                        value = acct.formatMoney(upl, signed = true),
                        subValue = "${state.openPositions.size}/${state.botConfig.maxOpenTrades} layers open",
                        accent = if (upl >= 0) BullGreen else BearRed,
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val realized = state.totalRealizedPnl
                    KpiStatCard(
                        label = "REALIZED P&L",
                        value = acct.formatMoney(realized, signed = true),
                        subValue = String.format(Locale.US, "%+.1f net pips", state.totalPips),
                        accent = if (realized >= 0) BullGreen else BearRed,
                        modifier = Modifier.weight(1f)
                    )
                    KpiStatCard(
                        label = "WIN RATE",
                        value = String.format(Locale.US, "%.1f%%", state.winRatePercent),
                        subValue = "${state.tradeHistory.size} closed trades",
                        accent = CyberCyan,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        item {
            FlipGrowthDashboard(
                accountSummary = acct,
                liveEquityPoints = state.liveEquityTicks,
                tradeHistory = state.tradeHistory,
                targetBalanceZar = state.botConfig.targetProfitStopZar
            )
        }

        item {
            DashboardAlertsPanel(
                alerts = state.alertHistory,
                onClearAlerts = onClearAlerts
            )
        }

        // Segmented Toggle: Open vs History
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(CarbonSurface)
                    .border(1.dp, BorderSlate, RoundedCornerShape(10.dp))
                    .padding(4.dp)
            ) {
                SegmentTab(
                    label = "OPEN (${state.openPositions.size})",
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    testTag = "tab_open_positions",
                    modifier = Modifier.weight(1f)
                )
                SegmentTab(
                    label = "HISTORY (${state.tradeHistory.size})",
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    testTag = "tab_trade_history",
                    modifier = Modifier.weight(1f)
                )
            }
        }

        if (selectedTab == 0) {
            if (state.openPositions.isEmpty()) {
                item {
                    EmptyLedgerState(
                        title = "No Active Scalps",
                        subtitle = "Engage the 1m bot on the Terminal tab to open and scale up to ${state.botConfig.maxOpenTrades} positions."
                    )
                }
            } else {
                items(state.openPositions, key = { it.id }) { pos ->
                    OpenPositionCard(
                        position = pos,
                        accountSummary = acct,
                        onClose = { onClosePosition(pos.id) }
                    )
                }
            }
        } else {
            if (state.tradeHistory.isEmpty()) {
                item {
                    EmptyLedgerState(
                        title = "No Closed Trades Yet",
                        subtitle = "Completed scalps hitting Take Profit, Stop Loss, or manual close are logged permanently here."
                    )
                }
            } else {
                items(state.tradeHistory, key = { "${it.id}_${it.closedAt}" }) { record ->
                    ClosedTradeCard(record = record)
                }
            }
        }
    }
}

@Composable
private fun KpiStatCard(
    label: String,
    value: String,
    subValue: String,
    accent: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(ElevatedCard)
            .border(1.dp, BorderSlate, RoundedCornerShape(12.dp))
            .padding(12.dp)
    ) {
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = TextMuted)
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = value,
            style = MonoTextStyle.copy(fontSize = 18.sp, fontWeight = FontWeight.Bold, color = accent)
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = subValue,
            style = MonoTextStyle.copy(fontSize = 10.sp, color = TextSecondary)
        )
    }
}

@Composable
private fun SegmentTab(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    testTag: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) ElevatedCard else CarbonSurface)
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp)
            .testTag(testTag),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MonoTextStyle.copy(
                fontSize = 12.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = if (selected) CyberCyan else TextSecondary
            )
        )
    }
}

@Composable
private fun OpenPositionCard(
    position: TradePosition,
    accountSummary: AccountSummary,
    onClose: () -> Unit
) {
    val pnl = position.unrealizedPnlAccount
    val isProfit = pnl >= 0
    val sideColor = if (position.side == TradeSide.BUY) BullGreen else BearRed
    val sideBg = if (position.side == TradeSide.BUY) BullGreenMuted else BearRedMuted

    val sl = position.effectiveStopLoss
    val tp = position.takeProfit
    val totalSpan = abs(tp - sl).coerceAtLeast(position.pair.pipSize)
    val distFromSl = if (position.side == TradeSide.BUY) position.currentPrice - sl else sl - position.currentPrice
    val progress = (distFromSl / totalSpan).toFloat().coerceIn(0f, 1f)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(ElevatedCard)
            .border(1.dp, BorderSlate, RoundedCornerShape(12.dp))
            .padding(14.dp)
            .testTag("open_position_card_${position.id}")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(sideBg)
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = position.side.name,
                        style = MonoTextStyle.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold, color = sideColor)
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(CyberCyanMuted)
                        .padding(horizontal = 6.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = "L${position.pyramidLayer}",
                        style = MonoTextStyle.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold, color = CyberCyan)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = position.pair.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = String.format(Locale.US, "%.2fL", position.lotSize),
                    style = MonoTextStyle.copy(fontSize = 11.sp, color = TextSecondary)
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = accountSummary.formatMoney(pnl, signed = true),
                    style = MonoTextStyle.copy(
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isProfit) BullGreen else BearRed
                    )
                )
                Text(
                    text = String.format(Locale.US, "%+.1f pips", position.pipsPnl),
                    style = MonoTextStyle.copy(
                        fontSize = 11.sp,
                        color = if (isProfit) BullGreen else BearRed
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            PriceMiniColumn("ENTRY", position.pair.formatPrice(position.entryPrice), TextPrimary)
            PriceMiniColumn("MARK", position.pair.formatPrice(position.currentPrice), CyberCyan)
            PriceMiniColumn(
                if (position.trailingStopPrice != null) "TRAIL SL" else "SL",
                position.pair.formatPrice(position.effectiveStopLoss),
                BearRed
            )
            PriceMiniColumn("TP", position.pair.formatPrice(position.takeProfit), BullGreen)
        }

        Spacer(modifier = Modifier.height(8.dp))

        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .fillMaxWidth()
                .height(5.dp)
                .clip(RoundedCornerShape(999.dp)),
            color = if (isProfit) BullGreen else BearRed,
            trackColor = ObsidianBg
        )

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = position.strategyName,
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted
            )
            OutlinedButton(
                onClick = onClose,
                modifier = Modifier.testTag("close_position_${position.id}"),
                border = androidx.compose.foundation.BorderStroke(1.dp, BorderSlate),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary)
            ) {
                Icon(Icons.Filled.Close, contentDescription = "Close trade", modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Close", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
private fun PriceMiniColumn(label: String, value: String, color: androidx.compose.ui.graphics.Color) {
    Column {
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = TextMuted)
        Text(text = value, style = MonoTextStyle.copy(fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = color))
    }
}

@Composable
private fun ClosedTradeCard(record: ClosedTradeRecord) {
    val isProfit = record.realizedPnlZar >= 0
    val fmt = SimpleDateFormat("MM/dd HH:mm:ss", Locale.US)
    val sign = if (record.realizedPnlZar >= 0) "+" else "-"

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
                    text = "${record.side.name} ${record.pair.displayName}",
                    style = MonoTextStyle.copy(
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (record.side == TradeSide.BUY) BullGreen else BearRed
                    )
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = record.closeReason,
                    style = MonoTextStyle.copy(fontSize = 10.sp, color = CyberCyan)
                )
            }
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = "${record.pair.formatPrice(record.entryPrice)} → ${record.pair.formatPrice(record.exitPrice)} • ${String.format(Locale.US, "%.2fL", record.lotSize)}",
                style = MonoTextStyle.copy(fontSize = 11.sp, color = TextSecondary)
            )
            Text(
                text = "${record.strategyName} • ${fmt.format(Date(record.closedAt))}",
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted
            )
        }

        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = String.format(Locale.US, "%sR%,.2f", sign, abs(record.realizedPnlZar)),
                style = MonoTextStyle.copy(
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isProfit) BullGreen else BearRed
                )
            )
            Text(
                text = String.format(Locale.US, "%+.1fp", record.pipsPnl),
                style = MonoTextStyle.copy(
                    fontSize = 11.sp,
                    color = if (isProfit) BullGreen else BearRed
                )
            )
        }
    }
}

@Composable
private fun EmptyLedgerState(title: String, subtitle: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(CarbonSurface)
            .border(1.dp, BorderSlate, RoundedCornerShape(14.dp))
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Filled.ShowChart,
            contentDescription = title,
            tint = TextMuted,
            modifier = Modifier.size(32.dp)
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = title, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
        Spacer(modifier = Modifier.height(4.dp))
        Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
    }
}
