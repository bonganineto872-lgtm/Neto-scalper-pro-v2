package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.data.repository.BotEngineLocator
import com.example.domain.model.BotRunState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class ScalpingBotForegroundService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var collectorJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP_SERVICE) {
            val repo = BotEngineLocator.getRepository(applicationContext)
            repo.disableKeepRunningOnPhone()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        val initialNotification = buildNotification(
            title = "Neto Master • Gold & BTC Signal Engine",
            body = "Monitoring live M1–D1 XAU/USD & BTC/USD signals"
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                initialNotification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(NOTIFICATION_ID, initialNotification)
        }

        observeEngineForNotification()
        return START_STICKY
    }

    private fun observeEngineForNotification() {
        collectorJob?.cancel()
        val repo = BotEngineLocator.getRepository(applicationContext)
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        collectorJob = serviceScope.launch {
            combine(
                repo.marketSnapshot,
                repo.flipPlan,
                repo.botRunState,
                repo.activeSignals
            ) { market, flip, runState, signals ->
                val stateLabel = when (runState) {
                    BotRunState.IDLE -> "Paused"
                    BotRunState.SCANNING -> "Scanning M1–D1"
                    BotRunState.CONFIRMING -> "Confirming Signal..."
                    BotRunState.SIGNAL_ACTIVE -> "${signals.count { it.isLive }} Live Signal"
                }
                val title = "Neto Master • ${market.pair.shortTitle} ${market.timeframe.label} ($stateLabel)"
                val body = "Bid ${market.pair.formatPrice(market.quote.bid)} | Flip ${flip.formatMoney(flip.projectedCapital)} | ${flip.spotFeedSource}"
                title to body
            }.collect { (title, body) ->
                manager.notify(NOTIFICATION_ID, buildNotification(title, body))
            }
        }
    }

    private fun buildNotification(title: String, body: String): Notification {
        val openAppIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, ScalpingBotForegroundService::class.java).apply {
                action = ACTION_STOP_SERVICE
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, SignalNotificationHelper.ENGINE_CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(body)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openAppIntent)
            .addAction(0, "Stop Signal Engine", stopIntent)
            .build()
    }

    private fun createChannel() {
        SignalNotificationHelper.ensureChannels(this)
    }

    override fun onDestroy() {
        collectorJob?.cancel()
        serviceScope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL_ID = "neto_scalper_engine_channel"
        private const val NOTIFICATION_ID = 8787
        const val ACTION_STOP_SERVICE = "com.example.action.STOP_SCALPER_SERVICE"

        fun syncServiceState(context: Context, keepRunning: Boolean) {
            val appCtx = context.applicationContext
            val intent = Intent(appCtx, ScalpingBotForegroundService::class.java)
            if (keepRunning) {
                ContextCompat.startForegroundService(appCtx, intent)
            } else {
                appCtx.stopService(intent)
            }
        }
    }
}
