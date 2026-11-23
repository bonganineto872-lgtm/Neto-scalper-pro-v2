package com.example.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.domain.model.BotConfig
import com.example.domain.model.BrokerCredentials
import com.example.domain.model.BrokerEnvironment
import com.example.domain.model.FxPair
import com.example.domain.model.Mt5BridgeType
import com.example.domain.model.ScalpingStrategyType
import com.example.domain.model.SessionFilter
import com.example.domain.model.Timeframe
import com.example.domain.model.TradeDirectionFilter
import com.example.domain.model.TradingRiskProfile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.security.SecureRandom

private val Context.botDataStore: DataStore<Preferences> by preferencesDataStore(name = "pippulse_prefs_v2")

class BotPreferencesRepository(private val context: Context) {

    private object Keys {
        val SELECTED_PAIR = stringPreferencesKey("selected_pair")
        val SELECTED_TIMEFRAME = stringPreferencesKey("selected_timeframe")
        val STRATEGY = stringPreferencesKey("strategy")
        val DIRECTION_FILTER = stringPreferencesKey("direction_filter")
        val RISK_PROFILE = stringPreferencesKey("risk_profile")
        val FAST_EMA = intPreferencesKey("fast_ema")
        val SLOW_EMA = intPreferencesKey("slow_ema")
        val RSI_PERIOD = intPreferencesKey("rsi_period")
        val RSI_OVERSOLD = intPreferencesKey("rsi_oversold")
        val RSI_OVERBOUGHT = intPreferencesKey("rsi_overbought")
        val LOT_SIZE = doublePreferencesKey("lot_size")
        val AUTO_COMPOUND = booleanPreferencesKey("auto_compound")
        val STOP_LOSS_PIPS = doublePreferencesKey("sl_pips")
        val TAKE_PROFIT_PIPS = doublePreferencesKey("tp_pips")
        val MAX_SPREAD_PIPS = doublePreferencesKey("max_spread_pips")
        val MAX_OPEN_TRADES = intPreferencesKey("max_open_trades")
        val SCALE_IN_ON_PROFIT = booleanPreferencesKey("scale_in_on_profit")
        val SCALE_IN_STEP_PIPS = doublePreferencesKey("scale_in_step_pips")
        val DAILY_LOSS_LIMIT_ZAR = doublePreferencesKey("daily_loss_limit_zar")
        val TRAILING_STOP = booleanPreferencesKey("trailing_stop")
        val TRAILING_TRIGGER = doublePreferencesKey("trailing_trigger")
        val BREAKEVEN_LOCK = booleanPreferencesKey("breakeven_lock")
        val BREAKEVEN_TRIGGER = doublePreferencesKey("breakeven_trigger")
        val TARGET_PROFIT_STOP_ZAR = doublePreferencesKey("target_profit_stop_zar")
        val STOP_ON_TARGET = booleanPreferencesKey("stop_on_target")
        val KEEP_RUNNING_ON_PHONE = booleanPreferencesKey("keep_running_on_phone")
        val SESSION_FILTER = stringPreferencesKey("session_filter")
        val SHOW_BB = booleanPreferencesKey("show_bb")
        val SHOW_EMA = booleanPreferencesKey("show_ema")
        val BROKER_ENV = stringPreferencesKey("broker_env")
        val API_TOKEN = stringPreferencesKey("oanda_token")
        val ACCOUNT_ID = stringPreferencesKey("oanda_account_id")
        val MT5_BROKER_NAME = stringPreferencesKey("mt5_broker_name")
        val MT5_SERVER = stringPreferencesKey("mt5_server")
        val MT5_LOGIN = stringPreferencesKey("mt5_login")
        val MT5_BRIDGE_TYPE = stringPreferencesKey("mt5_bridge_type")
        val MT5_BRIDGE_URL = stringPreferencesKey("mt5_bridge_url")
        val MT5_SYMBOL_SUFFIX = stringPreferencesKey("mt5_symbol_suffix")
        val LOCAL_API_ENABLED = booleanPreferencesKey("local_api_enabled")
        val LOCAL_API_PORT = intPreferencesKey("local_api_port")
        val LOCAL_API_KEY = stringPreferencesKey("local_api_key")
        val PAPER_BALANCE_ZAR = doublePreferencesKey("paper_balance_zar")
    }

    val selectedPair: Flow<FxPair> = context.botDataStore.data.map { prefs ->
        FxPair.entries.find { it.name == prefs[Keys.SELECTED_PAIR] } ?: FxPair.XAU_USD
    }

    val selectedTimeframe: Flow<Timeframe> = context.botDataStore.data.map { prefs ->
        Timeframe.entries.find { it.name == prefs[Keys.SELECTED_TIMEFRAME] } ?: Timeframe.M1
    }

    val paperBalance: Flow<Double> = context.botDataStore.data.map { prefs ->
        prefs[Keys.PAPER_BALANCE_ZAR] ?: 200.0
    }

    val botConfig: Flow<BotConfig> = context.botDataStore.data.map { prefs ->
        BotConfig(
            strategy = ScalpingStrategyType.entries.find { it.name == prefs[Keys.STRATEGY] }
                ?: ScalpingStrategyType.EMA_RSI_MOMENTUM,
            directionFilter = TradeDirectionFilter.entries.find { it.name == prefs[Keys.DIRECTION_FILTER] }
                ?: TradeDirectionFilter.BOTH,
            riskProfile = TradingRiskProfile.entries.find { it.name == prefs[Keys.RISK_PROFILE] }
                ?: TradingRiskProfile.AGGRESSIVE_1M_FLIP,
            fastEmaPeriod = prefs[Keys.FAST_EMA] ?: 9,
            slowEmaPeriod = prefs[Keys.SLOW_EMA] ?: 21,
            rsiPeriod = prefs[Keys.RSI_PERIOD] ?: 14,
            rsiOversold = prefs[Keys.RSI_OVERSOLD] ?: 32,
            rsiOverbought = prefs[Keys.RSI_OVERBOUGHT] ?: 68,
            lotSize = prefs[Keys.LOT_SIZE] ?: 0.02,
            autoCompoundSizing = prefs[Keys.AUTO_COMPOUND] ?: true,
            stopLossPips = prefs[Keys.STOP_LOSS_PIPS] ?: 6.0,
            takeProfitPips = prefs[Keys.TAKE_PROFIT_PIPS] ?: 12.0,
            maxSpreadPips = prefs[Keys.MAX_SPREAD_PIPS] ?: 3.5,
            maxOpenTrades = prefs[Keys.MAX_OPEN_TRADES] ?: 4,
            scaleInOnProfit = prefs[Keys.SCALE_IN_ON_PROFIT] ?: true,
            scaleInStepPips = prefs[Keys.SCALE_IN_STEP_PIPS] ?: 2.5,
            dailyLossLimitZar = prefs[Keys.DAILY_LOSS_LIMIT_ZAR] ?: 180.0,
            trailingStopEnabled = prefs[Keys.TRAILING_STOP] ?: true,
            trailingTriggerPips = prefs[Keys.TRAILING_TRIGGER] ?: 4.5,
            breakevenLockEnabled = prefs[Keys.BREAKEVEN_LOCK] ?: true,
            breakevenTriggerPips = prefs[Keys.BREAKEVEN_TRIGGER] ?: 3.5,
            targetProfitStopZar = prefs[Keys.TARGET_PROFIT_STOP_ZAR] ?: 10_000.0,
            stopOnTargetReached = prefs[Keys.STOP_ON_TARGET] ?: true,
            keepRunningOnPhone = prefs[Keys.KEEP_RUNNING_ON_PHONE] ?: false,
            sessionFilter = SessionFilter.entries.find { it.name == prefs[Keys.SESSION_FILTER] }
                ?: SessionFilter.ALL_24H,
            showBollingerBands = prefs[Keys.SHOW_BB] ?: true,
            showEmaLines = prefs[Keys.SHOW_EMA] ?: true
        )
    }

    val brokerCredentials: Flow<BrokerCredentials> = context.botDataStore.data.map { prefs ->
        BrokerCredentials(
            environment = BrokerEnvironment.entries.find { it.name == prefs[Keys.BROKER_ENV] }
                ?: BrokerEnvironment.PAPER_SIMULATION,
            apiToken = prefs[Keys.API_TOKEN] ?: "",
            accountId = prefs[Keys.ACCOUNT_ID] ?: "",
            mt5BrokerName = prefs[Keys.MT5_BROKER_NAME] ?: "XM Global",
            mt5Server = prefs[Keys.MT5_SERVER] ?: "XMGlobal-MT5",
            mt5Login = prefs[Keys.MT5_LOGIN] ?: "",
            mt5BridgeType = Mt5BridgeType.entries.find { it.name == prefs[Keys.MT5_BRIDGE_TYPE] }
                ?: Mt5BridgeType.META_API,
            mt5BridgeUrl = prefs[Keys.MT5_BRIDGE_URL] ?: "",
            mt5SymbolSuffix = prefs[Keys.MT5_SYMBOL_SUFFIX] ?: "",
            localApiEnabled = prefs[Keys.LOCAL_API_ENABLED] ?: true,
            localApiPort = prefs[Keys.LOCAL_API_PORT] ?: 8787,
            localApiKey = prefs[Keys.LOCAL_API_KEY] ?: ""
        )
    }

    suspend fun ensureLocalApiKey(): String {
        val current = context.botDataStore.data.first()[Keys.LOCAL_API_KEY]
        if (!current.isNullOrBlank()) return current
        val generated = generateSecureApiKey()
        context.botDataStore.edit { prefs ->
            prefs[Keys.LOCAL_API_KEY] = generated
        }
        return generated
    }

    suspend fun regenerateLocalApiKey(): String {
        val generated = generateSecureApiKey()
        context.botDataStore.edit { prefs ->
            prefs[Keys.LOCAL_API_KEY] = generated
        }
        return generated
    }

    private fun generateSecureApiKey(): String {
        val alphabet = "abcdefghjkmnpqrstuvwxyz23456789"
        val rnd = SecureRandom()
        return "nsp_" + (1..20).map { alphabet[rnd.nextInt(alphabet.length)] }.joinToString("")
    }

    suspend fun setSelectedPair(pair: FxPair) {
        context.botDataStore.edit { it[Keys.SELECTED_PAIR] = pair.name }
    }

    suspend fun setSelectedTimeframe(timeframe: Timeframe) {
        context.botDataStore.edit { it[Keys.SELECTED_TIMEFRAME] = timeframe.name }
    }

    suspend fun saveBotConfig(config: BotConfig) {
        context.botDataStore.edit { prefs ->
            prefs[Keys.STRATEGY] = config.strategy.name
            prefs[Keys.DIRECTION_FILTER] = config.directionFilter.name
            prefs[Keys.RISK_PROFILE] = config.riskProfile.name
            prefs[Keys.FAST_EMA] = config.fastEmaPeriod
            prefs[Keys.SLOW_EMA] = config.slowEmaPeriod
            prefs[Keys.RSI_PERIOD] = config.rsiPeriod
            prefs[Keys.RSI_OVERSOLD] = config.rsiOversold
            prefs[Keys.RSI_OVERBOUGHT] = config.rsiOverbought
            prefs[Keys.LOT_SIZE] = config.lotSize
            prefs[Keys.AUTO_COMPOUND] = config.autoCompoundSizing
            prefs[Keys.STOP_LOSS_PIPS] = config.stopLossPips
            prefs[Keys.TAKE_PROFIT_PIPS] = config.takeProfitPips
            prefs[Keys.MAX_SPREAD_PIPS] = config.maxSpreadPips
            prefs[Keys.MAX_OPEN_TRADES] = config.maxOpenTrades
            prefs[Keys.SCALE_IN_ON_PROFIT] = config.scaleInOnProfit
            prefs[Keys.SCALE_IN_STEP_PIPS] = config.scaleInStepPips
            prefs[Keys.DAILY_LOSS_LIMIT_ZAR] = config.dailyLossLimitZar
            prefs[Keys.TRAILING_STOP] = config.trailingStopEnabled
            prefs[Keys.TRAILING_TRIGGER] = config.trailingTriggerPips
            prefs[Keys.BREAKEVEN_LOCK] = config.breakevenLockEnabled
            prefs[Keys.BREAKEVEN_TRIGGER] = config.breakevenTriggerPips
            prefs[Keys.TARGET_PROFIT_STOP_ZAR] = config.targetProfitStopZar
            prefs[Keys.STOP_ON_TARGET] = config.stopOnTargetReached
            prefs[Keys.KEEP_RUNNING_ON_PHONE] = config.keepRunningOnPhone
            prefs[Keys.SESSION_FILTER] = config.sessionFilter.name
            prefs[Keys.SHOW_BB] = config.showBollingerBands
            prefs[Keys.SHOW_EMA] = config.showEmaLines
        }
    }

    suspend fun saveBrokerCredentials(credentials: BrokerCredentials) {
        val currentKey = ensureLocalApiKey()
        context.botDataStore.edit { prefs ->
            prefs[Keys.BROKER_ENV] = credentials.environment.name
            prefs[Keys.API_TOKEN] = credentials.apiToken.trim()
            prefs[Keys.ACCOUNT_ID] = credentials.accountId.trim()
            prefs[Keys.MT5_BROKER_NAME] = credentials.mt5BrokerName.trim().ifEmpty { "XM Global" }
            prefs[Keys.MT5_SERVER] = credentials.mt5Server.trim().ifEmpty { "XMGlobal-MT5" }
            prefs[Keys.MT5_LOGIN] = credentials.mt5Login.trim()
            prefs[Keys.MT5_BRIDGE_TYPE] = credentials.mt5BridgeType.name
            prefs[Keys.MT5_BRIDGE_URL] = credentials.mt5BridgeUrl.trim()
            prefs[Keys.MT5_SYMBOL_SUFFIX] = credentials.mt5SymbolSuffix.trim()
            prefs[Keys.LOCAL_API_ENABLED] = credentials.localApiEnabled
            prefs[Keys.LOCAL_API_PORT] = credentials.localApiPort.coerceIn(1024, 65535)
            prefs[Keys.LOCAL_API_KEY] = credentials.localApiKey.trim().ifEmpty { currentKey }
        }
    }

    suspend fun savePaperBalance(balance: Double) {
        context.botDataStore.edit { it[Keys.PAPER_BALANCE_ZAR] = balance }
    }
}
