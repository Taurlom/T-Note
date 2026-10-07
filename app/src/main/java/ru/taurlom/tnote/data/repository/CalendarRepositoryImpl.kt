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
}
