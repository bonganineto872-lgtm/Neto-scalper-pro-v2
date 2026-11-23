package com.example.data.repository

import android.content.Context
import com.example.data.local.BotDatabase
import com.example.data.local.BotPreferencesRepository
import com.example.data.local.TradeHistoryLocalSource

/**
 * Process-scoped locator so the UI ViewModel and Foreground Service share one live trading engine.
 */
object BotEngineLocator {
    @Volatile
    private var instance: ScalpingBotRepository? = null

    fun getRepository(context: Context): ScalpingBotRepository {
        return instance ?: synchronized(this) {
            instance ?: run {
                val appContext = context.applicationContext
                val db = BotDatabase.getInstance(appContext)
                val prefs = BotPreferencesRepository(appContext)
                val localSource = TradeHistoryLocalSource(db.botDao())
                ScalpingBotRepository(prefs = prefs, localSource = localSource).also {
                    instance = it
                }
            }
        }
    }
}
