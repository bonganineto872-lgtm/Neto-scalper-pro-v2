package com.example

import com.example.domain.engine.Mt5BridgeScriptGenerator
import com.example.domain.engine.TechnicalIndicators
import com.example.domain.model.BotConfig
import com.example.domain.model.BrokerCredentials
import com.example.domain.model.Candle
import com.example.domain.model.ConfirmationStage
import com.example.domain.model.FlipCurrency
import com.example.domain.model.FxPair
import com.example.domain.model.GoldBtcStrategyType
import com.example.domain.model.MarketQuote
import com.example.domain.model.SessionFilter
import com.example.domain.model.Timeframe
import com.example.domain.model.TradeDirectionFilter
import com.example.domain.model.TradeSide
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TechnicalIndicatorsTest {

    @Test
    fun `ema series converges upward on rising gold prices`() {
        val prices = (1..30).map { 2650.0 + it * 1.25 }
        val ema9 = TechnicalIndicators.calculateEmaSeries(prices, 9)
        assertEquals(30, ema9.size)
        assertNull(ema9[7])
        assertNotNull(ema9[8])
        assertTrue(ema9.last()!! > ema9[8]!!)
    }

    @Test
    fun `wilder rsi stays bounded and reflects strong bitcoin uptrend`() {
        val prices = (1..25).map { 94000.0 + it * 120.0 }
        val rsi = TechnicalIndicators.calculateRsiSeries(prices, 14)
        val lastRsi = rsi.last()
        assertNotNull(lastRsi)
        assertTrue(lastRsi!! > 70.0 && lastRsi <= 100.0)
    }

    @Test
    fun `spread guard blocks gold signal when spread exceeds maxGoldSpreadPips`() {
        val candles = (1..60).map { i ->
            val p = 2660.0 + i * 0.40
            Candle(i * 300_000L, p - 0.2, p + 0.5, p - 0.4, p, 220.0)
        }
        val wideQuote = MarketQuote(
            pair = FxPair.XAU_USD,
            bid = 2684.00,
            ask = 2684.70, // 7.0 pips spread
            timestamp = 18_000_000L,
            source = "Test"
        )
        val config = BotConfig(maxGoldSpreadPips = 4.5, sessionFilter = SessionFilter.ALL_24H)
        val eval = TechnicalIndicators.evaluatePrecisionSignal(
            pair = FxPair.XAU_USD,
            timeframe = Timeframe.M5,
            candles = candles,
            quote = wideQuote,
            config = config
        )
        assertNull(eval.candidateSide)
        assertEquals("SPREAD GUARD", eval.actionLabel)
    }

    @Test
    fun `smc liquidity sweep triggers confirming buy with clean pips to target on gold M5`() {
        val candles = MutableList(58) { i ->
            val base = 2660.0 + i * 0.12
            Candle(
                timestamp = i * 300_000L,
                open = base - 0.15,
                high = base + 0.85,
                low = base - 0.70,
                close = base + 0.15,
                volume = 200.0
            )
        }
        val recentSwingLow = candles.subList(candles.size - 16, candles.size - 1).minOf { it.low }
        // Previous bar sweeps below swingLow, latest bar displaces strongly bullish above swingLow
        candles.add(
            Candle(
                timestamp = 58 * 300_000L,
                open = recentSwingLow + 0.40,
                high = recentSwingLow + 0.70,
                low = recentSwingLow - 1.20,
                close = recentSwingLow + 0.10,
                volume = 350.0
            )
        )
        candles.add(
            Candle(
                timestamp = 59 * 300_000L,
                open = recentSwingLow + 0.10,
                high = recentSwingLow + 2.40,
                low = recentSwingLow - 0.10,
                close = recentSwingLow + 2.25,
                volume = 480.0
            )
        )

        val lastClose = candles.last().close
        val quote = MarketQuote(
            pair = FxPair.XAU_USD,
            bid = lastClose,
            ask = lastClose + 0.18, // 1.8 pips spread
            timestamp = 60 * 300_000L,
            source = "Binance Spot"
        )
        val config = BotConfig(
            strategy = GoldBtcStrategyType.SMC_LIQUIDITY_SNIPER,
            minConfidencePercent = 74,
            maxGoldSpreadPips = 4.5,
            sessionFilter = SessionFilter.ALL_24H
        )
        val eval = TechnicalIndicators.evaluatePrecisionSignal(
            pair = FxPair.XAU_USD,
            timeframe = Timeframe.M5,
            candles = candles,
            quote = quote,
            config = config
        )

        assertEquals(TradeSide.BUY, eval.candidateSide)
        assertEquals(ConfirmationStage.CONFIRMING, eval.stage)
        assertNotNull(eval.entryPrice)
        assertNotNull(eval.stopLoss)
        assertNotNull(eval.tp1)
        assertNotNull(eval.tp2)
        assertNotNull(eval.tp3)
        assertTrue(eval.tp1Pips!! > 0.0)
        assertTrue(eval.tp2Pips!! > eval.tp1Pips)
        assertTrue(eval.tp3Pips!! > eval.tp2Pips)
        assertTrue(eval.tp1!! > eval.entryPrice!! && eval.stopLoss!! < eval.entryPrice)

        val confirmed = TechnicalIndicators.buildConfirmedTradeSignal(
            id = "sig_test_1",
            pair = FxPair.XAU_USD,
            timeframe = Timeframe.M5,
            eval = eval,
            quote = quote,
            config = config,
            currentCapital = 50.0,
            usdToZarRate = 18.1,
            confirmationDurationMs = 2_000L
        )
        assertNotNull(confirmed)
        val copyText = confirmed!!.formattedCopyText()
        assertTrue(copyText.contains("M5"))
        assertTrue(copyText.contains("BUY"))
        assertTrue(copyText.contains("pips to target"))
    }

    @Test
    fun `direction filter blocks buy setup when set to SELL_ONLY on bitcoin M15`() {
        val candles = MutableList(58) { i ->
            val base = 94000.0 + i * 18.0
            Candle(i * 900_000L, base - 10.0, base + 45.0, base - 40.0, base + 12.0, 150.0)
        }
        val recentSwingLow = candles.subList(candles.size - 16, candles.size - 1).minOf { it.low }
        candles.add(Candle(58 * 900_000L, recentSwingLow + 30.0, recentSwingLow + 60.0, recentSwingLow - 150.0, recentSwingLow + 5.0, 290.0))
        candles.add(Candle(59 * 900_000L, recentSwingLow + 5.0, recentSwingLow + 220.0, recentSwingLow - 10.0, recentSwingLow + 205.0, 410.0))

        val lastClose = candles.last().close
        val quote = MarketQuote(
            pair = FxPair.BTC_USD,
            bid = lastClose,
            ask = lastClose + 8.0,
            timestamp = 60 * 900_000L,
            source = "BTC Test"
        )
        val config = BotConfig(
            strategy = GoldBtcStrategyType.SMC_LIQUIDITY_SNIPER,
            directionFilter = TradeDirectionFilter.SELL_ONLY,
            minConfidencePercent = 74,
            maxBtcSpreadPips = 35.0,
            sessionFilter = SessionFilter.ALL_24H
        )
        val eval = TechnicalIndicators.evaluatePrecisionSignal(
            pair = FxPair.BTC_USD,
            timeframe = Timeframe.M15,
            candles = candles,
            quote = quote,
            config = config
        )
        assertNull(eval.candidateSide)
        assertEquals("SELL-ONLY FILTER", eval.actionLabel)
    }

    @Test
    fun `account flip compounding calculates lot size and steps remaining cleanly`() {
        val config = BotConfig(
            flipCurrency = FlipCurrency.USD,
            startingCapital = 50.0,
            targetFlipCapital = 2500.0,
            riskPercentPerTrade = 5.0,
            autoCompoundFlipLot = true
        )
        val lotSmall = TechnicalIndicators.computeFlipLotSize(FxPair.XAU_USD, config, 50.0, 15.0, 18.1)
        val lotGrown = TechnicalIndicators.computeFlipLotSize(FxPair.XAU_USD, config, 500.0, 15.0, 18.1)
        assertTrue(lotSmall >= 0.01)
        assertTrue(lotGrown > lotSmall)

        val steps = TechnicalIndicators.estimateFlipStepsRemaining(50.0, 2500.0, 5.0)
        assertTrue(steps in 10..60)
    }

    @Test
    fun `mt5 bridge script generator targets gold and bitcoin M5 M15 H1`() {
        val creds = BrokerCredentials(
            mt5BrokerName = "XM Global",
            mt5Server = "XMGlobal-MT5 2",
            mt5Login = "90881122",
            apiToken = "my_secret_token",
            mt5SymbolSuffix = ""
        )
        val script = Mt5BridgeScriptGenerator.generatePythonMt5BridgeScript(creds)
        assertTrue(script.contains("XM Global"))
        assertTrue(script.contains("XMGlobal-MT5 2"))
        assertTrue(script.contains("90881122"))
        assertTrue(script.contains("1m"))
        assertTrue(script.contains("5m"))
        assertTrue(script.contains("15m"))
        assertTrue(script.contains("30m"))
        assertTrue(script.contains("1h"))
        assertTrue(script.contains("4h"))
        assertTrue(script.contains("1d"))
        assertEquals(7, Timeframe.entries.size)
    }

    @Test
    fun `fvg order block detects clean bullish imbalance with tp pips to target on gold`() {
        val candles = MutableList(55) { i ->
            val wave = if (i % 2 == 0) 0.25 else -0.22
            val base = 2655.0 + (i * 0.02) + wave
            Candle(i * 300_000L, base - 0.10, base + 0.35, base - 0.35, base + (if (i % 2 == 0) 0.12 else -0.10), 180.0)
        }
        val last = candles.last().close
        candles.add(Candle(55 * 300_000L, last, last + 0.30, last - 0.25, last - 0.05, 190.0))
        candles.add(Candle(56 * 300_000L, last - 0.05, last + 1.40, last - 0.10, last + 1.25, 320.0))
        candles.add(Candle(57 * 300_000L, last + 1.25, last + 2.30, last + 0.65, last + 2.15, 360.0))

        val quote = MarketQuote(
            pair = FxPair.XAU_USD,
            bid = last + 2.15,
            ask = last + 2.33,
            timestamp = 58 * 300_000L,
            source = "Test"
        )
        val config = BotConfig(
            strategy = GoldBtcStrategyType.FVG_ORDER_BLOCK,
            minConfidencePercent = 74,
            maxGoldSpreadPips = 4.5,
            sessionFilter = SessionFilter.ALL_24H
        )
        val eval = TechnicalIndicators.evaluatePrecisionSignal(
            pair = FxPair.XAU_USD,
            timeframe = Timeframe.M15,
            candles = candles,
            quote = quote,
            config = config
        )
        assertEquals(TradeSide.BUY, eval.candidateSide)
        assertTrue(eval.alignedStrategies.contains(GoldBtcStrategyType.FVG_ORDER_BLOCK.shortName))
        assertTrue((eval.tp1Pips ?: 0.0) > 0.0)
    }
}
