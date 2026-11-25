package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.model.BotRunState
import com.example.domain.model.ConfirmationStage
import com.example.domain.model.Timeframe
import com.example.domain.model.TimeframeSignalRow
import com.example.domain.model.TradeSide
import com.example.ui.theme.AmberWarn
import com.example.ui.theme.AmberWarnMuted
import com.example.ui.theme.BearRed
import com.example.ui.theme.BorderSlate
import com.example.ui.theme.BullGreen
import com.example.ui.theme.BullGreenMuted
import com.example.ui.theme.CarbonSurface
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.CyberCyanMuted
import com.example.ui.theme.ElevatedCard
import com.example.ui.theme.MonoTextStyle
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun BotStatusBadge(
    runState: BotRunState,
    modifier: Modifier = Modifier
) {
    val pulseTransition = rememberInfiniteTransition(label = "bot_pulse")
    val pulseAlpha by pulseTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(700),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_alpha"
    )

    val (label, color, bg) = when (runState) {
        BotRunState.IDLE -> Triple("PAUSED", TextSecondary, ElevatedCard)
        BotRunState.SCANNING -> Triple("SCANNING", CyberCyan, CyberCyanMuted)
        BotRunState.CONFIRMING -> Triple("CONFIRMING", AmberWarn, AmberWarnMuted)
        BotRunState.SIGNAL_ACTIVE -> Triple("LIVE SIGNAL", BullGreen, BullGreenMuted)
    }

    val animatedBg by animateColorAsState(bg, label = "badge_bg")
    val isLive = runState != BotRunState.IDLE

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(animatedBg)
            .border(1.dp, color.copy(alpha = 0.5f), RoundedCornerShape(999.dp))
            .padding(horizontal = 10.dp, vertical = 5.dp)
            .testTag("bot_status_badge"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(if (isLive) color.copy(alpha = pulseAlpha) else color)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = label,
            style = MonoTextStyle.copy(
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = color
            )
        )
    }
}

@Composable
fun TimeframePillRow(
    selected: Timeframe,
    matrixRows: List<TimeframeSignalRow>,
    onSelect: (Timeframe) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Timeframe.entries.forEach { tf ->
            val isSelected = tf == selected
            val row = matrixRows.find { it.timeframe == tf }
            val dotColor = when {
                row?.stage == ConfirmationStage.CONFIRMED && row.side == TradeSide.BUY -> BullGreen
                row?.stage == ConfirmationStage.CONFIRMED && row.side == TradeSide.SELL -> BearRed
                row?.stage == ConfirmationStage.CONFIRMING -> AmberWarn
                else -> null
            }

            Row(
                modifier = Modifier
                    .widthIn(min = 58.dp)
                    .minimumInteractiveComponentSize()
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (isSelected) CyberCyanMuted else CarbonSurface)
                    .border(
                        width = 1.dp,
                        color = if (isSelected) CyberCyan else BorderSlate,
                        shape = RoundedCornerShape(10.dp)
                    )
                    .clickable { onSelect(tf) }
                    .padding(horizontal = 12.dp, vertical = 8.dp)
                    .testTag("tf_chip_${tf.name}"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                if (dotColor != null) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(dotColor)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                }
                Text(
                    text = tf.label,
                    style = MonoTextStyle.copy(
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isSelected) CyberCyan else TextPrimary
                    )
                )
            }
        }
    }
}
