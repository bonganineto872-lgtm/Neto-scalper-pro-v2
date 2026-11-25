package com.example.domain.engine

import com.example.domain.model.BollingerPoint
import com.example.domain.model.BotConfig
import com.example.domain.model.Candle
import com.example.domain.model.ConfirmationStage
import com.example.domain.model.FlipCurrency
import com.example.domain.model.FxPair
import com.example.domain.model.GoldBtcStrategyType
import com.example.domain.model.IndicatorSummary
import com.example.domain.model.MarketQuote
import com.example.domain.model.SessionFilter
import com.example.domain.model.Timeframe
import com.example.domain.model.TradeDirectionFilter
import com.example.domain.model.TradeSide
import com.example.domain.model.TradeSignal
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt

data class StrategyVote(
    val strategyName: String,
    val side: TradeSide,
    val reason: String,
    val weight: Int = 1
)

data class SignalEvaluation(
    val candidateSide: TradeSide?,
    val stage: ConfirmationStage,
    val actionLabel: String,
    val reason: String,
    val alignedStrategies: List<String>,
    val confidencePercent: Int,
    val entryPrice: Double?,
    val stopLoss: Double?,
    val tp1: Double?,
    val tp2: Double?,
    val tp3: Double?,
    val slPips: Double?,
    val tp1Pips: Double?,
    val tp2Pips: Double?,
    val tp3Pips: Double?,
    val summary: IndicatorSummary
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

    fun calculateRsiSeries(closes: List<Double>, period: Int = 14): List<Double?> {
        if (closes.size <= period || period <= 0) return List(closes.size) { null }
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
            trs[i] = max(
                curr.high - curr.low,
                max(abs(curr.high - prevClose), abs(curr.low - prevClose))
            )
        }

        var atr = (1..period).sumOf { trs[it] } / period
        result[period] = atr
        for (i in (period + 1) until candles.size) {
            atr = ((atr * (period - 1)) + trs[i]) / period
            result[i] = atr
        }
        return result
    }

    fun calculateVwapSeries(candles: List<Candle>, windowBars: Int = 28): List<Double?> {
        if (candles.isEmpty()) return emptyList()
        val result = MutableList<Double?>(candles.size) { null }
        for (i in candles.indices) {
            val start = (i - windowBars + 1).coerceAtLeast(0)
            var pvSum = 0.0
            var volSum = 0.0
            for (j in start..i) {
                val c = candles[j]
                val typical = (c.high + c.low + c.close) / 3.0
                val vol = c.volume.coerceAtLeast(1.0)
                pvSum += typical * vol
                volSum += vol
            }
            result[i] = if (volSum > 0.0) pvSum / volSum else candles[i].close
        }
        return result
    }

    fun calculateAdxSeries(candles: List<Candle>, period: Int = 14): List<Double?> {
        if (candles.size <= period * 2) return List(candles.size) { null }
        val result = MutableList<Double?>(candles.size) { null }
        val tr = DoubleArray(candles.size)
        val plusDm = DoubleArray(candles.size)
        val minusDm = DoubleArray(candles.size)

        for (i in 1 until candles.size) {
            val curr = candles[i]
            val prev = candles[i - 1]
            tr[i] = max(curr.high - curr.low, max(abs(curr.high - prev.close), abs(curr.low - prev.close)))
            val upMove = curr.high - prev.high
            val downMove = prev.low - curr.low
            plusDm[i] = if (upMove > downMove && upMove > 0) upMove else 0.0
            minusDm[i] = if (downMove > upMove && downMove > 0) downMove else 0.0
        }

        var smoothedTr = (1..period).sumOf { tr[it] }
        var smoothedPlus = (1..period).sumOf { plusDm[it] }
        var smoothedMinus = (1..period).sumOf { minusDm[it] }

        val dxValues = mutableListOf<Double>()
        for (i in (period + 1) until candles.size) {
            smoothedTr = smoothedTr - (smoothedTr / period) + tr[i]
            smoothedPlus = smoothedPlus - (smoothedPlus / period) + plusDm[i]
            smoothedMinus = smoothedMinus - (smoothedMinus / period) + minusDm[i]

            if (smoothedTr > 0.0) {
                val pdi = 100.0 * (smoothedPlus / smoothedTr)
                val mdi = 100.0 * (smoothedMinus / smoothedTr)
                val sum = pdi + mdi
                val dx = if (sum > 0.0) 100.0 * abs(pdi - mdi) / sum else 0.0
                dxValues.add(dx)
                if (dxValues.size >= period) {
                    result[i] = dxValues.takeLast(period).average()
                }
            }
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
     * Computes compounding lot size for flipping a small account to a big target.
     */
    fun computeFlipLotSize(
        pair: FxPair,
        config: BotConfig,
        currentCapital: Double,
        slPips: Double,
        usdToZarRate: Double
    ): Double {
        if (!config.autoCompoundFlipLot) return config.baseLotSize.coerceIn(0.01, 10.0)
        val capitalUsd = if (config.flipCurrency == FlipCurrency.ZAR) {
            currentCapital / usdToZarRate.coerceAtLeast(1.0)
        } else {
            currentCapital
        }
        val riskUsd = capitalUsd * (config.riskPercentPerTrade / 100.0)
        val pipValuePerLot = pair.usdValuePerPipPerLot().coerceAtLeast(0.1)
        val rawLot = riskUsd / (slPips.coerceAtLeast(1.0) * pipValuePerLot)
        val rounded = (rawLot * 100.0).roundToInt() / 100.0
        return rounded.coerceIn(0.01, 5.0)
    }

    fun estimateFlipStepsRemaining(
        currentCapital: Double,
        targetCapital: Double,
        riskPercent: Double,
        avgRewardRisk: Double = 2.2
    ): Int {
        if (currentCapital >= targetCapital || currentCapital <= 0.0) return 0
        val stepGrowth = 1.0 + (riskPercent / 100.0) * avgRewardRisk
        if (stepGrowth <= 1.01) return 30
        val steps = kotlin.math.ceil(ln(targetCapital / currentCapital) / ln(stepGrowth)).toInt()
        return steps.coerceIn(1, 99)
    }

    /**
     * Evaluates all 5 world-class intraday Gold & Bitcoin strategies and constructs clean-sheet Entry / SL / TP1-TP2-TP3 levels.
     */
    fun evaluatePrecisionSignal(
        pair: FxPair,
        timeframe: Timeframe,
        candles: List<Candle>,
        quote: MarketQuote,
        config: BotConfig,
        nowMs: Long = System.currentTimeMillis()
    ): SignalEvaluation {
        if (candles.size < 52) {
            return emptyEval("WARMING UP", "Loading ${candles.size}/52 ${timeframe.label} candles for confluence", IndicatorSummary())
        }

        val closes = candles.map { it.close }
        val ema9 = calculateEmaSeries(closes, 9)
        val ema21 = calculateEmaSeries(closes, 21)
        val ema50 = calculateEmaSeries(closes, 50)
        val rsiSeries = calculateRsiSeries(closes, 14)
        val bbSeries = calculateBollingerBands(closes, 20, 2.0)
        val atrSeries = calculateAtrSeries(candles, 14)
        val vwapSeries = calculateVwapSeries(candles, 28)
        val adxSeries = calculateAdxSeries(candles, 14)

        val lastIdx = candles.lastIndex
        val fastNow = ema9[lastIdx]
        val slowNow = ema21[lastIdx]
        val trendNow = ema50[lastIdx]
        val rsiNow = rsiSeries[lastIdx]
        val bbNow = bbSeries[lastIdx]
        val atrNow = atrSeries[lastIdx]
        val vwapNow = vwapSeries[lastIdx]
        val adxNow = adxSeries[lastIdx]

        val bias = when {
            fastNow != null && slowNow != null && trendNow != null && fastNow > slowNow && slowNow >= trendNow -> "BULLISH"
            fastNow != null && slowNow != null && trendNow != null && fastNow < slowNow && slowNow <= trendNow -> "BEARISH"
            fastNow != null && slowNow != null && fastNow > slowNow -> "BULLISH"
            fastNow != null && slowNow != null && fastNow < slowNow -> "BEARISH"
            else -> "NEUTRAL"
        }

        val summary = IndicatorSummary(
            fastEma = fastNow,
            slowEma = slowNow,
            trendEma50 = trendNow,
            vwap = vwapNow,
            rsi = rsiNow,
            adx = adxNow,
            bbUpper = bbNow?.upper,
            bbMiddle = bbNow?.middle,
            bbLower = bbNow?.lower,
            atrPips = atrNow?.let { pair.priceDiffToPips(it) },
            trendBias = bias
        )

        if (!isWithinSession(config.sessionFilter, nowMs)) {
            return emptyEval("SESSION HOLD", "Outside ${config.sessionFilter.label} window", summary)
        }

        val maxSpread = config.maxSpreadFor(pair)
        if (quote.spreadPips > maxSpread) {
            return emptyEval(
                "SPREAD GUARD",
                String.format(Locale.US, "Spread %.1fp exceeds %.1fp clean-sheet limit", quote.spreadPips, maxSpread),
                summary
            )
        }

        if (fastNow == null || slowNow == null || trendNow == null || rsiNow == null || atrNow == null || vwapNow == null) {
            return emptyEval("SCANNING", "Waiting for indicator convergence", summary)
        }

        // Collect votes from all 6 elite strategies
        val votes = mutableListOf<StrategyVote>()
        detectSmcLiquiditySweep(candles, ema50, rsiNow)?.let { votes.add(it) }
        detectFvgOrderBlock(candles, ema21, ema50, rsiNow)?.let { votes.add(it) }
        detectEmaSniperPullback(candles, ema9, ema21, ema50, rsiNow)?.let { votes.add(it) }
        detectVwapSessionBreakout(candles, vwapSeries, adxNow, rsiNow)?.let { votes.add(it) }
        detectBollingerAtrSqueeze(candles, bbSeries, atrSeries, rsiNow)?.let { votes.add(it) }
        detectRsiDivergenceReversal(candles, rsiSeries)?.let { votes.add(it) }

        val selectedVote: StrategyVote?
        val alignedNames: List<String>

        if (config.strategy == GoldBtcStrategyType.APEX_CONFLUENCE) {
            val buyVotes = votes.filter { it.side == TradeSide.BUY }
            val sellVotes = votes.filter { it.side == TradeSide.SELL }
            val buyScore = buyVotes.sumOf { it.weight }
            val sellScore = sellVotes.sumOf { it.weight }

            when {
                buyScore > sellScore && buyVotes.isNotEmpty() && sellVotes.isEmpty() -> {
                    selectedVote = buyVotes.maxByOrNull { it.weight }
                    alignedNames = buyVotes.map { it.strategyName }
                }
                sellScore > buyScore && sellVotes.isNotEmpty() && buyVotes.isEmpty() -> {
                    selectedVote = sellVotes.maxByOrNull { it.weight }
                    alignedNames = sellVotes.map { it.strategyName }
                }
                buyScore >= sellScore + 2 && buyVotes.size >= 2 -> {
                    selectedVote = buyVotes.maxByOrNull { it.weight }
                    alignedNames = buyVotes.map { it.strategyName }
                }
                sellScore >= buyScore + 2 && sellVotes.size >= 2 -> {
                    selectedVote = sellVotes.maxByOrNull { it.weight }
                    alignedNames = sellVotes.map { it.strategyName }
                }
                else -> {
                    selectedVote = null
                    alignedNames = emptyList()
                }
            }
        } else {
            val targetName = config.strategy.shortName
            val matching = votes.find { it.strategyName == targetName }
            selectedVote = matching
            alignedNames = if (matching != null) {
                votes.filter { it.side == matching.side }.map { it.strategyName }
            } else {
                emptyList()
            }
        }

        if (selectedVote == null) {
            val statusMsg = String.format(
                Locale.US,
                "%s %s • RSI %.1f • ATR %.1fp • No clean-sheet setup yet",
                timeframe.label,
                bias,
                rsiNow,
                summary.atrPips ?: 0.0
            )
            return emptyEval("SCANNING", statusMsg, summary)
        }

        // Direction filter
        val filteredSide = when {
            config.directionFilter == TradeDirectionFilter.BUY_ONLY && selectedVote.side == TradeSide.SELL -> null
            config.directionFilter == TradeDirectionFilter.SELL_ONLY && selectedVote.side == TradeSide.BUY -> null
            else -> selectedVote.side
        }
        if (filteredSide == null) {
            return emptyEval(
                if (config.directionFilter == TradeDirectionFilter.BUY_ONLY) "BUY-ONLY FILTER" else "SELL-ONLY FILTER",
                "Signal filtered by direction mode (${config.directionFilter.label})",
                summary
            )
        }

        // Clean-Sheet Wick & Close-Momentum Guard (prevents early drawdown on entry)
        val lastCandle = candles[lastIdx]
        val upperWick = lastCandle.high - max(lastCandle.open, lastCandle.close)
        val lowerWick = min(lastCandle.open, lastCandle.close) - lastCandle.low
        val badUpperWick = filteredSide == TradeSide.BUY && (upperWick / lastCandle.totalRange) > 0.50
        val badLowerWick = filteredSide == TradeSide.SELL && (lowerWick / lastCandle.totalRange) > 0.50
        val closePosition = (lastCandle.close - lastCandle.low) / lastCandle.totalRange
        val weakBullClose = filteredSide == TradeSide.BUY && closePosition < 0.52
        val weakBearClose = filteredSide == TradeSide.SELL && closePosition > 0.48
        if (badUpperWick || badLowerWick || weakBullClose || weakBearClose) {
            return emptyEval(
                "WICK GUARD",
                "Rejected weak close/rejection wick (protecting clean-sheet entry)",
                summary
            )
        }

        // Calculate calibrated ATR-based Entry, SL, TP1, TP2, TP3 and exact pips to target
        val entry = if (filteredSide == TradeSide.BUY) quote.ask else quote.bid
        val atrPipsRaw = pair.priceDiffToPips(atrNow)
        val tfMult = when (timeframe) {
            Timeframe.M1 -> 0.70
            Timeframe.M5 -> 1.0
            Timeframe.M15 -> 1.35
            Timeframe.M30 -> 1.60
            Timeframe.H1 -> 1.85
            Timeframe.H4 -> 2.40
            Timeframe.D1 -> 3.20
        }
        val minSlPips = when (pair) {
            FxPair.XAU_USD -> 10.0 * tfMult
            FxPair.BTC_USD -> 65.0 * tfMult
        }
        val maxSlPips = when (pair) {
            FxPair.XAU_USD -> 38.0 * tfMult
            FxPair.BTC_USD -> 320.0 * tfMult
        }
        val slPips = (atrPipsRaw * 1.15).coerceIn(minSlPips, maxSlPips)
        val tp1Pips = (slPips * 1.35).let { (it * 10.0).roundToInt() / 10.0 }
        val tp2Pips = (slPips * 2.40).let { (it * 10.0).roundToInt() / 10.0 }
        val tp3Pips = (slPips * 3.80).let { (it * 10.0).roundToInt() / 10.0 }
        val roundedSlPips = (slPips * 10.0).roundToInt() / 10.0

        val slPrice = if (filteredSide == TradeSide.BUY) {
            entry - pair.pipsToPriceDiff(roundedSlPips)
        } else {
            entry + pair.pipsToPriceDiff(roundedSlPips)
        }
        val tp1Price = if (filteredSide == TradeSide.BUY) {
            entry + pair.pipsToPriceDiff(tp1Pips)
        } else {
            entry - pair.pipsToPriceDiff(tp1Pips)
        }
        val tp2Price = if (filteredSide == TradeSide.BUY) {
            entry + pair.pipsToPriceDiff(tp2Pips)
        } else {
            entry - pair.pipsToPriceDiff(tp2Pips)
        }
        val tp3Price = if (filteredSide == TradeSide.BUY) {
            entry + pair.pipsToPriceDiff(tp3Pips)
        } else {
            entry - pair.pipsToPriceDiff(tp3Pips)
        }

        val confidence = computeConfidenceScore(
            side = filteredSide,
            alignedCount = alignedNames.size,
            summary = summary,
            spreadPips = quote.spreadPips,
            maxSpreadPips = maxSpread
        )

        if (confidence < config.minConfidencePercent) {
            return emptyEval(
                "LOW CONFIDENCE",
                "Setup $confidence% < ${config.minConfidencePercent}% min precision threshold",
                summary
            )
        }

        return SignalEvaluation(
            candidateSide = filteredSide,
            stage = ConfirmationStage.CONFIRMING,
            actionLabel = "CONFIRMING ${filteredSide.name}",
            reason = "${selectedVote.reason} • ${alignedNames.size}x Confluence",
            alignedStrategies = alignedNames,
            confidencePercent = confidence,
            entryPrice = entry,
            stopLoss = slPrice,
            tp1 = tp1Price,
            tp2 = tp2Price,
            tp3 = tp3Price,
            slPips = roundedSlPips,
            tp1Pips = tp1Pips,
            tp2Pips = tp2Pips,
            tp3Pips = tp3Pips,
            summary = summary
        )
    }

    /**
     * Strategy 1: Smart Money Concepts (SMC) Liquidity Sweep + Fair Value Gap / Displacement
     */
    private fun detectSmcLiquiditySweep(
        candles: List<Candle>,
        ema50: List<Double?>,
        rsiNow: Double
    ): StrategyVote? {
        val lastIdx = candles.lastIndex
        val prevIdx = lastIdx - 1
        val lookback = candles.subList((lastIdx - 18).coerceAtLeast(0), prevIdx)
        if (lookback.size < 6) return null

        val swingLow = lookback.minOf { it.low }
        val swingHigh = lookback.maxOf { it.high }
        val prev = candles[prevIdx]
        val curr = candles[lastIdx]
        val trend = ema50[lastIdx] ?: curr.close

        // Bullish liquidity sweep: prior or current bar swept below swingLow and closed sharply bullish above swingLow
        val sweptLow = (prev.low < swingLow || curr.low < swingLow) &&
            curr.close > swingLow &&
            curr.isBullish &&
            curr.bodyRatio >= 0.45 &&
            curr.close >= trend * 0.996

        if (sweptLow && rsiNow in 32.0..66.0) {
            return StrategyVote(
                strategyName = GoldBtcStrategyType.SMC_LIQUIDITY_SNIPER.shortName,
                side = TradeSide.BUY,
                reason = "SMC Sell-Side Liquidity Sweep + Bull Displacement",
                weight = 2
            )
        }

        val sweptHigh = (prev.high > swingHigh || curr.high > swingHigh) &&
            curr.close < swingHigh &&
            !curr.isBullish &&
            curr.bodyRatio >= 0.45 &&
            curr.close <= trend * 1.004

        if (sweptHigh && rsiNow in 34.0..68.0) {
            return StrategyVote(
                strategyName = GoldBtcStrategyType.SMC_LIQUIDITY_SNIPER.shortName,
                side = TradeSide.SELL,
                reason = "SMC Buy-Side Liquidity Sweep + Bear Displacement",
                weight = 2
            )
        }
        return null
    }

    /**
     * Strategy 2: Fair Value Gap (FVG) + Institutional Order Block Displacement
     */
    private fun detectFvgOrderBlock(
        candles: List<Candle>,
        ema21: List<Double?>,
        ema50: List<Double?>,
        rsiNow: Double
    ): StrategyVote? {
        val i = candles.lastIndex
        if (i < 4) return null
        val c0 = candles[i - 2]
        val c1 = candles[i - 1]
        val c2 = candles[i]
        val slow = ema21[i] ?: c2.close
        val trend = ema50[i] ?: c2.close

        // Bullish 3-candle FVG imbalance: c2.low > c0.high or strong displacement reclaiming OB after down candle c0
        val bullFvg = c1.isBullish &&
            c1.bodyRatio >= 0.50 &&
            c2.isBullish &&
            c2.low > c0.high &&
            c2.close > slow &&
            c2.close >= trend * 0.997 &&
            rsiNow in 44.0..69.0

        if (bullFvg) {
            return StrategyVote(
                strategyName = GoldBtcStrategyType.FVG_ORDER_BLOCK.shortName,
                side = TradeSide.BUY,
                reason = "Bullish FVG Imbalance + Order Block Displacement",
                weight = 2
            )
        }

        val bearFvg = !c1.isBullish &&
            c1.bodyRatio >= 0.50 &&
            !c2.isBullish &&
            c2.high < c0.low &&
            c2.close < slow &&
            c2.close <= trend * 1.003 &&
            rsiNow in 31.0..56.0

        if (bearFvg) {
            return StrategyVote(
                strategyName = GoldBtcStrategyType.FVG_ORDER_BLOCK.shortName,
                side = TradeSide.SELL,
                reason = "Bearish FVG Imbalance + Order Block Displacement",
                weight = 2
            )
        }
        return null
    }

    /**
     * Strategy 2: EMA 9/21/50 Institutional Sniper Pullback
     */
    private fun detectEmaSniperPullback(
        candles: List<Candle>,
        ema9: List<Double?>,
        ema21: List<Double?>,
        ema50: List<Double?>,
        rsiNow: Double
    ): StrategyVote? {
        val i = candles.lastIndex
        val p = i - 1
        val f = ema9[i] ?: return null
        val s = ema21[i] ?: return null
        val t = ema50[i] ?: return null
        val fp = ema9[p] ?: return null
        val sp = ema21[p] ?: return null
        val curr = candles[i]
        val prev = candles[p]

        val bullCross = fp <= sp && f > s && curr.isBullish && rsiNow in 46.0..68.0
        val bullPullback = f > s && s > t &&
            (prev.low <= fp || curr.low <= f) &&
            curr.close > f &&
            curr.isBullish &&
            rsiNow in 46.0..65.0

        if (bullCross || bullPullback) {
            return StrategyVote(
                strategyName = GoldBtcStrategyType.EMA_SNIPER_PULLBACK.shortName,
                side = TradeSide.BUY,
                reason = if (bullCross) "EMA 9/21 Bullish Sniper Cross" else "EMA 9/21/50 Trend Pullback Bounce",
                weight = 2
            )
        }

        val bearCross = fp >= sp && f < s && !curr.isBullish && rsiNow in 32.0..54.0
        val bearPullback = f < s && s < t &&
            (prev.high >= fp || curr.high >= f) &&
            curr.close < f &&
            !curr.isBullish &&
            rsiNow in 35.0..54.0

        if (bearCross || bearPullback) {
            return StrategyVote(
                strategyName = GoldBtcStrategyType.EMA_SNIPER_PULLBACK.shortName,
                side = TradeSide.SELL,
                reason = if (bearCross) "EMA 9/21 Bearish Sniper Cross" else "EMA 9/21/50 Trend Pullback Reject",
                weight = 2
            )
        }
        return null
    }

    /**
     * Strategy 3: VWAP + Intraday Session Momentum Impulse
     */
    private fun detectVwapSessionBreakout(
        candles: List<Candle>,
        vwapSeries: List<Double?>,
        adxNow: Double?,
        rsiNow: Double
    ): StrategyVote? {
        val i = candles.lastIndex
        val p = i - 1
        val vwapNow = vwapSeries[i] ?: return null
        val vwapPrev = vwapSeries[p] ?: return null
        val curr = candles[i]
        val prev = candles[p]
        val adxOk = (adxNow ?: 22.0) >= 16.0

        val bullVwap = adxOk &&
            (prev.close <= vwapPrev || prev.low <= vwapPrev) &&
            curr.close > vwapNow &&
            curr.isBullish &&
            curr.bodyRatio >= 0.50 &&
            rsiNow in 50.0..70.0

        if (bullVwap) {
            return StrategyVote(
                strategyName = GoldBtcStrategyType.VWAP_SESSION_BREAKOUT.shortName,
                side = TradeSide.BUY,
                reason = "VWAP Bullish Reclaim + Strong Body Impulse",
                weight = 1
            )
        }

        val bearVwap = adxOk &&
            (prev.close >= vwapPrev || prev.high >= vwapPrev) &&
            curr.close < vwapNow &&
            !curr.isBullish &&
            curr.bodyRatio >= 0.50 &&
            rsiNow in 30.0..50.0

        if (bearVwap) {
            return StrategyVote(
                strategyName = GoldBtcStrategyType.VWAP_SESSION_BREAKOUT.shortName,
                side = TradeSide.SELL,
                reason = "VWAP Bearish Breakdown + Strong Body Impulse",
                weight = 1
            )
        }
        return null
    }

    /**
     * Strategy 4: Bollinger Band Compression Squeeze + ATR Surge Breakout
     */
    private fun detectBollingerAtrSqueeze(
        candles: List<Candle>,
        bbSeries: List<BollingerPoint?>,
        atrSeries: List<Double?>,
        rsiNow: Double
    ): StrategyVote? {
        val i = candles.lastIndex
        val bbNow = bbSeries[i] ?: return null
        val atrNow = atrSeries[i] ?: return null
        val recentBb = bbSeries.subList((i - 10).coerceAtLeast(0), i).filterNotNull()
        val recentAtr = atrSeries.subList((i - 10).coerceAtLeast(0), i).filterNotNull()
        if (recentBb.size < 4 || recentAtr.isEmpty()) return null

        val avgBandwidth = recentBb.map { it.bandwidth }.average()
        val avgAtr = recentAtr.average()
        val curr = candles[i]
        val prev = candles[i - 1]
        val squeezeExpanding = bbNow.bandwidth >= avgBandwidth * 0.96 && atrNow >= avgAtr * 0.96

        if (squeezeExpanding && curr.close > bbNow.middle && prev.close <= bbNow.middle && curr.isBullish && rsiNow in 51.0..72.0) {
            return StrategyVote(
                strategyName = GoldBtcStrategyType.BOLLINGER_ATR_SQUEEZE.shortName,
                side = TradeSide.BUY,
                reason = "BB Squeeze + ATR Surge Bullish Break",
                weight = 1
            )
        }
        if (squeezeExpanding && curr.close < bbNow.middle && prev.close >= bbNow.middle && !curr.isBullish && rsiNow in 28.0..49.0) {
            return StrategyVote(
                strategyName = GoldBtcStrategyType.BOLLINGER_ATR_SQUEEZE.shortName,
                side = TradeSide.SELL,
                reason = "BB Squeeze + ATR Surge Bearish Break",
                weight = 1
            )
        }
        return null
    }

    /**
     * Strategy 5: RSI Divergence + Reversal Candle Rejection
     */
    private fun detectRsiDivergenceReversal(
        candles: List<Candle>,
        rsiSeries: List<Double?>
    ): StrategyVote? {
        val i = candles.lastIndex
        if (i < 16) return null
        val curr = candles[i]
        val currRsi = rsiSeries[i] ?: return null

        // Compare recent 5-bar extreme vs prior 12-bar extreme
        val priorIndices = (i - 14)..(i - 5)
        val recentIndices = (i - 4)..i

        val priorLowIdx = priorIndices.minByOrNull { candles[it].low } ?: return null
        val recentLowIdx = recentIndices.minByOrNull { candles[it].low } ?: return null
        val priorLowRsi = rsiSeries[priorLowIdx]
        val recentLowRsi = rsiSeries[recentLowIdx]

        if (priorLowRsi != null && recentLowRsi != null &&
            candles[recentLowIdx].low <= candles[priorLowIdx].low &&
            recentLowRsi > priorLowRsi + 2.5 &&
            currRsi <= 46.0 &&
            curr.isBullish
        ) {
            return StrategyVote(
                strategyName = GoldBtcStrategyType.RSI_DIVERGENCE_REVERSAL.shortName,
                side = TradeSide.BUY,
                reason = String.format(Locale.US, "Bullish RSI Divergence (%.1f → %.1f)", priorLowRsi, recentLowRsi),
                weight = 1
            )
        }

        val priorHighIdx = priorIndices.maxByOrNull { candles[it].high } ?: return null
        val recentHighIdx = recentIndices.maxByOrNull { candles[it].high } ?: return null
        val priorHighRsi = rsiSeries[priorHighIdx]
        val recentHighRsi = rsiSeries[recentHighIdx]

        if (priorHighRsi != null && recentHighRsi != null &&
            candles[recentHighIdx].high >= candles[priorHighIdx].high &&
            recentHighRsi < priorHighRsi - 2.5 &&
            currRsi >= 54.0 &&
            !curr.isBullish
        ) {
            return StrategyVote(
                strategyName = GoldBtcStrategyType.RSI_DIVERGENCE_REVERSAL.shortName,
                side = TradeSide.SELL,
                reason = String.format(Locale.US, "Bearish RSI Divergence (%.1f → %.1f)", priorHighRsi, recentHighRsi),
                weight = 1
            )
        }
        return null
    }

    private fun computeConfidenceScore(
        side: TradeSide,
        alignedCount: Int,
        summary: IndicatorSummary,
        spreadPips: Double,
        maxSpreadPips: Double
    ): Int {
        var score = 74 + (alignedCount.coerceAtMost(4) * 5)
        val trendMatches = (side == TradeSide.BUY && summary.trendBias == "BULLISH") ||
            (side == TradeSide.SELL && summary.trendBias == "BEARISH")
        if (trendMatches) score += 5
        if ((summary.adx ?: 0.0) >= 20.0) score += 3
        if (spreadPips <= maxSpreadPips * 0.65) score += 3
        return score.coerceIn(72, 98)
    }

    fun buildConfirmedTradeSignal(
        id: String,
        pair: FxPair,
        timeframe: Timeframe,
        eval: SignalEvaluation,
        quote: MarketQuote,
        config: BotConfig,
        currentCapital: Double,
        usdToZarRate: Double,
        confirmationDurationMs: Long,
        createdAt: Long = System.currentTimeMillis()
    ): TradeSignal? {
        val side = eval.candidateSide ?: return null
        val entry = eval.entryPrice ?: return null
        val sl = eval.stopLoss ?: return null
        val tp1 = eval.tp1 ?: return null
        val tp2 = eval.tp2 ?: return null
        val tp3 = eval.tp3 ?: return null
        val slPips = eval.slPips ?: return null
        val tp1Pips = eval.tp1Pips ?: return null
        val tp2Pips = eval.tp2Pips ?: return null
        val tp3Pips = eval.tp3Pips ?: return null

        val lot = computeFlipLotSize(pair, config, currentCapital, slPips, usdToZarRate)
        return TradeSignal(
            id = id,
            pair = pair,
            timeframe = timeframe,
            side = side,
            entryPrice = entry,
            currentPrice = if (side == TradeSide.BUY) quote.bid else quote.ask,
            stopLoss = sl,
            takeProfit1 = tp1,
            takeProfit2 = tp2,
            takeProfit3 = tp3,
            slPips = slPips,
            tp1Pips = tp1Pips,
            tp2Pips = tp2Pips,
            tp3Pips = tp3Pips,
            confidencePercent = eval.confidencePercent,
            recommendedLot = lot,
            strategyName = eval.alignedStrategies.firstOrNull() ?: config.strategy.shortName,
            alignedStrategies = eval.alignedStrategies,
            reason = eval.reason,
            spreadPips = quote.spreadPips,
            rsi = eval.summary.rsi,
            confirmationDurationMs = confirmationDurationMs,
            createdAt = createdAt
        )
    }

    private fun emptyEval(action: String, reason: String, summary: IndicatorSummary): SignalEvaluation {
        return SignalEvaluation(
            candidateSide = null,
            stage = ConfirmationStage.SCANNING,
            actionLabel = action,
            reason = reason,
            alignedStrategies = emptyList(),
            confidencePercent = 0,
            entryPrice = null,
            stopLoss = null,
            tp1 = null,
            tp2 = null,
            tp3 = null,
            slPips = null,
            tp1Pips = null,
            tp2Pips = null,
            tp3Pips = null,
            summary = summary
        )
    }
}
