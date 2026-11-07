package ru.taurlom.tnote.data.repository

import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import ru.taurlom.tnote.data.local.AppDatabase
import ru.taurlom.tnote.data.local.CalendarNoteDao
import ru.taurlom.tnote.data.local.ScheduledEventDao
import ru.taurlom.tnote.data.mapper.toDomain
import ru.taurlom.tnote.data.mapper.toEntity
import ru.taurlom.tnote.domain.model.CalendarNote
import ru.taurlom.tnote.domain.repository.CalendarRepository
import ru.taurlom.tnote.domain.repository.PastCalendarCleanup
import java.time.LocalDate
import javax.inject.Inject

class CalendarRepositoryImpl @Inject constructor(
    private val database: AppDatabase,
    private val noteDao: CalendarNoteDao,
    private val eventDao: ScheduledEventDao,
) : CalendarRepository {

    override fun getNotesByMonthPrefix(monthPrefix: String): Flow<List<CalendarNote>> =
        noteDao.getByMonthPrefix(monthPrefix).map { list -> list.map { it.toDomain() } }

    override suspend fun saveNote(note: CalendarNote) {
        noteDao.insert(note.toEntity())
    }

    /**
     * Два DAO — одна транзакция: день не должен остаться очищенным
     * наполовину (заметка стёрта, события остались — или наоборот).
     */
    override suspend fun deleteDayData(date: String) {
        database.withTransaction {
            noteDao.deleteByDate(date)
            eventDao.deleteByDate(date)
        }
    }

    override suspend fun clearCalendar() {
        database.withTransaction {
            noteDao.deleteAll()
            eventDao.deleteAll()
        }
    }

    /**
     * Чистка прошедшего за одну транзакцию: отрывные заметки уходят все
     * (строка «yyyy-MM-dd» сравнивается лексикографически — безопасно),
     * события — только без будущих вхождений: правило сидит в модели
     * ([ScheduledEvent.isOverBefore]), чтобы дни рождения и живые
     * повторения в список кандидатов не попадали.
     */
    override suspend fun deletePast(): PastCalendarCleanup {
        val cutoffDate = LocalDate.now()
        val cutoff = cutoffDate.toString()
        return database.withTransaction {
            val notes = noteDao.deleteBefore(cutoff)
            val removable = eventDao
                .getByDateBefore(cutoff)
                .map { it.toDomain() }
                .filter { it.isOverBefore(cutoffDate) }
            eventDao.delete(removable.map { it.toEntity() })
            PastCalendarCleanup(events = removable.size, notes = notes)
        }
    }
}
