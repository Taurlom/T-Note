package ru.taurlom.tnote.domain.repository

import ru.taurlom.tnote.domain.model.ScheduledEvent
import ru.taurlom.tnote.domain.model.ScheduledEventType
import kotlinx.coroutines.flow.Flow

interface ScheduledEventRepository {

    fun getByMonthPrefix(monthPrefix: String): Flow<List<ScheduledEvent>>
    fun getByType(type: ScheduledEventType): Flow<List<ScheduledEvent>>

    suspend fun getById(id: Long): ScheduledEvent?

    /** @return id вставленной записи. */
    suspend fun add(event: ScheduledEvent): Long
    suspend fun update(event: ScheduledEvent)
    suspend fun delete(event: ScheduledEvent)
    suspend fun deleteByDate(date: String)
    suspend fun clearAll()
}
