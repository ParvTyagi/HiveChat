package com.example.hivechat.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Hive Color Palette - Honey & Bee Inspired
val BrownBorder = Color(0xFF8D6E63) // Light brown for borders

val HoneyYellow = Color(0xFFFFC107)
val HoneyGold = Color(0xFFFFB300)
val AmberOrange = Color(0xFFFF8F00)
val HoneyLight = Color(0xFFFFF8E1)
val BeeBlack = Color(0xFF212121)
val BeeGray = Color(0xFF424242)
val BeeStripe = Color(0xFF616161)
val HiveWhite = Color(0xFFFFFBF5)

// Message bubbles
val MyMessageBubble = Color(0xFFFFC107)
val TheirMessageBubble = Color(0xFFF5F5F5)
val MyMessageText = Color(0xFF212121)
val TheirMessageText = Color(0xFF212121)

private val LightColorScheme = lightColorScheme(
    primary = HoneyYellow,
    onPrimary = BeeBlack,
    primaryContainer = HoneyLight,
    onPrimaryContainer = BeeBlack,

    secondary = AmberOrange,
    onSecondary = Color.White,
    secondaryContainer = HoneyGold,
    onSecondaryContainer = BeeBlack,

    background = HiveWhite,
    onBackground = BeeBlack,

    surface = Color.White,
    onSurface = BeeBlack,

    surfaceVariant = HoneyLight,
    onSurfaceVariant = BeeGray,

    outline = HoneyGold,
    outlineVariant = HoneyLight,

    error = Color(0xFFD32F2F),
    onError = Color.White
)

private val DarkColorScheme = darkColorScheme(
    primary = HoneyYellow,
    onPrimary = BeeBlack,
    primaryContainer = HoneyGold,
    onPrimaryContainer = BeeBlack,

    secondary = AmberOrange,
    onSecondary = BeeBlack,
    secondaryContainer = HoneyGold,
    onSecondaryContainer = BeeBlack,

    background = BeeBlack,
    onBackground = HoneyLight,

    surface = BeeGray,
    onSurface = HoneyLight,

    surfaceVariant = BeeStripe,
    onSurfaceVariant = HoneyLight,

    outline = HoneyGold,
    outlineVariant = BeeStripe,

    error = Color(0xFFEF5350),
    onError = BeeBlack
)

@Composable
fun HiveChatTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = HiveTypography,
        content = content
    )
}