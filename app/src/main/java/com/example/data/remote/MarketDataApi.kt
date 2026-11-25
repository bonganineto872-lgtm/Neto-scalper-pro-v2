package com.example.data.remote

import com.squareup.moshi.JsonClass
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

// --- Frankfurter Live FX Reference API (Zero-Key USD/ZAR Conversion Feed) ---

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
        @Query("symbols") symbols: String = "ZAR"
    ): FrankfurterLatestResponse
}

// --- Binance Public Spot Feed for Real-Time Gold (PAXGUSDT) & Bitcoin (BTCUSDT) ---

@JsonClass(generateAdapter = true)
data class BinanceBookTickerDto(
    val symbol: String,
    val bidPrice: String,
    val bidQty: String,
    val askPrice: String,
    val askQty: String
)

interface BinanceSpotApiService {
    @GET("api/v3/ticker/bookTicker")
    suspend fun getBookTicker(
        @Query("symbol") symbol: String
    ): BinanceBookTickerDto

    @GET("api/v3/klines")
    suspend fun getKlines(
        @Query("symbol") symbol: String,
        @Query("interval") interval: String,
        @Query("limit") limit: Int = 80
    ): List<List<Any>>
}

// --- MT5 Live Bridge REST API (Optional Live Signal Execution on XM Global / Any MT5 Broker) ---

@JsonClass(generateAdapter = true)
data class Mt5MetaApiAccountInfo(
    val broker: String? = null,
    val currency: String = "USD",
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
data class Mt5MetaApiTradeRequest(
    val actionType: String,
    val symbol: String? = null,
    val volume: Double? = null,
    val stopLoss: Double? = null,
    val takeProfit: Double? = null,
    val positionId: String? = null,
    val comment: String = "Neto Master"
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

    @POST("users/current/accounts/{accountId}/trade")
    suspend fun executeTrade(
        @Header("auth-token") authToken: String,
        @Path("accountId") accountId: String,
        @Body request: Mt5MetaApiTradeRequest
    ): Response<Mt5MetaApiTradeResponse>
}

@JsonClass(generateAdapter = true)
data class CustomMt5OrderRequest(
    val login: String,
    val server: String,
    val symbol: String,
    val side: String,
    val volume: Double,
    val sl: Double,
    val tp: Double,
    val comment: String = "Neto Master"
)

interface CustomMt5BridgeService {
    @GET("mt5/account")
    suspend fun getAccount(
        @Header("Authorization") bearerToken: String,
        @Query("login") login: String,
        @Query("server") server: String
    ): Response<Mt5MetaApiAccountInfo>

    @POST("mt5/order")
    suspend fun placeOrder(
        @Header("Authorization") bearerToken: String,
        @Body request: CustomMt5OrderRequest
    ): Response<Mt5MetaApiTradeResponse>
}
