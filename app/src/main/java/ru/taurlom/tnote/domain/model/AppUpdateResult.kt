package ru.taurlom.tnote.domain.model

/** Результат проверки обновлений в RuStore. */
sealed interface AppUpdateResult {
    /** В магазине опубликована версия новее установленной. */
    data class Available(val versionName: String) : AppUpdateResult

    /** Установленная версия — последняя из опубликованных (или даже новее: сборка с GitHub). */
    data object UpToDate : AppUpdateResult

    /**
     * Проверить не удалось: нет сети, каталог не отвечает или разметка
     * страницы изменилась и версию из неё не вытащить.
     */
    data object Unreachable : AppUpdateResult
}
