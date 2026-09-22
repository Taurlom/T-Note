package com.example.timemanager.presentation.theme

import android.app.Activity
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

private val AppShapes = Shapes(
    small = RoundedCornerShape(3.dp),
    medium = RoundedCornerShape(3.dp),
    large = RoundedCornerShape(3.dp)
)

/**
 * Оболочка темы: по выбранному [ThemeKind] берёт маппинг ролей из Themes.kt
 * и кладёт его в MaterialTheme и LocalAppColors.
 */
@Composable
fun TNoteTheme(
    fontFamily: FontFamily = PtSansFontFamily,
    theme: ThemeKind = ThemeKind.DARK,
    content: @Composable () -> Unit
) {
    val spec = themeSpecOf(theme)

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = spec.colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars =
                spec.lightStatusBar
        }
    }

    // Typography пересобирается только при смене шрифта: иначе на каждую
    // перерисовку темы создаётся новый объект и инвалидируется весь текст в app.
    val typography = remember(fontFamily) { appTypography(fontFamily) }

    CompositionLocalProvider(LocalAppColors provides spec.colors) {
        MaterialTheme(
            colorScheme = spec.colorScheme,
            typography = typography,
            shapes = AppShapes,
            content = content
        )
    }
}
