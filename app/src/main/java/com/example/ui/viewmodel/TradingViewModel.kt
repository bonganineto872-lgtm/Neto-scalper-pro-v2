package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.remote.LocalApiStatus
import com.example.data.repository.BotEngineLocator
import com.example.data.repository.LiveMarketSnapshot
import com.example.data.repository.ScalpingBotRepository
import com.example.domain.model.AccountFlipPlan
import com.example.domain.model.BotAlertEvent
import com.example.domain.model.BotConfig
import com.example.domain.model.BotRunState
import com.example.domain.model.BrokerCredentials
import com.example.domain.model.FxPair
import com.example.domain.model.SignalHistoryRecord
import com.example.domain.model.SignalLogEntry
import com.example.domain.model.Timeframe
import com.example.domain.model.TimeframeSignalRow
import com.example.domain.model.TradeSignal
import com.example.service.ScalpingBotForegroundService
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class TradingUiState(
    val market: LiveMarketSnapshot = LiveMarketSnapshot(),
    val botRunState: BotRunState = BotRunState.SCANNING,
    val botConfig: BotConfig = BotConfig(),
    val brokerCredentials: BrokerCredentials = BrokerCredentials(),
    val flipPlan: AccountFlipPlan = AccountFlipPlan(),
    val activeSignals: List<TradeSignal> = emptyList(),
    val timeframeMatrix: List<TimeframeSignalRow> = emptyList(),
    val signalHistory: List<SignalHistoryRecord> = emptyList(),
    val signalLogs: List<SignalLogEntry> = emptyList(),
    val brokerTestMessage: Pair<Boolean, String>? = null,
    val localApiStatus: LocalApiStatus = LocalApiStatus(),
    val activeAlertBanner: BotAlertEvent? = null,
    val alertHistory: List<BotAlertEvent> = emptyList()
)

class TradingViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: ScalpingBotRepository = BotEngineLocator.getRepository(application)

    private val coreFlow = combine(
        repository.marketSnapshot,
        repository.botRunState,
        repository.botConfig,
        repository.brokerCredentials,
        repository.flipPlan
    ) { market, runState, config, creds, flip ->
        CoreBundle(market, runState, config, creds, flip)
    }

    private val signalsFlow = combine(
        repository.activeSignals,
        repository.timeframeMatrix,
        repository.signalHistory,
        repository.signalLogs,
        repository.brokerTestMessage
    ) { active, matrix, history, logs, testMsg ->
        SignalsBundle(active, matrix, history, logs, testMsg)
    }

    private val alertsFlow = combine(
        repository.activeAlertBanner,
        repository.alertHistory,
        repository.localApiStatus
    ) { active, history, apiStatus ->
        Triple(active, history, apiStatus)
    }

    val uiState: StateFlow<TradingUiState> = combine(
        coreFlow,
        signalsFlow,
        alertsFlow
    ) { core, signals, alerts ->
        TradingUiState(
            market = core.market,
            botRunState = core.runState,
            botConfig = core.config,
            brokerCredentials = core.creds,
            flipPlan = core.flip,
            activeSignals = signals.active,
            timeframeMatrix = signals.matrix,
            signalHistory = signals.history,
            signalLogs = signals.logs,
            brokerTestMessage = signals.testMsg,
            localApiStatus = alerts.third,
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
    fun forceScanNow() = repository.forceScanNow()
    fun dismissActiveSignal(signalId: String) = repository.dismissActiveSignal(signalId)
    fun selectPair(pair: FxPair) = repository.selectPair(pair)
    fun selectTimeframe(timeframe: Timeframe) = repository.selectTimeframe(timeframe)
    fun selectPairAndTimeframe(pair: FxPair, timeframe: Timeframe) = repository.selectPairAndTimeframe(pair, timeframe)
    fun updateBotConfig(config: BotConfig) = repository.updateBotConfig(config)
    fun resetFlipProgress() = repository.resetFlipProgress()
    fun saveBrokerCredentials(creds: BrokerCredentials) = repository.saveBrokerCredentials(creds)
    fun testAndSyncBrokerConnection(creds: BrokerCredentials) = repository.testAndSyncBrokerConnection(creds)
    fun regenerateLocalApiKey() = repository.regenerateLocalApiKey()
    fun dismissActiveAlert() = repository.dismissActiveAlert()
    fun clearAlertHistory() = repository.clearAlertHistory()

    private data class CoreBundle(
        val market: LiveMarketSnapshot,
        val runState: BotRunState,
        val config: BotConfig,
        val creds: BrokerCredentials,
        val flip: AccountFlipPlan
    )

    private data class SignalsBundle(
        val active: List<TradeSignal>,
        val matrix: List<TimeframeSignalRow>,
        val history: List<SignalHistoryRecord>,
        val logs: List<SignalLogEntry>,
        val testMsg: Pair<Boolean, String>?
    )
}
