package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

import androidx.compose.ui.graphics.Color

private val DarkColorScheme =
  darkColorScheme(
    primary = NavyLight,
    secondary = GreySecondary,
    background = GreyBackgroundDark,
    surface = GreySurfaceDark,
    onPrimary = Color(0xFF0F172A),
    onBackground = Color(0xFFF9FAFB),
    onSurface = Color(0xFFF9FAFB)
  )

private val LightColorScheme =
  lightColorScheme(
    primary = NavyDark,
    secondary = GreySecondary,
    background = GreyBackgroundLight,
    surface = GreySurfaceLight,
    onPrimary = Color.White,
    onBackground = Color(0xFF111827),
    onSurface = Color(0xFF111827)
  )

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  // Dynamic color is available on Android 12+
  dynamicColor: Boolean = false,
  content: @Composable () -> Unit,
) {
  val colorScheme =
    when {
      dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
        val context = LocalContext.current
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
      }

      darkTheme -> DarkColorScheme
      else -> LightColorScheme
    }

  MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
