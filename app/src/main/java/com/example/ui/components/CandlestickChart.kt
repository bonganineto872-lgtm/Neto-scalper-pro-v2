package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.engine.TechnicalIndicators
import com.example.domain.model.BotConfig
import com.example.domain.model.Candle
import com.example.domain.model.EquityPoint
import com.example.domain.model.FxPair
import com.example.domain.model.TradePosition
import com.example.ui.theme.AmberWarn
import com.example.ui.theme.BearRed
import com.example.ui.theme.BorderSlate
import com.example.ui.theme.BullGreen
import com.example.ui.theme.CarbonSurface
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.ElevatedCard
import com.example.ui.theme.GridLineColor
import com.example.ui.theme.MonoTextStyle
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.VioletIndicator
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

@Composable
fun CandlestickTerminalChart(
    pair: FxPair,
    candles: List<Candle>,
    config: BotConfig,
    openPositions: List<TradePosition>,
    modifier: Modifier = Modifier
) {
    val visibleCandles = remember(candles) { candles.takeLast(46) }
    val closes = remember(candles) { candles.map { it.close } }
    val fastEmaAll = remember(candles, config.fastEmaPeriod) {
        TechnicalIndicators.calculateEmaSeries(closes, config.fastEmaPeriod)
    }
    val slowEmaAll = remember(candles, config.slowEmaPeriod) {
        TechnicalIndicators.calculateEmaSeries(closes, config.slowEmaPeriod)
    }
    val bbAll = remember(candles) {
        TechnicalIndicators.calculateBollingerBands(closes, 20, 2.0)
    }
    val rsiAll = remember(candles, config.rsiPeriod) {
        TechnicalIndicators.calculateRsiSeries(closes, config.rsiPeriod)
    }

    val offsetIndex = (candles.size - visibleCandles.size).coerceAtLeast(0)
    val visibleFastEma = remember(fastEmaAll, offsetIndex) { fastEmaAll.drop(offsetIndex) }
    val visibleSlowEma = remember(slowEmaAll, offsetIndex) { slowEmaAll.drop(offsetIndex) }
    val visibleBb = remember(bbAll, offsetIndex) { bbAll.drop(offsetIndex) }
    val visibleRsi = remember(rsiAll, offsetIndex) { rsiAll.drop(offsetIndex) }

    var selectedBarIndex by remember { mutableStateOf<Int?>(null) }
    val textMeasurer = rememberTextMeasurer()

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(CarbonSurface)
            .border(1.dp, BorderSlate, RoundedCornerShape(14.dp))
            .padding(10.dp)
    ) {
        // Legend + Crosshair OHLC HUD
        val inspectIdx = selectedBarIndex?.coerceIn(0, (visibleCandles.size - 1).coerceAtLeast(0))
        val inspectCandle = inspectIdx?.let { visibleCandles.getOrNull(it) } ?: visibleCandles.lastOrNull()
        val inspectRsi = inspectIdx?.let { visibleRsi.getOrNull(it) } ?: visibleRsi.lastOrNull()
        val inspectFast = inspectIdx?.let { visibleFastEma.getOrNull(it) } ?: visibleFastEma.lastOrNull()
        val inspectSlow = inspectIdx?.let { visibleSlowEma.getOrNull(it) } ?: visibleSlowEma.lastOrNull()

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (inspectCandle != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OhlcTag("O", pair.formatPrice(inspectCandle.open), TextSecondary)
                    OhlcTag("H", pair.formatPrice(inspectCandle.high), BullGreen)
                    OhlcTag("L", pair.formatPrice(inspectCandle.low), BearRed)
                    OhlcTag("C", pair.formatPrice(inspectCandle.close), if (inspectCandle.isBullish) BullGreen else BearRed)
                }
            }
            if (inspectRsi != null) {
                Text(
                    text = String.format(Locale.US, "RSI(%d) %.1f", config.rsiPeriod, inspectRsi),
                    style = MonoTextStyle.copy(
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = when {
                            inspectRsi >= config.rsiOverbought -> BearRed
                            inspectRsi <= config.rsiOversold -> BullGreen
                            else -> VioletIndicator
                        }
                    )
                )
            }
        }

        if (config.showEmaLines || config.showBollingerBands) {
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (config.showEmaLines) {
                    Text(
                        text = "EMA${config.fastEmaPeriod}: ${inspectFast?.let { pair.formatPrice(it) } ?: "--"}",
                        style = MonoTextStyle.copy(fontSize = 10.sp, color = CyberCyan)
                    )
                    Text(
                        text = "EMA${config.slowEmaPeriod}: ${inspectSlow?.let { pair.formatPrice(it) } ?: "--"}",
                        style = MonoTextStyle.copy(fontSize = 10.sp, color = AmberWarn)
                    )
                }
                if (config.showBollingerBands) {
                    Text(
                        text = "BB(20,2σ)",
                        style = MonoTextStyle.copy(fontSize = 10.sp, color = TextMuted)
                    )
                }
                if (selectedBarIndex != null) {
                    val fmt = SimpleDateFormat("HH:mm:ss", Locale.US)
                    Text(
                        text = inspectCandle?.let { fmt.format(Date(it.timestamp)) } ?: "",
                        style = MonoTextStyle.copy(fontSize = 10.sp, color = TextPrimary)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        if (visibleCandles.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Streaming FX candlesticks...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextMuted
                )
            }
        } else {
            val pairPositions = remember(openPositions, pair) {
                openPositions.filter { it.pair == pair }
            }

            // Main Candlestick + Overlay Canvas
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(205.dp)
                    .pointerInput(visibleCandles.size) {
                        detectTapGestures(
                            onTap = { offset ->
                                val chartWidth = (size.width - 76.dp.toPx()).coerceAtLeast(1f)
                                val idx = ((offset.x / chartWidth) * visibleCandles.size)
                                    .roundToInt()
                                    .coerceIn(0, visibleCandles.lastIndex)
                                selectedBarIndex = if (selectedBarIndex == idx) null else idx
                            }
                        )
                    }
                    .pointerInput(visibleCandles.size) {
                        detectDragGestures(
                            onDragStart = { offset ->
                                val chartWidth = (size.width - 76.dp.toPx()).coerceAtLeast(1f)
                                selectedBarIndex = ((offset.x / chartWidth) * visibleCandles.size)
                                    .roundToInt()
                                    .coerceIn(0, visibleCandles.lastIndex)
                            },
                            onDrag = { change, _ ->
                                val chartWidth = (size.width - 76.dp.toPx()).coerceAtLeast(1f)
                                selectedBarIndex = ((change.position.x / chartWidth) * visibleCandles.size)
                                    .roundToInt()
                                    .coerceIn(0, visibleCandles.lastIndex)
                            },
                            onDragEnd = { selectedBarIndex = null },
                            onDragCancel = { selectedBarIndex = null }
                        )
                    }
            ) {
                val axisWidth = 68.dp.toPx()
                val plotWidth = (size.width - axisWidth).coerceAtLeast(1f)
                val plotHeight = size.height

                var minPrice = visibleCandles.minOf { it.low }
                var maxPrice = visibleCandles.maxOf { it.high }

                if (config.showBollingerBands) {
                    visibleBb.filterNotNull().forEach {
                        minPrice = min(minPrice, it.lower)
                        maxPrice = max(maxPrice, it.upper)
                    }
                }
                pairPositions.forEach { pos ->
                    minPrice = min(minPrice, min(pos.effectiveStopLoss, pos.takeProfit))
                    maxPrice = max(maxPrice, max(pos.effectiveStopLoss, pos.takeProfit))
                }

                val rawRange = (maxPrice - minPrice).coerceAtLeast(pair.pipSize * 4.0)
                val paddedMin = minPrice - rawRange * 0.08
                val paddedMax = maxPrice + rawRange * 0.08
                val priceRange = (paddedMax - paddedMin).coerceAtLeast(1e-6)

                fun priceToY(p: Double): Float {
                    val ratio = ((p - paddedMin) / priceRange).toFloat().coerceIn(0f, 1f)
                    return plotHeight - (ratio * plotHeight)
                }

                // Horizontal Gridlines + Price Labels
                val gridSteps = 4
                for (g in 0..gridSteps) {
                    val fraction = g.toFloat() / gridSteps
                    val y = plotHeight * fraction
                    val gridPrice = paddedMax - (priceRange * fraction)
                    drawLine(
                        color = GridLineColor,
                        start = Offset(0f, y),
                        end = Offset(plotWidth, y),
                        strokeWidth = 1.dp.toPx()
                    )
                    drawAxisLabel(
                        textMeasurer = textMeasurer,
                        text = pair.formatPrice(gridPrice),
                        x = plotWidth + 4.dp.toPx(),
                        y = (y - 7.dp.toPx()).coerceIn(0f, plotHeight - 14.dp.toPx()),
                        color = TextMuted
                    )
                }

                val stepX = plotWidth / visibleCandles.size.coerceAtLeast(1)
                val candleBodyWidth = (stepX * 0.62f).coerceIn(3.dp.toPx(), 12.dp.toPx())

                // Bollinger Band shaded channel
                if (config.showBollingerBands) {
                    val upperPath = Path()
                    val lowerPath = Path()
                    val fillPath = Path()
                    val upperPts = mutableListOf<Offset>()
                    val lowerPts = mutableListOf<Offset>()

                    visibleBb.forEachIndexed { idx, bb ->
                        if (bb != null) {
                            val x = idx * stepX + stepX / 2f
                            upperPts.add(Offset(x, priceToY(bb.upper)))
                            lowerPts.add(Offset(x, priceToY(bb.lower)))
                        }
                    }

                    if (upperPts.size >= 2) {
                        upperPath.moveTo(upperPts.first().x, upperPts.first().y)
                        lowerPath.moveTo(lowerPts.first().x, lowerPts.first().y)
                        fillPath.moveTo(upperPts.first().x, upperPts.first().y)

                        for (i in 1 until upperPts.size) {
                            upperPath.lineTo(upperPts[i].x, upperPts[i].y)
                            lowerPath.lineTo(lowerPts[i].x, lowerPts[i].y)
                            fillPath.lineTo(upperPts[i].x, upperPts[i].y)
                        }
                        for (i in lowerPts.lastIndex downTo 0) {
                            fillPath.lineTo(lowerPts[i].x, lowerPts[i].y)
                        }
                        fillPath.close()

                        drawPath(
                            path = fillPath,
                            color = CyberCyan.copy(alpha = 0.06f)
                        )
                        drawPath(
                            path = upperPath,
                            color = CyberCyan.copy(alpha = 0.30f),
                            style = Stroke(width = 1.dp.toPx())
                        )
                        drawPath(
                            path = lowerPath,
                            color = CyberCyan.copy(alpha = 0.30f),
                            style = Stroke(width = 1.dp.toPx())
                        )
                    }
                }

                // Candlesticks
                visibleCandles.forEachIndexed { idx, candle ->
                    val centerX = idx * stepX + stepX / 2f
                    val color = if (candle.isBullish) BullGreen else BearRed
                    val yHigh = priceToY(candle.high)
                    val yLow = priceToY(candle.low)
                    val yOpen = priceToY(candle.open)
                    val yClose = priceToY(candle.close)

                    // Wick
                    drawLine(
                        color = color.copy(alpha = 0.85f),
                        start = Offset(centerX, yHigh),
                        end = Offset(centerX, yLow),
                        strokeWidth = 1.3.dp.toPx(),
                        cap = StrokeCap.Round
                    )

                    // Body
                    val top = min(yOpen, yClose)
                    val height = max(abs(yClose - yOpen), 2.dp.toPx())
                    drawRect(
                        color = color,
                        topLeft = Offset(centerX - candleBodyWidth / 2f, top),
                        size = Size(candleBodyWidth, height)
                    )
                }

                // EMA Overlays
                if (config.showEmaLines) {
                    drawLineSeries(visibleFastEma, stepX, ::priceToY, CyberCyan, 1.8.dp.toPx())
                    drawLineSeries(visibleSlowEma, stepX, ::priceToY, AmberWarn, 1.8.dp.toPx())
                }

                // Active Position Levels (Entry, TP, SL)
                val dashEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f), 0f)
                pairPositions.take(2).forEach { pos ->
                    val yEntry = priceToY(pos.entryPrice)
                    val yTp = priceToY(pos.takeProfit)
                    val ySl = priceToY(pos.effectiveStopLoss)

                    drawLine(
                        color = CyberCyan.copy(alpha = 0.85f),
                        start = Offset(0f, yEntry),
                        end = Offset(plotWidth, yEntry),
                        strokeWidth = 1.2.dp.toPx(),
                        pathEffect = dashEffect
                    )
                    drawLine(
                        color = BullGreen.copy(alpha = 0.85f),
                        start = Offset(0f, yTp),
                        end = Offset(plotWidth, yTp),
                        strokeWidth = 1.2.dp.toPx(),
                        pathEffect = dashEffect
                    )
                    drawLine(
                        color = BearRed.copy(alpha = 0.85f),
                        start = Offset(0f, ySl),
                        end = Offset(plotWidth, ySl),
                        strokeWidth = 1.2.dp.toPx(),
                        pathEffect = dashEffect
                    )
                }

                // Live Current Price Line + Pill
                val lastClose = visibleCandles.last().close
                val liveY = priceToY(lastClose)
                val liveColor = if (visibleCandles.last().isBullish) BullGreen else BearRed
                drawLine(
                    color = liveColor.copy(alpha = 0.65f),
                    start = Offset(0f, liveY),
                    end = Offset(plotWidth, liveY),
                    strokeWidth = 1.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(5f, 5f), 0f)
                )
                val tagTop = (liveY - 9.dp.toPx()).coerceIn(0f, plotHeight - 18.dp.toPx())
                drawRoundRect(
                    color = liveColor,
                    topLeft = Offset(plotWidth + 2.dp.toPx(), tagTop),
                    size = Size(axisWidth - 2.dp.toPx(), 18.dp.toPx()),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx(), 4.dp.toPx())
                )
                drawAxisLabel(
                    textMeasurer = textMeasurer,
                    text = pair.formatPrice(lastClose),
                    x = plotWidth + 5.dp.toPx(),
                    y = tagTop + 2.dp.toPx(),
                    color = ObsidianBg,
                    bold = true
                )

                // Touch Crosshair
                selectedBarIndex?.let { idx ->
                    val cx = idx * stepX + stepX / 2f
                    val cy = priceToY(visibleCandles[idx].close)
                    drawLine(
                        color = TextPrimary.copy(alpha = 0.45f),
                        start = Offset(cx, 0f),
                        end = Offset(cx, plotHeight),
                        strokeWidth = 1.dp.toPx()
                    )
                    drawCircle(
                        color = TextPrimary,
                        radius = 4.dp.toPx(),
                        center = Offset(cx, cy)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Synchronized RSI Oscillator Sub-Pane
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(ObsidianBg.copy(alpha = 0.65f))
            ) {
                val axisWidth = 68.dp.toPx()
                val plotWidth = (size.width - axisWidth).coerceAtLeast(1f)
                val h = size.height

                fun rsiToY(rsi: Double): Float {
                    val clamped = rsi.coerceIn(0.0, 100.0).toFloat()
                    return h - (clamped / 100f) * h
                }

                val y70 = rsiToY(config.rsiOverbought.toDouble())
                val y30 = rsiToY(config.rsiOversold.toDouble())

                // Overbought / Oversold bands
                drawRect(
                    color = BearRed.copy(alpha = 0.08f),
                    topLeft = Offset(0f, 0f),
                    size = Size(plotWidth, y70)
                )
                drawRect(
                    color = BullGreen.copy(alpha = 0.08f),
                    topLeft = Offset(0f, y30),
                    size = Size(plotWidth, h - y30)
                )

                val dash = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f)
                drawLine(BearRed.copy(alpha = 0.45f), Offset(0f, y70), Offset(plotWidth, y70), 1.dp.toPx(), pathEffect = dash)
                drawLine(BullGreen.copy(alpha = 0.45f), Offset(0f, y30), Offset(plotWidth, y30), 1.dp.toPx(), pathEffect = dash)

                drawAxisLabel(textMeasurer, "${config.rsiOverbought}", plotWidth + 6.dp.toPx(), (y70 - 6.dp.toPx()).coerceAtLeast(0f), BearRed.copy(alpha = 0.8f))
                drawAxisLabel(textMeasurer, "${config.rsiOversold}", plotWidth + 6.dp.toPx(), (y30 - 6.dp.toPx()).coerceAtMost(h - 12.dp.toPx()), BullGreen.copy(alpha = 0.8f))

                val stepX = plotWidth / visibleCandles.size.coerceAtLeast(1)
                drawLineSeries(visibleRsi, stepX, ::rsiToY, VioletIndicator, 1.6.dp.toPx())
            }
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawLineSeries(
    values: List<Double?>,
    stepX: Float,
    valueToY: (Double) -> Float,
    color: Color,
    strokeWidth: Float
) {
    val path = Path()
    var started = false
    values.forEachIndexed { idx, v ->
        if (v != null) {
            val x = idx * stepX + stepX / 2f
            val y = valueToY(v)
            if (!started) {
                path.moveTo(x, y)
                started = true
            } else {
                path.lineTo(x, y)
            }
        }
    }
    if (started) {
        drawPath(
            path = path,
            color = color,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
        )
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawAxisLabel(
    textMeasurer: TextMeasurer,
    text: String,
    x: Float,
    y: Float,
    color: Color,
    bold: Boolean = false
) {
    val result = textMeasurer.measure(
        text = text,
        style = MonoTextStyle.copy(
            fontSize = 9.sp,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
            color = color
        )
    )
    drawText(
        textLayoutResult = result,
        topLeft = Offset(x, y)
    )
}

@Composable
private fun OhlcTag(label: String, value: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = "$label ",
            style = MonoTextStyle.copy(fontSize = 10.sp, color = TextMuted)
        )
        Text(
            text = value,
            style = MonoTextStyle.copy(fontSize = 10.sp, fontWeight = FontWeight.Medium, color = color)
        )
    }
}

@Composable
fun EquityCurveChart(
    points: List<EquityPoint>,
    currencySymbol: String = "R",
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(ElevatedCard)
            .border(1.dp, BorderSlate, RoundedCornerShape(14.dp))
            .padding(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "EQUITY CURVE",
                style = MaterialTheme.typography.labelMedium,
                color = TextSecondary
            )
            val latest = points.lastOrNull()?.equity ?: 200.0
            val first = points.firstOrNull()?.equity ?: latest
            val delta = latest - first
            val sign = if (delta >= 0) "+" else "-"
            Text(
                text = String.format(Locale.US, "%s%s%,.2f", sign, currencySymbol, abs(delta)),
                style = MonoTextStyle.copy(
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (delta >= 0) BullGreen else BearRed
                )
            )
        }
        Spacer(modifier = Modifier.height(10.dp))
        if (points.size < 2) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(84.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Equity curve updates as trades close",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted
                )
            }
        } else {
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(92.dp)
            ) {
                val minEq = points.minOf { it.equity }
                val maxEq = points.maxOf { it.equity }
                val range = (maxEq - minEq).coerceAtLeast(10.0)
                val padMin = minEq - range * 0.1
                val padMax = maxEq + range * 0.1
                val span = (padMax - padMin).coerceAtLeast(1.0)

                val lineColor = if (points.last().equity >= points.first().equity) BullGreen else BearRed
                val path = Path()
                val fillPath = Path()
                val stepX = size.width / (points.size - 1).coerceAtLeast(1)

                points.forEachIndexed { idx, pt ->
                    val x = idx * stepX
                    val y = size.height - (((pt.equity - padMin) / span).toFloat() * size.height)
                    if (idx == 0) {
                        path.moveTo(x, y)
                        fillPath.moveTo(x, size.height)
                        fillPath.lineTo(x, y)
                    } else {
                        path.lineTo(x, y)
                        fillPath.lineTo(x, y)
                    }
                    if (idx == points.lastIndex) {
                        fillPath.lineTo(x, size.height)
                        fillPath.close()
                    }
                }

                drawPath(
                    path = fillPath,
                    brush = Brush.verticalGradient(
                        colors = listOf(lineColor.copy(alpha = 0.28f), Color.Transparent)
                    )
                )
                drawPath(
                    path = path,
                    color = lineColor,
                    style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
                )
            }
        }
    }
}
