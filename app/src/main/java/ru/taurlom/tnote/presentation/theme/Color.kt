package ru.taurlom.tnote.presentation.theme

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
internal val Navy = Color(0xFF445474) // основной акцент: кнопки, чипы, «сегодня»
internal val Sand = Color(0xFFDAD8B5) // светлая поверхность: панели, диалоги, вторичный текст
internal val Gold = Color(0xFFF5CD7A) // вторичный акцент: fab, маркеры, иконки действий
internal val Slate = Color(0xFF3F485A) // фон карточек
internal val Ink = Color(0xFF2C3342) // фон экрана
internal val Snow = Color(0xFFFAFDFD) // основной текст на тёмном
internal val Red = Color(0xFFE74955) // заметки, дни недели
internal val Fog = Color(0xFF7E879B) // прошедшие (неактуальные) отметки
internal val Paper = Color(0xFFFFFFFF) // текст на Navy / белый фон карточек

// ─── Примитивы светлой темы ───────────────────────────────────────
internal val Cloud = Color(0xFFF5F3ED) // мягкий нейтрально-тёплый фон экрана
internal val Amber = Color(0xFFA58A55) // приглушённый песочный акцент
internal val LightInk = Color(0xFF465363) // текст и иконки светлой темы
internal val LightPrimary = Color(0xFF63768B) // сине-серый для основных действий
internal val LightHeader = Color(0xFFE7E9E1) // мягкая шалфейно-серая шапка
internal val LightSurface = Color(0xFFFFFEFB) // тёплая поверхность карточек
internal val LightNav = Color(0xFFFAF9F5) // слегка отличимый фон навигации
internal val LightSelected = Color(0xFFE2E5DA) // ненавязчивое выделение
internal val LightOutline = Color(0xFF92978F) // границы полей и контролов
internal val LightRed = Color(0xFFB7655A) // приглушённый красный маркер
internal val LightFab = Color(0xFFE8D19A) // мягкий песочный фон FAB

// ─── Примитивы светлой темы «Атлас» ────────────────────────────────
internal val AtlasPaper = Color(0xFFF3F0E6) // бумажный фон
internal val AtlasInk = Color(0xFF46534B) // чернильный серо-зелёный текст
internal val AtlasSage = Color(0xFF65796A) // шалфейный основной акцент
internal val AtlasPanel = Color(0xFFE4E8DC) // светлая шалфейная панель
internal val AtlasSurface = Color(0xFFFCFAF3) // кремовая поверхность карточек
internal val AtlasNav = Color(0xFFF9F7F0) // светлая нижняя навигация
internal val AtlasSelected = Color(0xFFDDE3D6) // мягкое выделение
internal val AtlasOutline = Color(0xFF91988D) // спокойный контур
internal val AtlasOchre = Color(0xFF987C49) // приглушённая охра
internal val AtlasTerracotta = Color(0xFFA96F5F) // мягкая терракота
internal val AtlasFab = Color(0xFFE4C98E) // светлый охристый FAB

// ─── Примитивы темы «Океан» ───────────────────────────────────────
internal val Abyss = Color(0xFF0C2A33) // глубокий сине-зелёный фон
internal val Reef = Color(0xFF143E49) // поверхности
internal val Teal = Color(0xFF1F7A85) // основной акцент
internal val Coral = Color(0xFFFF8A6B) // вторичный акцент
internal val Aqua = Color(0xFF8FD8D0) // вторичный текст
internal val Foam = Color(0xFFEAF7F4) // основной текст

// ─── Примитивы темы «Лес» ─────────────────────────────────────────
internal val Loam = Color(0xFF1E2117) // тёмный почвенно-зелёный фон
internal val Bark = Color(0xFF2F3323) // поверхности, карточки
internal val Pine = Color(0xFF55713F) // основной акцент (кнопки)
internal val PineDark = Color(0xFF2E3D22) // текст на светлых поверхностях
internal val Moss = Color(0xFFB7C4A0) // вторичный текст
internal val Cream = Color(0xFFEFEAD6) // основной текст
internal val Clay = Color(0xFFB0793F) // коричнево-охристый акцент
internal val Walnut = Color(0xFF332c21)
internal val Lichen = Color(0xFFE8E0C8) // светлые панели и диалоги

// ─── Примитивы темы «Осень» ───────────────────────────────────────
internal val Espresso = Color(0xFF2B1D14) // кофейно-коричневый фон экрана
internal val Umber = Color(0xFF3E2B1F) // поверхности, выходные
internal val Rust = Color(0xFFB85C38) // выжженный оранжевый: кнопки, акценты
internal val Ochre = Color(0xFFD9A441) // охра: вторичный акцент, fab, маркеры
internal val Wheat = Color(0xFFE8CFA9) // вторичный текст на тёмном
internal val Parchment = Color(0xFFEFDFC2) // тёплая бумага: диалоги
internal val Cocoa = Color(0xFF4A2E1B) // текст на «бумаге»
