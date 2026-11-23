package com.example.domain.engine

import com.example.domain.model.BollingerPoint
import com.example.domain.model.BotConfig
import com.example.domain.model.Candle
import com.example.domain.model.FxPair
import com.example.domain.model.IndicatorSummary
import com.example.domain.model.MarketQuote
import com.example.domain.model.ScalpingStrategyType
import com.example.domain.model.SessionFilter
import com.example.domain.model.TradeDirectionFilter
import com.example.domain.model.TradePosition
import com.example.domain.model.TradeSide
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt

data class SignalEvaluation(
    val signal: TradeSide?,
    val actionLabel: String,
    val reason: String,
    val summary: IndicatorSummary
)

data class PyramidEvaluation(
    val side: TradeSide,
    val nextLayer: Int,
    val reason: String
)

object TechnicalIndicators {

    fun calculateEmaSeries(closes: List<Double>, period: Int): List<Double?> {
        if (closes.isEmpty() || period <= 0) return emptyList()
        val result = MutableList<Double?>(closes.size) { null }
        if (closes.size < period) return result

        val multiplier = 2.0 / (period + 1.0)
        var ema = closes.take(period).average()
        result[period - 1] = ema

        for (i in period until closes.size) {
            ema = (closes[i] - ema) * multiplier + ema
            result[i] = ema
        }
        return result
    }

    /**
     * Wilder's Smoothed RSI
     */
    fun calculateRsiSeries(closes: List<Double>, period: Int): List<Double?> {
        if (closes.size <= period || period <= 0) {
            return List(closes.size) { null }
        }
        val result = MutableList<Double?>(closes.size) { null }

        var gainSum = 0.0
        var lossSum = 0.0
        for (i in 1..period) {
            val change = closes[i] - closes[i - 1]
            if (change >= 0) gainSum += change else lossSum += abs(change)
        }

        var avgGain = gainSum / period
        var avgLoss = lossSum / period
        result[period] = computeRsiValue(avgGain, avgLoss)

        for (i in (period + 1) until closes.size) {
            val change = closes[i] - closes[i - 1]
            val gain = if (change > 0) change else 0.0
            val loss = if (change < 0) abs(change) else 0.0
            avgGain = ((avgGain * (period - 1)) + gain) / period
            avgLoss = ((avgLoss * (period - 1)) + loss) / period
            result[i] = computeRsiValue(avgGain, avgLoss)
        }
        return result
    }

    private fun computeRsiValue(avgGain: Double, avgLoss: Double): Double {
        if (avgLoss == 0.0) return if (avgGain == 0.0) 50.0 else 100.0
        val rs = avgGain / avgLoss
        return 100.0 - (100.0 / (1.0 + rs))
    }

    fun calculateBollingerBands(
        closes: List<Double>,
        period: Int = 20,
        stdDevMult: Double = 2.0
    ): List<BollingerPoint?> {
        if (closes.isEmpty() || period <= 1) return emptyList()
        val result = MutableList<BollingerPoint?>(closes.size) { null }
        if (closes.size < period) return result

        for (i in (period - 1) until closes.size) {
            val window = closes.subList(i - period + 1, i + 1)
            val mean = window.average()
            val variance = window.sumOf { (it - mean).pow(2) } / period
            val stdDev = sqrt(variance)
            result[i] = BollingerPoint(
                upper = mean + stdDevMult * stdDev,
                middle = mean,
                lower = mean - stdDevMult * stdDev
            )
        }
        return result
    }

    fun calculateAtrSeries(candles: List<Candle>, period: Int = 14): List<Double?> {
        if (candles.size <= period || period <= 0) return List(candles.size) { null }
        val result = MutableList<Double?>(candles.size) { null }
        val trs = DoubleArray(candles.size)
        trs[0] = candles[0].high - candles[0].low

        for (i in 1 until candles.size) {
            val curr = candles[i]
            val prevClose = candles[i - 1].close
            val tr = max(
                curr.high - curr.low,
                max(abs(curr.high - prevClose), abs(curr.low - prevClose))
            )
            trs[i] = tr
        }

        var atr = (1..period).sumOf { trs[it] } / period
        result[period] = atr
        for (i in (period + 1) until candles.size) {
            atr = ((atr * (period - 1)) + trs[i]) / period
            result[i] = atr
        }
        return result
    }

    fun isWithinSession(filter: SessionFilter, timestampMs: Long = System.currentTimeMillis()): Boolean {
        if (filter == SessionFilter.ALL_24H) return true
        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            timeInMillis = timestampMs
        }
        val hourUtc = cal.get(Calendar.HOUR_OF_DAY)
        return hourUtc in filter.utcHoursRange
    }

    /**
     * Calculates compounded micro-to-standard lot size for the 200 ZAR -> 10,000 ZAR flip challenge.
     * Scales smoothly from 0.02 lot at R200 up to 1.00 lot at R10,000 when auto-compound is enabled.
     */
    fun computeEffectiveLotSize(config: BotConfig, currentEquityZar: Double): Double {
        if (!config.autoCompoundSizing) return config.lotSize.coerceIn(0.01, 5.0)
        val scaled = (currentEquityZar / 10_000.0).coerceIn(0.02, 2.50)
        return ((scaled * 100.0).roundToInt() / 100.0).coerceAtLeast(config.lotSize.coerceAtLeast(0.01))
    }

    /**
     * Evaluates whether the market has moved favorably enough to pyramid (add another position)
     * up to [BotConfig.maxOpenTrades] (default 4 positions).
     */
    fun evaluatePyramidScaleIn(
        pair: FxPair,
        quote: MarketQuote,
        openPositionsForPair: List<TradePosition>,
        config: BotConfig
    ): PyramidEvaluation? {
        if (!config.scaleInOnProfit) return null
        if (openPositionsForPair.isEmpty()) return null
        if (openPositionsForPair.size >= config.maxOpenTrades) return null
        if (quote.spreadPips > config.maxSpreadPips) return null

        val primarySide = openPositionsForPair.first().side
        // Only pyramid if all open layers on this pair share the winning trend direction
        if (openPositionsForPair.any { it.side != primarySide }) return null

        val latestPosition = openPositionsForPair.maxByOrNull { it.openedAt } ?: return null
        val favorablePipsSinceLastEntry = if (primarySide == TradeSide.BUY) {
            pair.priceDiffToPips(quote.bid - latestPosition.entryPrice)
        } else {
            pair.priceDiffToPips(latestPosition.entryPrice - quote.ask)
        }

        return if (favorablePipsSinceLastEntry >= config.scaleInStepPips) {
            val nextLayer = openPositionsForPair.size + 1
            PyramidEvaluation(
                side = primarySide,
                nextLayer = nextLayer,
                reason = String.format(
                    Locale.US,
                    "Market favored +%s (+%.1fp >= +%.1fp step) • Scaling Layer %d/%d",
                    primarySide.name,
                    favorablePipsSinceLastEntry,
                    config.scaleInStepPips,
                    nextLayer,
                    config.maxOpenTrades
                )
            )
        } else {
            null
        }
    }

    fun evaluateScalpingSignal(
        pair: FxPair,
        candles: List<Candle>,
        quote: MarketQuote,
        config: BotConfig,
        nowMs: Long = System.currentTimeMillis()
    ): SignalEvaluation {
        if (candles.size < maxOf(config.slowEmaPeriod + 3, 25)) {
            return SignalEvaluation(
                signal = null,
                actionLabel = "WARMING UP",
                reason = "Collecting ${candles.size}/25 bars for indicator convergence",
                summary = IndicatorSummary()
            )
        }

        val closes = candles.map { it.close }
        val fastEmaSeries = calculateEmaSeries(closes, config.fastEmaPeriod)
        val slowEmaSeries = calculateEmaSeries(closes, config.slowEmaPeriod)
        val rsiSeries = calculateRsiSeries(closes, config.rsiPeriod)
        val bbSeries = calculateBollingerBands(closes, 20, 2.0)
        val atrSeries = calculateAtrSeries(candles, 14)

        val lastIdx = candles.lastIndex
        val prevIdx = lastIdx - 1

        val fastNow = fastEmaSeries[lastIdx]
        val fastPrev = fastEmaSeries[prevIdx]
        val slowNow = slowEmaSeries[lastIdx]
        val slowPrev = slowEmaSeries[prevIdx]
        val rsiNow = rsiSeries[lastIdx]
        val bbNow = bbSeries[lastIdx]
        val atrNow = atrSeries[lastIdx]

        val bias = when {
            fastNow != null && slowNow != null && fastNow > slowNow -> "BULLISH"
            fastNow != null && slowNow != null && fastNow < slowNow -> "BEARISH"
            else -> "NEUTRAL"
        }

        val summary = IndicatorSummary(
            fastEma = fastNow,
            slowEma = slowNow,
            rsi = rsiNow,
            bbUpper = bbNow?.upper,
            bbMiddle = bbNow?.middle,
            bbLower = bbNow?.lower,
            atrPips = atrNow?.let { pair.priceDiffToPips(it) },
            trendBias = bias
        )

        if (!isWithinSession(config.sessionFilter, nowMs)) {
            return SignalEvaluation(
                signal = null,
                actionLabel = "SESSION HOLD",
                reason = "Outside ${config.sessionFilter.label} active window",
                summary = summary
            )
        }

        if (quote.spreadPips > config.maxSpreadPips) {
            return SignalEvaluation(
                signal = null,
                actionLabel = "SPREAD GUARD",
                reason = String.format(
                    Locale.US,
                    "Spread %.1fp > max %.1fp limit",
                    quote.spreadPips,
                    config.maxSpreadPips
                ),
                summary = summary
            )
        }

        if (fastNow == null || fastPrev == null || slowNow == null || slowPrev == null || rsiNow == null) {
            return SignalEvaluation(
                signal = null,
                actionLabel = "SCANNING",
                reason = "Waiting for valid indicator series",
                summary = summary
            )
        }

        val lastCandle = candles[lastIdx]
        val prevCandle = candles[prevIdx]

        val rawEval = when (config.strategy) {
            ScalpingStrategyType.EMA_RSI_MOMENTUM -> {
                val bullCross = fastPrev <= slowPrev && fastNow > slowNow
                val bearCross = fastPrev >= slowPrev && fastNow < slowNow
                val bullPullback = fastNow > slowNow &&
                    prevCandle.low <= fastPrev &&
                    lastCandle.close > fastNow &&
                    rsiNow in 47.0..config.rsiOverbought.toDouble()
                val bearPullback = fastNow < slowNow &&
                    prevCandle.high >= fastPrev &&
                    lastCandle.close < fastNow &&
                    rsiNow in config.rsiOversold.toDouble()..53.0
                val bullImpulse = fastNow > slowNow && lastCandle.isBullish && rsiNow in 52.0..66.0
                val bearImpulse = fastNow < slowNow && !lastCandle.isBullish && rsiNow in 34.0..48.0

                when {
                    (bullCross && rsiNow in 45.0..config.rsiOverbought.toDouble()) || bullPullback || bullImpulse -> {
                        val tag = when {
                            bullCross -> "EMA${config.fastEmaPeriod}/${config.slowEmaPeriod} bull cross"
                            bullPullback -> "EMA${config.fastEmaPeriod} trend bounce"
                            else -> "M1 bull impulse"
                        }
                        SignalEvaluation(
                            signal = TradeSide.BUY,
                            actionLabel = "BUY SIGNAL",
                            reason = String.format(Locale.US, "%s • RSI %.1f • Spr %.1fp", tag, rsiNow, quote.spreadPips),
                            summary = summary
                        )
                    }
                    (bearCross && rsiNow in config.rsiOversold.toDouble()..55.0) || bearPullback || bearImpulse -> {
                        val tag = when {
                            bearCross -> "EMA${config.fastEmaPeriod}/${config.slowEmaPeriod} bear cross"
                            bearPullback -> "EMA${config.fastEmaPeriod} trend rejection"
                            else -> "M1 bear impulse"
                        }
                        SignalEvaluation(
                            signal = TradeSide.SELL,
                            actionLabel = "SELL SIGNAL",
                            reason = String.format(Locale.US, "%s • RSI %.1f • Spr %.1fp", tag, rsiNow, quote.spreadPips),
                            summary = summary
                        )
                    }
                    else -> SignalEvaluation(
                        signal = null,
                        actionLabel = "SCANNING",
                        reason = String.format(
                            Locale.US,
                            "EMA%d %s EMA%d • RSI %.1f",
                            config.fastEmaPeriod,
                            if (fastNow >= slowNow) ">" else "<",
                            config.slowEmaPeriod,
                            rsiNow
                        ),
                        summary = summary
                    )
                }
            }

            ScalpingStrategyType.BOLLINGER_MEAN_REVERSION -> {
                if (bbNow == null) {
                    SignalEvaluation(null, "SCANNING", "Warming up Bollinger Bands", summary)
                } else when {
                    lastCandle.close <= bbNow.lower && rsiNow <= config.rsiOversold -> {
                        SignalEvaluation(
                            signal = TradeSide.BUY,
                            actionLabel = "BUY REVERSION",
                            reason = String.format(Locale.US, "Price <= Lower BB & RSI %.1f <= %d", rsiNow, config.rsiOversold),
                            summary = summary
                        )
                    }
                    lastCandle.close >= bbNow.upper && rsiNow >= config.rsiOverbought -> {
                        SignalEvaluation(
                            signal = TradeSide.SELL,
                            actionLabel = "SELL REVERSION",
                            reason = String.format(Locale.US, "Price >= Upper BB & RSI %.1f >= %d", rsiNow, config.rsiOverbought),
                            summary = summary
                        )
                    }
                    else -> SignalEvaluation(
                        signal = null,
                        actionLabel = "SCANNING",
                        reason = String.format(Locale.US, "Inside BB channel • RSI %.1f", rsiNow),
                        summary = summary
                    )
                }
            }

            ScalpingStrategyType.ATR_VOLATILITY_BREAKOUT -> {
                val lookback = candles.subList((lastIdx - 8).coerceAtLeast(0), lastIdx)
                val rangeHigh = lookback.maxOf { it.high }
                val rangeLow = lookback.minOf { it.low }
                val atrBaseline = atrSeries.filterNotNull().takeLast(10).average()
                val atrExpanding = atrNow != null && atrNow >= atrBaseline * 0.95

                when {
                    lastCandle.close > rangeHigh && atrExpanding && rsiNow > 52.0 && rsiNow < 76.0 -> {
                        SignalEvaluation(
                            signal = TradeSide.BUY,
                            actionLabel = "BUY BREAKOUT",
                            reason = String.format(Locale.US, "8-bar High break • ATR %.1fp • RSI %.1f", summary.atrPips ?: 0.0, rsiNow),
                            summary = summary
                        )
                    }
                    lastCandle.close < rangeLow && atrExpanding && rsiNow < 48.0 && rsiNow > 24.0 -> {
                        SignalEvaluation(
                            signal = TradeSide.SELL,
                            actionLabel = "SELL BREAKOUT",
                            reason = String.format(Locale.US, "8-bar Low break • ATR %.1fp • RSI %.1f", summary.atrPips ?: 0.0, rsiNow),
                            summary = summary
                        )
                    }
                    else -> SignalEvaluation(
                        signal = null,
                        actionLabel = "SCANNING",
                        reason = String.format(Locale.US, "Range consolidation • ATR %.1fp", summary.atrPips ?: 0.0),
                        summary = summary
                    )
                }
            }
        }

        return applyDirectionFilter(rawEval, config.directionFilter)
    }

    private fun applyDirectionFilter(
        eval: SignalEvaluation,
        filter: TradeDirectionFilter
    ): SignalEvaluation {
        val signal = eval.signal ?: return eval
        return when {
            filter == TradeDirectionFilter.BUY_ONLY && signal == TradeSide.SELL -> eval.copy(
                signal = null,
                actionLabel = "BUY-ONLY FILTER",
                reason = "Short signal filtered (Mode: Buy Only)"
            )
            filter == TradeDirectionFilter.SELL_ONLY && signal == TradeSide.BUY -> eval.copy(
                signal = null,
                actionLabel = "SELL-ONLY FILTER",
                reason = "Long signal filtered (Mode: Sell Only)"
            )
            else -> eval
        }
    }
}
