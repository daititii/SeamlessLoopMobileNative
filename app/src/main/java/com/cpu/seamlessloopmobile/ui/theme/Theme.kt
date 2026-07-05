package com.cpu.seamlessloopmobile.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = SeamlessLoopColors.PurpleAccent,
    secondary = SeamlessLoopColors.TealAccent,
    background = SeamlessLoopColors.DarkBgGradientStart,
    surface = SeamlessLoopColors.ComponentDarkBg,
    primaryContainer = Color(0xFF2A2638),
    onPrimaryContainer = SeamlessLoopColors.White,
    secondaryContainer = Color(0xFF4B4161),
    onSecondaryContainer = SeamlessLoopColors.White,
    surfaceVariant = Color(0xFF373747),
    onSurfaceVariant = Color(0xFFE1DDED),
    outlineVariant = Color(0xFF57576A),
    onPrimary = SeamlessLoopColors.White,
    onBackground = SeamlessLoopColors.White,
    onSurface = SeamlessLoopColors.White
)

private val LightColorScheme = lightColorScheme(
    primary = SeamlessLoopColors.PurpleDark,
    secondary = SeamlessLoopColors.TealAccent,
    background = SeamlessLoopColors.White,
    surface = SeamlessLoopColors.White,
    primaryContainer = Color(0xFFF1EAFE),
    onPrimaryContainer = Color(0xFF2B2238),
    secondaryContainer = Color(0xFFD9F3EF),
    onSecondaryContainer = Color(0xFF123D39),
    surfaceVariant = Color(0xFFF4EFFB),
    onSurfaceVariant = Color(0xFF655B70),
    outlineVariant = Color(0xFFD9D0E4),
    onPrimary = SeamlessLoopColors.White,
    onBackground = SeamlessLoopColors.DarkBgGradientEnd,
    onSurface = SeamlessLoopColors.DarkBgGradientEnd
)

/**
 * 莱芙为 CPU 大人量身定制的主题系统包装喵！(๑•̀ㅂ•́)و✧
 */
@Composable
fun SeamlessLoopTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val view = LocalView.current
    
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            // 设置状态栏与导航栏颜色喵，保持整体视觉高度统一！
            window.statusBarColor = colorScheme.background.toArgb()
            window.navigationBarColor = colorScheme.background.toArgb()
            
            val windowInsetsController = WindowCompat.getInsetsController(window, view)
            // 依据深浅色主题，灵动调整系统状态栏和导航栏文字的高亮模式喵 ^ㅅ^
            windowInsetsController.isAppearanceLightStatusBars = !darkTheme
            windowInsetsController.isAppearanceLightNavigationBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
