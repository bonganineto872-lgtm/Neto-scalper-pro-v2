package com.example.data.repository

import android.content.Context
import com.example.data.local.BotDatabase
import com.example.data.local.BotPreferencesRepository
import com.example.data.local.SignalHistoryLocalSource

object BotEngineLocator {
    @Volatile
    private var instance: ScalpingBotRepository? = null

    fun getRepository(context: Context): ScalpingBotRepository {
        return instance ?: synchronized(this) {
            instance ?: run {
                val appContext = context.applicationContext
                val db = BotDatabase.getInstance(appContext)
                val prefs = BotPreferencesRepository(appContext)
                val localSource = SignalHistoryLocalSource(db.botDao())
                ScalpingBotRepository(
                    appContext = appContext,
                    prefs = prefs,
                    localSource = localSource
                ).also {
                    instance = it
                }
            }
        }
    }
}
