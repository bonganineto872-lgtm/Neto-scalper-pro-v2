package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.model.AccountSummary
import com.example.domain.model.ClosedTradeRecord
import com.example.domain.model.EquityPoint
import com.example.ui.theme.AmberWarn
import com.example.ui.theme.BearRed
import com.example.ui.theme.BorderSlate
import com.example.ui.theme.BullGreen
import com.example.ui.theme.BullGreenMuted
import com.example.ui.theme.CarbonSurface
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.CyberCyanMuted
import com.example.ui.theme.ElevatedCard
import com.example.ui.theme.GridLineColor
import com.example.ui.theme.MonoTextStyle
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Native Jetpack Compose Real-Time Balance & ZAR Flip (R200 -> R10,000) Visualization Dashboard.
 * Provides interactive area growth chart with touch tooltip inspection, milestone track, and per-trade P&L bar breakdown.
 */
@Composable
fun FlipGrowthDashboard(
    accountSummary: AccountSummary,
    liveEquityPoints: List<EquityPoint>,
    tradeHistory: List<ClosedTradeRecord>,
    targetBalanceZar: Double = 10_000.0,
    modifier: Modifier = Modifier
) {
    val currencySymbol = if (accountSummary.currency == "ZAR") "R" else "$"
    var fullTargetScale by remember { mutableStateOf(false) }
    var selectedPointIndex by remember { mutableStateOf<Int?>(null) }
    val textMeasurer = rememberTextMeasurer()

    val startBal = accountSummary.startingBalance.coerceAtLeast(1.0)
    val effectiveTarget = targetBalanceZar.coerceAtLeast(startBal + 100.0)
    val progressPct = (((accountSummary.equity - startBal) / (effectiveTarget - startBal)) * 100.0)
        .coerceIn(0.0, 100.0)
    val remainingZar = (effectiveTarget - accountSummary.equity).coerceAtLeast(0.0)

    val series = remember(liveEquityPoints, accountSummary.equity) {
        if (liveEquityPoints.isEmpty()) {
            val now = System.currentTimeMillis()
            listOf(
                EquityPoint(now - 5_000L, accountSummary.balance),
                EquityPoint(now, accountSummary.equity)
            )
        } else if (liveEquityPoints.size == 1) {
            val only = liveEquityPoints.first()
            listOf(
                only.copy(timestamp = only.timestamp - 2_000L),
                EquityPoint(System.currentTimeMillis(), accountSummary.equity)
            )
        } else {
            liveEquityPoints
        }
    }

    val safeSelectedIdx = selectedPointIndex?.coerceIn(0, series.lastIndex)
    val activePoint = safeSelectedIdx?.let { series[it] } ?: series.last()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(ElevatedCard)
            .border(1.dp, CyberCyan.copy(alpha = 0.45f), RoundedCornerShape(14.dp))
            .padding(14.dp)
            .testTag("flip_growth_dashboard"),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Header Row + Scale Switcher
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(BullGreen)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "REAL-TIME ZAR FLIP DASHBOARD",
                        style = MaterialTheme.typography.labelMedium,
                        color = CyberCyan
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = String.format(
                        Locale.US,
                        "%s%,.2f → %s%,.0f (%.1f%% • %.2fx)",
                        currencySymbol,
                        startBal,
                        currencySymbol,
                        effectiveTarget,
                        progressPct,
                        accountSummary.flipMultiplier
                    ),
                    style = MonoTextStyle.copy(fontSize = 11.sp, color = TextSecondary)
                )
            }

            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(CarbonSurface)
                    .border(1.dp, BorderSlate, RoundedCornerShape(8.dp))
                    .padding(2.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                ScaleToggleChip(
                    label = "AUTO",
                    selected = !fullTargetScale,
                    onClick = { fullTargetScale = false },
                    testTag = "chart_scale_auto"
                )
                ScaleToggleChip(
                    label = "R10K",
                    selected = fullTargetScale,
                    onClick = { fullTargetScale = true },
                    testTag = "chart_scale_full"
                )
            }
        }

        // Milestone Checkpoint Track (R200 -> R1k -> R2.5k -> R5k -> R10k)
        FlipMilestoneTrack(
            currentEquity = accountSummary.equity,
            startBalance = startBal,
            targetBalance = effectiveTarget,
            currencySymbol = currencySymbol
        )

        // Interactive Tooltip Readout Bar
        val timeFmt = remember { SimpleDateFormat("HH:mm:ss", Locale.US) }
        val netDelta = activePoint.equity - startBal
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(ObsidianBg)
                .border(1.dp, BorderSlate, RoundedCornerShape(8.dp))
                .padding(horizontal = 10.dp, vertical = 7.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = if (safeSelectedIdx != null) "INSPECT @ ${timeFmt.format(Date(activePoint.timestamp))}" else "LIVE EQUITY / SETTLED BAL",
                    style = MonoTextStyle.copy(fontSize = 9.sp, color = TextMuted)
                )
                Text(
                    text = String.format(
                        Locale.US,
                        "%s%,.2f  (Bal %s%,.2f)",
                        currencySymbol,
                        activePoint.equity,
                        currencySymbol,
                        accountSummary.balance
                    ),
                    style = MonoTextStyle.copy(
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (activePoint.equity >= startBal) BullGreen else BearRed
                    )
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = if (netDelta >= 0) "NET GROWTH" else "DRAWDOWN",
                    style = MonoTextStyle.copy(fontSize = 9.sp, color = TextMuted)
                )
                Text(
                    text = String.format(
                        Locale.US,
                        "%s%s%,.2f • %s%,.0f left",
                        if (netDelta >= 0) "+" else "-",
                        currencySymbol,
                        abs(netDelta),
                        currencySymbol,
                        remainingZar
                    ),
                    style = MonoTextStyle.copy(
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (netDelta >= 0) CyberCyan else BearRed
                    )
                )
            }
        }

        // Main Real-Time Area & Reference Line Canvas Chart
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(ObsidianBg.copy(alpha = 0.85f))
                .border(1.dp, BorderSlate, RoundedCornerShape(10.dp))
                .padding(top = 8.dp, bottom = 6.dp, start = 6.dp, end = 6.dp)
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(168.dp)
                    .pointerInput(series.size) {
                        detectTapGestures { offset ->
                            val axisW = 54.dp.toPx()
                            val plotW = (size.width - axisW).coerceAtLeast(1f)
                            val frac = (offset.x / plotW).coerceIn(0f, 1f)
                            val idx = (frac * (series.lastIndex)).roundToInt()
                            selectedPointIndex = if (selectedPointIndex == idx) null else idx
                        }
                    }
                    .pointerInput(series.size) {
                        detectDragGestures(
                            onDragEnd = { selectedPointIndex = null },
                            onDragCancel = { selectedPointIndex = null }
                        ) { change, _ ->
                            val axisW = 54.dp.toPx()
                            val plotW = (size.width - axisW).coerceAtLeast(1f)
                            val frac = (change.position.x / plotW).coerceIn(0f, 1f)
                            selectedPointIndex = (frac * (series.lastIndex)).roundToInt()
                        }
                    }
                    .testTag("flip_area_chart_canvas")
            ) {
                val axisWidth = 54.dp.toPx()
                val plotWidth = (size.width - axisWidth).coerceAtLeast(1f)
                val plotHeight = (size.height - 14.dp.toPx()).coerceAtLeast(1f)

                val rawMin = series.minOf { min(it.equity, accountSummary.balance) }
                val rawMax = series.maxOf { max(it.equity, accountSummary.balance) }

                val domainMin: Double
                val domainMax: Double
                if (fullTargetScale) {
                    domainMin = min(rawMin, startBal * 0.5)
                    domainMax = max(rawMax, effectiveTarget * 1.03)
                } else {
                    val span = (rawMax - rawMin).coerceAtLeast(25.0)
                    domainMin = min(rawMin, startBal) - span * 0.14
                    domainMax = max(rawMax, startBal) + span * 0.18
                }
                val domainSpan = (domainMax - domainMin).coerceAtLeast(1.0)

                fun valueToY(v: Double): Float {
                    val frac = ((v - domainMin) / domainSpan).toFloat().coerceIn(0f, 1f)
                    return plotHeight - (frac * plotHeight)
                }

                // Gridlines + Y-axis labels
                val gridCount = 4
                for (i in 0..gridCount) {
                    val f = i.toFloat() / gridCount
                    val y = plotHeight * f
                    val labelVal = domainMax - (domainSpan * f)
                    drawLine(
                        color = GridLineColor,
                        start = Offset(0f, y),
                        end = Offset(plotWidth, y),
                        strokeWidth = 1.dp.toPx()
                    )
                    val labelTxt = if (labelVal >= 1000) {
                        String.format(Locale.US, "%s%.1fk", currencySymbol, labelVal / 1000.0)
                    } else {
                        String.format(Locale.US, "%s%.0f", currencySymbol, labelVal)
                    }
                    val layout = textMeasurer.measure(
                        text = labelTxt,
                        style = MonoTextStyle.copy(fontSize = 9.sp, color = TextMuted)
                    )
                    drawText(
                        textLayoutResult = layout,
                        topLeft = Offset(plotWidth + 5.dp.toPx(), (y - 6.dp.toPx()).coerceIn(0f, plotHeight - 12.dp.toPx()))
                    )
                }

                val dash = PathEffect.dashPathEffect(floatArrayOf(8f, 6f), 0f)

                // Start Reference Line (R200)
                val yStart = valueToY(startBal)
                drawLine(
                    color = AmberWarn.copy(alpha = 0.55f),
                    start = Offset(0f, yStart),
                    end = Offset(plotWidth, yStart),
                    strokeWidth = 1.dp.toPx(),
                    pathEffect = dash
                )

                // Settled Balance Reference Line
                val yBal = valueToY(accountSummary.balance)
                drawLine(
                    color = CyberCyan.copy(alpha = 0.45f),
                    start = Offset(0f, yBal),
                    end = Offset(plotWidth, yBal),
                    strokeWidth = 1.1.dp.toPx(),
                    pathEffect = dash
                )

                // Target Reference Line (R10,000) when visible in current scale
                if (effectiveTarget <= domainMax) {
                    val yTarget = valueToY(effectiveTarget)
                    drawLine(
                        color = BullGreen.copy(alpha = 0.75f),
                        start = Offset(0f, yTarget),
                        end = Offset(plotWidth, yTarget),
                        strokeWidth = 1.3.dp.toPx(),
                        pathEffect = dash
                    )
                }

                // Equity Area & Line Path
                val stepX = plotWidth / (series.size - 1).coerceAtLeast(1)
                val isPositive = series.last().equity >= startBal
                val strokeColor = if (isPositive) BullGreen else BearRed

                val linePath = Path()
                val areaPath = Path()
                series.forEachIndexed { idx, pt ->
                    val x = idx * stepX
                    val y = valueToY(pt.equity)
                    if (idx == 0) {
                        linePath.moveTo(x, y)
                        areaPath.moveTo(x, plotHeight)
                        areaPath.lineTo(x, y)
                    } else {
                        linePath.lineTo(x, y)
                        areaPath.lineTo(x, y)
                    }
                    if (idx == series.lastIndex) {
                        areaPath.lineTo(x, plotHeight)
                        areaPath.close()
                    }
                }

                drawPath(
                    path = areaPath,
                    brush = Brush.verticalGradient(
                        colors = listOf(strokeColor.copy(alpha = 0.34f), Color.Transparent),
                        startY = 0f,
                        endY = plotHeight
                    )
                )
                drawPath(
                    path = linePath,
                    color = strokeColor,
                    style = Stroke(width = 2.2.dp.toPx(), cap = StrokeCap.Round)
                )

                // Live endpoint pulse dot
                val lastX = series.lastIndex * stepX
                val lastY = valueToY(series.last().equity)
                drawCircle(
                    color = strokeColor.copy(alpha = 0.25f),
                    radius = 7.dp.toPx(),
                    center = Offset(lastX, lastY)
                )
                drawCircle(
                    color = strokeColor,
                    radius = 3.5.dp.toPx(),
                    center = Offset(lastX, lastY)
                )

                // Active Tooltip Crosshair
                safeSelectedIdx?.let { idx ->
                    val cx = idx * stepX
                    val cy = valueToY(series[idx].equity)
                    drawLine(
                        color = TextPrimary.copy(alpha = 0.5f),
                        start = Offset(cx, 0f),
                        end = Offset(cx, plotHeight),
                        strokeWidth = 1.dp.toPx()
                    )
                    drawCircle(
                        color = CyberCyan,
                        radius = 4.5.dp.toPx(),
                        center = Offset(cx, cy)
                    )
                }
            }
        }

        // Recent Per-Trade P&L Bar Chart
        if (tradeHistory.isNotEmpty()) {
            RecentTradesPnlBarChart(
                trades = tradeHistory.take(12).reversed(),
                currencySymbol = currencySymbol
            )
        }
    }
}

@Composable
private fun FlipMilestoneTrack(
    currentEquity: Double,
    startBalance: Double,
    targetBalance: Double,
    currencySymbol: String
) {
    val milestones = remember(startBalance, targetBalance) {
        if (targetBalance >= 9_000.0) {
            listOf(startBalance, 1_000.0, 2_500.0, 5_000.0, targetBalance)
        } else {
            listOf(
                startBalance,
                startBalance + (targetBalance - startBalance) * 0.25,
                startBalance + (targetBalance - startBalance) * 0.50,
                startBalance + (targetBalance - startBalance) * 0.75,
                targetBalance
            )
        }
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        milestones.forEach { goal ->
            val reached = currentEquity >= goal
            val label = if (goal >= 1000.0) {
                String.format(Locale.US, "%s%.1fk", currencySymbol, goal / 1000.0).replace(".0k", "k")
            } else {
                String.format(Locale.US, "%s%.0f", currencySymbol, goal)
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (reached) BullGreenMuted else CarbonSurface)
                    .border(
                        1.dp,
                        if (reached) BullGreen.copy(alpha = 0.7f) else BorderSlate,
                        RoundedCornerShape(6.dp)
                    )
                    .padding(vertical = 5.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (reached) "✓ $label" else label,
                    style = MonoTextStyle.copy(
                        fontSize = 10.sp,
                        fontWeight = if (reached) FontWeight.Bold else FontWeight.Normal,
                        color = if (reached) BullGreen else TextMuted
                    )
                )
            }
        }
    }
}

@Composable
private fun RecentTradesPnlBarChart(
    trades: List<ClosedTradeRecord>,
    currencySymbol: String
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(ObsidianBg.copy(alpha = 0.75f))
            .border(1.dp, BorderSlate, RoundedCornerShape(10.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        val lastTrade = trades.lastOrNull()
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "RECENT SCALP P&L BARS (${trades.size})",
                style = MonoTextStyle.copy(fontSize = 10.sp, color = TextSecondary)
            )
            if (lastTrade != null) {
                val sign = if (lastTrade.realizedPnlZar >= 0) "+" else "-"
                Text(
                    text = String.format(
                        Locale.US,
                        "Last: %s%s%.2f",
                        sign,
                        currencySymbol,
                        abs(lastTrade.realizedPnlZar)
                    ),
                    style = MonoTextStyle.copy(
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (lastTrade.realizedPnlZar >= 0) BullGreen else BearRed
                    )
                )
            }
        }

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
        ) {
            val zeroY = size.height / 2f
            drawLine(
                color = BorderSlate,
                start = Offset(0f, zeroY),
                end = Offset(size.width, zeroY),
                strokeWidth = 1.dp.toPx()
            )
            val maxAbs = trades.maxOf { abs(it.realizedPnlZar) }.coerceAtLeast(1.0)
            val slotW = size.width / trades.size.coerceAtLeast(1)
            val barW = (slotW * 0.62f).coerceIn(6.dp.toPx(), 24.dp.toPx())

            trades.forEachIndexed { idx, trade ->
                val ratio = (abs(trade.realizedPnlZar) / maxAbs).toFloat().coerceIn(0.08f, 1f)
                val barH = ratio * (zeroY - 3.dp.toPx())
                val left = idx * slotW + (slotW - barW) / 2f
                val isWin = trade.realizedPnlZar >= 0
                val top = if (isWin) zeroY - barH else zeroY
                drawRoundRect(
                    color = if (isWin) BullGreen else BearRed,
                    topLeft = Offset(left, top),
                    size = Size(barW, barH),
                    cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx())
                )
            }
        }
    }
}

@Composable
private fun ScaleToggleChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    testTag: String
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(if (selected) CyberCyanMuted else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 9.dp, vertical = 4.dp)
            .testTag(testTag)
    ) {
        Text(
            text = label,
            style = MonoTextStyle.copy(
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = if (selected) CyberCyan else TextMuted
            )
        )
    }
}
