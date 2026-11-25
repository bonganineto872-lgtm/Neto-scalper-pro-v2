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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
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
import com.example.domain.model.FxPair
import com.example.domain.model.TradeSignal
import com.example.ui.theme.AmberWarn
import com.example.ui.theme.BearRed
import com.example.ui.theme.BorderSlate
import com.example.ui.theme.BullGreen
import com.example.ui.theme.CarbonSurface
import com.example.ui.theme.CyberCyan
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
    activeSignal: TradeSignal?,
    modifier: Modifier = Modifier
) {
    val visibleCandles = remember(candles) { candles.takeLast(46) }
    val closes = remember(candles) { candles.map { it.close } }
    val ema9All = remember(candles) { TechnicalIndicators.calculateEmaSeries(closes, 9) }
    val ema21All = remember(candles) { TechnicalIndicators.calculateEmaSeries(closes, 21) }
    val vwapAll = remember(candles) { TechnicalIndicators.calculateVwapSeries(candles, 28) }
    val bbAll = remember(candles) { TechnicalIndicators.calculateBollingerBands(closes, 20, 2.0) }
    val rsiAll = remember(candles) { TechnicalIndicators.calculateRsiSeries(closes, 14) }

    val offsetIndex = (candles.size - visibleCandles.size).coerceAtLeast(0)
    val visibleEma9 = remember(ema9All, offsetIndex) { ema9All.drop(offsetIndex) }
    val visibleEma21 = remember(ema21All, offsetIndex) { ema21All.drop(offsetIndex) }
    val visibleVwap = remember(vwapAll, offsetIndex) { vwapAll.drop(offsetIndex) }
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
        val inspectIdx = selectedBarIndex?.coerceIn(0, (visibleCandles.size - 1).coerceAtLeast(0))
        val inspectCandle = inspectIdx?.let { visibleCandles.getOrNull(it) } ?: visibleCandles.lastOrNull()
        val inspectRsi = inspectIdx?.let { visibleRsi.getOrNull(it) } ?: visibleRsi.lastOrNull()
        val inspectFast = inspectIdx?.let { visibleEma9.getOrNull(it) } ?: visibleEma9.lastOrNull()
        val inspectSlow = inspectIdx?.let { visibleEma21.getOrNull(it) } ?: visibleEma21.lastOrNull()
        val inspectVwap = inspectIdx?.let { visibleVwap.getOrNull(it) } ?: visibleVwap.lastOrNull()

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
                    text = String.format(Locale.US, "RSI %.1f", inspectRsi),
                    style = MonoTextStyle.copy(
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = when {
                            inspectRsi >= 68 -> BearRed
                            inspectRsi <= 32 -> BullGreen
                            else -> VioletIndicator
                        }
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (config.showEmaLines) {
                Text(
                    text = "EMA9: ${inspectFast?.let { pair.formatPrice(it) } ?: "--"}",
                    style = MonoTextStyle.copy(fontSize = 10.sp, color = CyberCyan)
                )
                Text(
                    text = "EMA21: ${inspectSlow?.let { pair.formatPrice(it) } ?: "--"}",
                    style = MonoTextStyle.copy(fontSize = 10.sp, color = AmberWarn)
                )
            }
            if (config.showVwapLine) {
                Text(
                    text = "VWAP: ${inspectVwap?.let { pair.formatPrice(it) } ?: "--"}",
                    style = MonoTextStyle.copy(fontSize = 10.sp, color = VioletIndicator)
                )
            }
            if (selectedBarIndex != null) {
                val fmt = SimpleDateFormat("HH:mm", Locale.US)
                Text(
                    text = inspectCandle?.let { fmt.format(Date(it.timestamp)) } ?: "",
                    style = MonoTextStyle.copy(fontSize = 10.sp, color = TextPrimary)
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        if (visibleCandles.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(210.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Loading live ${pair.displayName} candlesticks...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextMuted
                )
            }
        } else {
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(210.dp)
                    .pointerInput(visibleCandles.size) {
                        detectTapGestures(
                            onTap = { offset ->
                                val chartWidth = (size.width - 74.dp.toPx()).coerceAtLeast(1f)
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
                                val chartWidth = (size.width - 74.dp.toPx()).coerceAtLeast(1f)
                                selectedBarIndex = ((offset.x / chartWidth) * visibleCandles.size)
                                    .roundToInt()
                                    .coerceIn(0, visibleCandles.lastIndex)
                            },
                            onDrag = { change, _ ->
                                val chartWidth = (size.width - 74.dp.toPx()).coerceAtLeast(1f)
                                selectedBarIndex = ((change.position.x / chartWidth) * visibleCandles.size)
                                    .roundToInt()
                                    .coerceIn(0, visibleCandles.lastIndex)
                            },
                            onDragEnd = { selectedBarIndex = null },
                            onDragCancel = { selectedBarIndex = null }
                        )
                    }
            ) {
                val axisWidth = 72.dp.toPx()
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
                activeSignal?.let { sig ->
                    minPrice = min(minPrice, min(sig.stopLoss, min(sig.takeProfit1, sig.takeProfit2)))
                    maxPrice = max(maxPrice, max(sig.stopLoss, max(sig.takeProfit1, sig.takeProfit2)))
                }

                val rawRange = (maxPrice - minPrice).coerceAtLeast(pair.pipSize * 6.0)
                val paddedMin = minPrice - rawRange * 0.08
                val paddedMax = maxPrice + rawRange * 0.08
                val priceRange = (paddedMax - paddedMin).coerceAtLeast(1e-6)

                fun priceToY(p: Double): Float {
                    val ratio = ((p - paddedMin) / priceRange).toFloat().coerceIn(0f, 1f)
                    return plotHeight - (ratio * plotHeight)
                }

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

                        drawPath(path = fillPath, color = CyberCyan.copy(alpha = 0.05f))
                        drawPath(path = upperPath, color = CyberCyan.copy(alpha = 0.25f), style = Stroke(width = 1.dp.toPx()))
                        drawPath(path = lowerPath, color = CyberCyan.copy(alpha = 0.25f), style = Stroke(width = 1.dp.toPx()))
                    }
                }

                visibleCandles.forEachIndexed { idx, candle ->
                    val centerX = idx * stepX + stepX / 2f
                    val color = if (candle.isBullish) BullGreen else BearRed
                    val yHigh = priceToY(candle.high)
                    val yLow = priceToY(candle.low)
                    val yOpen = priceToY(candle.open)
                    val yClose = priceToY(candle.close)

                    drawLine(
                        color = color.copy(alpha = 0.85f),
                        start = Offset(centerX, yHigh),
                        end = Offset(centerX, yLow),
                        strokeWidth = 1.3.dp.toPx(),
                        cap = StrokeCap.Round
                    )

                    val top = min(yOpen, yClose)
                    val height = max(abs(yClose - yOpen), 2.dp.toPx())
                    drawRect(
                        color = color,
                        topLeft = Offset(centerX - candleBodyWidth / 2f, top),
                        size = Size(candleBodyWidth, height)
                    )
                }

                if (config.showEmaLines) {
                    drawLineSeries(visibleEma9, stepX, ::priceToY, CyberCyan, 1.6.dp.toPx())
                    drawLineSeries(visibleEma21, stepX, ::priceToY, AmberWarn, 1.6.dp.toPx())
                }
                if (config.showVwapLine) {
                    drawLineSeries(visibleVwap, stepX, ::priceToY, VioletIndicator, 1.4.dp.toPx())
                }

                // Active Confirmed Signal Price Lines (Entry, SL, TP1, TP2)
                activeSignal?.let { sig ->
                    val dashEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 6f), 0f)
                    drawSignalLevelLine(plotWidth, plotHeight, priceToY(sig.entryPrice), CyberCyan, "ENT", textMeasurer, dashEffect)
                    drawSignalLevelLine(plotWidth, plotHeight, priceToY(sig.stopLoss), BearRed, "SL", textMeasurer, dashEffect)
                    drawSignalLevelLine(plotWidth, plotHeight, priceToY(sig.takeProfit1), BullGreen, "TP1", textMeasurer, dashEffect)
                    drawSignalLevelLine(plotWidth, plotHeight, priceToY(sig.takeProfit2), BullGreen.copy(alpha = 0.75f), "TP2", textMeasurer, dashEffect)
                }

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
                    cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
                )
                drawAxisLabel(
                    textMeasurer = textMeasurer,
                    text = pair.formatPrice(lastClose),
                    x = plotWidth + 4.dp.toPx(),
                    y = tagTop + 2.dp.toPx(),
                    color = ObsidianBg,
                    bold = true
                )

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

            // Synchronized RSI(14) Sub-Pane
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(ObsidianBg.copy(alpha = 0.65f))
            ) {
                val axisWidth = 72.dp.toPx()
                val plotWidth = (size.width - axisWidth).coerceAtLeast(1f)
                val h = size.height

                fun rsiToY(rsi: Double): Float {
                    val clamped = rsi.coerceIn(0.0, 100.0).toFloat()
                    return h - (clamped / 100f) * h
                }

                val y70 = rsiToY(70.0)
                val y30 = rsiToY(30.0)

                drawRect(color = BearRed.copy(alpha = 0.08f), topLeft = Offset(0f, 0f), size = Size(plotWidth, y70))
                drawRect(color = BullGreen.copy(alpha = 0.08f), topLeft = Offset(0f, y30), size = Size(plotWidth, h - y30))

                val dash = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f)
                drawLine(BearRed.copy(alpha = 0.45f), Offset(0f, y70), Offset(plotWidth, y70), 1.dp.toPx(), pathEffect = dash)
                drawLine(BullGreen.copy(alpha = 0.45f), Offset(0f, y30), Offset(plotWidth, y30), 1.dp.toPx(), pathEffect = dash)

                drawAxisLabel(textMeasurer, "70", plotWidth + 6.dp.toPx(), (y70 - 6.dp.toPx()).coerceAtLeast(0f), BearRed.copy(alpha = 0.8f))
                drawAxisLabel(textMeasurer, "30", plotWidth + 6.dp.toPx(), (y30 - 6.dp.toPx()).coerceAtMost(h - 12.dp.toPx()), BullGreen.copy(alpha = 0.8f))

                val stepX = plotWidth / visibleCandles.size.coerceAtLeast(1)
                drawLineSeries(visibleRsi, stepX, ::rsiToY, VioletIndicator, 1.5.dp.toPx())
            }
        }
    }
}

private fun DrawScope.drawSignalLevelLine(
    plotWidth: Float,
    plotHeight: Float,
    y: Float,
    color: Color,
    tag: String,
    textMeasurer: TextMeasurer,
    dashEffect: PathEffect
) {
    val clampedY = y.coerceIn(2.dp.toPx(), plotHeight - 2.dp.toPx())
    drawLine(
        color = color.copy(alpha = 0.85f),
        start = Offset(0f, clampedY),
        end = Offset(plotWidth, clampedY),
        strokeWidth = 1.2.dp.toPx(),
        pathEffect = dashEffect
    )
    drawAxisLabel(
        textMeasurer = textMeasurer,
        text = tag,
        x = 4.dp.toPx(),
        y = (clampedY - 12.dp.toPx()).coerceAtLeast(0f),
        color = color,
        bold = true
    )
}

private fun DrawScope.drawLineSeries(
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

private fun DrawScope.drawAxisLabel(
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
        Text(text = "$label ", style = MonoTextStyle.copy(fontSize = 10.sp, color = TextMuted))
        Text(text = value, style = MonoTextStyle.copy(fontSize = 10.sp, fontWeight = FontWeight.Medium, color = color))
    }
}
