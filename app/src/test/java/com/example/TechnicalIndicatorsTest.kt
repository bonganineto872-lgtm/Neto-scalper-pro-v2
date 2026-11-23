package com.example

import com.example.domain.engine.Mt5BridgeScriptGenerator
import com.example.domain.engine.TechnicalIndicators
import com.example.domain.model.BotConfig
import com.example.domain.model.BrokerCredentials
import com.example.domain.model.Candle
import com.example.domain.model.FxPair
import com.example.domain.model.MarketQuote
import com.example.domain.model.ScalpingStrategyType
import com.example.domain.model.SessionFilter
import com.example.domain.model.TradeDirectionFilter
import com.example.domain.model.TradePosition
import com.example.domain.model.TradeSide
import com.example.domain.model.TradingRiskProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TechnicalIndicatorsTest {

    @Test
    fun `ema series converges upward on rising prices`() {
        val prices = (1..30).map { 1.0800 + it * 0.0005 }
        val ema9 = TechnicalIndicators.calculateEmaSeries(prices, 9)
        assertEquals(30, ema9.size)
        assertNull(ema9[7])
        assertNotNull(ema9[8])
        assertTrue(ema9.last()!! > ema9[8]!!)
    }

    @Test
    fun `wilder rsi stays bounded and reflects strong uptrend`() {
        val prices = (1..25).map { 1.0800 + it * 0.0010 }
        val rsi = TechnicalIndicators.calculateRsiSeries(prices, 14)
        val lastRsi = rsi.last()
        assertNotNull(lastRsi)
        assertTrue(lastRsi!! > 70.0 && lastRsi <= 100.0)
    }

    @Test
    fun `spread guard blocks trade when spread exceeds maxSpreadPips`() {
        val candles = (1..35).map { i ->
            val p = 1.0800 + i * 0.0002
            Candle(i * 60_000L, p - 0.0001, p + 0.0002, p - 0.0002, p)
        }
        val wideQuote = MarketQuote(
            pair = FxPair.EUR_USD,
            bid = 1.08680,
            ask = 1.08720,
            timestamp = 2_100_000L,
            source = "Test"
        )
        val config = BotConfig(maxSpreadPips = 1.5, sessionFilter = SessionFilter.ALL_24H)
        val eval = TechnicalIndicators.evaluateScalpingSignal(FxPair.EUR_USD, candles, wideQuote, config)
        assertNull(eval.signal)
        assertEquals("SPREAD GUARD", eval.actionLabel)
    }

    @Test
    fun `bollinger mean reversion triggers buy on oversold band pierce`() {
        val base = MutableList(30) { i ->
            Candle(i * 60_000L, 1.0850, 1.0855, 1.0845, 1.0850 + (if (i % 2 == 0) 0.0002 else -0.0002))
        }
        for (k in 1..5) {
            val close = 1.0850 - k * 0.0018
            base.add(Candle((30 + k) * 60_000L, close + 0.0010, close + 0.0012, close - 0.0002, close))
        }
        val quote = MarketQuote(
            pair = FxPair.EUR_USD,
            bid = 1.07595,
            ask = 1.07604,
            timestamp = 3_000_000L,
            source = "Test"
        )
        val config = BotConfig(
            strategy = ScalpingStrategyType.BOLLINGER_MEAN_REVERSION,
            rsiOversold = 40,
            maxSpreadPips = 2.0,
            sessionFilter = SessionFilter.ALL_24H
        )
        val eval = TechnicalIndicators.evaluateScalpingSignal(FxPair.EUR_USD, base, quote, config)
        assertEquals(TradeSide.BUY, eval.signal)
    }

    @Test
    fun `pyramid scale in adds up to 4 positions when market moves favorably`() {
        val config = BotConfig(maxOpenTrades = 4, scaleInOnProfit = true, scaleInStepPips = 2.5)
        val pos1 = TradePosition(
            id = "p1",
            pair = FxPair.EUR_USD,
            side = TradeSide.BUY,
            lotSize = 0.02,
            entryPrice = 1.08500,
            currentPrice = 1.08530,
            stopLoss = 1.08440,
            takeProfit = 1.08620,
            openedAt = 1_000L,
            strategyName = "Test",
            pyramidLayer = 1
        )
        val quoteMovedUp = MarketQuote(
            pair = FxPair.EUR_USD,
            bid = 1.08530,
            ask = 1.08538,
            timestamp = 2_000L,
            source = "Test"
        )
        val pyramid = TechnicalIndicators.evaluatePyramidScaleIn(FxPair.EUR_USD, quoteMovedUp, listOf(pos1), config)
        assertNotNull(pyramid)
        assertEquals(TradeSide.BUY, pyramid!!.side)
        assertEquals(2, pyramid.nextLayer)

        val fourPositions = (1..4).map { layer ->
            pos1.copy(id = "p$layer", pyramidLayer = layer, openedAt = layer * 1_000L)
        }
        val capped = TechnicalIndicators.evaluatePyramidScaleIn(FxPair.EUR_USD, quoteMovedUp, fourPositions, config)
        assertNull(capped)
    }

    @Test
    fun `gold XAU_USD computes 0_10 pip increments and pyramids on favorable move`() {
        assertEquals(25.0, FxPair.XAU_USD.priceDiffToPips(2.50), 0.001)
        assertEquals("2665.50", FxPair.XAU_USD.formatPrice(2665.50))
        assertEquals(100, FxPair.XAU_USD.unitsPerLot)

        val config = BotConfig(maxOpenTrades = 4, scaleInOnProfit = true, scaleInStepPips = 2.5, maxSpreadPips = 3.5)
        val goldPos1 = TradePosition(
            id = "g1",
            pair = FxPair.XAU_USD,
            side = TradeSide.BUY,
            lotSize = 0.02,
            entryPrice = 2665.00,
            currentPrice = 2665.30,
            stopLoss = 2664.40,
            takeProfit = 2666.20,
            openedAt = 1_000L,
            strategyName = "Gold Scalp",
            pyramidLayer = 1
        )
        val goldQuote = MarketQuote(
            pair = FxPair.XAU_USD,
            bid = 2665.30, // +3.0 pips favorable ($0.30)
            ask = 2665.52, // 2.2 pips spread
            timestamp = 2_000L,
            source = "Gold Test"
        )
        val pyramid = TechnicalIndicators.evaluatePyramidScaleIn(FxPair.XAU_USD, goldQuote, listOf(goldPos1), config)
        assertNotNull(pyramid)
        assertEquals(TradeSide.BUY, pyramid!!.side)
        assertEquals(2, pyramid.nextLayer)
    }

    @Test
    fun `direction filter blocks buy signals when set to SELL_ONLY`() {
        val base = MutableList(30) { i ->
            Candle(i * 60_000L, 1.0850, 1.0855, 1.0845, 1.0850 + (if (i % 2 == 0) 0.0002 else -0.0002))
        }
        for (k in 1..5) {
            val close = 1.0850 - k * 0.0018
            base.add(Candle((30 + k) * 60_000L, close + 0.0010, close + 0.0012, close - 0.0002, close))
        }
        val quote = MarketQuote(
            pair = FxPair.EUR_USD,
            bid = 1.07595,
            ask = 1.07604,
            timestamp = 3_000_000L,
            source = "Test"
        )
        val config = BotConfig(
            strategy = ScalpingStrategyType.BOLLINGER_MEAN_REVERSION,
            directionFilter = TradeDirectionFilter.SELL_ONLY,
            rsiOversold = 40,
            maxSpreadPips = 2.0,
            sessionFilter = SessionFilter.ALL_24H
        )
        val eval = TechnicalIndicators.evaluateScalpingSignal(FxPair.EUR_USD, base, quote, config)
        assertNull(eval.signal)
        assertEquals("SELL-ONLY FILTER", eval.actionLabel)
    }

    @Test
    fun `risk profile preset updates pyramid count and SL TP cleanly`() {
        val sniper = BotConfig().withRiskProfile(TradingRiskProfile.CONSERVATIVE_SNIPER)
        assertEquals(TradingRiskProfile.CONSERVATIVE_SNIPER, sniper.riskProfile)
        assertEquals(2, sniper.maxOpenTrades)
        assertEquals(7.0, sniper.stopLossPips, 0.001)
        assertEquals(18.0, sniper.takeProfitPips, 0.001)
    }

    @Test
    fun `mt5 bridge script generator injects XM Global server and endpoints`() {
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
        assertTrue(script.contains("/mt5/account"))
        assertTrue(script.contains("/mt5/order"))
        assertTrue(script.contains("/mt5/close"))
    }
}
