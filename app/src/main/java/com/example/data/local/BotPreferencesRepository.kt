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
import com.example.domain.model.ConfirmationSpeed
import com.example.domain.model.FlipCurrency
import com.example.domain.model.FxPair
import com.example.domain.model.GoldBtcStrategyType
import com.example.domain.model.Mt5BridgeType
import com.example.domain.model.SessionFilter
import com.example.domain.model.Timeframe
import com.example.domain.model.TradeDirectionFilter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.security.SecureRandom

private val Context.botDataStore: DataStore<Preferences> by preferencesDataStore(name = "pippulse_signals_v3")

class BotPreferencesRepository(private val context: Context) {

    private object Keys {
        val SELECTED_PAIR = stringPreferencesKey("selected_pair")
        val SELECTED_TIMEFRAME = stringPreferencesKey("selected_timeframe")
        val STRATEGY = stringPreferencesKey("strategy")
        val DIRECTION_FILTER = stringPreferencesKey("direction_filter")
        val CONFIRMATION_SPEED = stringPreferencesKey("confirmation_speed")
        val MIN_CONFIDENCE = intPreferencesKey("min_confidence")
        val MAX_GOLD_SPREAD = doublePreferencesKey("max_gold_spread")
        val MAX_BTC_SPREAD = doublePreferencesKey("max_btc_spread")
        val RISK_PERCENT = doublePreferencesKey("risk_percent")
        val FLIP_CURRENCY = stringPreferencesKey("flip_currency")
        val STARTING_CAPITAL = doublePreferencesKey("starting_capital")
        val TARGET_CAPITAL = doublePreferencesKey("target_capital")
        val PROJECTED_CAPITAL = doublePreferencesKey("projected_capital")
        val AUTO_COMPOUND_FLIP = booleanPreferencesKey("auto_compound_flip")
        val BASE_LOT_SIZE = doublePreferencesKey("base_lot_size")
        val BREAKEVEN_ON_TP1 = booleanPreferencesKey("breakeven_on_tp1")
        val KEEP_RUNNING_ON_PHONE = booleanPreferencesKey("keep_running_on_phone")
        val SESSION_FILTER = stringPreferencesKey("session_filter")
        val SHOW_BB = booleanPreferencesKey("show_bb")
        val SHOW_EMA = booleanPreferencesKey("show_ema")
        val SHOW_VWAP = booleanPreferencesKey("show_vwap")
        val BROKER_ENV = stringPreferencesKey("broker_env")
        val API_TOKEN = stringPreferencesKey("mt5_token")
        val ACCOUNT_ID = stringPreferencesKey("mt5_account_id")
        val MT5_BROKER_NAME = stringPreferencesKey("mt5_broker_name")
        val MT5_SERVER = stringPreferencesKey("mt5_server")
        val MT5_LOGIN = stringPreferencesKey("mt5_login")
        val MT5_BRIDGE_TYPE = stringPreferencesKey("mt5_bridge_type")
        val MT5_BRIDGE_URL = stringPreferencesKey("mt5_bridge_url")
        val MT5_SYMBOL_SUFFIX = stringPreferencesKey("mt5_symbol_suffix")
        val LOCAL_API_ENABLED = booleanPreferencesKey("local_api_enabled")
        val LOCAL_API_PORT = intPreferencesKey("local_api_port")
        val LOCAL_API_KEY = stringPreferencesKey("local_api_key")
    }

    val selectedPair: Flow<FxPair> = context.botDataStore.data.map { prefs ->
        FxPair.entries.find { it.name == prefs[Keys.SELECTED_PAIR] } ?: FxPair.XAU_USD
    }

    val selectedTimeframe: Flow<Timeframe> = context.botDataStore.data.map { prefs ->
        Timeframe.entries.find { it.name == prefs[Keys.SELECTED_TIMEFRAME] } ?: Timeframe.M5
    }

    val projectedCapital: Flow<Double> = context.botDataStore.data.map { prefs ->
        prefs[Keys.PROJECTED_CAPITAL] ?: prefs[Keys.STARTING_CAPITAL] ?: 50.0
    }

    val botConfig: Flow<BotConfig> = context.botDataStore.data.map { prefs ->
        BotConfig(
            strategy = GoldBtcStrategyType.entries.find { it.name == prefs[Keys.STRATEGY] }
                ?: GoldBtcStrategyType.APEX_CONFLUENCE,
            directionFilter = TradeDirectionFilter.entries.find { it.name == prefs[Keys.DIRECTION_FILTER] }
                ?: TradeDirectionFilter.BOTH,
            confirmationSpeed = ConfirmationSpeed.entries.find { it.name == prefs[Keys.CONFIRMATION_SPEED] }
                ?: ConfirmationSpeed.ULTRA_FAST,
            minConfidencePercent = prefs[Keys.MIN_CONFIDENCE] ?: 82,
            maxGoldSpreadPips = prefs[Keys.MAX_GOLD_SPREAD] ?: 4.5,
            maxBtcSpreadPips = prefs[Keys.MAX_BTC_SPREAD] ?: 35.0,
            riskPercentPerTrade = prefs[Keys.RISK_PERCENT] ?: 5.0,
            flipCurrency = FlipCurrency.entries.find { it.name == prefs[Keys.FLIP_CURRENCY] }
                ?: FlipCurrency.USD,
            startingCapital = prefs[Keys.STARTING_CAPITAL] ?: 50.0,
            targetFlipCapital = prefs[Keys.TARGET_CAPITAL] ?: 2500.0,
            autoCompoundFlipLot = prefs[Keys.AUTO_COMPOUND_FLIP] ?: true,
            baseLotSize = prefs[Keys.BASE_LOT_SIZE] ?: 0.01,
            breakevenOnTp1 = prefs[Keys.BREAKEVEN_ON_TP1] ?: true,
            keepRunningOnPhone = prefs[Keys.KEEP_RUNNING_ON_PHONE] ?: true,
            sessionFilter = SessionFilter.entries.find { it.name == prefs[Keys.SESSION_FILTER] }
                ?: SessionFilter.ALL_24H,
            showBollingerBands = prefs[Keys.SHOW_BB] ?: true,
            showEmaLines = prefs[Keys.SHOW_EMA] ?: true,
            showVwapLine = prefs[Keys.SHOW_VWAP] ?: true
        )
    }

    val brokerCredentials: Flow<BrokerCredentials> = context.botDataStore.data.map { prefs ->
        BrokerCredentials(
            environment = BrokerEnvironment.entries.find { it.name == prefs[Keys.BROKER_ENV] }
                ?: BrokerEnvironment.SIGNAL_ENGINE,
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
        context.botDataStore.edit { it[Keys.LOCAL_API_KEY] = generated }
        return generated
    }

    suspend fun regenerateLocalApiKey(): String {
        val generated = generateSecureApiKey()
        context.botDataStore.edit { it[Keys.LOCAL_API_KEY] = generated }
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
            prefs[Keys.CONFIRMATION_SPEED] = config.confirmationSpeed.name
            prefs[Keys.MIN_CONFIDENCE] = config.minConfidencePercent
            prefs[Keys.MAX_GOLD_SPREAD] = config.maxGoldSpreadPips
            prefs[Keys.MAX_BTC_SPREAD] = config.maxBtcSpreadPips
            prefs[Keys.RISK_PERCENT] = config.riskPercentPerTrade
            prefs[Keys.FLIP_CURRENCY] = config.flipCurrency.name
            prefs[Keys.STARTING_CAPITAL] = config.startingCapital
            prefs[Keys.TARGET_CAPITAL] = config.targetFlipCapital
            prefs[Keys.AUTO_COMPOUND_FLIP] = config.autoCompoundFlipLot
            prefs[Keys.BASE_LOT_SIZE] = config.baseLotSize
            prefs[Keys.BREAKEVEN_ON_TP1] = config.breakevenOnTp1
            prefs[Keys.KEEP_RUNNING_ON_PHONE] = config.keepRunningOnPhone
            prefs[Keys.SESSION_FILTER] = config.sessionFilter.name
            prefs[Keys.SHOW_BB] = config.showBollingerBands
            prefs[Keys.SHOW_EMA] = config.showEmaLines
            prefs[Keys.SHOW_VWAP] = config.showVwapLine
        }
    }

    suspend fun saveProjectedCapital(capital: Double) {
        context.botDataStore.edit { it[Keys.PROJECTED_CAPITAL] = capital }
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
}
