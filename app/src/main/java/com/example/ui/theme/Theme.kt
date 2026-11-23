package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val TerminalDarkColorScheme = darkColorScheme(
    primary = CyberCyan,
    onPrimary = ObsidianBg,
    primaryContainer = CyberCyanMuted,
    onPrimaryContainer = CyberCyan,
    secondary = BullGreen,
    onSecondary = ObsidianBg,
    secondaryContainer = BullGreenMuted,
    onSecondaryContainer = BullGreen,
    tertiary = AmberWarn,
    onTertiary = ObsidianBg,
    tertiaryContainer = AmberWarnMuted,
    onTertiaryContainer = AmberWarn,
    error = BearRed,
    onError = ObsidianBg,
    errorContainer = BearRedMuted,
    onErrorContainer = BearRed,
    background = ObsidianBg,
    onBackground = TextPrimary,
    surface = CarbonSurface,
    onSurface = TextPrimary,
    surfaceVariant = ElevatedCard,
    onSurfaceVariant = TextSecondary,
    surfaceContainerLowest = ObsidianBg,
    surfaceContainerLow = CarbonSurface,
    surfaceContainer = ElevatedCard,
    surfaceContainerHigh = ElevatedCardHigh,
    outline = BorderSlate,
    outlineVariant = GridLineColor
)

val TerminalShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(18.dp),
    extraLarge = RoundedCornerShape(24.dp)
)

@Composable
fun PipPulseTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = TerminalDarkColorScheme,
        typography = AppTypography,
        shapes = TerminalShapes,
        content = content
    )
}
