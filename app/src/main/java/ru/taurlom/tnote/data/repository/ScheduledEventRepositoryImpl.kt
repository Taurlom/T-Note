package ru.taurlom.tnote.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import ru.taurlom.tnote.data.local.ScheduledEventDao
import ru.taurlom.tnote.data.mapper.toDomain
import ru.taurlom.tnote.data.mapper.toEntity
import ru.taurlom.tnote.domain.model.ScheduledEvent
import ru.taurlom.tnote.domain.model.ScheduledEventType
import ru.taurlom.tnote.domain.repository.ScheduledEventRepository
import javax.inject.Inject

class ScheduledEventRepositoryImpl @Inject constructor(private val eventDao: ScheduledEventDao) : ScheduledEventRepository {

    override fun getByMonthPrefix(monthPrefix: String): Flow<List<ScheduledEvent>> =
        eventDao.getByMonthPrefix(monthPrefix).map { list -> list.map { it.toDomain() } }

    override fun getByType(type: ScheduledEventType): Flow<List<ScheduledEvent>> =
        eventDao.getByType(type.name).map { list -> list.map { it.toDomain() } }

    override suspend fun getById(id: Long): ScheduledEvent? = eventDao.getByIdOnce(id)?.toDomain()

    override suspend fun add(event: ScheduledEvent): Long = eventDao.insert(event.toEntity())

    override suspend fun update(event: ScheduledEvent) {
        var entity = event.toEntity()
        // В редакторе нет поля даты: в снапшоте лежит день, в котором открыт
        // диалог. Для дней рождения и повторяющихся событий UI показывает
        // развёрнутые вхождения, поэтому записи всегда возвращаем сохранённую
        // якорную дату — иначе конвертация «день рождения → обычное» молча
        // затирала год рождения годом просматриваемого вхождения.
        // Для «Выходного» правило обратное: он помечает конкретный открытый
        // день, поэтому берётся дата из снапшота.
        if (event.type != ScheduledEventType.WEEKEND) {
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
