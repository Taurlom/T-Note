package com.example.timemanager.presentation.theme

import androidx.compose.ui.graphics.Color

/**
 * Основная цветовая схема приложения.
 *
 * Роли Material 3:
 * - Primary / OnPrimary: основной акцент (#445474 на белом)
 * - PrimaryContainer / OnPrimaryContainer: мягкие контейнеры под основной цвет
 * - Secondary / OnSecondary: вторичный акцент (#F5CD7A на #445474)
 * - Tertiary / OnTertiary: бежевый (#DAD8B5 на #445474) для AppBar/Dialog
 * - Background / OnBackground: фон экрана
 * - Surface / OnSurface / OnSurfaceVariant: фон карточек и текст на них
 * - Outline: границы и разделители
 */

// Primary
val Primary = Color(0xFF445474)
val OnPrimary = Color(0xFFFFFFFF)
val PrimaryContainer = Color(0xFF3F485A)
val OnPrimaryContainer = Color(0xFFDAD8B5)

// Secondary
val Secondary = Color(0xFFF5CD7A)
val OnSecondary = Color(0xFF445474)
val SecondaryContainer = Color(0xFFF5CD7A)
val OnSecondaryContainer = Color(0xFF445474)

// Accent
val Accent = Color(0xFFE74955)

// Tertiary
val Tertiary = Color(0xFFDAD8B5)
val OnTertiary = Color(0xFF445474)

// Background / Surface
val Background = Color(0xFF2C3342)
val OnBackground = Color(0xFFFAFDFD)
val Surface = Color(0xFF3F485A)
val OnSurface = Color(0xFFFAFDFD)
val OnSurfaceVariant = Color(0xFFDAD8B5)
val Outline = Color(0xFFDAD8B5)

// Семантические алиасы для конкретных компонентов
val AppBarBackground = Tertiary
val AddButtonBackground = Secondary
val DialogBackground = Tertiary
val DialogButtonBackground = Primary

val LounchBackground = Background
