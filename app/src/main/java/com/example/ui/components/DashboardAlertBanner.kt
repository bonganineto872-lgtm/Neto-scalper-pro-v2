package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.model.AlertType
import com.example.domain.model.BotAlertEvent
import com.example.domain.model.TradeSide
import com.example.ui.theme.AmberWarn
import com.example.ui.theme.AmberWarnMuted
import com.example.ui.theme.BearRed
import com.example.ui.theme.BearRedMuted
import com.example.ui.theme.BullGreen
import com.example.ui.theme.BullGreenMuted
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.CyberCyanMuted
import com.example.ui.theme.MonoTextStyle
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import java.util.Locale

@Composable
fun DashboardAlertBanner(
    alert: BotAlertEvent?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    LaunchedEffect(alert?.id) {
        if (alert != null) {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }

    AnimatedVisibility(
        visible = alert != null,
        enter = expandVertically(tween(220)) + fadeIn(tween(220)),
        exit = shrinkVertically(tween(200)) + fadeOut(tween(180)),
        modifier = modifier
    ) {
        if (alert != null) {
            val pulseTransition = rememberInfiniteTransition(label = "alert_pulse")
            val pulseAlpha by pulseTransition.animateFloat(
                initialValue = 0.45f,
                targetValue = 1.0f,
                animationSpec = infiniteRepeatable(
                    animation = tween(550),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "alert_pulse_alpha"
            )

            val (accent, bg, icon) = when (alert.type) {
                AlertType.NEW_SIGNAL -> {
                    if (alert.side == TradeSide.SELL) {
                        Triple(BearRed, BearRedMuted.copy(alpha = 0.94f), Icons.AutoMirrored.Filled.TrendingDown)
                    } else {
                        Triple(BullGreen, BullGreenMuted.copy(alpha = 0.94f), Icons.AutoMirrored.Filled.TrendingUp)
                    }
                }
                AlertType.TP1_HIT, AlertType.TP2_HIT -> Triple(
                    CyberCyan,
                    CyberCyanMuted.copy(alpha = 0.92f),
                    Icons.Filled.CheckCircle
                )
                AlertType.TP3_HIT -> Triple(
                    BullGreen,
                    BullGreenMuted.copy(alpha = 0.95f),
                    Icons.Filled.Bolt
                )
                AlertType.FLIP_TARGET_REACHED -> Triple(
                    AmberWarn,
                    AmberWarnMuted.copy(alpha = 0.95f),
                    Icons.Filled.EmojiEvents
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(ObsidianBg)
                    .background(bg)
                    .border(1.5.dp, accent.copy(alpha = pulseAlpha), RoundedCornerShape(12.dp))
                    .padding(start = 12.dp, top = 8.dp, bottom = 8.dp, end = 4.dp)
                    .testTag("dashboard_visual_alert_banner"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(accent.copy(alpha = 0.18f))
                            .border(1.dp, accent.copy(alpha = 0.65f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = alert.title,
                            tint = accent,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = alert.title,
                                style = MonoTextStyle.copy(
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = accent
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            alert.pips?.let { p ->
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = String.format(Locale.US, "+%.1fp", p),
                                    style = MonoTextStyle.copy(
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = accent
                                    )
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = alert.message,
                            style = MaterialTheme.typography.bodySmall,
                            color = TextPrimary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.testTag("dismiss_alert_banner_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "Dismiss alert",
                        tint = TextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}
