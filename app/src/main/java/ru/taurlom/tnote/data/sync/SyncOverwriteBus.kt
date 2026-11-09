package ru.taurlom.tnote.data.sync

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Канал перезаписей (этап 2): когда у сервера оказывается более свежая
 * правка, локальная версия уступает без диалога — но «молча» её терять
 * нельзя, пользователь должен узнать о случившемся на любом экране.
 * Издатель — репозиторий синка (после удачного прогона), подписчик —
 * MainActivity со снеком.
 *
 * Объект, а не Hilt-синглтон: подписчику не нужна подмена в тестах, а
 * издателю двигателя не нужен ещё один параметр конструктора.
 */
object SyncOverwriteBus {

    /** Сколько событий уступило серверу в прогоне; 0 не издаётся. */
    private val _overwritten = MutableSharedFlow<Int>(extraBufferCapacity = 16)
    val overwritten: SharedFlow<Int> = _overwritten.asSharedFlow()

    fun report(count: Int) {
        if (count > 0) _overwritten.tryEmit(count)
    }
}
