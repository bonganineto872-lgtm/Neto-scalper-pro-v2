package com.example.service

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.domain.model.AlertType
import com.example.domain.model.BotAlertEvent
import com.example.domain.model.TradeSignal
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger

object SignalNotificationHelper {

    const val ENGINE_CHANNEL_ID = "neto_scalper_engine_channel"
    const val ENTRY_ALERT_CHANNEL_ID = "neto_master_entry_alerts_channel"
    private val alertNotificationIdSeq = AtomicInteger(20000)

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return

        val engineChannel = NotificationChannel(
            ENGINE_CHANNEL_ID,
            "Neto Master Background Monitor",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Keeps live Gold & Bitcoin M1–D1 confirmation active outside the app."
            setShowBadge(false)
        }

        val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        val audioAttrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_NOTIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        val entryAlertChannel = NotificationChannel(
            ENTRY_ALERT_CHANNEL_ID,
            "Confirmed Entry & TP Alerts",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "High-priority alerts when a confirmed Gold or Bitcoin entry or TP target triggers."
            enableVibration(true)
            vibrationPattern = longArrayOf(0L, 250L, 120L, 250L)
            enableLights(true)
            setSound(soundUri, audioAttrs)
            lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
        }

        manager.createNotificationChannel(engineChannel)
        manager.createNotificationChannel(entryAlertChannel)
    }

    fun hasNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun postSignalAlertNotification(
        context: Context,
        alert: BotAlertEvent,
        signal: TradeSignal? = null
    ) {
        if (!hasNotificationPermission(context)) return
        ensureChannels(context)
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return

        val openAppIntent = PendingIntent.getActivity(
            context,
            alert.id.hashCode(),
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val expandedBody = if (signal != null && alert.type == AlertType.NEW_SIGNAL) {
            buildString {
                appendLine(
                    String.format(
                        Locale.US,
                        "Entry: %s | SL: %s (-%.1fp)",
                        signal.pair.formatPrice(signal.entryPrice),
                        signal.pair.formatPrice(signal.stopLoss),
                        signal.slPips
                    )
                )
                appendLine(
                    String.format(
                        Locale.US,
                        "TP1: %s (+%.1fp) • TP2: %s (+%.1fp)",
                        signal.pair.formatPrice(signal.takeProfit1),
                        signal.tp1Pips,
                        signal.pair.formatPrice(signal.takeProfit2),
                        signal.tp2Pips
                    )
                )
                append(
                    String.format(
                        Locale.US,
                        "TP3: %s (+%.1fp) • %s (%d%% Conf)",
                        signal.pair.formatPrice(signal.takeProfit3),
                        signal.tp3Pips,
                        signal.strategyName,
                        signal.confidencePercent
                    )
                )
            }
        } else {
            alert.message
        }

        val notifId = alertNotificationIdSeq.incrementAndGet()
        val notification = NotificationCompat.Builder(context, ENTRY_ALERT_CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(alert.title)
            .setContentText(alert.message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(expandedBody))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setContentIntent(openAppIntent)
            .build()

        manager.notify(notifId, notification)
    }
}
