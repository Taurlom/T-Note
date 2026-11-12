package ru.taurlom.tnote.domain.repository

import kotlinx.coroutines.flow.Flow
import ru.taurlom.tnote.domain.model.AppUpdateResult

/**
 * Проверка обновлений. Последняя опубликованная версия читается из
 * каталога RuStore: магазин — единственный источник правды, обновляться
 * нужно на то, что там выложено, а не на сборки с GitHub (могут быть
 * впереди магазина и не проходить модерацию).
 */
interface AppUpdateRepository {
    /**
     * Проверка наличия обновления. [force] отключает суточное окно
     * (кнопка «Проверить обновления» в настройках); без [force] частые
     * вызовы переиспользуют результат последней успешной проверки, не
     * обращаясь к сети повторно.
     */
    suspend fun check(force: Boolean): AppUpdateResult

    /** Ссылка на страницу приложения в каталоге RuStore (кнопки «Обновить»). */
    fun storePageUrl(): String

    /** Версия, которую пользователь попросил больше не показывать (или пусто). */
    val skippedVersion: Flow<String>

    /** Запомнить версию как «пропущенную»: диалог за неё не показывается. */
    suspend fun skip(versionName: String)
}
