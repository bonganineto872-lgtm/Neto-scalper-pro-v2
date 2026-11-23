package com.example.data.remote

import com.example.domain.model.AccountSummary
import com.example.domain.model.BotRunState
import com.example.domain.model.MarketQuote
import com.example.domain.model.TradePosition
import com.example.domain.model.TradeSide
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.charset.StandardCharsets
import java.util.Locale

data class LocalApiStatus(
    val running: Boolean = false,
    val baseUrl: String = "http://127.0.0.1:8787",
    val port: Int = 8787,
    val apiKey: String = "",
    val requestCount: Int = 0,
    val errorMessage: String? = null
)

/**
 * Self-hosted lightweight HTTP/1.1 JSON API running directly inside Neto Scalper Pro on loopback (127.0.0.1).
 * Lets the trader query Gold/FX quotes, ZAR flip equity, open positions, and trigger bot actions via REST.
 */
class LocalTradingApiServer(
    private val getSnapshotQuote: () -> MarketQuote,
    private val getAccountSummary: () -> AccountSummary,
    private val getBotState: () -> BotRunState,
    private val getOpenPositions: () -> List<TradePosition>,
    private val onToggleBot: () -> Unit,
    private val onPlaceManualOrder: (TradeSide) -> Unit,
    private val onFlattenAll: () -> Unit,
    private val onStatusChanged: (LocalApiStatus) -> Unit
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var serverSocket: ServerSocket? = null
    private var acceptJob: Job? = null
    private var currentPort: Int = 8787
    private var currentApiKey: String = ""
    private var requestsServed: Int = 0

    @Synchronized
    fun startOrUpdate(enabled: Boolean, port: Int, apiKey: String) {
        if (!enabled) {
            stop()
            currentApiKey = apiKey
            currentPort = port
            onStatusChanged(
                LocalApiStatus(
                    running = false,
                    baseUrl = "http://127.0.0.1:$port",
                    port = port,
                    apiKey = apiKey,
                    requestCount = requestsServed
                )
            )
            return
        }

        if (serverSocket != null && currentPort == port && serverSocket?.isClosed == false) {
            currentApiKey = apiKey
            onStatusChanged(
                LocalApiStatus(
                    running = true,
                    baseUrl = "http://127.0.0.1:$port",
                    port = port,
                    apiKey = apiKey,
                    requestCount = requestsServed
                )
            )
            return
        }

        stop()
        currentPort = port
        currentApiKey = apiKey

        acceptJob = scope.launch {
            try {
                val socket = ServerSocket(port, 32, InetAddress.getByName("127.0.0.1"))
                serverSocket = socket
                onStatusChanged(
                    LocalApiStatus(
                        running = true,
                        baseUrl = "http://127.0.0.1:$port",
                        port = port,
                        apiKey = currentApiKey,
                        requestCount = requestsServed
                    )
                )

                while (isActive && !socket.isClosed) {
                    val client = try {
                        socket.accept()
                    } catch (_: Exception) {
                        break
                    }
                    launch { handleClient(client) }
                }
            } catch (e: Exception) {
                onStatusChanged(
                    LocalApiStatus(
                        running = false,
                        baseUrl = "http://127.0.0.1:$port",
                        port = port,
                        apiKey = currentApiKey,
                        requestCount = requestsServed,
                        errorMessage = e.message ?: "Port $port unavailable"
                    )
                )
            }
        }
    }

    @Synchronized
    fun stop() {
        acceptJob?.cancel()
        acceptJob = null
        try {
            serverSocket?.close()
        } catch (_: Exception) {
        }
        serverSocket = null
    }

    private fun handleClient(client: Socket) {
        client.use { sock ->
            sock.soTimeout = 4_000
            val reader = BufferedReader(InputStreamReader(sock.getInputStream(), StandardCharsets.UTF_8))
            val requestLine = reader.readLine() ?: return
            val parts = requestLine.split(" ")
            if (parts.size < 2) return
            val method = parts[0].uppercase(Locale.US)
            val fullPath = parts[1].substringBefore("?")

            val headers = mutableMapOf<String, String>()
            var contentLength = 0
            while (true) {
                val line = reader.readLine() ?: break
                if (line.isEmpty()) break
                val colon = line.indexOf(':')
                if (colon > 0) {
                    val key = line.substring(0, colon).trim().lowercase(Locale.US)
                    val value = line.substring(colon + 1).trim()
                    headers[key] = value
                    if (key == "content-length") {
                        contentLength = value.toIntOrNull()?.coerceIn(0, 16_384) ?: 0
                    }
                }
            }

            val bodyChars = if (contentLength > 0) {
                val buf = CharArray(contentLength)
                var totalRead = 0
                while (totalRead < contentLength) {
                    val read = reader.read(buf, totalRead, contentLength - totalRead)
                    if (read <= 0) break
                    totalRead += read
                }
                String(buf, 0, totalRead)
            } else {
                ""
            }

            val (statusCode, statusText, jsonPayload) = routeRequest(method, fullPath, headers, bodyChars)
            val bytes = jsonPayload.toByteArray(StandardCharsets.UTF_8)
            val responseHeader = buildString {
                append("HTTP/1.1 $statusCode $statusText\r\n")
                append("Content-Type: application/json; charset=utf-8\r\n")
                append("Content-Length: ${bytes.size}\r\n")
                append("Connection: close\r\n")
                append("\r\n")
            }.toByteArray(StandardCharsets.UTF_8)

            sock.getOutputStream().apply {
                write(responseHeader)
                write(bytes)
                flush()
            }
        }
    }

    private fun isAuthorized(headers: Map<String, String>): Boolean {
        if (currentApiKey.isBlank()) return false
        val apiKeyHeader = headers["x-api-key"]
        val authHeader = headers["authorization"]
        val bearer = authHeader?.removePrefix("Bearer ")?.removePrefix("bearer ")?.trim()
        return apiKeyHeader == currentApiKey || bearer == currentApiKey
    }

    private fun routeRequest(
        method: String,
        path: String,
        headers: Map<String, String>,
        body: String
    ): Triple<Int, String, String> {
        requestsServed++
        onStatusChanged(
            LocalApiStatus(
                running = serverSocket != null && serverSocket?.isClosed == false,
                baseUrl = "http://127.0.0.1:$currentPort",
                port = currentPort,
                apiKey = currentApiKey,
                requestCount = requestsServed
            )
        )

        if (method == "GET" && path == "/api/v1/health") {
            val quote = getSnapshotQuote()
            val payload = JSONObject().apply {
                put("app", "Neto Scalper Pro")
                put("status", "ok")
                put("botState", getBotState().name)
                put("activeInstrument", quote.pair.displayName)
                put("port", currentPort)
            }
            return Triple(200, "OK", payload.toString())
        }

        if (!isAuthorized(headers)) {
            val err = JSONObject().put("error", "Unauthorized. Provide X-API-Key or Authorization: Bearer <key>.")
            return Triple(401, "Unauthorized", err.toString())
        }

        return when {
            method == "GET" && path == "/api/v1/account" -> {
                val acct = getAccountSummary()
                val payload = JSONObject().apply {
                    put("balance", acct.balance)
                    put("equity", acct.equity)
                    put("startingBalance", acct.startingBalance)
                    put("targetFlipBalance", acct.targetFlipBalance)
                    put("flipMultiplier", acct.flipMultiplier)
                    put("unrealizedPnl", acct.unrealizedPnl)
                    put("realizedTodayPnl", acct.realizedTodayPnl)
                    put("currency", acct.currency)
                    put("usdToZarRate", acct.usdToZarRate)
                    put("brokerStatus", acct.brokerStatusText)
                    put("botState", getBotState().name)
                }
                Triple(200, "OK", payload.toString())
            }

            method == "GET" && path == "/api/v1/quote" -> {
                val q = getSnapshotQuote()
                val payload = JSONObject().apply {
                    put("instrument", q.pair.oandaInstrument)
                    put("mt5Symbol", q.pair.mt5Symbol)
                    put("bid", q.bid)
                    put("ask", q.ask)
                    put("mid", q.mid)
                    put("spreadPips", q.spreadPips)
                    put("source", q.source)
                    put("timestamp", q.timestamp)
                }
                Triple(200, "OK", payload.toString())
            }

            method == "GET" && path == "/api/v1/positions" -> {
                val positions = getOpenPositions()
                val arr = JSONArray()
                positions.forEach { p ->
                    arr.put(
                        JSONObject().apply {
                            put("id", p.id)
                            put("pair", p.pair.mt5Symbol)
                            put("side", p.side.name)
                            put("lotSize", p.lotSize)
                            put("entryPrice", p.entryPrice)
                            put("currentPrice", p.currentPrice)
                            put("stopLoss", p.effectiveStopLoss)
                            put("takeProfit", p.takeProfit)
                            put("pipsPnl", p.pipsPnl)
                            put("unrealizedPnl", p.unrealizedPnlAccount)
                            put("layer", p.pyramidLayer)
                        }
                    )
                }
                Triple(200, "OK", JSONObject().put("count", positions.size).put("positions", arr).toString())
            }

            method == "POST" && path == "/api/v1/bot/toggle" -> {
                onToggleBot()
                Triple(200, "OK", JSONObject().put("ok", true).put("action", "bot_toggled").toString())
            }

            method == "POST" && path == "/api/v1/order" -> {
                val sideStr = try {
                    JSONObject(body).optString("side", "BUY").uppercase(Locale.US)
                } catch (_: Exception) {
                    "BUY"
                }
                val side = if (sideStr == "SELL") TradeSide.SELL else TradeSide.BUY
                onPlaceManualOrder(side)
                Triple(200, "OK", JSONObject().put("ok", true).put("side", side.name).toString())
            }

            method == "POST" && path == "/api/v1/flatten" -> {
                onFlattenAll()
                Triple(200, "OK", JSONObject().put("ok", true).put("action", "flattened_all").toString())
            }

            else -> Triple(404, "Not Found", JSONObject().put("error", "Unknown endpoint: $method $path").toString())
        }
    }
}
