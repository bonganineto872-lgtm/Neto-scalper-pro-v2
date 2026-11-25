package com.example.domain.model

import java.util.Locale
import kotlin.math.abs

enum class FxPair(
    val binanceSymbol: String,
    val mt5Symbol: String,
    val displayName: String,
    val shortTitle: String,
    val baseCurrency: String,
    val quoteCurrency: String,
    val pipSize: Double,
    val decimals: Int,
    val defaultMidPrice: Double,
    val typicalSpreadPips: Double,
    val unitsPerLot: Int
) {
    XAU_USD(
        binanceSymbol = "PAXGUSDT",
        mt5Symbol = "XAUUSD",
        displayName = "XAU/USD",
        shortTitle = "GOLD",
        baseCurrency = "XAU",
        quoteCurrency = "USD",
        pipSize = 0.10,
        decimals = 2,
        defaultMidPrice = 2665.50,
        typicalSpreadPips = 2.0,
        unitsPerLot = 100
    ),
    BTC_USD(
        binanceSymbol = "BTCUSDT",
        mt5Symbol = "BTCUSD",
        displayName = "BTC/USD",
        shortTitle = "BITCOIN",
        baseCurrency = "BTC",
        quoteCurrency = "USD",
        pipSize = 1.0,
        decimals = 2,
        defaultMidPrice = 94250.00,
        typicalSpreadPips = 12.0,
        unitsPerLot = 1
    );

    fun formatPrice(price: Double): String {
        return String.format(Locale.US, "%.${decimals}f", price)
    }

    fun usdValuePerPipPerLot(): Double {
        // XAU/USD: 1 lot = 100 oz, 1 pip = $0.10 -> $10.00 per pip per 1.00 lot
        // BTC/USD: 1 lot = 1 BTC, 1 pip = $1.00 -> $1.00 per pip per 1.00 lot
        return unitsPerLot * pipSize
    }

    fun priceDiffToPips(diff: Double): Double = diff / pipSize
    fun pipsToPriceDiff(pips: Double): Double = pips * pipSize
}

enum class Timeframe(
    val label: String,
    val binanceInterval: String,
    val mt5Timeframe: String,
    val intervalMs: Long
) {
    M1("M1", "1m", "1m", 60_000L),
    M5("M5", "5m", "5m", 300_000L),
    M15("M15", "15m", "15m", 900_000L),
    M30("M30", "30m", "30m", 1_800_000L),
    H1("H1", "1h", "1h", 3_600_000L),
    H4("H4", "4h", "4h", 14_400_000L),
    D1("D1", "1d", "1d", 86_400_000L)
}

enum class GoldBtcStrategyType(
    val title: String,
    val shortName: String,
    val subtitle: String,
    val description: String
) {
    APEX_CONFLUENCE(
        title = "Apex Gold & BTC Confluence",
        shortName = "Apex Confluence",
        subtitle = "All 6 World-Class Strategies Combined",
        description = "Scores SMC Liquidity Sweeps, FVG + Order Blocks, EMA 9/21/50 Pullbacks, VWAP Breakouts, BB+ATR Squeeze, and RSI Divergence together for clean-sheet precision entries."
    ),
    SMC_LIQUIDITY_SNIPER(
        title = "SMC Liquidity Sweep & OB",
        shortName = "SMC Sniper",
        subtitle = "Stop-Hunt + Displacement",
        description = "Detects institutional stop-hunts below/above swing liquidity followed by rapid order-block displacement for minimal-drawdown sniper entries."
    ),
    FVG_ORDER_BLOCK(
        title = "Fair Value Gap + Order Block",
        shortName = "FVG + OB",
        subtitle = "Institutional Imbalance",
        description = "Captures 3-candle Fair Value Gaps paired with institutional Order Block displacement for high-precision intraday Gold & Bitcoin entries."
    ),
    EMA_SNIPER_PULLBACK(
        title = "EMA 9/21/50 Sniper Pullback",
        shortName = "EMA Sniper",
        subtitle = "Trend Pullback Continuation",
        description = "Enters only when EMA 9 > 21 > 50 (or inverse) and price rejects cleanly off the dynamic EMA21 zone with momentum confirmation."
    ),
    VWAP_SESSION_BREAKOUT(
        title = "Session Open + VWAP Impulse",
        shortName = "VWAP Impulse",
        subtitle = "London / NY / Crypto Flow",
        description = "Captures high-velocity intraday pushes when price reclaims VWAP with strong candle body dominance (>55%) and rising ADX."
    ),
    BOLLINGER_ATR_SQUEEZE(
        title = "Bollinger Squeeze + ATR Surge",
        shortName = "BB + ATR Squeeze",
        subtitle = "Volatility Compression Break",
        description = "Spots tight Bollinger Band compression before explosive Gold & Bitcoin range expansion confirmed by ATR surge."
    ),
    RSI_DIVERGENCE_REVERSAL(
        title = "RSI Divergence + Key Reject",
        shortName = "RSI Divergence",
        subtitle = "Exhaustion Snap Reversal",
        description = "Identifies price vs Wilder RSI divergence at intraday swing extremes and confirms reversal candle close."
    )
}

enum class SessionFilter(val label: String, val utcHoursRange: IntRange) {
    ALL_24H("24/7 Gold & Crypto", 0..23),
    LONDON_NY("London + NY (07–21 UTC)", 7..21),
    NY_OVERLAP("LDN/NY Overlap (12–17 UTC)", 12..17),
    ASIAN_TOKYO("Asian Session (00–08 UTC)", 0..8)
}

enum class ConfirmationSpeed(
    val label: String,
    val requiredConfirmTicks: Int,
    val subtitle: String
) {
    ULTRA_FAST("Ultra-Fast (2s)", 2, "Confirms after 2 ticks of directional follow-through"),
    BALANCED("Balanced (4s)", 4, "Confirms after 4 ticks + tight zero-chase check"),
    STRICT("Strict Sniper (6s)", 6, "Deep confirmation against fakeouts & spread spikes")
}

enum class BrokerEnvironment(val label: String) {
    SIGNAL_ENGINE("Live Signal Bot (Zero Paper Trading)"),
    MT5_LIVE("MT5 Live Bridge (Optional Execution)")
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

enum class ConfirmationStage(val label: String) {
    SCANNING("SCANNING"),
    CONFIRMING("CONFIRMING"),
    CONFIRMED("CONFIRMED")
}

enum class BotRunState {
    IDLE,
    SCANNING,
    CONFIRMING,
    SIGNAL_ACTIVE
}

enum class SignalStatus(val label: String) {
    ACTIVE("ACTIVE"),
    TP1_HIT("TP1 HIT • BE LOCKED"),
    TP2_HIT("TP2 HIT"),
    TP3_HIT("TP3 FULL TARGET"),
    SL_HIT("SL HIT"),
    CLOSED("CLOSED")
}

enum class FlipCurrency(val code: String, val symbol: String) {
    USD("USD", "$"),
    ZAR("ZAR", "R")
}

data class TradeSignal(
    val id: String,
    val pair: FxPair,
    val timeframe: Timeframe,
    val side: TradeSide,
    val entryPrice: Double,
    val currentPrice: Double,
    val stopLoss: Double,
    val takeProfit1: Double,
    val takeProfit2: Double,
    val takeProfit3: Double,
    val slPips: Double,
    val tp1Pips: Double,
    val tp2Pips: Double,
    val tp3Pips: Double,
    val confidencePercent: Int,
    val recommendedLot: Double,
    val strategyName: String,
    val alignedStrategies: List<String>,
    val reason: String,
    val spreadPips: Double,
    val rsi: Double?,
    val confirmationDurationMs: Long,
    val maxDrawdownPips: Double = 0.0,
    val breakevenProtected: Boolean = false,
    val createdAt: Long,
    val status: SignalStatus = SignalStatus.ACTIVE
) {
    val pipsMove: Double
        get() {
            val rawDiff = if (side == TradeSide.BUY) currentPrice - entryPrice else entryPrice - currentPrice
            return pair.priceDiffToPips(rawDiff)
        }

    val remainingPipsToTp1: Double
        get() = (tp1Pips - pipsMove).coerceAtLeast(0.0)

    val remainingPipsToTp2: Double
        get() = (tp2Pips - pipsMove).coerceAtLeast(0.0)

    val remainingPipsToTp3: Double
        get() = (tp3Pips - pipsMove).coerceAtLeast(0.0)

    val isCleanSheet: Boolean
        get() = maxDrawdownPips <= pair.typicalSpreadPips * 1.25

    val riskRewardRatio: Double
        get() = if (slPips > 0.0) tp2Pips / slPips else 2.0

    val isLive: Boolean
        get() = status == SignalStatus.ACTIVE || status == SignalStatus.TP1_HIT || status == SignalStatus.TP2_HIT

    fun formattedCopyText(): String {
        val assetLabel = "${pair.shortTitle} (${pair.displayName})"
        return buildString {
            appendLine("NETO MASTER • CONFIRMED ${timeframe.label} SIGNAL")
            appendLine("$assetLabel • ${side.name} • Conf: $confidencePercent%")
            appendLine("Strategy: $strategyName")
            appendLine("Entry: ${pair.formatPrice(entryPrice)}")
            appendLine(String.format(Locale.US, "SL:    %s (-%.1f pips)", pair.formatPrice(stopLoss), slPips))
            appendLine(String.format(Locale.US, "TP1:   %s (+%.1f pips to target)", pair.formatPrice(takeProfit1), tp1Pips))
            appendLine(String.format(Locale.US, "TP2:   %s (+%.1f pips to target)", pair.formatPrice(takeProfit2), tp2Pips))
            appendLine(String.format(Locale.US, "TP3:   %s (+%.1f pips to target)", pair.formatPrice(takeProfit3), tp3Pips))
            append(String.format(Locale.US, "Flip Lot: %.2fL | Confirmed in %.1fs | %s", recommendedLot, confirmationDurationMs / 1000.0, reason))
        }
    }
}

data class TimeframeSignalRow(
    val pair: FxPair,
    val timeframe: Timeframe,
    val stage: ConfirmationStage,
    val side: TradeSide?,
    val entryPrice: Double?,
    val stopLoss: Double?,
    val tp1: Double?,
    val tp2: Double?,
    val tp3: Double?,
    val tp1Pips: Double?,
    val tp2Pips: Double?,
    val tp3Pips: Double?,
    val confidencePercent: Int,
    val strategyTag: String,
    val confirmationProgress: Float = 0f,
    val activeSignal: TradeSignal? = null
)

data class Candle(
    val timestamp: Long,
    val open: Double,
    val high: Double,
    val low: Double,
    val close: Double,
    val volume: Double = 100.0
) {
    val isBullish: Boolean get() = close >= open
    val bodySize: Double get() = abs(close - open)
    val totalRange: Double get() = (high - low).coerceAtLeast(1e-9)
    val bodyRatio: Double get() = bodySize / totalRange
}

data class BollingerPoint(
    val upper: Double,
    val middle: Double,
    val lower: Double
) {
    val bandwidth: Double get() = if (middle > 0.0) (upper - lower) / middle else 0.0
}

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
    val strategy: GoldBtcStrategyType = GoldBtcStrategyType.APEX_CONFLUENCE,
    val directionFilter: TradeDirectionFilter = TradeDirectionFilter.BOTH,
    val confirmationSpeed: ConfirmationSpeed = ConfirmationSpeed.ULTRA_FAST,
    val minConfidencePercent: Int = 82,
    val maxGoldSpreadPips: Double = 4.5,
    val maxBtcSpreadPips: Double = 35.0,
    val riskPercentPerTrade: Double = 5.0,
    val flipCurrency: FlipCurrency = FlipCurrency.USD,
    val startingCapital: Double = 50.0,
    val targetFlipCapital: Double = 2500.0,
    val autoCompoundFlipLot: Boolean = true,
    val baseLotSize: Double = 0.01,
    val breakevenOnTp1: Boolean = true,
    val keepRunningOnPhone: Boolean = true,
    val sessionFilter: SessionFilter = SessionFilter.ALL_24H,
    val showBollingerBands: Boolean = true,
    val showEmaLines: Boolean = true,
    val showVwapLine: Boolean = true
) {
    fun maxSpreadFor(pair: FxPair): Double = when (pair) {
        FxPair.XAU_USD -> maxGoldSpreadPips
        FxPair.BTC_USD -> maxBtcSpreadPips
    }
}

data class AccountFlipPlan(
    val currency: FlipCurrency = FlipCurrency.USD,
    val startingCapital: Double = 50.0,
    val projectedCapital: Double = 50.0,
    val targetCapital: Double = 2500.0,
    val usdToZarRate: Double = 18.12,
    val nextRecommendedLotGold: Double = 0.01,
    val nextRecommendedLotBtc: Double = 0.01,
    val estimatedStepsRemaining: Int = 12,
    val cleanSheetWinRate: Double = 0.0,
    val totalConfirmedSignals: Int = 0,
    val tpHitsCount: Int = 0,
    val zeroDrawdownCount: Int = 0,
    val netPipsCaptured: Double = 0.0,
    val spotFeedSource: String = "Connecting Live Feed...",
    val lastSyncTime: Long? = null
) {
    val progressFraction: Float
        get() {
            val span = (targetCapital - startingCapital).coerceAtLeast(1.0)
            return ((projectedCapital - startingCapital) / span).toFloat().coerceIn(0f, 1f)
        }

    val multiplier: Double
        get() = if (startingCapital > 0.0) (projectedCapital / startingCapital).coerceAtLeast(1.0) else 1.0

    fun formatMoney(amount: Double, signed: Boolean = false): String {
        return if (signed) {
            val prefix = if (amount >= 0) "+" else "-"
            String.format(Locale.US, "%s%s%,.2f", prefix, currency.symbol, abs(amount))
        } else {
            String.format(Locale.US, "%s%,.2f", currency.symbol, amount)
        }
    }
}

data class SignalHistoryRecord(
    val id: Long = 0,
    val signalId: String,
    val pair: FxPair,
    val timeframe: Timeframe,
    val side: TradeSide,
    val entryPrice: Double,
    val exitPrice: Double,
    val stopLoss: Double,
    val tp1: Double,
    val tp2: Double,
    val tp3: Double,
    val tp1Pips: Double,
    val tp2Pips: Double,
    val tp3Pips: Double,
    val capturedPips: Double,
    val maxDrawdownPips: Double,
    val confidencePercent: Int,
    val strategyName: String,
    val outcome: SignalStatus,
    val createdAt: Long,
    val closedAt: Long
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
    val isConfirmedSignal: Boolean,
    val side: TradeSide? = null
)

data class IndicatorSummary(
    val fastEma: Double? = null,
    val slowEma: Double? = null,
    val trendEma50: Double? = null,
    val vwap: Double? = null,
    val rsi: Double? = null,
    val adx: Double? = null,
    val bbUpper: Double? = null,
    val bbMiddle: Double? = null,
    val bbLower: Double? = null,
    val atrPips: Double? = null,
    val trendBias: String = "NEUTRAL"
)

enum class AlertType {
    NEW_SIGNAL,
    TP1_HIT,
    TP2_HIT,
    TP3_HIT,
    FLIP_TARGET_REACHED
}

data class BotAlertEvent(
    val id: String,
    val type: AlertType,
    val title: String,
    val message: String,
    val pair: FxPair,
    val timeframe: Timeframe,
    val timestamp: Long,
    val pips: Double? = null,
    val side: TradeSide? = null
)

enum class Mt5BridgeType(val label: String, val subtitle: String) {
    META_API("MetaApi Cloud", "Cloud REST token"),
    CUSTOM_VPS("Self-Hosted VPS API", "Your own Python MT5 bridge")
}

data class BrokerCredentials(
    val environment: BrokerEnvironment = BrokerEnvironment.SIGNAL_ENGINE,
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
    val isMt5Configured: Boolean
        get() = environment == BrokerEnvironment.MT5_LIVE &&
            ((mt5BridgeType == Mt5BridgeType.META_API && apiToken.isNotBlank() && accountId.isNotBlank()) ||
                (mt5BridgeType == Mt5BridgeType.CUSTOM_VPS && mt5BridgeUrl.isNotBlank() && apiToken.isNotBlank()))
}

enum class Mt5BrokerPreset(
    val brokerName: String,
    val defaultServers: List<String>,
    val defaultSuffix: String
) {
    XM_GLOBAL("XM Global", listOf("XMGlobal-MT5", "XMGlobal-MT5 2", "XMGlobal-MT5 3"), ""),
    EXNESS("Exness", listOf("Exness-MT5Real", "Exness-MT5Real2"), "m"),
    DERIV("Deriv", listOf("Deriv-Server", "Deriv-Server-02"), ""),
    HFM("HFM", listOf("HFMarketsSA-Live2", "HFMarkets-Live"), "")
}
