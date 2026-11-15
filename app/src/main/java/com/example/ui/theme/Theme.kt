package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val ShroomAutoColorScheme =
  darkColorScheme(
    primary = MarioRed,
    onPrimary = InkLight,
    primaryContainer = MarioRedDark,
    onPrimaryContainer = InkLight,
    secondary = CoinGold,
    onSecondary = AsphaltDark,
    secondaryContainer = AsphaltCard,
    onSecondaryContainer = CoinGold,
    tertiary = PipeGreen,
    onTertiary = InkLight,
    tertiaryContainer = PipeGreenDark,
    onTertiaryContainer = InkLight,
    background = AsphaltDark,
    onBackground = InkLight,
    surface = AsphaltSurface,
    onSurface = InkLight,
    surfaceVariant = AsphaltCard,
    onSurfaceVariant = InkMuted,
    error = DangerCrimson,
    onError = InkLight,
  )

private val ShroomAutoShapes =
  Shapes(
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
  )

@Composable
fun ShroomAutoTheme(content: @Composable () -> Unit) {
  MaterialTheme(
    colorScheme = ShroomAutoColorScheme,
    typography = AppTypography,
    shapes = ShroomAutoShapes,
    content = content,
  )
}
