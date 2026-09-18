package com.example.timemanager.data.repository

import com.example.timemanager.data.local.CalendarNoteDao
import com.example.timemanager.data.local.CalendarTaskDao
import com.example.timemanager.data.mapper.toDomain
import com.example.timemanager.data.mapper.toEntity
import com.example.timemanager.domain.model.CalendarNote
import com.example.timemanager.domain.model.CalendarTask
import com.example.timemanager.domain.repository.CalendarRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class CalendarRepositoryImpl @Inject constructor(
    private val noteDao: CalendarNoteDao,
    private val taskDao: CalendarTaskDao
) : CalendarRepository {

    override fun getNotesByMonthPrefix(monthPrefix: String): Flow<List<CalendarNote>> =
        noteDao.getByMonthPrefix(monthPrefix).map { list -> list.map { it.toDomain() } }

    override fun getTasksByMonthPrefix(monthPrefix: String): Flow<List<CalendarTask>> =
        taskDao.getByMonthPrefix(monthPrefix).map { list -> list.map { it.toDomain() } }

    override fun getNoteByDate(date: String): Flow<CalendarNote?> =
        noteDao.getByDate(date).map { list -> list.firstOrNull()?.toDomain() }

    override fun getTasksByDate(date: String): Flow<List<CalendarTask>> =
        taskDao.getByDate(date).map { list -> list.map { it.toDomain() } }

    override suspend fun saveNote(note: CalendarNote) {
        noteDao.insert(note.toEntity())
    }

    override suspend fun deleteNote(date: String) {
        noteDao.getByDateOnce(date)?.let { noteDao.delete(it) }
    }

    override suspend fun deleteDayData(date: String) {
        noteDao.deleteByDate(date)
        taskDao.deleteByDate(date)
    }

    override suspend fun addTask(task: CalendarTask): Long =
        taskDao.insert(task.toEntity())

    override suspend fun updateTask(task: CalendarTask) {
        taskDao.update(task.toEntity())
    }

    override suspend fun deleteTask(task: CalendarTask) {
        taskDao.delete(task.toEntity())
    }

    override suspend fun updateTasks(tasks: List<CalendarTask>) {
        tasks.forEach { taskDao.update(it.toEntity()) }
    }

    override suspend fun clearCalendar() {
        noteDao.deleteAll()
        taskDao.deleteAll()
    }
}
