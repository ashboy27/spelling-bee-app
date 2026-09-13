package com.spellinggate.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val LightColors = lightColorScheme(
    primary = Eucalyptus,
    onPrimary = PureWhite,
    primaryContainer = SoftSage,
    onPrimaryContainer = DeepLeaf,
    background = WarmPaper,
    onBackground = Ink,
    surface = WarmSurface,
    onSurface = Ink,
    surfaceVariant = SoftSage,
    error = ClayError,
)

private val DarkColors = darkColorScheme(
    primary = EucalyptusLight,
    onPrimary = DeepLeaf,
    primaryContainer = DeepLeaf,
    onPrimaryContainer = EucalyptusLight,
    background = NightLeaf,
    onBackground = NightText,
    surface = NightSurface,
    onSurface = NightText,
    surfaceVariant = DeepLeaf,
)

private val GateShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(28.dp),
)

@Composable
fun SpellingGateTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        shapes = GateShapes,
        content = content,
    )
}
