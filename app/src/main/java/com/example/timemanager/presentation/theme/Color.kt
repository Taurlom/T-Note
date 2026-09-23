package com.example.timemanager.presentation.theme

import androidx.compose.ui.graphics.Color

/**
 * Цветовые примитивы — единственное место в проекте, где цвет задан hex-значением.
 *
 * Всё остальное строится поверх них двумя слоями ролей:
 *  - `MaterialTheme.colorScheme` (Theme.kt) — стандартные роли Material 3;
 *  - `AppTheme.colors` (AppColors.kt) — компонентные роли приложения.
 *
 * Экраны и компоненты обязаны брать цвет из роли, а не из примитива:
 * тогда новая тема = новый маппинг примитивов в роли, без правок в UI-коде.
 *
 * Синхронизация с XML: `res/values/colors.xml#splash_background` повторяет
 * [Ink] (в XML Kotlin не виден), правьте вместе.
 */

// ─── Примитивы палитры ────────────────────────────────────────────
internal val Navy = Color(0xFF445474)   // основной акцент: кнопки, чипы, «сегодня»
internal val Sand = Color(0xFFDAD8B5)   // светлая поверхность: панели, диалоги, вторичный текст
internal val Gold = Color(0xFFF5CD7A)   // вторичный акцент: fab, маркеры, иконки действий
internal val Slate = Color(0xFF3F485A)  // фон карточек
internal val Ink = Color(0xFF2C3342)    // фон экрана
internal val Snow = Color(0xFFFAFDFD)   // основной текст на тёмном
internal val Red = Color(0xFFE74955)    // заметки, дни недели
internal val Fog = Color(0xFF7E879B)    // прошедшие (неактуальные) отметки
internal val Paper = Color(0xFFFFFFFF)  // текст на Navy / белый фон карточек

// ─── Примитивы светлой темы ───────────────────────────────────────
internal val Cloud = Color(0xFFF4F1E4)  // тёплый светлый фон экрана
internal val Amber = Color(0xFFB98A2A)  // читаемое «золото» на белом

// ─── Примитивы темы «Океан» ───────────────────────────────────────
internal val Abyss = Color(0xFF0C2A33)  // глубокий сине-зелёный фон
internal val Reef = Color(0xFF143E49)   // поверхности
internal val Teal = Color(0xFF1F7A85)   // основной акцент
internal val Coral = Color(0xFFFF8A6B)  // вторичный акцент
internal val Aqua = Color(0xFF8FD8D0)   // вторичный текст
internal val Foam = Color(0xFFEAF7F4)   // основной текст

// ─── Примитивы темы «Лес» ─────────────────────────────────────────
internal val Loam = Color(0xFF1E2117)   // тёмный почвенно-зелёный фон
internal val Bark = Color(0xFF2F3323)   // поверхности, карточки
internal val Pine = Color(0xFF55713F)   // основной акцент (кнопки)
internal val PineDark = Color(0xFF2E3D22) // текст на светлых поверхностях
internal val Moss = Color(0xFFB7C4A0)   // вторичный текст
internal val Cream = Color(0xFFEFEAD6)  // основной текст
internal val Clay = Color(0xFFB0793F)   // коричнево-охристый акцент
internal val Walnut = Color(0xFF332c21)
internal val Lichen = Color(0xFFE8E0C8) // светлые панели и диалоги

// ─── Примитивы темы «Осень» ───────────────────────────────────────
internal val Espresso = Color(0xFF2B1D14)  // кофейно-коричневый фон экрана
internal val Umber = Color(0xFF3E2B1F)     // поверхности, выходные
internal val Rust = Color(0xFFB85C38)      // выжженный оранжевый: кнопки, акценты
internal val Ochre = Color(0xFFD9A441)     // охра: вторичный акцент, fab, маркеры
internal val Wheat = Color(0xFFE8CFA9)     // вторичный текст на тёмном
internal val Parchment = Color(0xFFEFDFC2) // тёплая бумага: диалоги
internal val Cocoa = Color(0xFF4A2E1B)     // текст на «бумаге»
