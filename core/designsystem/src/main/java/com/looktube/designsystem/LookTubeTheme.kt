package com.looktube.designsystem
import androidx.compose.foundation.isSystemInDarkTheme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LookTubeGold = Color(0xFFF6C445)
private val LookTubeGoldDim = Color(0xFF8F6C18)
private val LookTubeSlate = Color(0xFF14171B)
private val LookTubeSlateElevated = Color(0xFF1B1F24)
private val LookTubeSlateSoft = Color(0xFF262C33)
private val LookTubeInk = Color(0xFFE7EBF0)

private val LightColors = lightColorScheme(
    primary = Color(0xFF7A5A00),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFE08A),
    onPrimaryContainer = Color(0xFF251A00),
    secondary = Color(0xFF5B5F6A),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE1E3EA),
    onSecondaryContainer = Color(0xFF181B22),
    tertiary = Color(0xFF6B5B2A),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFF5E3A6),
    onTertiaryContainer = Color(0xFF241A00),
    background = Color(0xFFF4F1E8),
    onBackground = Color(0xFF171A1F),
    surface = Color(0xFFFFFBF3),
    onSurface = Color(0xFF171A1F),
    surfaceVariant = Color(0xFFE5DED0),
    onSurfaceVariant = Color(0xFF454137),
    outline = Color(0xFF77715F),
    outlineVariant = Color(0xFFC8C1B4),
)

private val DarkColors = darkColorScheme(
    primary = LookTubeGold,
    onPrimary = Color(0xFF2D2100),
    primaryContainer = LookTubeGoldDim,
    onPrimaryContainer = Color(0xFFFFE8A6),
    secondary = Color(0xFFBCC4D2),
    onSecondary = Color(0xFF232830),
    secondaryContainer = Color(0xFF303843),
    onSecondaryContainer = Color(0xFFDCE4F3),
    tertiary = Color(0xFFE1C87F),
    onTertiary = Color(0xFF2C2202),
    tertiaryContainer = Color(0xFF4B3A0F),
    onTertiaryContainer = Color(0xFFFFE9B0),
    background = LookTubeSlate,
    onBackground = LookTubeInk,
    surface = LookTubeSlateElevated,
    onSurface = LookTubeInk,
    surfaceVariant = LookTubeSlateSoft,
    onSurfaceVariant = Color(0xFFC5CBD5),
    outline = Color(0xFF8C94A0),
    outlineVariant = Color(0xFF3A424D),
)

@Composable
fun LookTubeTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        content = content,
    )
}
