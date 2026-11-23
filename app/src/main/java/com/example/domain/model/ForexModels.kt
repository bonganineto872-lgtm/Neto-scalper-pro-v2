package com.example.domain.model

import java.util.Locale
import kotlin.math.abs

enum class FxPair(
    val oandaInstrument: String,
    val mt5Symbol: String,
    val displayName: String,
    val baseCurrency: String,
    val quoteCurrency: String,
    val pipSize: Double,
    val decimals: Int,
    val defaultMidPrice: Double,
    val typicalSpreadPips: Double,
    val unitsPerLot: Int = 100_000
) {
    XAU_USD("XAU_USD", "XAUUSD", "XAU/USD", "XAU", "USD", 0.10, 2, 2665.50, 2.1, unitsPerLot = 100),
    EUR_USD("EUR_USD", "EURUSD", "EUR/USD", "EUR", "USD", 0.0001, 5, 1.08450, 0.8),
    GBP_USD("GBP_USD", "GBPUSD", "GBP/USD", "GBP", "USD", 0.0001, 5, 1.26820, 1.1),
    USD_JPY("USD_JPY", "USDJPY", "USD/JPY", "USD", "JPY", 0.01, 3, 154.350, 0.9),
    USD_ZAR("USD_ZAR", "USDZAR", "USD/ZAR", "USD", "ZAR", 0.0001, 5, 18.12000, 1.8),
    AUD_USD("AUD_USD", "AUDUSD", "AUD/USD", "AUD", "USD", 0.0001, 5, 0.65420, 1.0),
    USD_CAD("USD_CAD", "USDCAD", "USD/CAD", "USD", "CAD", 0.0001, 5, 1.39650, 1.2),
    EUR_GBP("EUR_GBP", "EURGBP", "EUR/GBP", "EUR", "GBP", 0.0001, 5, 0.85510, 1.3),
    EUR_JPY("EUR_JPY", "EURJPY", "EUR/JPY", "EUR", "JPY", 0.01, 3, 167.380, 1.4);

    fun formatPrice(price: Double): String {
        return String.format(Locale.US, "%.${decimals}f", price)
    }

    /**
     * Approximate USD value per 1 pip for 1.00 standard lot
     * (100,000 units for FX; 100 troy oz * $0.10/pip = $10.00/pip for Gold XAU/USD)
     */
    fun usdPipValuePerStandardLot(currentMid: Double): Double {
        return when (quoteCurrency) {
            "USD" -> 10.0
            "JPY" -> (0.01 / currentMid.coerceAtLeast(1.0)) * 100_000.0
            "ZAR" -> (0.0001 / currentMid.coerceAtLeast(1.0)) * 100_000.0
            "CAD" -> (0.0001 / currentMid.coerceAtLeast(0.5)) * 100_000.0
            "GBP" -> 12.68
            else -> 10.0
        }
    }

    fun zarPipValuePerStandardLot(currentMid: Double, usdToZarRate: Double): Double {
        return if (quoteCurrency == "ZAR") {
            10.0 // 0.0001 * 100,000 = 10 ZAR per pip per standard lot
        } else {
            usdPipValuePerStandardLot(currentMid) * usdToZarRate
        }
    }

    fun priceDiffToPips(diff: Double): Double = diff / pipSize
    fun pipsToPriceDiff(pips: Double): Double = pips * pipSize
}

enum class Timeframe(
    val label: String,
    val oandaGranularity: String,
    val mt5Timeframe: String,
    val intervalMs: Long
) {
    M1("1m", "M1", "1m", 60_000L),
    M5("5m", "M5", "5m", 300_000L),
    M15("15m", "M15", "15m", 900_000L)
}

enum class ScalpingStrategyType(
    val title: String,
    val subtitle: String,
    val description: String
) {
    EMA_RSI_MOMENTUM(
        title = "EMA + RSI Momentum",
        subtitle = "1m Trend Pyramid Scalp",
        description = "Opens on fast/slow EMA crossover with RSI confirmation, then scales up to 4 positions as M1 price moves favorably."
    ),
    BOLLINGER_MEAN_REVERSION(
        title = "Bollinger Mean Reversion",
        subtitle = "1m Band Exhaustion Fade",
        description = "Fades sharp deviations outside Bollinger Bands (2.0σ) when RSI confirms oversold/overbought extremes."
    ),
    ATR_VOLATILITY_BREAKOUT(
        title = "ATR Volatility Breakout",
        subtitle = "1m Range Squeeze Trigger",
        description = "Captures high-velocity 1-minute breakouts above/below the prior 8-bar range and pyramids with momentum."
    )
}

enum class SessionFilter(val label: String, val utcHoursRange: IntRange) {
    ALL_24H("24/5 Continuous", 0..23),
    LONDON_OPEN("London (07–16 UTC)", 7..16),
    NY_OVERLAP("LDN/NY Overlap (12–17 UTC)", 12..17),
    TOKYO_ASIAN("Tokyo (00–08 UTC)", 0..8)
}

enum class BrokerEnvironment(val label: String, val baseUrl: String) {
    PAPER_SIMULATION("Paper Simulation (R200 Flip)", ""),
    MT5_DEMO("MT5 Demo (Any Broker / XM Global)", "https://mt-client-api-v1.agiliumtrade.agiliumtrade.ai/"),
    MT5_LIVE("MT5 Live (Any Broker / XM Global)", "https://mt-client-api-v1.agiliumtrade.agiliumtrade.ai/"),
    OANDA_PRACTICE("OANDA Practice API", "https://api-fxpractice.oanda.com/v3/"),
    OANDA_LIVE("OANDA Live API", "https://api-fxtrade.oanda.com/v3/")
}

enum class Mt5BridgeType(val label: String, val subtitle: String) {
    META_API("MetaApi Cloud MT5", "Zero-VPS cloud MT5 REST gateway"),
    CUSTOM_VPS_BRIDGE("Custom MT5 VPS Bridge", "Direct HTTP REST to your MT5 terminal")
}

enum class Mt5BrokerPreset(
    val brokerName: String,
    val defaultServers: List<String>,
    val defaultSuffix: String
) {
    XM_GLOBAL(
        brokerName = "XM Global",
        defaultServers = listOf(
            "XMGlobal-MT5",
            "XMGlobal-MT5 2",
            "XMGlobal-MT5 3",
            "XMGlobal-MT5 4",
            "XMGlobal-Demo"
        ),
        defaultSuffix = ""
    ),
    EXNESS(
        brokerName = "Exness",
        defaultServers = listOf("Exness-MT5Trial", "Exness-MT5Real", "Exness-MT5Real2"),
        defaultSuffix = "m"
    ),
    DERIV(
        brokerName = "Deriv",
        defaultServers = listOf("Deriv-Demo", "Deriv-Server", "Deriv-Server-02"),
        defaultSuffix = ""
    ),
    HFM(
        brokerName = "HF Markets (HFM)",
        defaultServers = listOf("HFMarketsSA-Demo", "HFMarketsSA-Live2", "HFMarketsGlobal-Live"),
        defaultSuffix = ""
    ),
    CUSTOM(
        brokerName = "Any MT5 Broker",
        defaultServers = listOf("BrokerName-MT5-Live", "BrokerName-MT5-Demo"),
        defaultSuffix = ""
    )
}

enum class TradeSide {
    BUY,
    SELL
}

enum class TradeDirectionFilter(val label: String, val shortLabel: String) {
    BOTH("Buy + Sell", "2-Way"),
    BUY_ONLY("Buy Only", "Long Only"),
    SELL_ONLY("Sell Only", "Short Only")
}

enum class TradingRiskProfile(
    val title: String,
    val subtitle: String,
    val maxOpenTrades: Int,
    val scaleInOnProfit: Boolean,
    val scaleInStepPips: Double,
    val stopLossPips: Double,
    val takeProfitPips: Double,
    val maxSpreadPips: Double,
    val autoCompoundSizing: Boolean
) {
    AGGRESSIVE_1M_FLIP(
        title = "1m Flip Mode",
        subtitle = "4x pyramid • +2.5p step • R200→R10k",
        maxOpenTrades = 4,
        scaleInOnProfit = true,
        scaleInStepPips = 2.5,
        stopLossPips = 6.0,
        takeProfitPips = 12.0,
        maxSpreadPips = 3.5,
        autoCompoundSizing = true
    ),
    BALANCED_SCALP(
        title = "Balanced Scalp",
        subtitle = "3x pyramid • +4.0p step • 8p/16p",
        maxOpenTrades = 3,
        scaleInOnProfit = true,
        scaleInStepPips = 4.0,
        stopLossPips = 8.0,
        takeProfitPips = 16.0,
        maxSpreadPips = 3.0,
        autoCompoundSizing = true
    ),
    CONSERVATIVE_SNIPER(
        title = "Sniper Guard",
        subtitle = "2x max • +5.0p step • Tight spread",
        maxOpenTrades = 2,
        scaleInOnProfit = true,
        scaleInStepPips = 5.0,
        stopLossPips = 7.0,
        takeProfitPips = 18.0,
        maxSpreadPips = 2.2,
        autoCompoundSizing = false
    )
}

enum class BotRunState {
    IDLE,
    SCANNING,
    IN_POSITION,
    CIRCUIT_BREAKER_HALTED
}

data class Candle(
    val timestamp: Long,
    val open: Double,
    val high: Double,
    val low: Double,
    val close: Double,
    val volume: Int = 100
) {
    val isBullish: Boolean get() = close >= open
}

data class BollingerPoint(
    val upper: Double,
    val middle: Double,
    val lower: Double
)

data class MarketQuote(
    val pair: FxPair,
    val bid: Double,
    val ask: Double,
    val timestamp: Long,
    val source: String
) {
    val mid: Double get() = (bid + ask) / 2.0
    val spreadPips: Double get() = abs(ask - bid) / pair.pipSize
}

data class BotConfig(
    val strategy: ScalpingStrategyType = ScalpingStrategyType.EMA_RSI_MOMENTUM,
    val directionFilter: TradeDirectionFilter = TradeDirectionFilter.BOTH,
    val riskProfile: TradingRiskProfile = TradingRiskProfile.AGGRESSIVE_1M_FLIP,
    val fastEmaPeriod: Int = 9,
    val slowEmaPeriod: Int = 21,
    val rsiPeriod: Int = 14,
    val rsiOversold: Int = 32,
    val rsiOverbought: Int = 68,
    val lotSize: Double = 0.02,
    val autoCompoundSizing: Boolean = true,
    val stopLossPips: Double = 6.0,
    val takeProfitPips: Double = 12.0,
    val maxSpreadPips: Double = 3.5,
    val maxOpenTrades: Int = 4,
    val scaleInOnProfit: Boolean = true,
    val scaleInStepPips: Double = 2.5,
    val dailyLossLimitZar: Double = 180.0,
    val trailingStopEnabled: Boolean = true,
    val trailingTriggerPips: Double = 4.5,
    val breakevenLockEnabled: Boolean = true,
    val breakevenTriggerPips: Double = 3.5,
    val targetProfitStopZar: Double = 10_000.0,
    val stopOnTargetReached: Boolean = true,
    val keepRunningOnPhone: Boolean = false,
    val sessionFilter: SessionFilter = SessionFilter.ALL_24H,
    val showBollingerBands: Boolean = true,
    val showEmaLines: Boolean = true
) {
    val riskRewardRatio: Double
        get() = if (stopLossPips > 0.0) takeProfitPips / stopLossPips else 0.0

    fun withRiskProfile(profile: TradingRiskProfile): BotConfig = copy(
        riskProfile = profile,
        maxOpenTrades = profile.maxOpenTrades,
        scaleInOnProfit = profile.scaleInOnProfit,
        scaleInStepPips = profile.scaleInStepPips,
        stopLossPips = profile.stopLossPips,
        takeProfitPips = profile.takeProfitPips,
        maxSpreadPips = profile.maxSpreadPips,
        autoCompoundSizing = profile.autoCompoundSizing
    )
}

data class BrokerCredentials(
    val environment: BrokerEnvironment = BrokerEnvironment.PAPER_SIMULATION,
    val apiToken: String = "",
    val accountId: String = "",
    val mt5BrokerName: String = "XM Global",
    val mt5Server: String = "XMGlobal-MT5",
    val mt5Login: String = "",
    val mt5BridgeType: Mt5BridgeType = Mt5BridgeType.META_API,
    val mt5BridgeUrl: String = "",
    val mt5SymbolSuffix: String = "",
    val localApiEnabled: Boolean = true,
    val localApiPort: Int = 8787,
    val localApiKey: String = ""
) {
    val isOandaConfigured: Boolean
        get() = (environment == BrokerEnvironment.OANDA_PRACTICE || environment == BrokerEnvironment.OANDA_LIVE) &&
            apiToken.isNotBlank() &&
            accountId.isNotBlank()

    val isMt5Selected: Boolean
        get() = environment == BrokerEnvironment.MT5_DEMO || environment == BrokerEnvironment.MT5_LIVE

    val isMt5Configured: Boolean
        get() = isMt5Selected && when (mt5BridgeType) {
            Mt5BridgeType.META_API -> apiToken.isNotBlank() && accountId.isNotBlank()
            Mt5BridgeType.CUSTOM_VPS_BRIDGE -> mt5BridgeUrl.isNotBlank()
        }
}

data class TradePosition(
    val id: String,
    val pair: FxPair,
    val side: TradeSide,
    val lotSize: Double,
    val entryPrice: Double,
    val currentPrice: Double,
    val stopLoss: Double,
    val takeProfit: Double,
    val trailingStopPrice: Double? = null,
    val openedAt: Long,
    val strategyName: String,
    val pyramidLayer: Int = 1,
    val usdToZarRate: Double = 18.12,
    val isLiveBroker: Boolean = false,
    val brokerTradeId: String? = null
) {
    val pipsPnl: Double
        get() {
            val rawDiff = if (side == TradeSide.BUY) currentPrice - entryPrice else entryPrice - currentPrice
            return pair.priceDiffToPips(rawDiff)
        }

    val unrealizedPnlAccount: Double
        get() = if (isLiveBroker) {
            pipsPnl * lotSize * pair.usdPipValuePerStandardLot(currentPrice)
        } else {
            pipsPnl * lotSize * pair.zarPipValuePerStandardLot(currentPrice, usdToZarRate)
        }

    val effectiveStopLoss: Double
        get() = trailingStopPrice ?: stopLoss
}

data class ClosedTradeRecord(
    val id: Long = 0,
    val pair: FxPair,
    val side: TradeSide,
    val lotSize: Double,
    val entryPrice: Double,
    val exitPrice: Double,
    val pipsPnl: Double,
    val realizedPnlZar: Double,
    val strategyName: String,
    val closeReason: String,
    val openedAt: Long,
    val closedAt: Long,
    val environmentLabel: String
)

data class SignalLogEntry(
    val id: String,
    val timestamp: Long,
    val pair: FxPair,
    val timeframe: Timeframe,
    val action: String,
    val reason: String,
    val price: Double,
    val spreadPips: Double,
    val rsi: Double?,
    val isExecution: Boolean,
    val side: TradeSide? = null
)

data class EquityPoint(
    val timestamp: Long,
    val equity: Double
)

data class AccountSummary(
    val balance: Double = 200.0,
    val equity: Double = 200.0,
    val startingBalance: Double = 200.0,
    val targetFlipBalance: Double = 10_000.0,
    val unrealizedPnl: Double = 0.0,
    val realizedTodayPnl: Double = 0.0,
    val currency: String = "ZAR",
    val usdToZarRate: Double = 18.12,
    val marginUsed: Double = 0.0,
    val freeMargin: Double = 200.0,
    val brokerStatusText: String = "Paper Flip Engine (R200 → R10,000)",
    val lastSpotSyncTime: Long? = null,
    val spotFeedSource: String = "Connecting..."
) {
    val flipProgressFraction: Float
        get() {
            val span = (targetFlipBalance - startingBalance).coerceAtLeast(1.0)
            return ((equity - startingBalance) / span).toFloat().coerceIn(0f, 1f)
        }

    val flipMultiplier: Double
        get() = if (startingBalance > 0.0) (equity / startingBalance).coerceAtLeast(0.0) else 1.0

    fun formatMoney(amount: Double, signed: Boolean = false): String {
        val symbol = if (currency == "ZAR") "R" else "$"
        return if (signed) {
            val prefix = if (amount >= 0) "+" else "-"
            String.format(Locale.US, "%s%s%,.2f", prefix, symbol, abs(amount))
        } else {
            String.format(Locale.US, "%s%,.2f", symbol, amount)
        }
    }
}

data class IndicatorSummary(
    val fastEma: Double? = null,
    val slowEma: Double? = null,
    val rsi: Double? = null,
    val bbUpper: Double? = null,
    val bbMiddle: Double? = null,
    val bbLower: Double? = null,
    val atrPips: Double? = null,
    val trendBias: String = "NEUTRAL"
)

enum class AlertType {
    PYRAMID_LAYER_OPENED,
    TAKE_PROFIT_HIT,
    FLIP_TARGET_REACHED
}

data class BotAlertEvent(
    val id: String,
    val type: AlertType,
    val title: String,
    val message: String,
    val pair: FxPair,
    val timestamp: Long,
    val profitZar: Double? = null,
    val layer: Int? = null
)
