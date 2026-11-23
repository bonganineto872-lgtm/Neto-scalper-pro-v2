package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.remote.LocalApiStatus
import com.example.data.repository.BotEngineLocator
import com.example.data.repository.LiveMarketSnapshot
import com.example.data.repository.ScalpingBotRepository
import com.example.domain.model.AccountSummary
import com.example.domain.model.BotAlertEvent
import com.example.domain.model.BotConfig
import com.example.domain.model.BotRunState
import com.example.domain.model.BrokerCredentials
import com.example.domain.model.ClosedTradeRecord
import com.example.domain.model.EquityPoint
import com.example.domain.model.FxPair
import com.example.domain.model.SignalLogEntry
import com.example.domain.model.Timeframe
import com.example.domain.model.TradePosition
import com.example.domain.model.TradeSide
import com.example.domain.model.TradingRiskProfile
import com.example.service.ScalpingBotForegroundService
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class TradingUiState(
    val market: LiveMarketSnapshot = LiveMarketSnapshot(),
    val botRunState: BotRunState = BotRunState.IDLE,
    val botConfig: BotConfig = BotConfig(),
    val brokerCredentials: BrokerCredentials = BrokerCredentials(),
    val accountSummary: AccountSummary = AccountSummary(),
    val openPositions: List<TradePosition> = emptyList(),
    val tradeHistory: List<ClosedTradeRecord> = emptyList(),
    val equityCurve: List<EquityPoint> = emptyList(),
    val signalLogs: List<SignalLogEntry> = emptyList(),
    val brokerTestMessage: Pair<Boolean, String>? = null,
    val localApiStatus: LocalApiStatus = LocalApiStatus(),
    val liveEquityTicks: List<EquityPoint> = emptyList(),
    val activeAlertBanner: BotAlertEvent? = null,
    val alertHistory: List<BotAlertEvent> = emptyList()
) {
    val winRatePercent: Double
        get() {
            if (tradeHistory.isEmpty()) return 0.0
            val wins = tradeHistory.count { it.realizedPnlZar > 0.0 }
            return (wins.toDouble() / tradeHistory.size) * 100.0
        }

    val totalRealizedPnl: Double
        get() = tradeHistory.sumOf { it.realizedPnlZar }

    val totalPips: Double
        get() = tradeHistory.sumOf { it.pipsPnl }
}

class TradingViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: ScalpingBotRepository = BotEngineLocator.getRepository(application)

    private val coreMarketFlow = combine(
        repository.marketSnapshot,
        repository.botRunState,
        repository.botConfig,
        repository.brokerCredentials,
        repository.accountSummary
    ) { market, runState, config, creds, acct ->
        CoreBundle(market, runState, config, creds, acct)
    }

    private val ledgerFlow = combine(
        repository.openPositions,
        repository.tradeHistory,
        repository.equityCurve,
        repository.signalLogs,
        repository.brokerTestMessage
    ) { open, history, equity, logs, testMsg ->
        LedgerBundle(open, history, equity, logs, testMsg)
    }

    private val alertsFlow = combine(
        repository.activeAlertBanner,
        repository.alertHistory
    ) { active, history -> active to history }

    val uiState: StateFlow<TradingUiState> = combine(
        coreMarketFlow,
        ledgerFlow,
        repository.localApiStatus,
        repository.liveEquityTicks,
        alertsFlow
    ) { core, ledger, apiStatus, liveTicks, alerts ->
        TradingUiState(
            market = core.market,
            botRunState = core.runState,
            botConfig = core.config,
            brokerCredentials = core.creds,
            accountSummary = core.acct,
            openPositions = ledger.open,
            tradeHistory = ledger.history,
            equityCurve = ledger.equity,
            signalLogs = ledger.logs,
            brokerTestMessage = ledger.testMsg,
            localApiStatus = apiStatus,
            liveEquityTicks = liveTicks,
            activeAlertBanner = alerts.first,
            alertHistory = alerts.second
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000L),
        initialValue = TradingUiState()
    )

    init {
        viewModelScope.launch {
            var lastKeepRunning: Boolean? = null
            repository.botConfig.collect { cfg ->
                if (lastKeepRunning != cfg.keepRunningOnPhone) {
                    lastKeepRunning = cfg.keepRunningOnPhone
                    ScalpingBotForegroundService.syncServiceState(
                        getApplication(),
                        cfg.keepRunningOnPhone
                    )
                }
            }
        }
    }

    fun toggleBot() = repository.toggleBot()
    fun placeManualOrder(side: TradeSide) = repository.placeManualOrder(side)
    fun closePosition(positionId: String) = repository.closePosition(positionId)
    fun emergencyFlattenAll() = repository.emergencyFlattenAll()
    fun selectPair(pair: FxPair) = repository.selectPair(pair)
    fun selectTimeframe(timeframe: Timeframe) = repository.selectTimeframe(timeframe)
    fun updateBotConfig(config: BotConfig) = repository.updateBotConfig(config)
    fun applyRiskProfile(profile: TradingRiskProfile) = repository.applyRiskProfile(profile)
    fun saveBrokerCredentials(creds: BrokerCredentials) = repository.saveBrokerCredentials(creds)
    fun testAndSyncBrokerConnection(creds: BrokerCredentials) = repository.testAndSyncBrokerConnection(creds)
    fun resetPaperAccount() = repository.resetPaperAccount()
    fun refreshSpotRatesNow() = repository.refreshSpotRatesNow()
    fun regenerateLocalApiKey() = repository.regenerateLocalApiKey()
    fun dismissActiveAlert() = repository.dismissActiveAlert()
    fun clearAlertHistory() = repository.clearAlertHistory()

    private data class CoreBundle(
        val market: LiveMarketSnapshot,
        val runState: BotRunState,
        val config: BotConfig,
        val creds: BrokerCredentials,
        val acct: AccountSummary
    )

    private data class LedgerBundle(
        val open: List<TradePosition>,
        val history: List<ClosedTradeRecord>,
        val equity: List<EquityPoint>,
        val logs: List<SignalLogEntry>,
        val testMsg: Pair<Boolean, String>?
    )
}
