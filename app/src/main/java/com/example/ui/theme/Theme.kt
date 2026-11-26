package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val StudioDarkColorScheme = darkColorScheme(
    primary = CoralPrimary,
    onPrimary = CoralOnPrimary,
    primaryContainer = CoralContainer,
    onPrimaryContainer = CoralOnContainer,
    secondary = VioletSecondary,
    onSecondary = VioletOnSecondary,
    secondaryContainer = VioletContainer,
    onSecondaryContainer = VioletOnContainer,
    tertiary = MintTertiary,
    onTertiary = MintOnTertiary,
    tertiaryContainer = MintContainer,
    onTertiaryContainer = MintOnContainer,
    background = StudioVoid,
    onBackground = TextPrimaryDark,
    surface = StudioSurface,
    onSurface = TextPrimaryDark,
    surfaceVariant = StudioSurfaceVariant,
    onSurfaceVariant = TextSecondaryDark,
    surfaceContainerHigh = StudioSurfaceElevated,
    outline = StudioOutline,
    error = ErrorRed,
    errorContainer = ErrorContainerDark,
    onErrorContainer = ErrorOnContainerDark
)

private val StudioLightColorScheme = lightColorScheme(
    primary = CoralPrimary,
    onPrimary = LightSurface,
    primaryContainer = CoralOnContainer,
    onPrimaryContainer = CoralContainer,
    secondary = VioletSecondary,
    onSecondary = LightSurface,
    secondaryContainer = VioletOnContainer,
    onSecondaryContainer = VioletContainer,
    tertiary = MintTertiary,
    onTertiary = MintOnTertiary,
    tertiaryContainer = MintOnContainer,
    onTertiaryContainer = MintContainer,
    background = LightBackground,
    onBackground = LightTextPrimary,
    surface = LightSurface,
    onSurface = LightTextPrimary,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightTextSecondary,
    outline = LightOutline
)

val StudioShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun VidsStudioTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) StudioDarkColorScheme else StudioLightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = StudioShapes,
        content = content
    )
}
