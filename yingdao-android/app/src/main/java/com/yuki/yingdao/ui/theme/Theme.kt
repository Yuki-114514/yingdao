package com.yuki.yingdao.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColors = lightColorScheme(
    primary = Emerald,
    onPrimary = WarmWhite,
    primaryContainer = Mint,
    onPrimaryContainer = Ink,
    secondary = DeepBlue,
    onSecondary = WarmWhite,
    secondaryContainer = SkyTint,
    onSecondaryContainer = Ink,
    tertiary = Coral,
    onTertiary = WarmWhite,
    tertiaryContainer = Peach,
    onTertiaryContainer = Ink,
    background = WarmWhite,
    onBackground = Ink,
    surface = Cream,
    onSurface = Ink,
    surfaceVariant = Mist,
    onSurfaceVariant = MutedInk,
    outlineVariant = Mist,
)

@Composable
fun YingDaoTheme(
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = LightColors,
        typography = Typography,
        content = content,
    )
}
