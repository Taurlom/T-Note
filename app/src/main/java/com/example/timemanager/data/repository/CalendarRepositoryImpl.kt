package com.example.timemanager.data.repository

import com.example.timemanager.data.local.CalendarNoteDao
import com.example.timemanager.data.local.ScheduledEventDao
import com.example.timemanager.data.mapper.toDomain
import com.example.timemanager.data.mapper.toEntity
import com.example.timemanager.domain.model.CalendarNote
import com.example.timemanager.domain.repository.CalendarRepository
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
