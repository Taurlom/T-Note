package ru.taurlom.tnote.domain.repository

import kotlinx.coroutines.flow.Flow
import ru.taurlom.tnote.domain.model.ScheduledEvent
import ru.taurlom.tnote.domain.model.ScheduledEventType

interface ScheduledEventRepository {

    fun getByMonthPrefix(monthPrefix: String): Flow<List<ScheduledEvent>>
    fun getByType(type: ScheduledEventType): Flow<List<ScheduledEvent>>

    suspend fun getById(id: Long): ScheduledEvent?

    /** Максимальная позиция среди событий дня — для вставки «в конец». */
    suspend fun getMaxPosition(date: String): Int

    /** @return id вставленной записи. */
    suspend fun add(event: ScheduledEvent): Long
    suspend fun update(event: ScheduledEvent)
    suspend fun delete(event: ScheduledEvent)
}
