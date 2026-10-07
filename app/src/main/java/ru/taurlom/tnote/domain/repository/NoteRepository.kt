package ru.taurlom.tnote.domain.repository

import kotlinx.coroutines.flow.Flow
import ru.taurlom.tnote.domain.model.Note

interface NoteRepository {

    fun getAll(): Flow<List<Note>>
    fun getById(id: Long): Flow<Note?>
    suspend fun getByIdOnce(id: Long): Note?
    suspend fun getMaxPosition(): Int
    suspend fun insert(note: Note): Long
    suspend fun update(note: Note)
    suspend fun updatePositions(notes: List<Note>)
    suspend fun delete(note: Note)
    suspend fun deleteAll()
}
