package com.example.data.repository

import com.example.data.local.BotPreferencesRepository
import com.example.data.local.TradeHistoryLocalSource
import com.example.data.remote.CustomMt5CloseRequest
import com.example.data.remote.CustomMt5OrderRequest
import com.example.data.remote.LocalApiStatus
import com.example.data.remote.LocalTradingApiServer
import com.example.data.remote.Mt5MetaApiTradeRequest
import com.example.data.remote.NetworkModule
import com.example.data.remote.OandaMarketOrderBody
import com.example.data.remote.OandaOrderRequest
import com.example.data.remote.OandaPriceSpec
import com.example.domain.engine.SignalEvaluation
import com.example.domain.engine.TechnicalIndicators
import com.example.domain.model.AccountSummary
import com.example.domain.model.AlertType
import com.example.domain.model.BotAlertEvent
import com.example.domain.model.BotConfig
import com.example.domain.model.BotRunState
import com.example.domain.model.BrokerCredentials
import com.example.domain.model.BrokerEnvironment
import com.example.domain.model.Candle
import com.example.domain.model.ClosedTradeRecord
import com.example.domain.model.EquityPoint
import com.example.domain.model.FxPair
import com.example.domain.model.IndicatorSummary
import com.example.domain.model.MarketQuote
import com.example.domain.model.Mt5BridgeType
import com.example.domain.model.SignalLogEntry
import com.example.domain.model.Timeframe
import com.example.domain.model.TradePosition
import com.example.domain.model.TradeSide
import com.example.domain.model.TradingRiskProfile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.UUID
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

data class LiveMarketSnapshot(
    val pair: FxPair = FxPair.XAU_USD,
    val timeframe: Timeframe = Timeframe.M1,
    val quote: MarketQuote = MarketQuote(
        pair = FxPair.XAU_USD,
        bid = 2665.39,
        ask = 2665.61,
        timestamp = System.currentTimeMillis(),
        source = "Initializing..."
    ),
    val candles: List<Candle> = emptyList(),
    val indicators: IndicatorSummary = IndicatorSummary(),
    val lastEvaluation: SignalEvaluation? = null
)

class ScalpingBotRepository(
    private val prefs: BotPreferencesRepository,
    private val localSource: TradeHistoryLocalSource
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val spotAnchors = MutableStateFlow<Map<FxPair, Double>>(emptyMap())
    private val usdToZarRate = MutableStateFlow(18.12)
    private val _spotFeedStatus = MutableStateFlow("Syncing Spot FX..." to (null as Long?))

    private val _marketSnapshot = MutableStateFlow(LiveMarketSnapshot())
    val marketSnapshot: StateFlow<LiveMarketSnapshot> = _marketSnapshot.asStateFlow()

    private val _openPositions = MutableStateFlow<List<TradePosition>>(emptyList())
    val openPositions: StateFlow<List<TradePosition>> = _openPositions.asStateFlow()

    private val _signalLogs = MutableStateFlow<List<SignalLogEntry>>(emptyList())
    val signalLogs: StateFlow<List<SignalLogEntry>> = _signalLogs.asStateFlow()

    private val _botRunState = MutableStateFlow(BotRunState.IDLE)
    val botRunState: StateFlow<BotRunState> = _botRunState.asStateFlow()

    private val _accountSummary = MutableStateFlow(AccountSummary())
    val accountSummary: StateFlow<AccountSummary> = _accountSummary.asStateFlow()

    private val _brokerTestMessage = MutableStateFlow<Pair<Boolean, String>?>(null)
    val brokerTestMessage: StateFlow<Pair<Boolean, String>?> = _brokerTestMessage.asStateFlow()

    private val _localApiStatus = MutableStateFlow(LocalApiStatus())
    val localApiStatus: StateFlow<LocalApiStatus> = _localApiStatus.asStateFlow()

    private val _liveEquityTicks = MutableStateFlow<List<EquityPoint>>(emptyList())
    val liveEquityTicks: StateFlow<List<EquityPoint>> = _liveEquityTicks.asStateFlow()

    private val _activeAlertBanner = MutableStateFlow<BotAlertEvent?>(null)
    val activeAlertBanner: StateFlow<BotAlertEvent?> = _activeAlertBanner.asStateFlow()

    private val _alertHistory = MutableStateFlow<List<BotAlertEvent>>(emptyList())
    val alertHistory: StateFlow<List<BotAlertEvent>> = _alertHistory.asStateFlow()

    val tradeHistory: Flow<List<ClosedTradeRecord>> = localSource.tradeHistory
    val equityCurve: Flow<List<EquityPoint>> = localSource.equityCurve
    val botConfig: Flow<BotConfig> = prefs.botConfig
    val brokerCredentials: Flow<BrokerCredentials> = prefs.brokerCredentials
    val selectedPair: Flow<FxPair> = prefs.selectedPair
    val selectedTimeframe: Flow<Timeframe> = prefs.selectedTimeframe

    private val candleBuffers = mutableMapOf<Pair<FxPair, Timeframe>, MutableList<Candle>>()
    private var lastAutoTradeTimestampMs: Long = 0L
    private var lastScanLogTimestampMs: Long = 0L
    private var tickCounter: Int = 0
    private var engineLoopJob: Job? = null
    private var alertAutoDismissJob: Job? = null
    private val random = Random(System.currentTimeMillis())

    private val localApiServer = LocalTradingApiServer(
        getSnapshotQuote = { _marketSnapshot.value.quote },
        getAccountSummary = { _accountSummary.value },
        getBotState = { _botRunState.value },
        getOpenPositions = { _openPositions.value },
        onToggleBot = { toggleBot() },
        onPlaceManualOrder = { side -> placeManualOrder(side) },
        onFlattenAll = { emergencyFlattenAll() },
        onStatusChanged = { status -> _localApiStatus.value = status }
    )

    init {
        scope.launch {
            val initialBalance = prefs.paperBalance.first()
            localSource.seedInitialEquityIfEmpty(initialBalance)
            val persistedCurve = localSource.equityCurve.first()
            _liveEquityTicks.value = if (persistedCurve.isNotEmpty()) {
                persistedCurve.takeLast(80)
            } else {
                listOf(EquityPoint(System.currentTimeMillis(), initialBalance))
            }
            val ensuredKey = prefs.ensureLocalApiKey()
            val initialCreds = prefs.brokerCredentials.first()
            localApiServer.startOrUpdate(
                enabled = initialCreds.localApiEnabled,
                port = initialCreds.localApiPort,
                apiKey = initialCreds.localApiKey.ifBlank { ensuredKey }
            )
            launch {
                prefs.brokerCredentials.collect { creds ->
                    val activeKey = creds.localApiKey.ifBlank { prefs.ensureLocalApiKey() }
                    localApiServer.startOrUpdate(
                        enabled = creds.localApiEnabled,
                        port = creds.localApiPort,
                        apiKey = activeKey
                    )
                }
            }
            launch {
                var lastSampleAt = 0L
                _accountSummary.collect { acct ->
                    val now = System.currentTimeMillis()
                    val prev = _liveEquityTicks.value.lastOrNull()
                    if (prev == null || abs(prev.equity - acct.equity) >= 0.01 || (now - lastSampleAt) >= 3_000L) {
                        lastSampleAt = now
                        _liveEquityTicks.update { list ->
                            (list + EquityPoint(now, acct.equity)).takeLast(120)
                        }
                    }
                }
            }
            fetchLiveSpotAnchors()
            startMarketAndBotLoop()
        }
    }

    private fun startMarketAndBotLoop() {
        engineLoopJob?.cancel()
        engineLoopJob = scope.launch {
            launch {
                while (isActive) {
                    delay(60_000L)
                    fetchLiveSpotAnchors()
                }
            }

            while (isActive) {
                val pair = prefs.selectedPair.first()
                val timeframe = prefs.selectedTimeframe.first()
                val config = prefs.botConfig.first()
                val creds = prefs.brokerCredentials.first()
                val paperBal = prefs.paperBalance.first()

                tickCounter++

                when {
                    creds.isMt5Configured -> runMt5Cycle(pair, timeframe, config, creds)
                    creds.isOandaConfigured -> runOandaCycle(pair, timeframe, config, creds)
                    else -> runPaperSimulationCycle(pair, timeframe, config, paperBal)
                }

                delay(1_000L)
            }
        }
    }

    private suspend fun fetchLiveSpotAnchors() {
        try {
            val resp = NetworkModule.frankfurterApi.getLatestRates()
            val rates = resp.rates
            val eurPerUsd = rates["EUR"] ?: 0.922
            val gbpPerUsd = rates["GBP"] ?: 0.788
            val jpyPerUsd = rates["JPY"] ?: 154.35
            val audPerUsd = rates["AUD"] ?: 1.528
            val cadPerUsd = rates["CAD"] ?: 1.396
            val zarPerUsd = rates["ZAR"] ?: 18.12

            val goldUsd = try {
                NetworkModule.goldSpotApi.getGoldUsdSpot().price.toDoubleOrNull()
                    ?: FxPair.XAU_USD.defaultMidPrice
            } catch (_: Exception) {
                spotAnchors.value[FxPair.XAU_USD] ?: FxPair.XAU_USD.defaultMidPrice
            }

            usdToZarRate.value = zarPerUsd

            val mapped = mapOf(
                FxPair.XAU_USD to goldUsd,
                FxPair.EUR_USD to (1.0 / eurPerUsd),
                FxPair.GBP_USD to (1.0 / gbpPerUsd),
                FxPair.USD_JPY to jpyPerUsd,
                FxPair.USD_ZAR to zarPerUsd,
                FxPair.AUD_USD to (1.0 / audPerUsd),
                FxPair.USD_CAD to cadPerUsd,
                FxPair.EUR_GBP to (gbpPerUsd / eurPerUsd),
                FxPair.EUR_JPY to (jpyPerUsd / eurPerUsd)
            )
            spotAnchors.value = mapped
            _spotFeedStatus.value = "Live FX + Gold (${resp.date})" to System.currentTimeMillis()
        } catch (e: Exception) {
            try {
                val goldUsd = NetworkModule.goldSpotApi.getGoldUsdSpot().price.toDoubleOrNull()
                if (goldUsd != null) {
                    spotAnchors.update { it + (FxPair.XAU_USD to goldUsd) }
                    _spotFeedStatus.value = "Live Gold Spot" to System.currentTimeMillis()
                    return
                }
            } catch (_: Exception) {
            }
            _spotFeedStatus.value = "Offline Anchor (Cached Spot)" to _spotFeedStatus.value.second
        }
    }

    private suspend fun runMt5Cycle(
        pair: FxPair,
        timeframe: Timeframe,
        config: BotConfig,
        creds: BrokerCredentials
    ) {
        val mt5Symbol = pair.mt5Symbol + creds.mt5SymbolSuffix.trim()
        try {
            val accountDto: com.example.data.remote.Mt5MetaApiAccountInfo?
            val priceDto: com.example.data.remote.Mt5MetaApiPriceDto?
            val candleDtos: List<com.example.data.remote.Mt5MetaApiCandleDto>
            val posDtos: List<com.example.data.remote.Mt5MetaApiPositionDto>

            if (creds.mt5BridgeType == Mt5BridgeType.META_API) {
                val api = NetworkModule.mt5MetaApi
                val token = creds.apiToken.trim()
                val acctId = creds.accountId.trim()
                accountDto = api.getAccountInformation(token, acctId).body()
                priceDto = api.getCurrentPrice(token, acctId, mt5Symbol).body()
                candleDtos = api.getHistoricalCandles(token, acctId, mt5Symbol, timeframe.mt5Timeframe, 70).body().orEmpty()
                posDtos = api.getPositions(token, acctId).body().orEmpty()
            } else {
                val bridge = NetworkModule.getCustomMt5Bridge(creds.mt5BridgeUrl)
                    ?: run {
                        _accountSummary.update { it.copy(brokerStatusText = "Invalid MT5 Bridge URL") }
                        return
                    }
                val bearer = "Bearer ${creds.apiToken.trim()}"
                accountDto = bridge.getAccount(bearer, creds.mt5Login, creds.mt5Server).body()
                priceDto = bridge.getQuote(bearer, mt5Symbol).body()
                candleDtos = bridge.getCandles(bearer, mt5Symbol, timeframe.mt5Timeframe, 70).body().orEmpty()
                posDtos = bridge.getPositions(bearer).body().orEmpty()
            }

            val parsedCandles = candleDtos.map { dto ->
                Candle(
                    timestamp = parseIsoTimestamp(dto.time),
                    open = dto.open,
                    high = dto.high,
                    low = dto.low,
                    close = dto.close,
                    volume = dto.tickVolume.coerceAtLeast(1)
                )
            }

            val fallbackMid = parsedCandles.lastOrNull()?.close ?: spotAnchors.value[pair] ?: pair.defaultMidPrice
            val bid = priceDto?.bid ?: fallbackMid
            val ask = priceDto?.ask ?: (bid + pair.pipsToPriceDiff(pair.typicalSpreadPips))

            val quote = MarketQuote(
                pair = pair,
                bid = bid,
                ask = ask,
                timestamp = System.currentTimeMillis(),
                source = "MT5 ${creds.mt5BrokerName}"
            )

            val mappedPositions = posDtos.mapIndexed { idx, p ->
                val matchedPair = FxPair.entries.find {
                    p.symbol.startsWith(it.mt5Symbol, ignoreCase = true)
                } ?: pair
                val side = if (p.type.contains("BUY", ignoreCase = true)) TradeSide.BUY else TradeSide.SELL
                TradePosition(
                    id = "mt5_${p.id}",
                    pair = matchedPair,
                    side = side,
                    lotSize = p.volume,
                    entryPrice = p.openPrice,
                    currentPrice = p.currentPrice ?: (if (side == TradeSide.BUY) bid else ask),
                    stopLoss = p.stopLoss ?: (p.openPrice - matchedPair.pipsToPriceDiff(config.stopLossPips)),
                    takeProfit = p.takeProfit ?: (p.openPrice + matchedPair.pipsToPriceDiff(config.takeProfitPips)),
                    openedAt = parseIsoTimestamp(p.time ?: ""),
                    strategyName = p.comment ?: config.strategy.title,
                    pyramidLayer = idx + 1,
                    usdToZarRate = usdToZarRate.value,
                    isLiveBroker = true,
                    brokerTradeId = p.id
                )
            }
            _openPositions.value = mappedPositions

            if (accountDto != null) {
                val upl = accountDto.equity - accountDto.balance
                _accountSummary.value = AccountSummary(
                    balance = accountDto.balance,
                    equity = accountDto.equity,
                    startingBalance = 200.0,
                    targetFlipBalance = 10_000.0,
                    unrealizedPnl = upl,
                    realizedTodayPnl = computeTodayRealizedPnl(),
                    currency = accountDto.currency.ifBlank { "ZAR" },
                    usdToZarRate = usdToZarRate.value,
                    marginUsed = accountDto.margin,
                    freeMargin = accountDto.freeMargin,
                    brokerStatusText = "${creds.mt5BrokerName} (${accountDto.server ?: creds.mt5Server}) • 1:${accountDto.leverage}",
                    lastSpotSyncTime = System.currentTimeMillis(),
                    spotFeedSource = "${creds.mt5BrokerName} MT5 Live Feed"
                )
            }

            if (parsedCandles.isNotEmpty()) {
                evaluateTickAndBot(pair, timeframe, parsedCandles, quote, config, creds)
            }
        } catch (e: Exception) {
            _accountSummary.update {
                it.copy(brokerStatusText = "MT5 Sync Error: ${e.message?.take(42) ?: "Bridge Unreachable"}")
            }
        }
    }

    private suspend fun runOandaCycle(
        pair: FxPair,
        timeframe: Timeframe,
        config: BotConfig,
        creds: BrokerCredentials
    ) {
        val service = NetworkModule.getOandaService(creds.environment) ?: return
        val bearer = "Bearer ${creds.apiToken}"
        try {
            val candlesResp = service.getCandles(bearer, pair.oandaInstrument, timeframe.oandaGranularity, 70)
            val pricingResp = service.getPricing(bearer, creds.accountId, pair.oandaInstrument)
            val summaryResp = service.getAccountSummary(bearer, creds.accountId)
            val openTradesResp = service.getOpenTrades(bearer, creds.accountId)

            val parsedCandles = candlesResp.body()?.candles?.mapNotNull { dto ->
                val mid = dto.mid ?: return@mapNotNull null
                Candle(
                    timestamp = parseIsoTimestamp(dto.time),
                    open = mid.o.toDoubleOrNull() ?: return@mapNotNull null,
                    high = mid.h.toDoubleOrNull() ?: return@mapNotNull null,
                    low = mid.l.toDoubleOrNull() ?: return@mapNotNull null,
                    close = mid.c.toDoubleOrNull() ?: return@mapNotNull null,
                    volume = dto.volume
                )
            } ?: emptyList()

            val priceDto = pricingResp.body()?.prices?.firstOrNull()
            val bid = priceDto?.bids?.firstOrNull()?.price?.toDoubleOrNull()
                ?: parsedCandles.lastOrNull()?.close
                ?: pair.defaultMidPrice
            val ask = priceDto?.asks?.firstOrNull()?.price?.toDoubleOrNull()
                ?: (bid + pair.pipsToPriceDiff(pair.typicalSpreadPips))

            val quote = MarketQuote(
                pair = pair,
                bid = bid,
                ask = ask,
                timestamp = System.currentTimeMillis(),
                source = creds.environment.label
            )

            val oandaPositions = openTradesResp.body()?.trades?.mapIndexedNotNull { idx, t ->
                val p = FxPair.entries.find { it.oandaInstrument == t.instrument } ?: return@mapIndexedNotNull null
                val unitsInt = t.currentUnits.toDoubleOrNull() ?: 0.0
                val side = if (unitsInt >= 0) TradeSide.BUY else TradeSide.SELL
                val entry = t.price.toDoubleOrNull() ?: bid
                val sl = t.stopLossOrder?.price?.toDoubleOrNull() ?: (entry - p.pipsToPriceDiff(config.stopLossPips))
                val tp = t.takeProfitOrder?.price?.toDoubleOrNull() ?: (entry + p.pipsToPriceDiff(config.takeProfitPips))
                TradePosition(
                    id = "oanda_${t.id}",
                    pair = p,
                    side = side,
                    lotSize = abs(unitsInt) / 100_000.0,
                    entryPrice = entry,
                    currentPrice = if (side == TradeSide.BUY) bid else ask,
                    stopLoss = sl,
                    takeProfit = tp,
                    openedAt = parseIsoTimestamp(t.openTime),
                    strategyName = config.strategy.title,
                    pyramidLayer = idx + 1,
                    usdToZarRate = usdToZarRate.value,
                    isLiveBroker = true,
                    brokerTradeId = t.id
                )
            } ?: emptyList()

            _openPositions.value = oandaPositions

            val acct = summaryResp.body()?.account
            if (acct != null) {
                val balance = acct.balance.toDoubleOrNull() ?: 0.0
                val nav = acct.nav.toDoubleOrNull() ?: balance
                val upl = acct.unrealizedPl.toDoubleOrNull() ?: 0.0
                val marginUsed = acct.marginUsed.toDoubleOrNull() ?: 0.0
                val freeMargin = acct.marginAvailable.toDoubleOrNull() ?: nav
                _accountSummary.value = AccountSummary(
                    balance = balance,
                    equity = nav,
                    startingBalance = 200.0,
                    targetFlipBalance = 10_000.0,
                    unrealizedPnl = upl,
                    realizedTodayPnl = computeTodayRealizedPnl(),
                    currency = acct.currency,
                    usdToZarRate = usdToZarRate.value,
                    marginUsed = marginUsed,
                    freeMargin = freeMargin,
                    brokerStatusText = "${creds.environment.label} (#${acct.id.takeLast(6)})",
                    lastSpotSyncTime = System.currentTimeMillis(),
                    spotFeedSource = "${creds.environment.label} Streaming"
                )
            }

            if (parsedCandles.isNotEmpty()) {
                evaluateTickAndBot(pair, timeframe, parsedCandles, quote, config, creds)
            }
        } catch (e: Exception) {
            _accountSummary.update {
                it.copy(brokerStatusText = "OANDA Sync Error: ${e.message?.take(42) ?: "Network"}")
            }
        }
    }

    private suspend fun runPaperSimulationCycle(
        pair: FxPair,
        timeframe: Timeframe,
        config: BotConfig,
        paperBalance: Double
    ) {
        val key = pair to timeframe
        val buffer = candleBuffers.getOrPut(key) {
            generateInitialCandles(pair, timeframe, spotAnchors.value[pair] ?: pair.defaultMidPrice)
        }

        val now = System.currentTimeMillis()
        val lastCandle = buffer.last()
        val volatilityPerTick = pair.pipSize * (0.55 + abs(sin(tickCounter * 0.25)) * 0.95)
        val waveDrift = sin(tickCounter * 0.12) * pair.pipSize * 0.78 +
            cos(tickCounter * 0.045) * pair.pipSize * 0.50
        val noise = (random.nextDouble() - 0.5) * volatilityPerTick * 1.9
        val nextMid = (lastCandle.close + waveDrift + noise).coerceAtLeast(pair.pipSize * 10)

        val barRollMs = max(8_000L, timeframe.intervalMs / 7)
        if (now - lastCandle.timestamp >= barRollMs) {
            val newBar = Candle(
                timestamp = now,
                open = lastCandle.close,
                high = max(lastCandle.close, nextMid) + pair.pipSize * random.nextDouble(0.15, 0.8),
                low = min(lastCandle.close, nextMid) - pair.pipSize * random.nextDouble(0.15, 0.8),
                close = nextMid,
                volume = random.nextInt(45, 195)
            )
            buffer.add(newBar)
            if (buffer.size > 90) buffer.removeAt(0)
        } else {
            val updated = lastCandle.copy(
                high = max(lastCandle.high, nextMid),
                low = min(lastCandle.low, nextMid),
                close = nextMid,
                volume = lastCandle.volume + random.nextInt(1, 9)
            )
            buffer[buffer.lastIndex] = updated
        }

        val spreadNoise = pair.typicalSpreadPips + (random.nextDouble() - 0.35) * 0.45
        val maxSimSpread = if (pair == FxPair.XAU_USD) 4.2 else 3.2
        val spreadPrice = pair.pipsToPriceDiff(spreadNoise.coerceIn(0.4, maxSimSpread))
        val quote = MarketQuote(
            pair = pair,
            bid = nextMid - spreadPrice / 2.0,
            ask = nextMid + spreadPrice / 2.0,
            timestamp = now,
            source = _spotFeedStatus.value.first
        )

        updatePaperPositionsOnTick(quote, config, paperBalance)

        val unrealizedZar = _openPositions.value.sumOf { it.unrealizedPnlAccount }
        val latestBalance = prefs.paperBalance.first()
        val marginUsed = _openPositions.value.sumOf { it.lotSize * 180.0 }
        _accountSummary.value = AccountSummary(
            balance = latestBalance,
            equity = latestBalance + unrealizedZar,
            startingBalance = 200.0,
            targetFlipBalance = 10_000.0,
            unrealizedPnl = unrealizedZar,
            realizedTodayPnl = computeTodayRealizedPnl(),
            currency = "ZAR",
            usdToZarRate = usdToZarRate.value,
            marginUsed = marginUsed,
            freeMargin = (latestBalance + unrealizedZar - marginUsed).coerceAtLeast(0.0),
            brokerStatusText = "Paper Flip Engine (R200 → R10,000)",
            lastSpotSyncTime = _spotFeedStatus.value.second,
            spotFeedSource = _spotFeedStatus.value.first
        )

        evaluateTickAndBot(
            pair = pair,
            timeframe = timeframe,
            candles = buffer.toList(),
            quote = quote,
            config = config,
            creds = BrokerCredentials(BrokerEnvironment.PAPER_SIMULATION)
        )
    }

    private suspend fun updatePaperPositionsOnTick(
        quote: MarketQuote,
        config: BotConfig,
        currentBalance: Double
    ) {
        val currentList = _openPositions.value.filter { !it.isLiveBroker }
        if (currentList.isEmpty()) return

        var updatedBalance = currentBalance
        val surviving = mutableListOf<TradePosition>()

        for (pos in currentList) {
            if (pos.pair != quote.pair) {
                surviving.add(pos.copy(usdToZarRate = usdToZarRate.value))
                continue
            }

            val exitPrice = if (pos.side == TradeSide.BUY) quote.bid else quote.ask
            var updatedPos = pos.copy(currentPrice = exitPrice, usdToZarRate = usdToZarRate.value)

            if (config.breakevenLockEnabled && updatedPos.pipsPnl >= config.breakevenTriggerPips) {
                val bePrice = if (pos.side == TradeSide.BUY) {
                    pos.entryPrice + pos.pair.pipSize * 0.4
                } else {
                    pos.entryPrice - pos.pair.pipSize * 0.4
                }
                val currentEffSl = updatedPos.effectiveStopLoss
                val improvesSl = if (pos.side == TradeSide.BUY) bePrice > currentEffSl else bePrice < currentEffSl
                if (improvesSl) {
                    updatedPos = updatedPos.copy(trailingStopPrice = bePrice)
                }
            }

            if (config.trailingStopEnabled && updatedPos.pipsPnl >= config.trailingTriggerPips) {
                val trailDist = pos.pair.pipsToPriceDiff(config.stopLossPips * 0.55)
                val candidateTrail = if (pos.side == TradeSide.BUY) {
                    max(pos.entryPrice + pos.pair.pipSize * 0.4, exitPrice - trailDist)
                } else {
                    min(pos.entryPrice - pos.pair.pipSize * 0.4, exitPrice + trailDist)
                }
                val currentEffSl = updatedPos.effectiveStopLoss
                val shouldTighten = if (pos.side == TradeSide.BUY) candidateTrail > currentEffSl else candidateTrail < currentEffSl
                if (shouldTighten) {
                    updatedPos = updatedPos.copy(trailingStopPrice = candidateTrail)
                }
            }

            val hitTp = if (pos.side == TradeSide.BUY) exitPrice >= updatedPos.takeProfit else exitPrice <= updatedPos.takeProfit
            val hitSl = if (pos.side == TradeSide.BUY) exitPrice <= updatedPos.effectiveStopLoss else exitPrice >= updatedPos.effectiveStopLoss

            if (hitTp || hitSl) {
                val reason = when {
                    hitTp -> "TP HIT (L${updatedPos.pyramidLayer})"
                    updatedPos.trailingStopPrice != null -> "TRAIL SL (L${updatedPos.pyramidLayer})"
                    else -> "SL HIT (L${updatedPos.pyramidLayer})"
                }
                val closedPnlZar = updatedPos.unrealizedPnlAccount
                updatedBalance += closedPnlZar
                prefs.savePaperBalance(updatedBalance)

                localSource.recordClosedTrade(
                    ClosedTradeRecord(
                        pair = updatedPos.pair,
                        side = updatedPos.side,
                        lotSize = updatedPos.lotSize,
                        entryPrice = updatedPos.entryPrice,
                        exitPrice = exitPrice,
                        pipsPnl = updatedPos.pipsPnl,
                        realizedPnlZar = closedPnlZar,
                        strategyName = "${updatedPos.strategyName} #${updatedPos.pyramidLayer}",
                        closeReason = reason,
                        openedAt = updatedPos.openedAt,
                        closedAt = System.currentTimeMillis(),
                        environmentLabel = "Paper ZAR"
                    ),
                    newEquity = updatedBalance
                )

                appendSignalLog(
                    SignalLogEntry(
                        id = UUID.randomUUID().toString(),
                        timestamp = System.currentTimeMillis(),
                        pair = updatedPos.pair,
                        timeframe = _marketSnapshot.value.timeframe,
                        action = reason,
                        reason = String.format(
                            Locale.US,
                            "Closed %s %.2fL @ %s (%+.1fp / %sR%.2f)",
                            updatedPos.side.name,
                            updatedPos.lotSize,
                            updatedPos.pair.formatPrice(exitPrice),
                            updatedPos.pipsPnl,
                            if (closedPnlZar >= 0) "+" else "-",
                            abs(closedPnlZar)
                        ),
                        price = exitPrice,
                        spreadPips = quote.spreadPips,
                        rsi = _marketSnapshot.value.indicators.rsi,
                        isExecution = true,
                        side = updatedPos.side
                    )
                )

                if (hitTp) {
                    emitDashboardAlert(
                        type = AlertType.TAKE_PROFIT_HIT,
                        title = "PROFIT TARGET HIT (L${updatedPos.pyramidLayer})",
                        message = String.format(
                            Locale.US,
                            "%s %s %.2fL closed at TP @ %s (%+.1fp)",
                            updatedPos.pair.displayName,
                            updatedPos.side.name,
                            updatedPos.lotSize,
                            updatedPos.pair.formatPrice(exitPrice),
                            updatedPos.pipsPnl
                        ),
                        pair = updatedPos.pair,
                        profitZar = closedPnlZar,
                        layer = updatedPos.pyramidLayer
                    )
                }
            } else {
                surviving.add(updatedPos)
            }
        }

        _openPositions.value = surviving + _openPositions.value.filter { it.isLiveBroker }
    }

    private suspend fun evaluateTickAndBot(
        pair: FxPair,
        timeframe: Timeframe,
        candles: List<Candle>,
        quote: MarketQuote,
        config: BotConfig,
        creds: BrokerCredentials
    ) {
        val eval = TechnicalIndicators.evaluateScalpingSignal(pair, candles, quote, config)
        _marketSnapshot.value = LiveMarketSnapshot(
            pair = pair,
            timeframe = timeframe,
            quote = quote,
            candles = candles,
            indicators = eval.summary,
            lastEvaluation = eval
        )

        val runState = _botRunState.value
        if (runState == BotRunState.IDLE || runState == BotRunState.CIRCUIT_BREAKER_HALTED) {
            return
        }

        val totalDayPnl = _accountSummary.value.realizedTodayPnl + _accountSummary.value.unrealizedPnl
        if (totalDayPnl <= -config.dailyLossLimitZar) {
            _botRunState.value = BotRunState.CIRCUIT_BREAKER_HALTED
            appendSignalLog(
                SignalLogEntry(
                    id = UUID.randomUUID().toString(),
                    timestamp = System.currentTimeMillis(),
                    pair = pair,
                    timeframe = timeframe,
                    action = "CIRCUIT BREAKER",
                    reason = String.format(Locale.US, "Daily loss limit (-R%.0f) reached. Bot halted.", config.dailyLossLimitZar),
                    price = quote.mid,
                    spreadPips = quote.spreadPips,
                    rsi = eval.summary.rsi,
                    isExecution = true
                )
            )
            return
        }

        if (config.stopOnTargetReached && _accountSummary.value.equity >= config.targetProfitStopZar) {
            _botRunState.value = BotRunState.IDLE
            appendSignalLog(
                SignalLogEntry(
                    id = UUID.randomUUID().toString(),
                    timestamp = System.currentTimeMillis(),
                    pair = pair,
                    timeframe = timeframe,
                    action = "FLIP TARGET HIT",
                    reason = String.format(
                        Locale.US,
                        "Equity reached target (%s >= %s). Bot auto-locked profit.",
                        _accountSummary.value.formatMoney(_accountSummary.value.equity),
                        _accountSummary.value.formatMoney(config.targetProfitStopZar)
                    ),
                    price = quote.mid,
                    spreadPips = quote.spreadPips,
                    rsi = eval.summary.rsi,
                    isExecution = true
                )
            )
            emitDashboardAlert(
                type = AlertType.FLIP_TARGET_REACHED,
                title = "FLIP PROFIT TARGET REACHED",
                message = String.format(
                    Locale.US,
                    "Account equity hit %s (Goal: %s). Bot auto-locked gains.",
                    _accountSummary.value.formatMoney(_accountSummary.value.equity),
                    _accountSummary.value.formatMoney(config.targetProfitStopZar)
                ),
                pair = pair,
                profitZar = _accountSummary.value.equity - _accountSummary.value.startingBalance
            )
            return
        }

        val allOpen = _openPositions.value
        val openForPair = allOpen.filter { it.pair == pair }
        _botRunState.value = if (allOpen.isNotEmpty()) BotRunState.IN_POSITION else BotRunState.SCANNING

        val now = System.currentTimeMillis()
        val effectiveLot = TechnicalIndicators.computeEffectiveLotSize(config, _accountSummary.value.equity)

        if (openForPair.isNotEmpty() && allOpen.size < config.maxOpenTrades && (now - lastAutoTradeTimestampMs) >= 2_500L) {
            val pyramidEval = TechnicalIndicators.evaluatePyramidScaleIn(pair, quote, openForPair, config)
            if (pyramidEval != null) {
                lastAutoTradeTimestampMs = now
                protectEarlierLayersOnScaleIn(pair, pyramidEval.side)
                executeOrderInternal(
                    pair = pair,
                    side = pyramidEval.side,
                    lotSize = effectiveLot,
                    pyramidLayer = pyramidEval.nextLayer,
                    quote = quote,
                    config = config,
                    creds = creds,
                    strategyTag = "${config.strategy.title} (Scale)",
                    reasonText = pyramidEval.reason
                )
                return
            }
        }

        val signal = eval.signal
        if (signal != null && openForPair.isEmpty() && allOpen.size < config.maxOpenTrades && (now - lastAutoTradeTimestampMs) >= 4_000L) {
            lastAutoTradeTimestampMs = now
            executeOrderInternal(
                pair = pair,
                side = signal,
                lotSize = effectiveLot,
                pyramidLayer = 1,
                quote = quote,
                config = config,
                creds = creds,
                strategyTag = config.strategy.title,
                reasonText = "${eval.reason} • Layer 1/${config.maxOpenTrades}"
            )
        } else if (now - lastScanLogTimestampMs >= 10_000L) {
            lastScanLogTimestampMs = now
            appendSignalLog(
                SignalLogEntry(
                    id = UUID.randomUUID().toString(),
                    timestamp = now,
                    pair = pair,
                    timeframe = timeframe,
                    action = eval.actionLabel,
                    reason = eval.reason,
                    price = quote.mid,
                    spreadPips = quote.spreadPips,
                    rsi = eval.summary.rsi,
                    isExecution = false
                )
            )
        }
    }

    private fun protectEarlierLayersOnScaleIn(pair: FxPair, side: TradeSide) {
        _openPositions.update { list ->
            list.map { pos ->
                if (!pos.isLiveBroker && pos.pair == pair && pos.side == side && pos.pipsPnl > 1.5) {
                    val breakevenLock = if (side == TradeSide.BUY) {
                        pos.entryPrice + pair.pipSize * 0.5
                    } else {
                        pos.entryPrice - pair.pipSize * 0.5
                    }
                    val currentSl = pos.effectiveStopLoss
                    val better = if (side == TradeSide.BUY) breakevenLock > currentSl else breakevenLock < currentSl
                    if (better) pos.copy(trailingStopPrice = breakevenLock) else pos
                } else {
                    pos
                }
            }
        }
    }

    private suspend fun executeOrderInternal(
        pair: FxPair,
        side: TradeSide,
        lotSize: Double,
        pyramidLayer: Int,
        quote: MarketQuote,
        config: BotConfig,
        creds: BrokerCredentials,
        strategyTag: String,
        reasonText: String
    ) {
        val entryPrice = if (side == TradeSide.BUY) quote.ask else quote.bid
        val slDiff = pair.pipsToPriceDiff(config.stopLossPips)
        val tpDiff = pair.pipsToPriceDiff(config.takeProfitPips)
        val slPrice = if (side == TradeSide.BUY) entryPrice - slDiff else entryPrice + slDiff
        val tpPrice = if (side == TradeSide.BUY) entryPrice + tpDiff else entryPrice - tpDiff

        // 1. MT5 Bridge Order Execution (XM Global / Any Broker Live or Demo)
        if (creds.isMt5Configured) {
            val mt5Symbol = pair.mt5Symbol + creds.mt5SymbolSuffix.trim()
            try {
                val resp = if (creds.mt5BridgeType == Mt5BridgeType.META_API) {
                    NetworkModule.mt5MetaApi.executeTrade(
                        authToken = creds.apiToken.trim(),
                        accountId = creds.accountId.trim(),
                        request = Mt5MetaApiTradeRequest(
                            actionType = if (side == TradeSide.BUY) "ORDER_TYPE_BUY" else "ORDER_TYPE_SELL",
                            symbol = mt5Symbol,
                            volume = lotSize,
                            stopLoss = slPrice,
                            takeProfit = tpPrice,
                            comment = "Neto Scalper Pro L$pyramidLayer"
                        )
                    )
                } else {
                    val bridge = NetworkModule.getCustomMt5Bridge(creds.mt5BridgeUrl)
                    bridge?.placeOrder(
                        bearerToken = "Bearer ${creds.apiToken.trim()}",
                        request = CustomMt5OrderRequest(
                            login = creds.mt5Login,
                            server = creds.mt5Server,
                            symbol = mt5Symbol,
                            side = side.name,
                            volume = lotSize,
                            sl = slPrice,
                            tp = tpPrice,
                            comment = "Neto Scalper Pro L$pyramidLayer"
                        )
                    )
                }

                if (resp != null && resp.isSuccessful) {
                    appendSignalLog(
                        SignalLogEntry(
                            id = UUID.randomUUID().toString(),
                            timestamp = System.currentTimeMillis(),
                            pair = pair,
                            timeframe = _marketSnapshot.value.timeframe,
                            action = "MT5 ${side.name} L$pyramidLayer",
                            reason = "${creds.mt5BrokerName} $mt5Symbol • $reasonText",
                            price = entryPrice,
                            spreadPips = quote.spreadPips,
                            rsi = _marketSnapshot.value.indicators.rsi,
                            isExecution = true,
                            side = side
                        )
                    )
                    if (pyramidLayer >= 2) {
                        emitDashboardAlert(
                            type = AlertType.PYRAMID_LAYER_OPENED,
                            title = "PYRAMID LAYER $pyramidLayer/${config.maxOpenTrades} OPENED",
                            message = "${creds.mt5BrokerName} ${pair.displayName} ${side.name} ${String.format(Locale.US, "%.2fL", lotSize)} @ ${pair.formatPrice(entryPrice)}",
                            pair = pair,
                            layer = pyramidLayer
                        )
                    }
                } else {
                    appendSignalLog(
                        SignalLogEntry(
                            id = UUID.randomUUID().toString(),
                            timestamp = System.currentTimeMillis(),
                            pair = pair,
                            timeframe = _marketSnapshot.value.timeframe,
                            action = "MT5 REJECTED",
                            reason = resp?.body()?.message ?: "HTTP ${resp?.code() ?: "Bridge Error"}",
                            price = entryPrice,
                            spreadPips = quote.spreadPips,
                            rsi = _marketSnapshot.value.indicators.rsi,
                            isExecution = true
                        )
                    )
                }
            } catch (e: Exception) {
                appendSignalLog(
                    SignalLogEntry(
                        id = UUID.randomUUID().toString(),
                        timestamp = System.currentTimeMillis(),
                        pair = pair,
                        timeframe = _marketSnapshot.value.timeframe,
                        action = "MT5 BRIDGE ERROR",
                        reason = e.message ?: "Failed to route MT5 order",
                        price = entryPrice,
                        spreadPips = quote.spreadPips,
                        rsi = _marketSnapshot.value.indicators.rsi,
                        isExecution = true
                    )
                )
            }
            return
        }

        // 2. OANDA v20 Execution
        if (creds.isOandaConfigured) {
            val service = NetworkModule.getOandaService(creds.environment)
            if (service != null) {
                try {
                    val rawUnits = (lotSize * pair.unitsPerLot).toInt().coerceAtLeast(1)
                    val signedUnits = if (side == TradeSide.BUY) rawUnits else -rawUnits
                    val resp = service.placeMarketOrder(
                        bearerToken = "Bearer ${creds.apiToken}",
                        accountId = creds.accountId,
                        request = OandaOrderRequest(
                            order = OandaMarketOrderBody(
                                units = signedUnits.toString(),
                                instrument = pair.oandaInstrument,
                                takeProfitOnFill = OandaPriceSpec(pair.formatPrice(tpPrice)),
                                stopLossOnFill = OandaPriceSpec(pair.formatPrice(slPrice))
                            )
                        )
                    )
                    val fill = resp.body()?.orderFillTransaction
                    if (resp.isSuccessful && fill != null) {
                        val fillPrice = fill.price?.toDoubleOrNull() ?: entryPrice
                        appendSignalLog(
                            SignalLogEntry(
                                id = UUID.randomUUID().toString(),
                                timestamp = System.currentTimeMillis(),
                                pair = pair,
                                timeframe = _marketSnapshot.value.timeframe,
                                action = "OANDA ${side.name} L$pyramidLayer",
                                reason = "$reasonText • Fill @ ${pair.formatPrice(fillPrice)}",
                                price = fillPrice,
                                spreadPips = quote.spreadPips,
                                rsi = _marketSnapshot.value.indicators.rsi,
                                isExecution = true,
                                side = side
                            )
                        )
                        if (pyramidLayer >= 2) {
                            emitDashboardAlert(
                                type = AlertType.PYRAMID_LAYER_OPENED,
                                title = "PYRAMID LAYER $pyramidLayer/${config.maxOpenTrades} OPENED",
                                message = "OANDA ${pair.displayName} ${side.name} ${String.format(Locale.US, "%.2fL", lotSize)} @ ${pair.formatPrice(fillPrice)}",
                                pair = pair,
                                layer = pyramidLayer
                            )
                        }
                    } else {
                        appendSignalLog(
                            SignalLogEntry(
                                id = UUID.randomUUID().toString(),
                                timestamp = System.currentTimeMillis(),
                                pair = pair,
                                timeframe = _marketSnapshot.value.timeframe,
                                action = "ORDER REJECTED",
                                reason = resp.body()?.errorMessage ?: "HTTP ${resp.code()}",
                                price = entryPrice,
                                spreadPips = quote.spreadPips,
                                rsi = _marketSnapshot.value.indicators.rsi,
                                isExecution = true
                            )
                        )
                    }
                } catch (e: Exception) {
                    appendSignalLog(
                        SignalLogEntry(
                            id = UUID.randomUUID().toString(),
                            timestamp = System.currentTimeMillis(),
                            pair = pair,
                            timeframe = _marketSnapshot.value.timeframe,
                            action = "BROKER ERROR",
                            reason = e.message ?: "OANDA order failed",
                            price = entryPrice,
                            spreadPips = quote.spreadPips,
                            rsi = _marketSnapshot.value.indicators.rsi,
                            isExecution = true
                        )
                    )
                }
                return
            }
        }

        // 3. Paper ZAR Order Execution
        val position = TradePosition(
            id = UUID.randomUUID().toString(),
            pair = pair,
            side = side,
            lotSize = lotSize,
            entryPrice = entryPrice,
            currentPrice = if (side == TradeSide.BUY) quote.bid else quote.ask,
            stopLoss = slPrice,
            takeProfit = tpPrice,
            openedAt = System.currentTimeMillis(),
            strategyName = strategyTag,
            pyramidLayer = pyramidLayer,
            usdToZarRate = usdToZarRate.value,
            isLiveBroker = false
        )
        _openPositions.update { listOf(position) + it }
        appendSignalLog(
            SignalLogEntry(
                id = UUID.randomUUID().toString(),
                timestamp = System.currentTimeMillis(),
                pair = pair,
                timeframe = _marketSnapshot.value.timeframe,
                action = "SCALP ${side.name} L$pyramidLayer/${config.maxOpenTrades}",
                reason = "$reasonText • ${String.format(Locale.US, "%.2fL", lotSize)} @ ${pair.formatPrice(entryPrice)}",
                price = entryPrice,
                spreadPips = quote.spreadPips,
                rsi = _marketSnapshot.value.indicators.rsi,
                isExecution = true,
                side = side
            )
        )
        if (pyramidLayer >= 2) {
            emitDashboardAlert(
                type = AlertType.PYRAMID_LAYER_OPENED,
                title = "PYRAMID LAYER $pyramidLayer/${config.maxOpenTrades} OPENED",
                message = "Market favored ${pair.displayName} ${side.name} • Added ${String.format(Locale.US, "%.2fL", lotSize)} @ ${pair.formatPrice(entryPrice)}",
                pair = pair,
                layer = pyramidLayer
            )
        }
    }

    private fun generateInitialCandles(pair: FxPair, timeframe: Timeframe, anchorPrice: Double): MutableList<Candle> {
        val count = 65
        val now = System.currentTimeMillis()
        val list = mutableListOf<Candle>()
        var price = anchorPrice - pair.pipSize * 16.0
        val stepSize = pair.pipSize * 2.5

        for (i in count downTo 1) {
            val t = now - (i * max(8_000L, timeframe.intervalMs / 7))
            val wave = sin(i * 0.30) * stepSize + cos(i * 0.12) * stepSize * 0.6
            val delta = wave + (random.nextDouble() - 0.48) * stepSize * 1.4
            val open = price
            val close = (open + delta).coerceAtLeast(pair.pipSize * 10)
            val high = max(open, close) + pair.pipSize * random.nextDouble(0.4, 2.2)
            val low = min(open, close) - pair.pipSize * random.nextDouble(0.4, 2.2)
            list.add(Candle(timestamp = t, open = open, high = high, low = low, close = close, volume = random.nextInt(45, 190)))
            price = close
        }
        return list
    }

    private suspend fun computeTodayRealizedPnl(): Double {
        val history = tradeHistory.first()
        val startOfDay = System.currentTimeMillis() - 86_400_000L
        return history.filter { it.closedAt >= startOfDay }.sumOf { it.realizedPnlZar }
    }

    private fun appendSignalLog(entry: SignalLogEntry) {
        _signalLogs.update { (listOf(entry) + it).take(60) }
    }

    private fun emitDashboardAlert(
        type: AlertType,
        title: String,
        message: String,
        pair: FxPair,
        profitZar: Double? = null,
        layer: Int? = null
    ) {
        val alert = BotAlertEvent(
            id = UUID.randomUUID().toString(),
            type = type,
            title = title,
            message = message,
            pair = pair,
            timestamp = System.currentTimeMillis(),
            profitZar = profitZar,
            layer = layer
        )
        _alertHistory.update { (listOf(alert) + it).take(30) }
        _activeAlertBanner.value = alert
        alertAutoDismissJob?.cancel()
        alertAutoDismissJob = scope.launch {
            delay(7_000L)
            if (_activeAlertBanner.value?.id == alert.id) {
                _activeAlertBanner.value = null
            }
        }
    }

    private fun parseIsoTimestamp(iso: String): Long {
        if (iso.isBlank()) return System.currentTimeMillis()
        return try {
            val cleaned = iso.substringBefore(".") + "Z"
            val fmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }
            fmt.parse(cleaned)?.time ?: System.currentTimeMillis()
        } catch (_: Exception) {
            System.currentTimeMillis()
        }
    }

    // --- Public Actions ---

    fun toggleBot() {
        scope.launch {
            val next = when (_botRunState.value) {
                BotRunState.IDLE, BotRunState.CIRCUIT_BREAKER_HALTED -> {
                    val snap = _marketSnapshot.value
                    val cfg = prefs.botConfig.first()
                    appendSignalLog(
                        SignalLogEntry(
                            id = UUID.randomUUID().toString(),
                            timestamp = System.currentTimeMillis(),
                            pair = snap.pair,
                            timeframe = snap.timeframe,
                            action = "1M BOT ENGAGED",
                            reason = "Max ${cfg.maxOpenTrades} positions • Scale-in ${if (cfg.scaleInOnProfit) "+${cfg.scaleInStepPips}p" else "OFF"} • ${snap.pair.displayName}",
                            price = snap.quote.mid,
                            spreadPips = snap.quote.spreadPips,
                            rsi = snap.indicators.rsi,
                            isExecution = true
                        )
                    )
                    if (_openPositions.value.isNotEmpty()) BotRunState.IN_POSITION else BotRunState.SCANNING
                }
                BotRunState.SCANNING, BotRunState.IN_POSITION -> {
                    val snap = _marketSnapshot.value
                    appendSignalLog(
                        SignalLogEntry(
                            id = UUID.randomUUID().toString(),
                            timestamp = System.currentTimeMillis(),
                            pair = snap.pair,
                            timeframe = snap.timeframe,
                            action = "BOT HALTED",
                            reason = "Automated 1m scalping paused by trader",
                            price = snap.quote.mid,
                            spreadPips = snap.quote.spreadPips,
                            rsi = snap.indicators.rsi,
                            isExecution = true
                        )
                    )
                    BotRunState.IDLE
                }
            }
            _botRunState.value = next
        }
    }

    fun placeManualOrder(side: TradeSide) {
        scope.launch {
            val snap = _marketSnapshot.value
            val config = prefs.botConfig.first()
            val creds = prefs.brokerCredentials.first()
            val nextLayer = _openPositions.value.count { it.pair == snap.pair } + 1
            val effectiveLot = TechnicalIndicators.computeEffectiveLotSize(config, _accountSummary.value.equity)
            executeOrderInternal(
                pair = snap.pair,
                side = side,
                lotSize = effectiveLot,
                pyramidLayer = nextLayer,
                quote = snap.quote,
                config = config,
                creds = creds,
                strategyTag = "Manual 1m Scalp",
                reasonText = "Direct market order (L$nextLayer)"
            )
        }
    }

    fun closePosition(positionId: String) {
        scope.launch {
            val pos = _openPositions.value.find { it.id == positionId } ?: return@launch
            val creds = prefs.brokerCredentials.first()

            if (pos.isLiveBroker && pos.brokerTradeId != null) {
                if (creds.isMt5Configured) {
                    try {
                        if (creds.mt5BridgeType == Mt5BridgeType.META_API) {
                            NetworkModule.mt5MetaApi.executeTrade(
                                authToken = creds.apiToken.trim(),
                                accountId = creds.accountId.trim(),
                                request = Mt5MetaApiTradeRequest(
                                    actionType = "POSITION_CLOSE_ID",
                                    positionId = pos.brokerTradeId
                                )
                            )
                        } else {
                            NetworkModule.getCustomMt5Bridge(creds.mt5BridgeUrl)?.closePosition(
                                bearerToken = "Bearer ${creds.apiToken.trim()}",
                                request = CustomMt5CloseRequest(positionId = pos.brokerTradeId)
                            )
                        }
                    } catch (_: Exception) {
                    }
                    return@launch
                }

                if (creds.isOandaConfigured) {
                    val service = NetworkModule.getOandaService(creds.environment)
                    try {
                        service?.closeTrade("Bearer ${creds.apiToken}", creds.accountId, pos.brokerTradeId)
                    } catch (_: Exception) {
                    }
                    return@launch
                }
            }

            val exitPrice = pos.currentPrice
            val realized = pos.unrealizedPnlAccount
            val newBal = prefs.paperBalance.first() + realized
            prefs.savePaperBalance(newBal)
            _openPositions.update { list -> list.filterNot { it.id == positionId } }
            localSource.recordClosedTrade(
                ClosedTradeRecord(
                    pair = pos.pair,
                    side = pos.side,
                    lotSize = pos.lotSize,
                    entryPrice = pos.entryPrice,
                    exitPrice = exitPrice,
                    pipsPnl = pos.pipsPnl,
                    realizedPnlZar = realized,
                    strategyName = "${pos.strategyName} #${pos.pyramidLayer}",
                    closeReason = "MANUAL CLOSE",
                    openedAt = pos.openedAt,
                    closedAt = System.currentTimeMillis(),
                    environmentLabel = "Paper ZAR"
                ),
                newEquity = newBal
            )
            appendSignalLog(
                SignalLogEntry(
                    id = UUID.randomUUID().toString(),
                    timestamp = System.currentTimeMillis(),
                    pair = pos.pair,
                    timeframe = _marketSnapshot.value.timeframe,
                    action = "MANUAL CLOSE",
                    reason = String.format(
                        Locale.US,
                        "Closed %s L%d %+.1fp (%sR%.2f)",
                        pos.pair.displayName,
                        pos.pyramidLayer,
                        pos.pipsPnl,
                        if (realized >= 0) "+" else "-",
                        abs(realized)
                    ),
                    price = exitPrice,
                    spreadPips = _marketSnapshot.value.quote.spreadPips,
                    rsi = _marketSnapshot.value.indicators.rsi,
                    isExecution = true,
                    side = pos.side
                )
            )
        }
    }

    fun emergencyFlattenAll() {
        scope.launch {
            _botRunState.value = BotRunState.IDLE
            val positions = _openPositions.value.toList()
            for (pos in positions) {
                closePosition(pos.id)
            }
        }
    }

    fun selectPair(pair: FxPair) {
        scope.launch { prefs.setSelectedPair(pair) }
    }

    fun selectTimeframe(timeframe: Timeframe) {
        scope.launch { prefs.setSelectedTimeframe(timeframe) }
    }

    fun updateBotConfig(config: BotConfig) {
        scope.launch { prefs.saveBotConfig(config) }
    }

    fun saveBrokerCredentials(credentials: BrokerCredentials) {
        scope.launch {
            prefs.saveBrokerCredentials(credentials)
            _brokerTestMessage.value = null
            if (credentials.environment == BrokerEnvironment.PAPER_SIMULATION) {
                _openPositions.update { it.filterNot { p -> p.isLiveBroker } }
            }
        }
    }

    fun testAndSyncBrokerConnection(credentials: BrokerCredentials) {
        scope.launch {
            if (credentials.environment == BrokerEnvironment.PAPER_SIMULATION) {
                prefs.saveBrokerCredentials(credentials)
                _brokerTestMessage.value = true to "Switched to Safety Simulation Mode (R200 → R10,000 Paper Flip)"
                return@launch
            }

            if (credentials.isMt5Selected) {
                if (credentials.mt5BridgeType == Mt5BridgeType.META_API) {
                    if (credentials.apiToken.isBlank() || credentials.accountId.isBlank()) {
                        _brokerTestMessage.value = false to "Enter MetaApi Auth Token and MT5 Account ID for ${credentials.mt5BrokerName}"
                        return@launch
                    }
                    try {
                        val resp = NetworkModule.mt5MetaApi.getAccountInformation(
                            authToken = credentials.apiToken.trim(),
                            accountId = credentials.accountId.trim()
                        )
                        if (resp.isSuccessful && resp.body() != null) {
                            val info = resp.body()!!
                            prefs.saveBrokerCredentials(credentials)
                            _brokerTestMessage.value = true to "Connected MT5 (${info.broker ?: credentials.mt5BrokerName} • ${info.server ?: credentials.mt5Server}): ${info.currency} ${String.format(Locale.US, "%.2f", info.balance)}"
                        } else {
                            _brokerTestMessage.value = false to "MetaApi MT5 HTTP ${resp.code()}: Verify Auth Token & MetaApi Account ID"
                        }
                    } catch (e: Exception) {
                        _brokerTestMessage.value = false to "MT5 Cloud Error: ${e.message ?: "Network unreachable"}"
                    }
                } else {
                    val bridge = NetworkModule.getCustomMt5Bridge(credentials.mt5BridgeUrl)
                    if (bridge == null) {
                        _brokerTestMessage.value = false to "Enter a valid HTTP/HTTPS MT5 VPS Bridge URL"
                        return@launch
                    }
                    try {
                        val resp = bridge.getAccount(
                            bearerToken = "Bearer ${credentials.apiToken.trim()}",
                            login = credentials.mt5Login.trim(),
                            server = credentials.mt5Server.trim()
                        )
                        if (resp.isSuccessful && resp.body() != null) {
                            val info = resp.body()!!
                            prefs.saveBrokerCredentials(credentials)
                            _brokerTestMessage.value = true to "Connected VPS MT5 (${credentials.mt5BrokerName} • ${credentials.mt5Server}): ${info.currency} ${String.format(Locale.US, "%.2f", info.balance)}"
                        } else {
                            _brokerTestMessage.value = false to "MT5 VPS Bridge HTTP ${resp.code()}: Check login, server & bridge secret"
                        }
                    } catch (e: Exception) {
                        _brokerTestMessage.value = false to "MT5 Bridge Error: ${e.message ?: "Unable to reach VPS bridge"}"
                    }
                }
                return@launch
            }

            // OANDA Verification
            if (credentials.apiToken.isBlank() || credentials.accountId.isBlank()) {
                _brokerTestMessage.value = false to "Enter both OANDA API Token and Account ID"
                return@launch
            }
            val service = NetworkModule.getOandaService(credentials.environment) ?: return@launch
            try {
                val resp = service.getAccountSummary("Bearer ${credentials.apiToken.trim()}", credentials.accountId.trim())
                if (resp.isSuccessful && resp.body() != null) {
                    val acct = resp.body()!!.account
                    prefs.saveBrokerCredentials(credentials)
                    _brokerTestMessage.value = true to "Verified ${credentials.environment.label}: ${acct.currency} ${acct.balance} (NAV ${acct.nav})"
                } else {
                    _brokerTestMessage.value = false to "OANDA HTTP ${resp.code()}: Check token, account ID, or environment"
                }
            } catch (e: Exception) {
                _brokerTestMessage.value = false to "Connection error: ${e.message ?: "Unable to reach OANDA"}"
            }
        }
    }

    fun resetPaperAccount() {
        scope.launch {
            _botRunState.value = BotRunState.IDLE
            _openPositions.update { it.filter { p -> p.isLiveBroker } }
            prefs.savePaperBalance(200.0)
            localSource.resetAll(200.0)
            _liveEquityTicks.value = listOf(EquityPoint(System.currentTimeMillis(), 200.0))
            _signalLogs.value = emptyList()
            _alertHistory.value = emptyList()
            _activeAlertBanner.value = null
        }
    }

    fun refreshSpotRatesNow() {
        scope.launch { fetchLiveSpotAnchors() }
    }

    fun applyRiskProfile(profile: TradingRiskProfile) {
        scope.launch {
            val current = prefs.botConfig.first()
            prefs.saveBotConfig(current.withRiskProfile(profile))
        }
    }

    fun regenerateLocalApiKey() {
        scope.launch {
            prefs.regenerateLocalApiKey()
        }
    }

    fun dismissActiveAlert() {
        alertAutoDismissJob?.cancel()
        _activeAlertBanner.value = null
    }

    fun clearAlertHistory() {
        _alertHistory.value = emptyList()
    }

    fun disableKeepRunningOnPhone() {
        scope.launch {
            val current = prefs.botConfig.first()
            if (current.keepRunningOnPhone) {
                prefs.saveBotConfig(current.copy(keepRunningOnPhone = false))
            }
        }
    }
}
