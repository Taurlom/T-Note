package com.example.timemanager.domain.repository

import com.example.timemanager.domain.model.CalendarNote
import com.example.timemanager.domain.model.CalendarTask
import kotlinx.coroutines.flow.Flow

interface CalendarRepository {

    fun getNotesByMonthPrefix(monthPrefix: String): Flow<List<CalendarNote>>
    fun getTasksByMonthPrefix(monthPrefix: String): Flow<List<CalendarTask>>
    fun getNoteByDate(date: String): Flow<CalendarNote?>
    fun getTasksByDate(date: String): Flow<List<CalendarTask>>

    suspend fun saveNote(note: CalendarNote)
    suspend fun deleteNote(date: String)
    suspend fun deleteDayData(date: String)

    suspend fun addTask(task: CalendarTask): Long
    suspend fun updateTask(task: CalendarTask)
    suspend fun deleteTask(task: CalendarTask)
    suspend fun updateTasks(tasks: List<CalendarTask>)

    suspend fun clearCalendar()
}
