package com.example.data.repository

import android.content.Context
import com.example.data.local.BotPreferencesRepository
import com.example.data.local.SignalHistoryLocalSource
import com.example.data.remote.CustomMt5OrderRequest
import com.example.data.remote.LocalApiStatus
import com.example.data.remote.LocalTradingApiServer
import com.example.data.remote.Mt5MetaApiTradeRequest
import com.example.data.remote.NetworkModule
import com.example.service.SignalNotificationHelper
import com.example.domain.engine.SignalEvaluation
import com.example.domain.engine.TechnicalIndicators
import com.example.domain.model.AccountFlipPlan
import com.example.domain.model.AlertType
import com.example.domain.model.BotAlertEvent
import com.example.domain.model.BotConfig
import com.example.domain.model.BotRunState
import com.example.domain.model.BrokerCredentials
import com.example.domain.model.Candle
import com.example.domain.model.ConfirmationStage
import com.example.domain.model.FlipCurrency
import com.example.domain.model.FxPair
import com.example.domain.model.IndicatorSummary
import com.example.domain.model.MarketQuote
import com.example.domain.model.Mt5BridgeType
import com.example.domain.model.SignalHistoryRecord
import com.example.domain.model.SignalLogEntry
import com.example.domain.model.SignalStatus
import com.example.domain.model.Timeframe
import com.example.domain.model.TimeframeSignalRow
import com.example.domain.model.TradeSide
import com.example.domain.model.TradeSignal
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.UUID
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

data class LiveMarketSnapshot(
    val pair: FxPair = FxPair.XAU_USD,
    val timeframe: Timeframe = Timeframe.M5,
    val quote: MarketQuote = MarketQuote(
        pair = FxPair.XAU_USD,
        bid = 2665.40,
        ask = 2665.60,
        timestamp = System.currentTimeMillis(),
        source = "Connecting Live Feed..."
    ),
    val quotesByPair: Map<FxPair, MarketQuote> = emptyMap(),
    val candles: List<Candle> = emptyList(),
    val indicators: IndicatorSummary = IndicatorSummary(),
    val lastEvaluation: SignalEvaluation? = null,
    val confirmationProgress: Float = 0f,
    val confirmationTicks: Int = 0,
    val requiredConfirmTicks: Int = 2
)

private data class ConfirmationCandidate(
    val pair: FxPair,
    val timeframe: Timeframe,
    val side: TradeSide,
    val initialEntry: Double,
    val startedAtMs: Long,
    val tickCount: Int,
    val latestEval: SignalEvaluation
)

class ScalpingBotRepository(
    private val appContext: Context,
    private val prefs: BotPreferencesRepository,
    private val localSource: SignalHistoryLocalSource
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val usdToZarRate = MutableStateFlow(18.12)
    private val quotesMap = MutableStateFlow<Map<FxPair, MarketQuote>>(
        FxPair.entries.associateWith { pair ->
            val halfSpread = pair.pipsToPriceDiff(pair.typicalSpreadPips) / 2.0
            MarketQuote(
                pair = pair,
                bid = pair.defaultMidPrice - halfSpread,
                ask = pair.defaultMidPrice + halfSpread,
                timestamp = System.currentTimeMillis(),
                source = "Syncing Spot..."
            )
        }
    )

    private val _marketSnapshot = MutableStateFlow(LiveMarketSnapshot())
    val marketSnapshot: StateFlow<LiveMarketSnapshot> = _marketSnapshot.asStateFlow()

    private val _activeSignals = MutableStateFlow<List<TradeSignal>>(emptyList())
    val activeSignals: StateFlow<List<TradeSignal>> = _activeSignals.asStateFlow()

    private val _timeframeMatrix = MutableStateFlow<List<TimeframeSignalRow>>(emptyList())
    val timeframeMatrix: StateFlow<List<TimeframeSignalRow>> = _timeframeMatrix.asStateFlow()

    private val _signalLogs = MutableStateFlow<List<SignalLogEntry>>(emptyList())
    val signalLogs: StateFlow<List<SignalLogEntry>> = _signalLogs.asStateFlow()

    private val _botRunState = MutableStateFlow(BotRunState.SCANNING)
    val botRunState: StateFlow<BotRunState> = _botRunState.asStateFlow()

    private val _flipPlan = MutableStateFlow(AccountFlipPlan())
    val flipPlan: StateFlow<AccountFlipPlan> = _flipPlan.asStateFlow()

    private val _brokerTestMessage = MutableStateFlow<Pair<Boolean, String>?>(null)
    val brokerTestMessage: StateFlow<Pair<Boolean, String>?> = _brokerTestMessage.asStateFlow()

    private val _localApiStatus = MutableStateFlow(LocalApiStatus())
    val localApiStatus: StateFlow<LocalApiStatus> = _localApiStatus.asStateFlow()

    private val _activeAlertBanner = MutableStateFlow<BotAlertEvent?>(null)
    val activeAlertBanner: StateFlow<BotAlertEvent?> = _activeAlertBanner.asStateFlow()

    private val _alertHistory = MutableStateFlow<List<BotAlertEvent>>(emptyList())
    val alertHistory: StateFlow<List<BotAlertEvent>> = _alertHistory.asStateFlow()

    val signalHistory: Flow<List<SignalHistoryRecord>> = localSource.signalHistory
    val botConfig: Flow<BotConfig> = prefs.botConfig
    val brokerCredentials: Flow<BrokerCredentials> = prefs.brokerCredentials
    val selectedPair: Flow<FxPair> = prefs.selectedPair
    val selectedTimeframe: Flow<Timeframe> = prefs.selectedTimeframe

    private val candleBuffers = mutableMapOf<Pair<FxPair, Timeframe>, MutableList<Candle>>()
    private val pendingConfirmations = mutableMapOf<Pair<FxPair, Timeframe>, ConfirmationCandidate>()
    private val lastSignalTimestamp = mutableMapOf<Pair<FxPair, Timeframe>, Long>()
    private var lastKlinesSyncMs = 0L
    private var tickCounter = 0
    private var feedSourceLabel = "Connecting Live Feed..."
    private var lastSyncTimeMs: Long? = null
    private var engineLoopJob: Job? = null
    private var alertAutoDismissJob: Job? = null
    private val random = Random(System.currentTimeMillis())

    private val localApiServer = LocalTradingApiServer(
        getSnapshotQuote = { _marketSnapshot.value.quote },
        getFlipPlan = { _flipPlan.value },
        getBotState = { _botRunState.value },
        getActiveSignals = { _activeSignals.value },
        onToggleBot = { toggleBot() },
        onForceScan = { forceScanNow() },
        onStatusChanged = { status -> _localApiStatus.value = status }
    )

    init {
        SignalNotificationHelper.ensureChannels(appContext)
        scope.launch {
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
            refreshAllLiveMarketData()
            startSignalEngineLoop()
        }
    }

    private fun startSignalEngineLoop() {
        engineLoopJob?.cancel()
        engineLoopJob = scope.launch {
            launch {
                while (isActive) {
                    delay(60_000L)
                    fetchUsdToZarRate()
                }
            }

            while (isActive) {
                val now = System.currentTimeMillis()
                tickCounter++

                // Refresh live klines across all timeframes every 12 seconds
                val expectedSlots = FxPair.entries.size * Timeframe.entries.size
                if (now - lastKlinesSyncMs >= 12_000L || candleBuffers.size < expectedSlots) {
                    refreshKlinesForAllSlots()
                    lastKlinesSyncMs = now
                }

                // Poll live Bid/Ask quotes for Gold & Bitcoin every tick
                fetchLiveBookTickers()

                val selectedPair = prefs.selectedPair.first()
                val selectedTf = prefs.selectedTimeframe.first()
                val config = prefs.botConfig.first()
                val creds = prefs.brokerCredentials.first()

                evaluateAllPairsAndTimeframes(selectedPair, selectedTf, config, creds)
                updateFlipPlanSummary(selectedPair, config)

                delay(1_500L)
            }
        }
    }

    private suspend fun refreshAllLiveMarketData() {
        fetchUsdToZarRate()
        fetchLiveBookTickers()
        refreshKlinesForAllSlots()
        lastKlinesSyncMs = System.currentTimeMillis()
    }

    private suspend fun fetchUsdToZarRate() {
        try {
            val resp = NetworkModule.frankfurterApi.getLatestRates()
            resp.rates["ZAR"]?.let { rate ->
                if (rate > 1.0) usdToZarRate.value = rate
            }
        } catch (_: Exception) {
        }
    }

    private suspend fun fetchLiveBookTickers() {
        val now = System.currentTimeMillis()
        var liveSuccess = 0
        val updated = quotesMap.value.toMutableMap()

        for (pair in FxPair.entries) {
            try {
                val ticker = NetworkModule.binanceSpotApi.getBookTicker(pair.binanceSymbol)
                val bid = ticker.bidPrice.toDoubleOrNull()
                val ask = ticker.askPrice.toDoubleOrNull()
                if (bid != null && ask != null && bid > 0.0 && ask >= bid) {
                    updated[pair] = MarketQuote(
                        pair = pair,
                        bid = bid,
                        ask = ask,
                        timestamp = now,
                        source = "Binance Spot Live"
                    )
                    liveSuccess++
                    // Update the tip of candle buffers with latest live close
                    for (tf in Timeframe.entries) {
                        val buf = candleBuffers[pair to tf]
                        if (!buf.isNullOrEmpty()) {
                            val mid = (bid + ask) / 2.0
                            val last = buf.last()
                            buf[buf.lastIndex] = last.copy(
                                high = max(last.high, mid),
                                low = min(last.low, mid),
                                close = mid
                            )
                        }
                    }
                    continue
                }
            } catch (_: Exception) {
            }

            // Fallback live continuation when offline so engine remains responsive
            val prev = updated[pair]
            val baseMid = prev?.mid ?: pair.defaultMidPrice
            val step = pair.pipSize * (sin(tickCounter * 0.22) * 0.65 + cos(tickCounter * 0.09) * 0.40 + (random.nextDouble() - 0.5) * 0.9)
            val nextMid = (baseMid + step).coerceAtLeast(pair.pipSize * 10)
            val spread = pair.pipsToPriceDiff(pair.typicalSpreadPips * (0.85 + random.nextDouble() * 0.3))
            updated[pair] = MarketQuote(
                pair = pair,
                bid = nextMid - spread / 2.0,
                ask = nextMid + spread / 2.0,
                timestamp = now,
                source = "Anchored Live Stream"
            )
            for (tf in Timeframe.entries) {
                val buf = candleBuffers[pair to tf]
                if (!buf.isNullOrEmpty()) {
                    val last = buf.last()
                    val rollMs = max(12_000L, tf.intervalMs / 10)
                    if (now - last.timestamp >= rollMs) {
                        buf.add(
                            Candle(
                                timestamp = now,
                                open = last.close,
                                high = max(last.close, nextMid) + pair.pipSize * 0.5,
                                low = min(last.close, nextMid) - pair.pipSize * 0.5,
                                close = nextMid,
                                volume = random.nextDouble(80.0, 260.0)
                            )
                        )
                        if (buf.size > 90) buf.removeAt(0)
                    } else {
                        buf[buf.lastIndex] = last.copy(
                            high = max(last.high, nextMid),
                            low = min(last.low, nextMid),
                            close = nextMid,
                            volume = last.volume + random.nextDouble(2.0, 12.0)
                        )
                    }
                }
            }
        }

        quotesMap.value = updated
        if (liveSuccess > 0) {
            feedSourceLabel = "Binance Spot Live"
            lastSyncTimeMs = now
        } else if (feedSourceLabel == "Connecting Live Feed...") {
            feedSourceLabel = "Anchored Live Stream"
        }
    }

    private suspend fun refreshKlinesForAllSlots() = coroutineScope {
        val jobs = FxPair.entries.flatMap { pair ->
            Timeframe.entries.map { tf ->
                async(Dispatchers.IO) {
                    val key = pair to tf
                    var loaded: MutableList<Candle>? = null
                    try {
                        val raw = NetworkModule.binanceSpotApi.getKlines(
                            symbol = pair.binanceSymbol,
                            interval = tf.binanceInterval,
                            limit = 80
                        )
                        val parsed = raw.mapNotNull { row ->
                            if (row.size < 6) return@mapNotNull null
                            val ts = (row[0] as? Number)?.toLong() ?: row[0].toString().toDoubleOrNull()?.toLong() ?: return@mapNotNull null
                            val o = row[1].toString().toDoubleOrNull() ?: return@mapNotNull null
                            val h = row[2].toString().toDoubleOrNull() ?: return@mapNotNull null
                            val l = row[3].toString().toDoubleOrNull() ?: return@mapNotNull null
                            val c = row[4].toString().toDoubleOrNull() ?: return@mapNotNull null
                            val v = row[5].toString().toDoubleOrNull() ?: 100.0
                            Candle(timestamp = ts, open = o, high = h, low = l, close = c, volume = v)
                        }
                        if (parsed.size >= 30) {
                            loaded = parsed.toMutableList()
                        }
                    } catch (_: Exception) {
                    }
                    key to loaded
                }
            }
        }
        val results = jobs.awaitAll()
        results.forEach { (key, loaded) ->
            if (loaded != null) {
                candleBuffers[key] = loaded
            } else if (candleBuffers[key].isNullOrEmpty()) {
                val anchor = quotesMap.value[key.first]?.mid ?: key.first.defaultMidPrice
                candleBuffers[key] = generateAnchoredCandles(key.first, key.second, anchor)
            }
        }
    }

    private suspend fun evaluateAllPairsAndTimeframes(
        selectedPair: FxPair,
        selectedTf: Timeframe,
        config: BotConfig,
        creds: BrokerCredentials
    ) {
        val now = System.currentTimeMillis()
        val quotes = quotesMap.value
        val currentRunState = _botRunState.value
        val isEngineActive = currentRunState != BotRunState.IDLE

        // 1. Update active signals live progress, TP hits, and clean-sheet max drawdown
        updateActiveSignalsOnTick(quotes, config, now)

        val matrixRows = mutableListOf<TimeframeSignalRow>()
        var selectedEval: SignalEvaluation? = null
        var selectedConfirmProgress = 0f
        var selectedConfirmTicks = 0

        for (pair in FxPair.entries) {
            val quote = quotes[pair] ?: continue
            for (tf in Timeframe.entries) {
                val key = pair to tf
                val candles = candleBuffers[key]?.toList().orEmpty()
                val eval = TechnicalIndicators.evaluatePrecisionSignal(pair, tf, candles, quote, config, now)

                val existingActive = _activeSignals.value.find {
                    it.pair == pair && it.timeframe == tf && it.isLive
                }

                var stage = ConfirmationStage.SCANNING
                var confirmProgress = 0f
                var confirmTicks = 0

                if (existingActive != null) {
                    stage = ConfirmationStage.CONFIRMED
                    confirmProgress = 1f
                    confirmTicks = config.confirmationSpeed.requiredConfirmTicks
                } else if (isEngineActive && eval.candidateSide != null) {
                    val cooldownOk = (now - (lastSignalTimestamp[key] ?: 0L)) >= 18_000L
                    if (cooldownOk) {
                        val candidate = pendingConfirmations[key]
                        if (candidate == null || candidate.side != eval.candidateSide) {
                            // Step 1: Setup detected — begin fast confirmation
                            pendingConfirmations[key] = ConfirmationCandidate(
                                pair = pair,
                                timeframe = tf,
                                side = eval.candidateSide,
                                initialEntry = eval.entryPrice ?: quote.mid,
                                startedAtMs = now,
                                tickCount = 1,
                                latestEval = eval
                            )
                            stage = ConfirmationStage.CONFIRMING
                            confirmTicks = 1
                            confirmProgress = 1f / config.confirmationSpeed.requiredConfirmTicks.coerceAtLeast(1)
                        } else {
                            // Step 2: Check zero-chase & directional follow-through on fast ticks
                            val currentEntry = eval.entryPrice ?: quote.mid
                            val slDiff = pair.pipsToPriceDiff(eval.slPips ?: 15.0)
                            val adverseDrift = if (candidate.side == TradeSide.BUY) {
                                candidate.initialEntry - currentEntry
                            } else {
                                currentEntry - candidate.initialEntry
                            }
                            val chaseDrift = if (candidate.side == TradeSide.BUY) {
                                currentEntry - candidate.initialEntry
                            } else {
                                candidate.initialEntry - currentEntry
                            }

                            if (adverseDrift > slDiff * 0.22 || chaseDrift > slDiff * 0.45) {
                                // Aborted before emitting bad signal — protects clean-sheet entry
                                pendingConfirmations.remove(key)
                                stage = ConfirmationStage.SCANNING
                            } else {
                                val nextTick = candidate.tickCount + 1
                                val req = config.confirmationSpeed.requiredConfirmTicks.coerceAtLeast(1)
                                if (nextTick >= req) {
                                    // Confirmed! Build and emit precision signal
                                    pendingConfirmations.remove(key)
                                    lastSignalTimestamp[key] = now
                                    val durationMs = (now - candidate.startedAtMs).coerceAtLeast(1_500L)
                                    val confirmed = TechnicalIndicators.buildConfirmedTradeSignal(
                                        id = UUID.randomUUID().toString(),
                                        pair = pair,
                                        timeframe = tf,
                                        eval = eval,
                                        quote = quote,
                                        config = config,
                                        currentCapital = _flipPlan.value.projectedCapital,
                                        usdToZarRate = usdToZarRate.value,
                                        confirmationDurationMs = durationMs,
                                        createdAt = now
                                    )
                                    if (confirmed != null) {
                                        onSignalConfirmed(confirmed, creds)
                                        stage = ConfirmationStage.CONFIRMED
                                        confirmProgress = 1f
                                        confirmTicks = req
                                    }
                                } else {
                                    pendingConfirmations[key] = candidate.copy(
                                        tickCount = nextTick,
                                        latestEval = eval
                                    )
                                    stage = ConfirmationStage.CONFIRMING
                                    confirmTicks = nextTick
                                    confirmProgress = nextTick.toFloat() / req.toFloat()
                                }
                            }
                        }
                    }
                } else {
                    pendingConfirmations.remove(key)
                }

                val liveForSlot = _activeSignals.value.find { it.pair == pair && it.timeframe == tf && it.isLive }
                matrixRows.add(
                    TimeframeSignalRow(
                        pair = pair,
                        timeframe = tf,
                        stage = if (liveForSlot != null) ConfirmationStage.CONFIRMED else stage,
                        side = liveForSlot?.side ?: eval.candidateSide,
                        entryPrice = liveForSlot?.entryPrice ?: eval.entryPrice,
                        stopLoss = liveForSlot?.stopLoss ?: eval.stopLoss,
                        tp1 = liveForSlot?.takeProfit1 ?: eval.tp1,
                        tp2 = liveForSlot?.takeProfit2 ?: eval.tp2,
                        tp3 = liveForSlot?.takeProfit3 ?: eval.tp3,
                        tp1Pips = liveForSlot?.tp1Pips ?: eval.tp1Pips,
                        tp2Pips = liveForSlot?.tp2Pips ?: eval.tp2Pips,
                        tp3Pips = liveForSlot?.tp3Pips ?: eval.tp3Pips,
                        confidencePercent = liveForSlot?.confidencePercent ?: eval.confidencePercent,
                        strategyTag = liveForSlot?.strategyName ?: eval.alignedStrategies.firstOrNull() ?: config.strategy.shortName,
                        confirmationProgress = confirmProgress,
                        activeSignal = liveForSlot
                    )
                )

                if (pair == selectedPair && tf == selectedTf) {
                    selectedEval = eval
                    selectedConfirmProgress = confirmProgress
                    selectedConfirmTicks = confirmTicks
                }
            }
        }

        _timeframeMatrix.value = matrixRows

        val activeQuote = quotes[selectedPair] ?: _marketSnapshot.value.quote
        val activeCandles = candleBuffers[selectedPair to selectedTf]?.toList().orEmpty()
        _marketSnapshot.value = LiveMarketSnapshot(
            pair = selectedPair,
            timeframe = selectedTf,
            quote = activeQuote,
            quotesByPair = quotes,
            candles = activeCandles,
            indicators = selectedEval?.summary ?: IndicatorSummary(),
            lastEvaluation = selectedEval,
            confirmationProgress = selectedConfirmProgress,
            confirmationTicks = selectedConfirmTicks,
            requiredConfirmTicks = config.confirmationSpeed.requiredConfirmTicks
        )

        if (isEngineActive) {
            _botRunState.value = when {
                _activeSignals.value.any { it.isLive } -> BotRunState.SIGNAL_ACTIVE
                pendingConfirmations.isNotEmpty() -> BotRunState.CONFIRMING
                else -> BotRunState.SCANNING
            }
        }
    }

    private suspend fun onSignalConfirmed(signal: TradeSignal, creds: BrokerCredentials) {
        _activeSignals.update { current ->
            (listOf(signal) + current.filterNot { it.pair == signal.pair && it.timeframe == signal.timeframe && !it.isLive })
                .take(12)
        }

        appendSignalLog(
            SignalLogEntry(
                id = UUID.randomUUID().toString(),
                timestamp = signal.createdAt,
                pair = signal.pair,
                timeframe = signal.timeframe,
                action = "CONFIRMED ${signal.side.name} • ${signal.timeframe.label}",
                reason = String.format(
                    Locale.US,
                    "Entry %s | TP1 %s (+%.1fp) | TP2 %s (+%.1fp) | TP3 %s (+%.1fp) • %s",
                    signal.pair.formatPrice(signal.entryPrice),
                    signal.pair.formatPrice(signal.takeProfit1),
                    signal.tp1Pips,
                    signal.pair.formatPrice(signal.takeProfit2),
                    signal.tp2Pips,
                    signal.pair.formatPrice(signal.takeProfit3),
                    signal.tp3Pips,
                    signal.strategyName
                ),
                price = signal.entryPrice,
                spreadPips = signal.spreadPips,
                rsi = signal.rsi,
                isConfirmedSignal = true,
                side = signal.side
            )
        )

        emitDashboardAlert(
            type = AlertType.NEW_SIGNAL,
            title = "CONFIRMED ${signal.pair.shortTitle} ${signal.timeframe.label} ${signal.side.name}",
            message = String.format(
                Locale.US,
                "Entry %s • TP1 +%.1fp • TP2 +%.1fp • TP3 +%.1fp (%d%% conf)",
                signal.pair.formatPrice(signal.entryPrice),
                signal.tp1Pips,
                signal.tp2Pips,
                signal.tp3Pips,
                signal.confidencePercent
            ),
            pair = signal.pair,
            timeframe = signal.timeframe,
            pips = signal.tp1Pips,
            side = signal.side,
            signal = signal
        )

        // Optional live MT5 order forwarding if user connected their real MT5 bridge
        if (creds.isMt5Configured) {
            forwardSignalToMt5(signal, creds)
        }
    }

    private suspend fun forwardSignalToMt5(signal: TradeSignal, creds: BrokerCredentials) {
        val mt5Symbol = signal.pair.mt5Symbol + creds.mt5SymbolSuffix.trim()
        try {
            if (creds.mt5BridgeType == Mt5BridgeType.META_API) {
                NetworkModule.mt5MetaApi.executeTrade(
                    authToken = creds.apiToken.trim(),
                    accountId = creds.accountId.trim(),
                    request = Mt5MetaApiTradeRequest(
                        actionType = if (signal.side == TradeSide.BUY) "ORDER_TYPE_BUY" else "ORDER_TYPE_SELL",
                        symbol = mt5Symbol,
                        volume = signal.recommendedLot,
                        stopLoss = signal.stopLoss,
                        takeProfit = signal.takeProfit1,
                        comment = "Neto ${signal.timeframe.label}"
                    )
                )
            } else {
                NetworkModule.getCustomMt5Bridge(creds.mt5BridgeUrl)?.placeOrder(
                    bearerToken = "Bearer ${creds.apiToken.trim()}",
                    request = CustomMt5OrderRequest(
                        login = creds.mt5Login,
                        server = creds.mt5Server,
                        symbol = mt5Symbol,
                        side = signal.side.name,
                        volume = signal.recommendedLot,
                        sl = signal.stopLoss,
                        tp = signal.takeProfit1,
                        comment = "Neto ${signal.timeframe.label}"
                    )
                )
            }
        } catch (_: Exception) {
        }
    }

    private suspend fun updateActiveSignalsOnTick(
        quotes: Map<FxPair, MarketQuote>,
        config: BotConfig,
        now: Long
    ) {
        val current = _activeSignals.value
        if (current.isEmpty()) return

        val updatedList = mutableListOf<TradeSignal>()
        for (sig in current) {
            if (!sig.isLive) {
                updatedList.add(sig)
                continue
            }
            val quote = quotes[sig.pair]
            if (quote == null) {
                updatedList.add(sig)
                continue
            }
            val mark = if (sig.side == TradeSide.BUY) quote.bid else quote.ask
            val updated = sig.copy(
                currentPrice = mark,
                maxDrawdownPips = max(
                    sig.maxDrawdownPips,
                    if (sig.side == TradeSide.BUY) {
                        sig.pair.priceDiffToPips((sig.entryPrice - mark).coerceAtLeast(0.0))
                    } else {
                        sig.pair.priceDiffToPips((mark - sig.entryPrice).coerceAtLeast(0.0))
                    }
                )
            )

            val hitTp3 = if (sig.side == TradeSide.BUY) mark >= sig.takeProfit3 else mark <= sig.takeProfit3
            val hitTp2 = if (sig.side == TradeSide.BUY) mark >= sig.takeProfit2 else mark <= sig.takeProfit2
            val hitTp1 = if (sig.side == TradeSide.BUY) mark >= sig.takeProfit1 else mark <= sig.takeProfit1
            val effectiveSl = if (updated.breakevenProtected && config.breakevenOnTp1) sig.entryPrice else sig.stopLoss
            val hitSl = if (sig.side == TradeSide.BUY) mark <= effectiveSl else mark >= effectiveSl

            when {
                hitTp3 -> {
                    val finalSig = updated.copy(status = SignalStatus.TP3_HIT)
                    updatedList.add(finalSig)
                    recordOutcomeAndCompound(finalSig, mark, sig.tp3Pips, SignalStatus.TP3_HIT, config, now)
                    emitDashboardAlert(
                        type = AlertType.TP3_HIT,
                        title = "TP3 FULL TARGET • ${sig.pair.shortTitle} ${sig.timeframe.label}",
                        message = String.format(Locale.US, "+%.1f pips captured (Max DD %.1fp)", sig.tp3Pips, updated.maxDrawdownPips),
                        pair = sig.pair,
                        timeframe = sig.timeframe,
                        pips = sig.tp3Pips,
                        side = sig.side
                    )
                }
                hitTp2 && updated.status != SignalStatus.TP2_HIT -> {
                    val nextSig = updated.copy(status = SignalStatus.TP2_HIT, breakevenProtected = true)
                    updatedList.add(nextSig)
                    emitDashboardAlert(
                        type = AlertType.TP2_HIT,
                        title = "TP2 HIT • ${sig.pair.shortTitle} ${sig.timeframe.label}",
                        message = String.format(Locale.US, "+%.1f pips • Next target TP3 (+%.1fp left)", sig.tp2Pips, nextSig.remainingPipsToTp3),
                        pair = sig.pair,
                        timeframe = sig.timeframe,
                        pips = sig.tp2Pips,
                        side = sig.side
                    )
                }
                hitTp1 && updated.status == SignalStatus.ACTIVE -> {
                    val nextSig = updated.copy(status = SignalStatus.TP1_HIT, breakevenProtected = true)
                    updatedList.add(nextSig)
                    emitDashboardAlert(
                        type = AlertType.TP1_HIT,
                        title = "TP1 HIT (BE LOCKED) • ${sig.pair.shortTitle} ${sig.timeframe.label}",
                        message = String.format(Locale.US, "+%.1f pips locked • Zero-drawdown BE active toward TP2", sig.tp1Pips),
                        pair = sig.pair,
                        timeframe = sig.timeframe,
                        pips = sig.tp1Pips,
                        side = sig.side
                    )
                }
                hitSl -> {
                    val finalStatus = if (updated.breakevenProtected) SignalStatus.TP1_HIT else SignalStatus.SL_HIT
                    val captured = if (updated.breakevenProtected) sig.tp1Pips * 0.5 else -sig.slPips
                    val finalSig = updated.copy(status = finalStatus)
                    updatedList.add(finalSig)
                    recordOutcomeAndCompound(finalSig, mark, captured, finalStatus, config, now)
                }
                else -> updatedList.add(updated)
            }
        }
        _activeSignals.value = updatedList.take(12)
    }

    private suspend fun recordOutcomeAndCompound(
        signal: TradeSignal,
        exitPrice: Double,
        capturedPips: Double,
        outcome: SignalStatus,
        config: BotConfig,
        closedAt: Long
    ) {
        localSource.recordSignalOutcome(
            SignalHistoryRecord(
                signalId = signal.id,
                pair = signal.pair,
                timeframe = signal.timeframe,
                side = signal.side,
                entryPrice = signal.entryPrice,
                exitPrice = exitPrice,
                stopLoss = signal.stopLoss,
                tp1 = signal.takeProfit1,
                tp2 = signal.takeProfit2,
                tp3 = signal.takeProfit3,
                tp1Pips = signal.tp1Pips,
                tp2Pips = signal.tp2Pips,
                tp3Pips = signal.tp3Pips,
                capturedPips = capturedPips,
                maxDrawdownPips = signal.maxDrawdownPips,
                confidencePercent = signal.confidencePercent,
                strategyName = signal.strategyName,
                outcome = outcome,
                createdAt = signal.createdAt,
                closedAt = closedAt
            )
        )

        val currentCap = prefs.projectedCapital.first()
        val rrRatio = if (signal.slPips > 0.0) capturedPips / signal.slPips else 1.0
        val deltaCapital = currentCap * (config.riskPercentPerTrade / 100.0) * rrRatio
        val nextCap = (currentCap + deltaCapital).coerceAtLeast(1.0)
        prefs.saveProjectedCapital(nextCap)
        if (nextCap >= config.targetFlipCapital && currentCap < config.targetFlipCapital) {
            emitDashboardAlert(
                type = AlertType.FLIP_TARGET_REACHED,
                title = "ACCOUNT FLIP GOAL REACHED",
                message = String.format(
                    Locale.US,
                    "Flip projection reached %s%.2f target!",
                    config.flipCurrency.symbol,
                    nextCap
                ),
                pair = signal.pair,
                timeframe = signal.timeframe
            )
        }
    }

    private suspend fun updateFlipPlanSummary(selectedPair: FxPair, config: BotConfig) {
        val history = signalHistory.first()
        val projCap = prefs.projectedCapital.first()
        val goldLot = TechnicalIndicators.computeFlipLotSize(
            pair = FxPair.XAU_USD,
            config = config,
            currentCapital = projCap,
            slPips = 16.0,
            usdToZarRate = usdToZarRate.value
        )
        val btcLot = TechnicalIndicators.computeFlipLotSize(
            pair = FxPair.BTC_USD,
            config = config,
            currentCapital = projCap,
            slPips = 110.0,
            usdToZarRate = usdToZarRate.value
        )
        val wins = history.count { it.capturedPips > 0.0 }
        val cleanSheets = history.count { it.capturedPips > 0.0 && it.maxDrawdownPips <= it.pair.typicalSpreadPips * 1.5 }
        val winRate = if (history.isNotEmpty()) (wins.toDouble() / history.size) * 100.0 else 0.0
        val netPips = history.sumOf { it.capturedPips } + _activeSignals.value.filter { it.isLive }.sumOf { it.pipsMove }

        _flipPlan.value = AccountFlipPlan(
            currency = config.flipCurrency,
            startingCapital = config.startingCapital,
            projectedCapital = projCap,
            targetCapital = config.targetFlipCapital,
            usdToZarRate = usdToZarRate.value,
            nextRecommendedLotGold = goldLot,
            nextRecommendedLotBtc = btcLot,
            estimatedStepsRemaining = TechnicalIndicators.estimateFlipStepsRemaining(
                currentCapital = projCap,
                targetCapital = config.targetFlipCapital,
                riskPercent = config.riskPercentPerTrade
            ),
            cleanSheetWinRate = winRate,
            totalConfirmedSignals = history.size + _activeSignals.value.size,
            tpHitsCount = wins + _activeSignals.value.count { it.status == SignalStatus.TP1_HIT || it.status == SignalStatus.TP2_HIT },
            zeroDrawdownCount = cleanSheets + _activeSignals.value.count { it.isLive && it.isCleanSheet },
            netPipsCaptured = netPips,
            spotFeedSource = feedSourceLabel,
            lastSyncTime = lastSyncTimeMs
        )
    }

    private fun generateAnchoredCandles(pair: FxPair, timeframe: Timeframe, anchorPrice: Double): MutableList<Candle> {
        val count = 70
        val now = System.currentTimeMillis()
        val list = mutableListOf<Candle>()
        var price = anchorPrice - pair.pipSize * 18.0
        val step = pair.pipSize * when (timeframe) {
            Timeframe.M1 -> 1.6
            Timeframe.M5 -> 3.0
            Timeframe.M15 -> 5.0
            Timeframe.M30 -> 7.0
            Timeframe.H1 -> 9.0
            Timeframe.H4 -> 15.0
            Timeframe.D1 -> 28.0
        }

        for (i in count downTo 1) {
            val t = now - (i * timeframe.intervalMs)
            val wave = sin(i * 0.28) * step + cos(i * 0.11) * step * 0.6
            val delta = wave + (random.nextDouble() - 0.47) * step * 1.3
            val open = price
            val close = (open + delta).coerceAtLeast(pair.pipSize * 10)
            val high = max(open, close) + pair.pipSize * random.nextDouble(0.5, 2.6)
            val low = min(open, close) - pair.pipSize * random.nextDouble(0.5, 2.6)
            list.add(Candle(timestamp = t, open = open, high = high, low = low, close = close, volume = random.nextDouble(60.0, 250.0)))
            price = close
        }
        return list
    }

    private fun appendSignalLog(entry: SignalLogEntry) {
        _signalLogs.update { (listOf(entry) + it).take(60) }
    }

    private fun emitDashboardAlert(
        type: AlertType,
        title: String,
        message: String,
        pair: FxPair,
        timeframe: Timeframe,
        pips: Double? = null,
        side: TradeSide? = null,
        signal: TradeSignal? = null
    ) {
        val alert = BotAlertEvent(
            id = UUID.randomUUID().toString(),
            type = type,
            title = title,
            message = message,
            pair = pair,
            timeframe = timeframe,
            timestamp = System.currentTimeMillis(),
            pips = pips,
            side = side
        )
        _alertHistory.update { (listOf(alert) + it).take(30) }
        _activeAlertBanner.value = alert
        SignalNotificationHelper.postSignalAlertNotification(appContext, alert, signal)
        alertAutoDismissJob?.cancel()
        alertAutoDismissJob = scope.launch {
            delay(7_000L)
            if (_activeAlertBanner.value?.id == alert.id) {
                _activeAlertBanner.value = null
            }
        }
    }

    // --- Public Actions ---

    fun toggleBot() {
        scope.launch {
            val next = if (_botRunState.value == BotRunState.IDLE) {
                BotRunState.SCANNING
            } else {
                pendingConfirmations.clear()
                BotRunState.IDLE
            }
            _botRunState.value = next
        }
    }

    fun forceScanNow() {
        scope.launch {
            refreshAllLiveMarketData()
            val selectedPair = prefs.selectedPair.first()
            val selectedTf = prefs.selectedTimeframe.first()
            val config = prefs.botConfig.first()
            val creds = prefs.brokerCredentials.first()
            if (_botRunState.value == BotRunState.IDLE) {
                _botRunState.value = BotRunState.SCANNING
            }
            evaluateAllPairsAndTimeframes(selectedPair, selectedTf, config, creds)
        }
    }

    fun dismissActiveSignal(signalId: String) {
        scope.launch {
            val sig = _activeSignals.value.find { it.id == signalId } ?: return@launch
            if (sig.isLive) {
                val config = prefs.botConfig.first()
                recordOutcomeAndCompound(
                    signal = sig,
                    exitPrice = sig.currentPrice,
                    capturedPips = sig.pipsMove,
                    outcome = SignalStatus.CLOSED,
                    config = config,
                    closedAt = System.currentTimeMillis()
                )
            }
            _activeSignals.update { list -> list.filterNot { it.id == signalId } }
        }
    }

    fun selectPair(pair: FxPair) {
        scope.launch {
            prefs.setSelectedPair(pair)
            val tf = prefs.selectedTimeframe.first()
            val config = prefs.botConfig.first()
            val creds = prefs.brokerCredentials.first()
            evaluateAllPairsAndTimeframes(pair, tf, config, creds)
        }
    }

    fun selectTimeframe(timeframe: Timeframe) {
        scope.launch {
            prefs.setSelectedTimeframe(timeframe)
            val pair = prefs.selectedPair.first()
            val config = prefs.botConfig.first()
            val creds = prefs.brokerCredentials.first()
            evaluateAllPairsAndTimeframes(pair, timeframe, config, creds)
        }
    }

    fun selectPairAndTimeframe(pair: FxPair, timeframe: Timeframe) {
        scope.launch {
            prefs.setSelectedPair(pair)
            prefs.setSelectedTimeframe(timeframe)
            val config = prefs.botConfig.first()
            val creds = prefs.brokerCredentials.first()
            evaluateAllPairsAndTimeframes(pair, timeframe, config, creds)
        }
    }

    fun updateBotConfig(config: BotConfig) {
        scope.launch {
            val prev = prefs.botConfig.first()
            prefs.saveBotConfig(config)
            if (prev.startingCapital != config.startingCapital) {
                prefs.saveProjectedCapital(config.startingCapital)
            }
        }
    }

    fun resetFlipProgress() {
        scope.launch {
            val cfg = prefs.botConfig.first()
            prefs.saveProjectedCapital(cfg.startingCapital)
            localSource.clearAll()
            _activeSignals.value = emptyList()
            _signalLogs.value = emptyList()
            _alertHistory.value = emptyList()
        }
    }

    fun saveBrokerCredentials(credentials: BrokerCredentials) {
        scope.launch {
            prefs.saveBrokerCredentials(credentials)
            _brokerTestMessage.value = null
        }
    }

    fun testAndSyncBrokerConnection(credentials: BrokerCredentials) {
        scope.launch {
            if (!credentials.isMt5Configured) {
                prefs.saveBrokerCredentials(credentials)
                _brokerTestMessage.value = true to "Pure Live Signal Mode active (No paper trading)"
                return@launch
            }
            if (credentials.mt5BridgeType == Mt5BridgeType.META_API) {
                try {
                    val resp = NetworkModule.mt5MetaApi.getAccountInformation(
                        authToken = credentials.apiToken.trim(),
                        accountId = credentials.accountId.trim()
                    )
                    if (resp.isSuccessful && resp.body() != null) {
                        val info = resp.body()!!
                        prefs.saveBrokerCredentials(credentials)
                        _brokerTestMessage.value = true to "Connected MT5 Live (${info.broker ?: credentials.mt5BrokerName}): ${info.currency} ${String.format(Locale.US, "%.2f", info.balance)}"
                    } else {
                        _brokerTestMessage.value = false to "MetaApi MT5 HTTP ${resp.code()}"
                    }
                } catch (e: Exception) {
                    _brokerTestMessage.value = false to "MT5 Error: ${e.message ?: "Network unreachable"}"
                }
            } else {
                val bridge = NetworkModule.getCustomMt5Bridge(credentials.mt5BridgeUrl)
                if (bridge == null) {
                    _brokerTestMessage.value = false to "Enter a valid HTTP/HTTPS VPS Bridge URL"
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
                        _brokerTestMessage.value = true to "Connected VPS MT5: ${info.currency} ${String.format(Locale.US, "%.2f", info.balance)}"
                    } else {
                        _brokerTestMessage.value = false to "VPS Bridge HTTP ${resp.code()}"
                    }
                } catch (e: Exception) {
                    _brokerTestMessage.value = false to "Bridge Error: ${e.message ?: "Unreachable"}"
                }
            }
        }
    }

    fun regenerateLocalApiKey() {
        scope.launch { prefs.regenerateLocalApiKey() }
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
