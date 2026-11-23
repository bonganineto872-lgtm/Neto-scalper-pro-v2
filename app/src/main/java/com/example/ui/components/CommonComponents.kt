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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.domain.model.BotRunState
import com.example.domain.model.BrokerEnvironment
import com.example.domain.model.FxPair
import com.example.domain.model.Timeframe
import com.example.ui.theme.AmberWarn
import com.example.ui.theme.AmberWarnMuted
import com.example.ui.theme.BearRed
import com.example.ui.theme.BearRedMuted
import com.example.ui.theme.BorderSlate
import com.example.ui.theme.BullGreen
import com.example.ui.theme.BullGreenMuted
import com.example.ui.theme.CarbonSurface
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.CyberCyanMuted
import com.example.ui.theme.ElevatedCard
import com.example.ui.theme.MonoTextStyle
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun EnvironmentSafetyBanner(
    environment: BrokerEnvironment,
    spotFeedSource: String,
    mt5BrokerLabel: String = "",
    modifier: Modifier = Modifier
) {
    val (bg, border, fg, text, icon) = when (environment) {
        BrokerEnvironment.PAPER_SIMULATION -> BannerStyle(
            bg = AmberWarnMuted.copy(alpha = 0.72f),
            border = AmberWarn.copy(alpha = 0.55f),
            fg = AmberWarn,
            text = stringResource(R.string.paper_simulation_banner),
            icon = Icons.Filled.Shield
        )
        BrokerEnvironment.MT5_DEMO -> BannerStyle(
            bg = CyberCyanMuted.copy(alpha = 0.72f),
            border = CyberCyan.copy(alpha = 0.55f),
            fg = CyberCyan,
            text = if (mt5BrokerLabel.isNotBlank()) "MT5 DEMO — $mt5BrokerLabel" else stringResource(R.string.mt5_demo_banner),
            icon = Icons.Filled.VerifiedUser
        )
        BrokerEnvironment.MT5_LIVE -> BannerStyle(
            bg = BearRedMuted.copy(alpha = 0.85f),
            border = BearRed.copy(alpha = 0.75f),
            fg = BearRed,
            text = if (mt5BrokerLabel.isNotBlank()) "MT5 LIVE — $mt5BrokerLabel" else stringResource(R.string.mt5_live_banner),
            icon = Icons.Filled.WarningAmber
        )
        BrokerEnvironment.OANDA_PRACTICE -> BannerStyle(
            bg = CyberCyanMuted.copy(alpha = 0.72f),
            border = CyberCyan.copy(alpha = 0.55f),
            fg = CyberCyan,
            text = stringResource(R.string.oanda_practice_banner),
            icon = Icons.Filled.VerifiedUser
        )
        BrokerEnvironment.OANDA_LIVE -> BannerStyle(
            bg = BearRedMuted.copy(alpha = 0.85f),
            border = BearRed.copy(alpha = 0.75f),
            fg = BearRed,
            text = stringResource(R.string.oanda_live_banner),
            icon = Icons.Filled.WarningAmber
        )
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(bg)
            .border(1.dp, border, RoundedCornerShape(10.dp))
            .padding(horizontal = 12.dp, vertical = 7.dp)
            .testTag("environment_safety_banner"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = text,
                tint = fg,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = text,
                style = MonoTextStyle.copy(
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = fg,
                    letterSpacing = 0.4.sp
                ),
                maxLines = 1
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = spotFeedSource.take(18),
            style = MonoTextStyle.copy(fontSize = 9.sp, color = TextSecondary),
            maxLines = 1
        )
    }
}

private data class BannerStyle(
    val bg: Color,
    val border: Color,
    val fg: Color,
    val text: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector
)

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
            animation = tween(750),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_alpha"
    )

    val (label, color, bg) = when (runState) {
        BotRunState.IDLE -> Triple("IDLE", TextSecondary, ElevatedCard)
        BotRunState.SCANNING -> Triple("SCANNING", CyberCyan, CyberCyanMuted)
        BotRunState.IN_POSITION -> Triple("IN TRADE", BullGreen, BullGreenMuted)
        BotRunState.CIRCUIT_BREAKER_HALTED -> Triple("RISK HALT", BearRed, BearRedMuted)
    }

    val animatedBg by animateColorAsState(bg, label = "badge_bg")
    val isLive = runState == BotRunState.SCANNING || runState == BotRunState.IN_POSITION

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(animatedBg)
            .border(1.dp, color.copy(alpha = 0.45f), RoundedCornerShape(999.dp))
            .padding(horizontal = 10.dp, vertical = 5.dp),
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
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = color
            )
        )
    }
}

@Composable
fun PairSelectorStrip(
    selectedPair: FxPair,
    onSelectPair: (FxPair) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        FxPair.entries.forEach { pair ->
            val selected = pair == selectedPair
            Box(
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .heightIn(min = 40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (selected) CyberCyanMuted else CarbonSurface)
                    .border(
                        width = 1.dp,
                        color = if (selected) CyberCyan else BorderSlate,
                        shape = RoundedCornerShape(10.dp)
                    )
                    .clickable { onSelectPair(pair) }
                    .padding(horizontal = 12.dp, vertical = 8.dp)
                    .testTag("pair_chip_${pair.name}"),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (pair == FxPair.XAU_USD) "GOLD • XAU/USD" else pair.displayName,
                    style = MonoTextStyle.copy(
                        fontSize = 12.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                        color = if (selected) CyberCyan else TextSecondary
                    )
                )
            }
        }
    }
}

@Composable
fun TimeframePillRow(
    selected: Timeframe,
    onSelect: (Timeframe) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Timeframe.entries.forEach { tf ->
            val isSelected = tf == selected
            Box(
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (isSelected) TextPrimary else ElevatedCard)
                    .border(1.dp, if (isSelected) TextPrimary else BorderSlate, RoundedCornerShape(8.dp))
                    .clickable { onSelect(tf) }
                    .padding(horizontal = 10.dp, vertical = 6.dp)
                    .testTag("tf_chip_${tf.name}"),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = tf.label,
                    style = MonoTextStyle.copy(
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isSelected) CarbonSurface else TextSecondary
                    )
                )
            }
        }
    }
}
