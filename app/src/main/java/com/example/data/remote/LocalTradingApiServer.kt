package com.example.data.remote

import com.example.domain.model.AccountFlipPlan
import com.example.domain.model.BotRunState
import com.example.domain.model.MarketQuote
import com.example.domain.model.TradeSignal
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

class LocalTradingApiServer(
    private val getSnapshotQuote: () -> MarketQuote,
    private val getFlipPlan: () -> AccountFlipPlan,
    private val getBotState: () -> BotRunState,
    private val getActiveSignals: () -> List<TradeSignal>,
    private val onToggleBot: () -> Unit,
    private val onForceScan: () -> Unit,
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

            if (contentLength > 0) {
                val buf = CharArray(contentLength)
                var totalRead = 0
                while (totalRead < contentLength) {
                    val read = reader.read(buf, totalRead, contentLength - totalRead)
                    if (read <= 0) break
                    totalRead += read
                }
            }

            val (statusCode, statusText, jsonPayload) = routeRequest(method, fullPath, headers)
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
        headers: Map<String, String>
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
                put("app", "Neto Master")
                put("mode", "LIVE_SIGNALS_ONLY")
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
            method == "GET" && path == "/api/v1/flip" -> {
                val plan = getFlipPlan()
                val payload = JSONObject().apply {
                    put("currency", plan.currency.code)
                    put("startingCapital", plan.startingCapital)
                    put("projectedCapital", plan.projectedCapital)
                    put("targetCapital", plan.targetCapital)
                    put("multiplier", plan.multiplier)
                    put("cleanSheetWinRate", plan.cleanSheetWinRate)
                    put("netPipsCaptured", plan.netPipsCaptured)
                    put("botState", getBotState().name)
                }
                Triple(200, "OK", payload.toString())
            }

            method == "GET" && path == "/api/v1/quote" -> {
                val q = getSnapshotQuote()
                val payload = JSONObject().apply {
                    put("instrument", q.pair.displayName)
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

            method == "GET" && path == "/api/v1/signals" -> {
                val signals = getActiveSignals()
                val arr = JSONArray()
                signals.forEach { s ->
                    arr.put(
                        JSONObject().apply {
                            put("id", s.id)
                            put("pair", s.pair.mt5Symbol)
                            put("timeframe", s.timeframe.label)
                            put("side", s.side.name)
                            put("entryPrice", s.entryPrice)
                            put("stopLoss", s.stopLoss)
                            put("tp1", s.takeProfit1)
                            put("tp2", s.takeProfit2)
                            put("tp3", s.takeProfit3)
                            put("tp1Pips", s.tp1Pips)
                            put("tp2Pips", s.tp2Pips)
                            put("tp3Pips", s.tp3Pips)
                            put("remainingPipsToTp1", s.remainingPipsToTp1)
                            put("maxDrawdownPips", s.maxDrawdownPips)
                            put("confidencePercent", s.confidencePercent)
                            put("status", s.status.name)
                        }
                    )
                }
                Triple(200, "OK", JSONObject().put("count", signals.size).put("signals", arr).toString())
            }

            method == "POST" && path == "/api/v1/bot/toggle" -> {
                onToggleBot()
                Triple(200, "OK", JSONObject().put("ok", true).put("action", "bot_toggled").toString())
            }

            method == "POST" && path == "/api/v1/scan" -> {
                onForceScan()
                Triple(200, "OK", JSONObject().put("ok", true).put("action", "scan_triggered").toString())
            }

            else -> Triple(404, "Not Found", JSONObject().put("error", "Unknown endpoint: $method $path").toString())
        }
    }
}
