package com.athar.ludo3d.presentation.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

// Added by Athar
private val LudoDarkColorScheme = darkColorScheme(
    primary = PrimaryBlue,
    secondary = SpecialPurple,
    tertiary = RewardOrange,
    background = NavyBackground,
    surface = NavySurface,
    surfaceVariant = NavyCard,
    onPrimary = TextPrimary,
    onBackground = TextPrimary,
    onSurface = TextPrimary,
    error = TokenRed
)

@Composable
fun Ludo3DTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LudoDarkColorScheme,
        typography = LudoTypography,
        content = content
    )
}
