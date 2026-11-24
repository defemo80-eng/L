package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val DeiAiDarkColorScheme = darkColorScheme(
    primary = ElectricCyan,
    onPrimary = VoidBlack,
    primaryContainer = ElectricBlue,
    onPrimaryContainer = TextPrimaryDark,
    secondary = NeonPurple,
    onSecondary = TextPrimaryDark,
    secondaryContainer = DeepViolet,
    onSecondaryContainer = TextPrimaryDark,
    tertiary = MagentaGlow,
    onTertiary = TextPrimaryDark,
    background = VoidBlack,
    onBackground = TextPrimaryDark,
    surface = DeepObsidian,
    onSurface = TextPrimaryDark,
    surfaceVariant = MidnightSurface,
    onSurfaceVariant = TextSecondaryDark,
    surfaceContainer = MidnightSurface,
    surfaceContainerHigh = ElevatedObsidian,
    outline = OutlineDark,
    error = CoralAlert,
    onError = Color.White
)

private val DeiAiLightColorScheme = lightColorScheme(
    primary = ElectricBlueLight,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD6E6FF),
    onPrimaryContainer = TextPrimaryLight,
    secondary = NeonPurpleLight,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFF0DBFF),
    onSecondaryContainer = TextPrimaryLight,
    tertiary = MagentaGlow,
    onTertiary = Color.White,
    background = CloudWhite,
    onBackground = TextPrimaryLight,
    surface = FrostSurface,
    onSurface = TextPrimaryLight,
    surfaceVariant = SoftIndigoSurface,
    onSurfaceVariant = TextSecondaryLight,
    surfaceContainer = SoftIndigoSurface,
    surfaceContainerHigh = Color(0xFFDCE5F5),
    outline = OutlineLight,
    error = Color(0xFFD32F2F),
    onError = Color.White
)

val DeiAiShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

@Composable
fun DeiAiTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DeiAiDarkColorScheme else DeiAiLightColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = DeiAiShapes,
        content = content
    )
}
