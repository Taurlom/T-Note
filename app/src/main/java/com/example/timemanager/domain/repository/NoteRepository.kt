package com.example.timemanager.domain.repository

import com.example.timemanager.domain.model.Note
import kotlinx.coroutines.flow.Flow

interface NoteRepository {

    fun getAll(): Flow<List<Note>>
    fun getById(id: Long): Flow<Note?>
    suspend fun getMaxPosition(): Int
    suspend fun insert(note: Note): Long
    suspend fun update(note: Note)
    suspend fun updatePositions(notes: List<Note>)
    suspend fun delete(note: Note)
    suspend fun deleteAll()
}
