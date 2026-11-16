package ru.taurlom.tnote.data.sync

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Каналы конфликтных уведомлений (этап 2): всякий раз, когда движок
 * уступает что-то одной стороны другой без диалога, пользователь обязан
 * узнать об этом на любом экране. Три вида: локальная правка уступила
 * серверной ([overwritten]), локальное удаление уступило серверной правке
 * ([restored] — объект вернулся), правка воскресила объект, удалённый на
 * сервере ([resurrected]). Издатель — репозиторий синка (после удачного
 * прогона), подписчик — MainActivity со снеками.
 *
 * Объект, а не Hilt-синглтон: подписчику не нужна подмена в тестах, а
 * издателю двигателя не нужен ещё один параметр конструктора.
 */
object SyncOverwriteBus {

    /** Сколько событий уступило серверу в прогоне; 0 не издаётся. */
    private val _overwritten = MutableSharedFlow<Int>(extraBufferCapacity = 16)
    val overwritten: SharedFlow<Int> = _overwritten.asSharedFlow()

    /** Сколько локальных удалений уступили серверной правке (объекты возвращены). */
    private val _restored = MutableSharedFlow<Int>(extraBufferCapacity = 16)
    val restored: SharedFlow<Int> = _restored.asSharedFlow()

    /** Сколько объектов, удалённых на сервере, воскресила локальная правка. */
    private val _resurrected = MutableSharedFlow<Int>(extraBufferCapacity = 16)
    val resurrected: SharedFlow<Int> = _resurrected.asSharedFlow()

    fun report(count: Int) {
        if (count > 0) _overwritten.tryEmit(count)
    }

    fun reportRestored(count: Int) {
        if (count > 0) _restored.tryEmit(count)
    }

    fun reportResurrected(count: Int) {
        if (count > 0) _resurrected.tryEmit(count)
    }
}
