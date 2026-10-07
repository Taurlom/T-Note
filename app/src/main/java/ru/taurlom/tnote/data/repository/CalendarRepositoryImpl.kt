package ru.taurlom.tnote.data.repository

import ru.taurlom.tnote.data.local.CalendarNoteDao
import ru.taurlom.tnote.data.local.ScheduledEventDao
import ru.taurlom.tnote.data.mapper.toDomain
import ru.taurlom.tnote.data.mapper.toEntity
import ru.taurlom.tnote.domain.model.CalendarNote
import ru.taurlom.tnote.domain.repository.CalendarRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class CalendarRepositoryImpl @Inject constructor(
    private val noteDao: CalendarNoteDao,
    private val eventDao: ScheduledEventDao
) : CalendarRepository {

    override fun getNotesByMonthPrefix(monthPrefix: String): Flow<List<CalendarNote>> =
        noteDao.getByMonthPrefix(monthPrefix).map { list -> list.map { it.toDomain() } }

    override suspend fun saveNote(note: CalendarNote) {
        noteDao.insert(note.toEntity())
    }

    override suspend fun deleteDayData(date: String) {
        noteDao.deleteByDate(date)
        eventDao.deleteByDate(date)
    }

    override suspend fun clearCalendar() {
        noteDao.deleteAll()
        eventDao.deleteAll()
    }
}
