package ru.taurlom.tnote.domain.util

/**
 * Сравнение номеров версий вида «MAJOR.MINOR.PATCH» (возможны и более
 * короткие записи: «1.25» эквивалентна «1.25.0»). Компоненты сравниваются
 * числами: «1.10.0» больше «1.9.9», хотя строково «10» < «9». Не-числовой
 * компонент считается нулём — формат номеров контролирует сам проект
 * (semver, см. CHANGELOG.md), защита только от неожиданного ответа каталога.
 *
 * @return положительное число, если [a] новее [b]; отрицательное, если
 *         старее; 0, если версии эквивалентны.
 */
fun compareVersionNames(a: String, b: String): Int {
    val left = a.split('.')
    val right = b.split('.')
    for (index in 0 until maxOf(left.size, right.size)) {
        val l = left.getOrNull(index)?.toLongOrNull() ?: 0L
        val r = right.getOrNull(index)?.toLongOrNull() ?: 0L
        if (l != r) return if (l < r) -1 else 1
    }
    return 0
}
