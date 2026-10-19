package com.dalilyemen.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColors = lightColorScheme(
    primary = BrandPrimary,
    onPrimary = Color_White,
    primaryContainer = BrandPrimaryContainer,
    onPrimaryContainer = BrandOnPrimaryContainer,
    secondary = BrandSecondary,
    tertiary = BrandAccent,
    background = BrandBackground,
    onBackground = BrandOnPrimaryContainer,
    surface = BrandSurface,
    onSurface = BrandOnPrimaryContainer,
    outline = BrandOutline,
)

private val DarkColors = darkColorScheme(
    primary = DarkPrimary,
    onPrimary = DarkOnPrimary,
    primaryContainer = DarkPrimaryContainer,
    onPrimaryContainer = DarkOnPrimaryContainer,
    secondary = BrandSecondary,
    tertiary = BrandAccent,
    background = DarkBackground,
    onBackground = Color_White,
    surface = DarkSurface,
    onSurface = Color_White,
)

@Composable
fun DalilYemenTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) DarkColors else LightColors
    MaterialTheme(
        colorScheme = colors,
        typography = DalilTypography,
        content = content,
    )
}
