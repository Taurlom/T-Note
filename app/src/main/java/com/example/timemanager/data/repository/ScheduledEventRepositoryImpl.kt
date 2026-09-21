package com.example.timemanager.data.repository

import com.example.timemanager.data.local.ScheduledEventDao
import com.example.timemanager.data.mapper.toDomain
import com.example.timemanager.data.mapper.toEntity
import com.example.timemanager.domain.model.ScheduledEvent
import com.example.timemanager.domain.model.ScheduledEventType
import com.example.timemanager.domain.repository.ScheduledEventRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class ScheduledEventRepositoryImpl @Inject constructor(
    private val eventDao: ScheduledEventDao
) : ScheduledEventRepository {

    override fun getByMonthPrefix(monthPrefix: String): Flow<List<ScheduledEvent>> =
        eventDao.getByMonthPrefix(monthPrefix).map { list -> list.map { it.toDomain() } }

    override fun getByType(type: ScheduledEventType): Flow<List<ScheduledEvent>> =
        eventDao.getByType(type.name).map { list -> list.map { it.toDomain() } }

    override suspend fun getById(id: Long): ScheduledEvent? =
        eventDao.getByIdOnce(id)?.toDomain()

    override suspend fun add(event: ScheduledEvent): Long =
        eventDao.insert(event.toEntity())

    override suspend fun update(event: ScheduledEvent) {
        var entity = event.toEntity()
        // День рождения и повторяющееся событие хранятся с исходной якорной
        // датой: в UI они показываются как вхождения, и год/день вхждения
        // менять нельзя.
        if (event.type == ScheduledEventType.BIRTHDAY ||
            event.type == ScheduledEventType.REPEATING
        ) {
            eventDao.getByIdOnce(event.id)?.let { existing ->
                entity = entity.copy(eventDate = existing.eventDate)
            }
        }
        eventDao.update(entity)
    }

    override suspend fun delete(event: ScheduledEvent) {
        eventDao.delete(event.toEntity())
    }

    override suspend fun deleteByDate(date: String) {
        eventDao.deleteByDate(date)
    }

    override suspend fun clearAll() {
        eventDao.deleteAll()
    }
}
