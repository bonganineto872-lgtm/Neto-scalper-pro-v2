package com.example.data.remote

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

// --- Frankfurter Live FX Reference API (Zero-Key Public Spot Feed) ---

@JsonClass(generateAdapter = true)
data class FrankfurterLatestResponse(
    val amount: Double,
    val base: String,
    val date: String,
    val rates: Map<String, Double>
)

interface FrankfurterApiService {
    @GET("v1/latest")
    suspend fun getLatestRates(
        @Query("base") base: String = "USD",
        @Query("symbols") symbols: String = "EUR,GBP,JPY,AUD,CAD,ZAR"
    ): FrankfurterLatestResponse
}

@JsonClass(generateAdapter = true)
data class GoldSpotTickerDto(
    val symbol: String,
    val price: String
)

interface GoldSpotApiService {
    @GET("api/v3/ticker/price")
    suspend fun getGoldUsdSpot(
        @Query("symbol") symbol: String = "PAXGUSDT"
    ): GoldSpotTickerDto
}

// --- MT5 MetaApi Cloud Client REST API (Supports XM Global & Any MT5 Broker Demo/Live) ---

@JsonClass(generateAdapter = true)
data class Mt5MetaApiAccountInfo(
    val broker: String? = null,
    val currency: String = "ZAR",
    val server: String? = null,
    val balance: Double = 0.0,
    val equity: Double = 0.0,
    val margin: Double = 0.0,
    val freeMargin: Double = 0.0,
    val leverage: Int = 100,
    val login: Long? = null,
    val type: String? = null
)

@JsonClass(generateAdapter = true)
data class Mt5MetaApiPriceDto(
    val symbol: String,
    val bid: Double,
    val ask: Double,
    val time: String? = null
)

@JsonClass(generateAdapter = true)
data class Mt5MetaApiCandleDto(
    val time: String,
    val open: Double,
    val high: Double,
    val low: Double,
    val close: Double,
    val tickVolume: Int = 0
)

@JsonClass(generateAdapter = true)
data class Mt5MetaApiPositionDto(
    val id: String,
    val symbol: String,
    val type: String, // POSITION_TYPE_BUY / POSITION_TYPE_SELL
    val volume: Double,
    val openPrice: Double,
    val currentPrice: Double? = null,
    val stopLoss: Double? = null,
    val takeProfit: Double? = null,
    val profit: Double = 0.0,
    val time: String? = null,
    val comment: String? = null
)

@JsonClass(generateAdapter = true)
data class Mt5MetaApiTradeRequest(
    val actionType: String, // ORDER_TYPE_BUY, ORDER_TYPE_SELL, POSITION_CLOSE_ID
    val symbol: String? = null,
    val volume: Double? = null,
    val stopLoss: Double? = null,
    val takeProfit: Double? = null,
    val positionId: String? = null,
    val comment: String = "Neto Scalper Pro"
)

@JsonClass(generateAdapter = true)
data class Mt5MetaApiTradeResponse(
    val numericCode: Int? = null,
    val stringCode: String? = null,
    val message: String? = null,
    val orderId: String? = null,
    val positionId: String? = null
)

interface Mt5MetaApiService {
    @GET("users/current/accounts/{accountId}/account-information")
    suspend fun getAccountInformation(
        @Header("auth-token") authToken: String,
        @Path("accountId") accountId: String
    ): Response<Mt5MetaApiAccountInfo>

    @GET("users/current/accounts/{accountId}/symbols/{symbol}/current-price")
    suspend fun getCurrentPrice(
        @Header("auth-token") authToken: String,
        @Path("accountId") accountId: String,
        @Path("symbol") symbol: String
    ): Response<Mt5MetaApiPriceDto>

    @GET("users/current/accounts/{accountId}/historical-market-data/symbols/{symbol}/timeframes/{timeframe}/candles")
    suspend fun getHistoricalCandles(
        @Header("auth-token") authToken: String,
        @Path("accountId") accountId: String,
        @Path("symbol") symbol: String,
        @Path("timeframe") timeframe: String,
        @Query("limit") limit: Int = 70
    ): Response<List<Mt5MetaApiCandleDto>>

    @GET("users/current/accounts/{accountId}/positions")
    suspend fun getPositions(
        @Header("auth-token") authToken: String,
        @Path("accountId") accountId: String
    ): Response<List<Mt5MetaApiPositionDto>>

    @POST("users/current/accounts/{accountId}/trade")
    suspend fun executeTrade(
        @Header("auth-token") authToken: String,
        @Path("accountId") accountId: String,
        @Body request: Mt5MetaApiTradeRequest
    ): Response<Mt5MetaApiTradeResponse>
}

// --- Custom Self-Hosted MT5 VPS Bridge REST API (Connect directly to XM Global MT5 Terminal) ---

@JsonClass(generateAdapter = true)
data class CustomMt5OrderRequest(
    val login: String,
    val server: String,
    val symbol: String,
    val side: String, // BUY or SELL
    val volume: Double,
    val sl: Double,
    val tp: Double,
    val comment: String = "Neto Scalper Pro"
)

@JsonClass(generateAdapter = true)
data class CustomMt5CloseRequest(
    val positionId: String
)

interface CustomMt5BridgeService {
    @GET("mt5/account")
    suspend fun getAccount(
        @Header("Authorization") bearerToken: String,
        @Query("login") login: String,
        @Query("server") server: String
    ): Response<Mt5MetaApiAccountInfo>

    @GET("mt5/quote")
    suspend fun getQuote(
        @Header("Authorization") bearerToken: String,
        @Query("symbol") symbol: String
    ): Response<Mt5MetaApiPriceDto>

    @GET("mt5/candles")
    suspend fun getCandles(
        @Header("Authorization") bearerToken: String,
        @Query("symbol") symbol: String,
        @Query("timeframe") timeframe: String,
        @Query("count") count: Int = 70
    ): Response<List<Mt5MetaApiCandleDto>>

    @GET("mt5/positions")
    suspend fun getPositions(
        @Header("Authorization") bearerToken: String
    ): Response<List<Mt5MetaApiPositionDto>>

    @POST("mt5/order")
    suspend fun placeOrder(
        @Header("Authorization") bearerToken: String,
        @Body request: CustomMt5OrderRequest
    ): Response<Mt5MetaApiTradeResponse>

    @POST("mt5/close")
    suspend fun closePosition(
        @Header("Authorization") bearerToken: String,
        @Body request: CustomMt5CloseRequest
    ): Response<Mt5MetaApiTradeResponse>
}

// --- OANDA v20 Broker REST API ---

@JsonClass(generateAdapter = true)
data class OandaAccountSummaryResponse(
    val account: OandaAccountDto
)

@JsonClass(generateAdapter = true)
data class OandaAccountDto(
    val id: String,
    val currency: String = "USD",
    val balance: String = "0.0",
    @Json(name = "NAV") val nav: String = "0.0",
    @Json(name = "unrealizedPL") val unrealizedPl: String = "0.0",
    val marginUsed: String = "0.0",
    val marginAvailable: String = "0.0",
    val openTradeCount: Int = 0
)

@JsonClass(generateAdapter = true)
data class OandaCandlesResponse(
    val instrument: String,
    val granularity: String,
    val candles: List<OandaCandleDto>
)

@JsonClass(generateAdapter = true)
data class OandaCandleDto(
    val complete: Boolean = true,
    val volume: Int = 0,
    val time: String,
    val mid: OandaOhlcDto? = null
)

@JsonClass(generateAdapter = true)
data class OandaOhlcDto(
    val o: String,
    val h: String,
    val l: String,
    val c: String
)

@JsonClass(generateAdapter = true)
data class OandaPricingResponse(
    val prices: List<OandaPriceDto>
)

@JsonClass(generateAdapter = true)
data class OandaPriceDto(
    val instrument: String,
    val time: String,
    val bids: List<OandaQuoteBucketDto> = emptyList(),
    val asks: List<OandaQuoteBucketDto> = emptyList()
)

@JsonClass(generateAdapter = true)
data class OandaQuoteBucketDto(
    val price: String
)

@JsonClass(generateAdapter = true)
data class OandaOrderRequest(
    val order: OandaMarketOrderBody
)

@JsonClass(generateAdapter = true)
data class OandaMarketOrderBody(
    val units: String,
    val instrument: String,
    val type: String = "MARKET",
    val timeInForce: String = "FOK",
    val positionFill: String = "DEFAULT",
    val takeProfitOnFill: OandaPriceSpec? = null,
    val stopLossOnFill: OandaPriceSpec? = null
)

@JsonClass(generateAdapter = true)
data class OandaPriceSpec(
    val price: String
)

@JsonClass(generateAdapter = true)
data class OandaOrderResponse(
    val orderFillTransaction: OandaFillTransactionDto? = null,
    val errorMessage: String? = null
)

@JsonClass(generateAdapter = true)
data class OandaFillTransactionDto(
    val id: String,
    val price: String? = null,
    val units: String? = null,
    val tradeOpened: OandaTradeOpenedDto? = null
)

@JsonClass(generateAdapter = true)
data class OandaTradeOpenedDto(
    val tradeID: String
)

@JsonClass(generateAdapter = true)
data class OandaOpenTradesResponse(
    val trades: List<OandaOpenTradeDto> = emptyList()
)

@JsonClass(generateAdapter = true)
data class OandaOpenTradeDto(
    val id: String,
    val instrument: String,
    val currentUnits: String,
    val price: String,
    @Json(name = "unrealizedPL") val unrealizedPl: String = "0.0",
    val openTime: String,
    val takeProfitOrder: OandaPriceSpec? = null,
    val stopLossOrder: OandaPriceSpec? = null
)

interface OandaApiService {
    @GET("accounts/{accountId}/summary")
    suspend fun getAccountSummary(
        @Header("Authorization") bearerToken: String,
        @Path("accountId") accountId: String
    ): Response<OandaAccountSummaryResponse>

    @GET("instruments/{instrument}/candles")
    suspend fun getCandles(
        @Header("Authorization") bearerToken: String,
        @Path("instrument") instrument: String,
        @Query("granularity") granularity: String,
        @Query("count") count: Int = 80,
        @Query("price") price: String = "M"
    ): Response<OandaCandlesResponse>

    @GET("accounts/{accountId}/pricing")
    suspend fun getPricing(
        @Header("Authorization") bearerToken: String,
        @Path("accountId") accountId: String,
        @Query("instruments") instruments: String
    ): Response<OandaPricingResponse>

    @POST("accounts/{accountId}/orders")
    suspend fun placeMarketOrder(
        @Header("Authorization") bearerToken: String,
        @Path("accountId") accountId: String,
        @Body request: OandaOrderRequest
    ): Response<OandaOrderResponse>

    @GET("accounts/{accountId}/openTrades")
    suspend fun getOpenTrades(
        @Header("Authorization") bearerToken: String,
        @Path("accountId") accountId: String
    ): Response<OandaOpenTradesResponse>

    @PUT("accounts/{accountId}/trades/{tradeId}/close")
    suspend fun closeTrade(
        @Header("Authorization") bearerToken: String,
        @Path("accountId") accountId: String,
        @Path("tradeId") tradeId: String
    ): Response<Unit>
}
