package com.example.data.remote

import com.example.domain.model.BrokerEnvironment
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit

object NetworkModule {

    private val moshi: Moshi by lazy {
        Moshi.Builder()
            .addLast(KotlinJsonAdapterFactory())
            .build()
    }

    private val okHttpClient: OkHttpClient by lazy {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }
        OkHttpClient.Builder()
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(12, TimeUnit.SECONDS)
            .writeTimeout(12, TimeUnit.SECONDS)
            .addInterceptor(logging)
            .build()
    }

    val frankfurterApi: FrankfurterApiService by lazy {
        Retrofit.Builder()
            .baseUrl("https://api.frankfurter.dev/")
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(FrankfurterApiService::class.java)
    }

    val goldSpotApi: GoldSpotApiService by lazy {
        Retrofit.Builder()
            .baseUrl("https://api.binance.com/")
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(GoldSpotApiService::class.java)
    }

    val mt5MetaApi: Mt5MetaApiService by lazy {
        Retrofit.Builder()
            .baseUrl("https://mt-client-api-v1.agiliumtrade.agiliumtrade.ai/")
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(Mt5MetaApiService::class.java)
    }

    private var cachedCustomBridgeUrl: String? = null
    private var cachedCustomBridgeService: CustomMt5BridgeService? = null

    fun getCustomMt5Bridge(rawUrl: String): CustomMt5BridgeService? {
        val trimmed = rawUrl.trim()
        if (trimmed.isEmpty()) return null
        val normalized = if (trimmed.endsWith("/")) trimmed else "$trimmed/"
        if (!normalized.startsWith("http://") && !normalized.startsWith("https://")) return null

        if (cachedCustomBridgeUrl == normalized && cachedCustomBridgeService != null) {
            return cachedCustomBridgeService
        }
        return try {
            val service = Retrofit.Builder()
                .baseUrl(normalized)
                .client(okHttpClient)
                .addConverterFactory(MoshiConverterFactory.create(moshi))
                .build()
                .create(CustomMt5BridgeService::class.java)
            cachedCustomBridgeUrl = normalized
            cachedCustomBridgeService = service
            service
        } catch (_: Exception) {
            null
        }
    }

    private val oandaPracticeApi: OandaApiService by lazy {
        createOandaService(BrokerEnvironment.OANDA_PRACTICE.baseUrl)
    }

    private val oandaLiveApi: OandaApiService by lazy {
        createOandaService(BrokerEnvironment.OANDA_LIVE.baseUrl)
    }

    private fun createOandaService(baseUrl: String): OandaApiService {
        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(OandaApiService::class.java)
    }

    fun getOandaService(environment: BrokerEnvironment): OandaApiService? {
        return when (environment) {
            BrokerEnvironment.OANDA_PRACTICE -> oandaPracticeApi
            BrokerEnvironment.OANDA_LIVE -> oandaLiveApi
            else -> null
        }
    }
}
