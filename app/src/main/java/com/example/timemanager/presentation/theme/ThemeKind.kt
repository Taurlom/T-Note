package com.example.timemanager.presentation.theme

/** Доступные цветовые темы приложения. Выбор хранится в настройках. */
enum class ThemeKind(val displayName: String) {
    DARK("Тёмная"),
    LIGHT("Светлая"),
    OCEAN("Океан"),
    FOREST("Лес"),
    AUTUMN("Осень");

    companion object {
        fun fromName(name: String): ThemeKind = entries.find { it.name == name } ?: DARK
    }
}
