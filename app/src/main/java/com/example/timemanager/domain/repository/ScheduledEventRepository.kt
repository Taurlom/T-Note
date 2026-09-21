package com.example.timemanager.domain.repository

import com.example.timemanager.domain.model.ScheduledEvent
import com.example.timemanager.domain.model.ScheduledEventType
import kotlinx.coroutines.flow.Flow

interface ScheduledEventRepository {

    fun getByMonthPrefix(monthPrefix: String): Flow<List<ScheduledEvent>>
    fun getByType(type: ScheduledEventType): Flow<List<ScheduledEvent>>

    suspend fun getById(id: Long): ScheduledEvent?
    suspend fun getAllWithAlarm(): List<ScheduledEvent>

    /** @return id вставленной записи. */
    suspend fun add(event: ScheduledEvent): Long
    suspend fun update(event: ScheduledEvent)
    suspend fun delete(event: ScheduledEvent)
    suspend fun deleteByDate(date: String)
    suspend fun clearAll()
}
